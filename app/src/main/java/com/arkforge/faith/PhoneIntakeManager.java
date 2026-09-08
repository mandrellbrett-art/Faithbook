package com.arkforge.faith;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneIntakeManager {
    public static final String MODE_INDEX="INDEX_ONLY";
    public static final String MODE_COPY_ALL="COPY_ALL_ACCESSIBLE";
    private static final int MAX_ITEMS=250000,MAX_DEPTH=80,BATCH=500;
    private static final long MAX_SINGLE=4L*1024*1024*1024,MIN_FREE=2L*1024*1024*1024;
    private static final Pattern VERSION=Pattern.compile("(?i)(?:^|[^a-z0-9])((?:r\\d{1,4})|(?:v\\d+(?:\\.\\d+){0,3}))(?=$|[^a-z0-9])");

    private PhoneIntakeManager(){}

    public static JSONObject scanTree(Context context,R10Database db,Uri treeUri,String requested)throws Exception{
        String mode=MODE_COPY_ALL.equalsIgnoreCase(requested)?MODE_COPY_ALL:MODE_INDEX;
        String rootId=DocumentsContract.getTreeDocumentId(treeUri);
        String label=displayName(context,DocumentsContract.buildDocumentUriUsingTree(treeUri,rootId));
        if(label==null||label.isBlank())label="Phone storage";
        String runId=db.beginPhoneIntakeRun(treeUri.toString(),label,mode);
        State st=new State(context,db,treeUri,runId,mode);
        if(MODE_COPY_ALL.equals(mode)){
            st.vault=new File(context.getFilesDir(),"projects/phone-vault-"+safe(label)+"-"+shortId());
            if(!st.vault.mkdirs()&&!st.vault.isDirectory())throw new IllegalStateException("Could not create Phone Vault");
            st.projectId=db.createProject("Phone Vault — "+label,"Managed copies from a user-authorized Android folder tree. Originals remain untouched.","phone-vault",st.vault.getAbsolutePath(),treeUri.toString());
        }
        String status="COMPLETE",detail="Android scoped storage respected.";
        try{walk(st,rootId,"",0);st.flush();if(st.failed>0||st.unavailable>0)status="COMPLETE_WITH_REVIEW";}
        catch(Exception e){status="PARTIAL";detail=e.getClass().getSimpleName()+": "+e.getMessage();st.failed++;try{st.flush();}catch(Exception ignored){}}
        if(!st.projectId.isEmpty())db.updateProjectStatus(st.projectId,status.equals("COMPLETE")?"imported":"review");
        db.finishPhoneIntakeRun(runId,st.files,st.dirs,st.copied,st.linked,st.failed+st.unavailable,st.bytes,status,detail);
        JSONObject o=db.phoneIntakeSummary();o.put("run_id",runId);o.put("mode",mode);o.put("run_status",status);
        o.put("files_seen_this_run",st.files);o.put("copied_this_run",st.copied);o.put("linked_this_run",st.linked);o.put("unavailable_this_run",st.unavailable);o.put("bytes_copied_this_run",st.bytes);return o;
    }

    private static void walk(State st,String parent,String prefix,int depth)throws Exception{
        if(depth>MAX_DEPTH)throw new IllegalArgumentException("Folder nesting exceeded "+MAX_DEPTH);
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(st.tree,parent);
        String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        Cursor c=null;
        try{
            c=st.context.getContentResolver().query(children,cols,null,null,null);
            if(c==null){st.unavailable(prefix,"Provider did not expose folder");return;}
            int idc=c.getColumnIndexOrThrow(cols[0]),nc=c.getColumnIndexOrThrow(cols[1]),mc=c.getColumnIndexOrThrow(cols[2]),sc=c.getColumnIndex(cols[3]);
            while(c.moveToNext()){
                if(++st.items>MAX_ITEMS)throw new IllegalArgumentException("Phone intake reached "+MAX_ITEMS+" items");
                String id=c.getString(idc),name=c.getString(nc);if(name==null||name.isBlank())name="unnamed";
                String mime=c.getString(mc);long size=sc>=0&&!c.isNull(sc)?c.getLong(sc):-1;
                String rel=prefix.isEmpty()?name:prefix+"/"+name;Uri uri=DocumentsContract.buildDocumentUriUsingTree(st.tree,id);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){
                    st.dirs++;st.add(row(uri,rel,name,mime,-1,"directory",family(rel),version(rel),"","","LINKED_INDEXED",""));
                    try{walk(st,id,rel,depth+1);}catch(Exception e){st.unavailable(rel,e.getClass().getSimpleName()+": "+e.getMessage());}
                }else{
                    st.files++;String cat=category(name,mime,rel),fam=family(rel),ver=version(rel);
                    if(MODE_COPY_ALL.equals(st.mode)){
                        Copy co=copy(st,uri,rel,size);
                        st.add(row(uri,rel,name,mime,size,cat,fam,ver,co.sha,st.projectId,co.status,co.detail));
                        if("COPIED_MANAGED".equals(co.status)){st.copied++;st.bytes+=co.bytes;st.db.addManagedFile(st.projectId,co.rel,co.name,co.bytes,co.sha,mime==null?"application/octet-stream":mime,cat,uri.toString());}
                        else st.failed++;
                    }else{st.linked++;st.add(row(uri,rel,name,mime,size,cat,fam,ver,"","","LINKED_INDEXED",""));}
                }
            }
        }catch(SecurityException e){st.unavailable(prefix.isEmpty()?"(selected root)":prefix,"Android denied access");}
        finally{if(c!=null)c.close();}
    }

    private static Copy copy(State st,Uri uri,String rel,long size){
        try{
            if(size>MAX_SINGLE)return new Copy("TOO_LARGE_FOR_MANAGED_COPY","",0,"","","Over 4 GiB; indexed only");
            long free=new StatFs(st.context.getFilesDir().getAbsolutePath()).getAvailableBytes();
            if(size>0&&free-size<MIN_FREE)return new Copy("INSUFFICIENT_STORAGE","",0,"","","Copy skipped to preserve free storage");
            String managed=safeRel(rel);File root=st.vault.getCanonicalFile(),target=new File(root,managed).getCanonicalFile();
            if(!target.getPath().startsWith(root.getPath()+File.separator))throw new IllegalArgumentException("Unsafe path");
            if(target.exists())managed=managed+"_"+shortId();
            target=new File(root,managed).getCanonicalFile();File parent=target.getParentFile();if(parent!=null)parent.mkdirs();
            MessageDigest d=MessageDigest.getInstance("SHA-256");long bytes=0;
            try(InputStream raw=st.context.getContentResolver().openInputStream(uri);InputStream in=new BufferedInputStream(require(raw));OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                byte[] b=new byte[131072];int r;while((r=in.read(b))!=-1){bytes+=r;if(bytes>MAX_SINGLE)throw new IllegalArgumentException("Over 4 GiB");out.write(b,0,r);d.update(b,0,r);}
            }catch(Exception e){target.delete();throw e;}
            return new Copy("COPIED_MANAGED",hex(d.digest()),bytes,managed,target.getName(),"");
        }catch(Exception e){return new Copy("COPY_FAILED","",0,"","",e.getClass().getSimpleName()+": "+e.getMessage());}
    }

    private static JSONObject row(Uri uri,String rel,String name,String mime,long size,String cat,String fam,String ver,String sha,String project,String status,String detail)throws Exception{
        JSONObject o=new JSONObject();o.put("source_uri",uri.toString());o.put("relative_path",rel);o.put("name",name);o.put("mime",mime==null?"":mime);o.put("size",size);o.put("category",cat);o.put("family",fam);o.put("version_token",ver);o.put("sha256",sha);o.put("managed_project_id",project);o.put("intake_status",status);o.put("detail",detail);return o;
    }

    private static String family(String p){
        String x=(p==null?"":p).toLowerCase(Locale.US).replace('_','-');
        if(x.contains("kernel-computer")||x.contains("/kernel"))return "kernel";if(x.contains("constructor"))return "constructor";
        if(x.contains("garden-immortal")||x.contains("first-light")||x.contains("grovenaut"))return "garden-immortals";
        if(x.contains("cantus")||x.contains("ademic")||x.contains("adamic"))return "cantus";if(x.contains("civis"))return "civis";
        if(x.contains("chrono-compass")||x.contains("pocket-chrono"))return "chrono-compass";
        if(x.contains("homebase")||x.contains("home-base")||x.contains("thunderforge")||x.contains("farwater")||x.contains("fieldworks"))return "thunderforge-homebase";
        if(x.contains("heritagefaith")||x.contains("faithbook")||x.contains("bible"))return "heritagefaith";
        if(x.contains("garden"))return "garden";if(x.contains("patent"))return "patent";if(x.contains("games")||x.contains("rom"))return "games";return "unplaced";
    }

    private static String category(String n,String mime,String path){
        String x=(n==null?"":n).toLowerCase(Locale.US),m=(mime==null?"":mime).toLowerCase(Locale.US),p=(path==null?"":path).toLowerCase(Locale.US);String e=ext(x);
        if(p.contains("backup")||p.contains("rollback"))return "backup";if(e.equals("zip")||e.equals("7z")||e.equals("rar")||e.equals("tar")||e.equals("gz"))return "archive";
        if(e.equals("db")||e.equals("sqlite")||e.equals("sqlite3"))return "database";if(e.equals("py")||e.equals("js")||e.equals("java")||e.equals("kt")||e.equals("sh")||e.equals("json")||e.equals("html")||e.equals("css"))return "source-code";
        if(e.equals("pdf")||e.equals("epub")||e.equals("docx")||e.equals("txt")||e.equals("md"))return "document";
        if(m.startsWith("image/"))return "media-image";if(m.startsWith("video/"))return "media-video";if(m.startsWith("audio/"))return "media-audio";
        return "other";
    }

    private static String version(String p){Matcher m=VERSION.matcher(p==null?"":p);String v="";while(m.find())v=m.group(1).toUpperCase(Locale.US);return v;}
    private static String ext(String n){int i=n.lastIndexOf('.');return i<0?"":n.substring(i+1);}
    private static String displayName(Context c,Uri u){try(Cursor x=c.getContentResolver().query(u,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){return x!=null&&x.moveToFirst()?x.getString(0):null;}catch(Exception e){return null;}}
    private static String safeRel(String r){StringBuilder b=new StringBuilder();for(String p:(r==null?"item":r).replace('\\','/').split("/")){if(p.isEmpty()||p.equals(".")||p.equals(".."))continue;if(b.length()>0)b.append('/');b.append(safe(p));}return b.length()==0?"item":b.toString();}
    private static String safe(String s){String x=(s==null?"item":s).replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]","_").trim();return x.isEmpty()?"item":x;}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,8);}
    private static InputStream require(InputStream i){if(i==null)throw new IllegalArgumentException("Could not read file");return i;}
    private static String hex(byte[] h){StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.US,"%02x",x));return b.toString();}

    private static class Copy{final String status,sha,rel,name,detail;final long bytes;Copy(String s,String h,long b,String r,String n,String d){status=s;sha=h;bytes=b;rel=r;name=n;detail=d;}}
    private static class State{
        final Context context;final R10Database db;final Uri tree;final String runId,mode;JSONArray batch=new JSONArray();long items,files,dirs,copied,linked,failed,unavailable,bytes;File vault;String projectId="";
        State(Context c,R10Database d,Uri t,String id,String m){context=c;db=d;tree=t;runId=id;mode=m;}
        void add(JSONObject o)throws Exception{batch.put(o);if(batch.length()>=BATCH)flush();}
        void flush()throws Exception{if(batch.length()>0){db.addPhoneIntakeBatch(runId,batch);batch=new JSONArray();}}
        void unavailable(String p,String d)throws Exception{unavailable++;add(row(Uri.EMPTY,p,p,"",-1,"directory",family(p),version(p),"","","NOT_ACCESSIBLE_WITH_ANDROID",d));}
    }
}
