package com.arkforge.faith;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ContinuityManager {
    private static final long MAX_MANIFEST = 32L * 1024L * 1024L;
    private static final long MAX_PAYLOAD_FILE = 512L * 1024L * 1024L;
    private static final long MAX_IMPORTED_PAYLOAD = 4L * 1024L * 1024L * 1024L;
    private static final int MAX_ITEMS = 500000;
    private ContinuityManager() {}

    public static JSONObject importBundle(Context context, R10Database db, Uri uri) throws Exception {
        String display=ImportManager.displayName(context,uri);
        byte[] manifestBytes=findManifest(context,uri);
        String manifestSha=sha(manifestBytes);
        JSONObject manifest=new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
        if(!manifest.optString("schema").startsWith("thunderforge.r10.migration-bundle")) throw new IllegalArgumentException("This ZIP is not a Thunderforge R10 continuity migration bundle.");
        JSONArray items=manifest.optJSONArray("items"); if(items==null)throw new IllegalArgumentException("Migration manifest has no items array.");
        if(items.length()>MAX_ITEMS)throw new IllegalArgumentException("Migration manifest exceeds "+MAX_ITEMS+" items.");
        String runId=db.beginMigrationRun(manifest.optString("source_label",display),uri.toString(),manifestSha,items.length());
        File root=new File(context.getFilesDir(),"legacy/"+runId); if(!root.mkdirs()&&!root.isDirectory())throw new IllegalStateException("Could not create legacy migration storage");
        Map<String,JSONObject> byEntry=new HashMap<>();
        for(int i=0;i<items.length();i++){JSONObject x=items.getJSONObject(i);String pe=normalize(x.optString("payload_entry"));if(!pe.isEmpty())byEntry.put(pe,x);}
        long importedBytes=0;int imported=0,mismatch=0;
        try(InputStream raw=context.getContentResolver().openInputStream(uri);ZipInputStream zin=new ZipInputStream(new BufferedInputStream(require(raw)))){
            ZipEntry e;int entries=0;
            while((e=zin.getNextEntry())!=null){entries++;if(entries>MAX_ITEMS+1000)throw new IllegalArgumentException("Migration ZIP has too many entries");String name=normalize(e.getName());JSONObject item=byEntry.get(name);if(item==null||e.isDirectory()){zin.closeEntry();continue;}
                long expected=item.optLong("size",-1);if(expected>MAX_PAYLOAD_FILE){item.put("_actual_status","REFERENCE_ONLY_TOO_LARGE");item.put("_detail","Payload entry exceeds native import safety limit");zin.closeEntry();continue;}
                File out=safeTarget(root,name);File parent=out.getParentFile();if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())throw new IllegalStateException("Could not create migration directory");
                MessageDigest d=MessageDigest.getInstance("SHA-256");long n=copy(zin,new BufferedOutputStream(new FileOutputStream(out)),MAX_PAYLOAD_FILE,d);importedBytes+=n;
                if(importedBytes>MAX_IMPORTED_PAYLOAD){out.delete();throw new IllegalArgumentException("Continuity payload exceeds 4 GiB native import safety limit. Split the migration into more than one bundle.");}
                String got=hex(d.digest()),want=item.optString("sha256").toLowerCase(Locale.US);String status=(want.isEmpty()||want.equals(got))?"IMPORTED_BYTES":"HASH_MISMATCH";
                item.put("_actual_status",status);item.put("_payload_path",out.getAbsolutePath());item.put("_detail",status.equals("IMPORTED_BYTES")?"Exact managed copy retained in standalone R10":"SHA-256 differs from migration manifest; both evidence and extracted bytes retained for review");
                if(status.equals("IMPORTED_BYTES"))imported++;else mismatch++;zin.closeEntry();
            }
        }
        int references=0;
        for(int i=0;i<items.length();i++){
            JSONObject src=items.getJSONObject(i),row=new JSONObject();
            String intended=src.optString("migration_status",src.optString("status","REFERENCE_ONLY"));String actual=src.optString("_actual_status","");
            if(actual.isEmpty()){actual=intended.startsWith("IMPORTED")?"MISSING":"REFERENCE_ONLY";if(actual.equals("MISSING"))mismatch++;else references++;}
            row.put("original_path",src.optString("original_path"));row.put("payload_entry",src.optString("payload_entry"));row.put("payload_path",src.optString("_payload_path"));
            row.put("size",src.optLong("size",-1));row.put("sha256",src.optString("sha256"));row.put("kind",src.optString("kind","file"));row.put("family",src.optString("family","unplaced"));row.put("version_token",src.optString("version_token"));row.put("migration_status",actual);row.put("detail",src.optString("_detail",src.optString("detail")));row.put("source_uri",uri.toString());db.addMigrationItem(runId,row);
        }
        String status=mismatch==0?"IMPORTED_WITH_REFERENCES":"REVIEW_REQUIRED";db.finishMigrationRun(runId,imported,references,mismatch,status,"Manifest "+manifestSha+" · source "+display);
        JSONObject o=db.migrationSummary();o.put("ok",mismatch==0);o.put("run_id",runId);o.put("name",display);o.put("manifest_sha256",manifestSha);o.put("imported_payload_files",imported);o.put("reference_only",references);o.put("mismatches",mismatch);o.put("message",mismatch==0?"Continuity bundle imported. Reference-only items remain visible and block silent data loss.":"Continuity bundle imported with mismatches. Promotion must remain blocked until reviewed.");return o;
    }

    private static byte[] findManifest(Context context,Uri uri)throws Exception{
        try(InputStream raw=context.getContentResolver().openInputStream(uri);ZipInputStream zin=new ZipInputStream(new BufferedInputStream(require(raw)))){
            ZipEntry e;int n=0;while((e=zin.getNextEntry())!=null){n++;if(n>MAX_ITEMS+1000)throw new IllegalArgumentException("Migration ZIP has too many entries");String name=normalize(e.getName());if(name.equals("MIGRATION_BUNDLE_MANIFEST.json")||name.endsWith("/MIGRATION_BUNDLE_MANIFEST.json")){ByteArrayOutputStream b=new ByteArrayOutputStream();copy(zin,b,MAX_MANIFEST,null);return b.toByteArray();}zin.closeEntry();}
        }throw new IllegalArgumentException("MIGRATION_BUNDLE_MANIFEST.json was not found in the selected ZIP.");
    }
    private static InputStream require(InputStream in){if(in==null)throw new IllegalArgumentException("Could not open selected migration bundle");return in;}
    private static long copy(InputStream in,OutputStream out,long max,MessageDigest d)throws Exception{try(OutputStream target=out){byte[] b=new byte[65536];long n=0;int r;while((r=in.read(b))!=-1){n+=r;if(n>max)throw new IllegalArgumentException("Migration entry exceeds safety limit");target.write(b,0,r);if(d!=null)d.update(b,0,r);}return n;}}
    private static String normalize(String s){if(s==null)return "";String n=s.replace('\\','/');while(n.startsWith("/"))n=n.substring(1);if(n.isEmpty())return "";for(String p:n.split("/"))if(p.equals("..")||p.equals("."))throw new IllegalArgumentException("Unsafe ZIP path: "+s);if(n.indexOf('\0')>=0)throw new IllegalArgumentException("Unsafe ZIP path");return n;}
    private static File safeTarget(File root,String rel)throws Exception{File r=root.getCanonicalFile(),t=new File(r,rel).getCanonicalFile();if(!t.getPath().startsWith(r.getPath()+File.separator))throw new IllegalArgumentException("Migration ZIP path escapes managed root");return t;}
    private static String sha(byte[] data)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static String hex(byte[] h){StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.US,"%02x",x));return b.toString();}
}
