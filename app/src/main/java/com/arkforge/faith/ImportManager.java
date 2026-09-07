package com.arkforge.faith;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ImportManager {
    private static final long MAX_SINGLE_FILE = 2L * 1024 * 1024 * 1024;
    private static final long MAX_EXTRACTED = 4L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 120000;
    private static final Pattern VERSION = Pattern.compile("(?i)(?:^|[^a-z0-9])((?:r\\d{1,4})|(?:v\\d+(?:\\.\\d+){0,3}))(?=$|[^a-z0-9])");
    private static final String[] ROM_EXTS = {"nds","gba","gb","gbc","3ds","cia","nes","sfc","smc","n64","z64","iso","chd","pbp","rvz","wbfs","gcz","wad"};

    private ImportManager() {}

    public static JSONObject importUri(Context context, R10Database db, Uri uri, String kind) throws Exception {
        String purpose = kind == null ? "project" : kind.toLowerCase(Locale.US);
        if ("heritage".equals(purpose)) return indexHeritageArchive(context, db, uri);
        if ("continuity".equals(purpose)) return ContinuityManager.importBundle(context, db, uri);
        if ("backup".equals(purpose)) return BackupManager.restoreFromUri(context, db, uri);
        if ("bible_corpus".equals(purpose)) return BibleCorpusImporter.importText(context, db, uri);
        if ("assistant-return".equals(purpose)) return AssistantBridgeManager.importReturnUri(context, db, uri);
        if ("legacy-patents".equals(purpose)) return LegacyPatentImporter.importUri(context, db, uri);

        String name = displayName(context, uri);
        String ext = extension(name);
        if ("zip".equals(ext)) return importZip(context, db, uri, purpose, name);
        return importSingle(context, db, uri, purpose, name);
    }

    private static JSONObject importSingle(Context context, R10Database db, Uri uri, String purpose, String name) throws Exception {
        String family = familyForPurpose(purpose, name);
        File root = new File(context.getFilesDir(), "projects/" + safeName(name) + "_" + shortId());
        if (!root.mkdirs() && !root.isDirectory()) throw new IllegalStateException("Could not create project storage");
        File out = new File(root, safeName(name));
        CopyResult cr = copyUri(context, uri, out, MAX_SINGLE_FILE);
        String projectId = db.createProject(stripExtension(name), "Imported from Android document picker", family, root.getAbsolutePath(), uri.toString());
        db.addManagedFile(projectId, out.getName(), out.getName(), cr.bytes, cr.sha256, mimeFromName(name), fileKind(name), uri.toString());
        String status = isRom(name) ? "external-runtime-ready" : (isWebEntry(name) ? "runnable" : "imported");
        db.updateProjectStatus(projectId, status);
        db.log("files", "import", "PASS", name + " -> " + projectId + " sha256=" + cr.sha256);
        JSONObject j = db.getProject(projectId); j.put("ok", true); j.put("bytes", cr.bytes); j.put("sha256", cr.sha256); return j;
    }

    private static JSONObject importZip(Context context, R10Database db, Uri uri, String purpose, String name) throws Exception {
        String family = familyForPurpose(purpose, name);
        File root = new File(context.getFilesDir(), "projects/" + safeName(stripExtension(name)) + "_" + shortId());
        if (!root.mkdirs() && !root.isDirectory()) throw new IllegalStateException("Could not create project storage");
        int entries = 0; long total = 0;
        List<FileInfo> files = new ArrayList<>();
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(require(raw, "Could not open ZIP")))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries++; if (entries > MAX_ENTRIES) throw new IllegalArgumentException("ZIP has too many entries");
                String n = normalizeZipPath(e.getName());
                if (n.isEmpty()) { zin.closeEntry(); continue; }
                File target = safeTarget(root, n);
                if (e.isDirectory()) {
                    if (!target.mkdirs() && !target.isDirectory()) throw new IllegalStateException("Could not create ZIP folder");
                    zin.closeEntry(); continue;
                }
                File parent = target.getParentFile(); if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IllegalStateException("Could not create ZIP parent");
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); long count = 0;
                try(OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                    byte[] buf = new byte[1024*64]; int r;
                    while((r=zin.read(buf))!=-1){ count += r; total += r; if(total>MAX_EXTRACTED) throw new IllegalArgumentException("ZIP exceeds 4 GB extracted safety limit"); out.write(buf,0,r);digest.update(buf,0,r); }
                }
                files.add(new FileInfo(n, target.getName(), count, hex(digest.digest()), mimeFromName(n), fileKind(n)));
                zin.closeEntry();
            }
        } catch (Exception e) {
            deleteTree(root); throw e;
        }
        String projectId = db.createProject(stripExtension(name), "Safely extracted Android ZIP import", family, root.getAbsolutePath(), uri.toString());
        boolean hasIndex = false; boolean hasRom = false;
        for(FileInfo f:files){db.addManagedFile(projectId,f.rel,f.name,f.size,f.sha,f.mime,f.kind,uri.toString()); if(f.rel.equalsIgnoreCase("index.html")||f.rel.toLowerCase(Locale.US).endsWith("/index.html"))hasIndex=true; if("rom".equals(f.kind))hasRom=true;}
        db.updateProjectStatus(projectId, hasIndex ? "runnable" : (hasRom ? "external-runtime-ready" : "imported"));
        db.log("files", "zip-import", "PASS", name + " entries=" + entries + " bytes=" + total + " -> " + projectId);
        JSONObject j=db.getProject(projectId);j.put("ok",true);j.put("entries",entries);j.put("bytes",total);j.put("runnable",hasIndex);j.put("rom",hasRom);return j;
    }

    public static JSONObject installBundledGarden(Context context, R10Database db) throws Exception {
        File root = new File(context.getFilesDir(), "projects/garden-r10-field-lab");
        deleteTree(root); if (!root.mkdirs() && !root.isDirectory()) throw new IllegalStateException("Could not create Garden field lab");
        copyAssetTree(context, "garden-test", root);
        String existing = null;
        JSONArray ps = db.listProjects(false);
        for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);if("R10 Garden Field Lab".equals(p.optString("name"))){existing=p.optString("id");break;}}
        String id = existing;
        if(id==null||id.isEmpty()) id=db.createProject("R10 Garden Field Lab", "Bundled offline runtime proof. This does not replace an imported historical Garden Immortals branch.", "garden", root.getAbsolutePath(), "asset://garden-test");
        else db.updateProjectRoot(id,root.getAbsolutePath(),"runnable");
        indexManagedTree(db,id,root,root,"asset://garden-test");
        db.updateProjectStatus(id,"runnable");
        db.log("garden","install-field-lab","PASS",id+"; rest/autonomy behavior enabled");
        JSONObject o=db.getProject(id);o.put("ok",true);return o;
    }

    private static void copyAssetTree(Context context, String assetPath, File outDir) throws Exception {
        String[] names=context.getAssets().list(assetPath); if(names==null)return;
        for(String n:names){String child=assetPath+"/"+n;String[] children=context.getAssets().list(child);File out=new File(outDir,n);if(children!=null&&children.length>0){if(!out.mkdirs()&&!out.isDirectory())throw new IllegalStateException("Asset directory create failed");copyAssetTree(context,child,out);}else{try(InputStream in=context.getAssets().open(child);OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){copy(in,os,-1,null);}}}
    }

    private static void indexManagedTree(R10Database db,String projectId,File root,File here,String source) throws Exception {
        File[] list=here.listFiles();if(list==null)return;for(File f:list){if(f.isDirectory()){indexManagedTree(db,projectId,root,f,source);continue;}String rel=f.getCanonicalPath().substring(root.getCanonicalPath().length()+1);String sha=shaFile(f);db.addManagedFile(projectId,rel,f.getName(),f.length(),sha,mimeFromName(f.getName()),fileKind(f.getName()),source);}
    }

    public static JSONObject indexHeritageArchive(Context context, R10Database db, Uri uri) throws Exception {
        String name=displayName(context,uri); if(!"zip".equals(extension(name)))throw new IllegalArgumentException("Heritage intake expects a ZIP archive");
        db.beginCorpusIndex(uri.toString()); int count=0; long files=0,dirs=0; JSONArray batch=new JSONArray();
        try(InputStream raw=context.getContentResolver().openInputStream(uri);ZipInputStream zin=new ZipInputStream(new BufferedInputStream(require(raw,"Could not open heritage ZIP")))){
            ZipEntry e; while((e=zin.getNextEntry())!=null){count++;if(count>350000)throw new IllegalArgumentException("Heritage ZIP exceeded 350,000-entry safety limit");String path=normalizeZipPath(e.getName());if(path.isEmpty()){zin.closeEntry();continue;}JSONObject r=new JSONObject();r.put("source_uri",uri.toString());r.put("path",path);r.put("size",e.getSize());r.put("crc",e.getCrc());r.put("version_token",versionToken(path));r.put("family",inferFamily(path));r.put("kind",e.isDirectory()?"directory":inferArtifactKind(path));batch.put(r);if(e.isDirectory())dirs++;else files++;if(batch.length()>=1000){db.addCorpusBatch(batch);batch=new JSONArray();}zin.closeEntry();}
        }
        if(batch.length()>0)db.addCorpusBatch(batch);
        db.log("heritage","corpus-index","PASS",name+" files="+files+" dirs="+dirs+"; index is metadata-only, originals untouched");
        JSONObject out=new JSONObject();out.put("ok",true);out.put("name",name);out.put("files",files);out.put("directories",dirs);out.put("source_uri",uri.toString());out.put("message","Indexed archive metadata without rewriting the source archive.");return out;
    }

    public static String displayName(Context context, Uri uri) {
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.isBlank())return n;}}catch(Exception ignored){}
        String last=uri.getLastPathSegment();return last==null?"imported-file":last;
    }

    private static CopyResult copyUri(Context context,Uri uri,File out,long max) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long bytes;
        try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){bytes=copy(require(in,"Could not open selected file"),os,max,digest);}catch(Exception e){out.delete();throw e;}
        return new CopyResult(bytes,hex(digest.digest()));
    }
    private static long copy(InputStream in,OutputStream out,long max,MessageDigest d)throws Exception{byte[] b=new byte[65536];long n=0;int r;while((r=in.read(b))!=-1){n+=r;if(max>0&&n>max)throw new IllegalArgumentException("File exceeds safety limit");out.write(b,0,r);if(d!=null)d.update(b,0,r);}return n;}
    private static InputStream require(InputStream in,String msg){if(in==null)throw new IllegalArgumentException(msg);return in;}
    private static String shaFile(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int r;while((r=in.read(b))!=-1)d.update(b,0,r);}return hex(d.digest());}
    private static String hex(byte[] h){StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.US,"%02x",x));return b.toString();}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,8);}
    private static String safeName(String s){String x=(s==null?"item":s).replaceAll("[^A-Za-z0-9._() +\\-\\[\\]]","_").trim();return x.isEmpty()?"item":x;}
    private static String stripExtension(String n){int i=n.lastIndexOf('.');return i>0?n.substring(0,i):n;}
    private static String extension(String n){int i=n.lastIndexOf('.');return i>=0?n.substring(i+1).toLowerCase(Locale.US):"";}
    private static String mimeFromName(String n){String ext=extension(n);String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return m==null?"application/octet-stream":m;}
    private static boolean isRom(String n){String e=extension(n);for(String x:ROM_EXTS)if(x.equals(e))return true;return false;}
    private static boolean isWebEntry(String n){return n.equalsIgnoreCase("index.html")||n.toLowerCase(Locale.US).endsWith(".html");}
    private static String fileKind(String n){if(isRom(n))return "rom";String e=extension(n);if(e.equals("html")||e.equals("js")||e.equals("css"))return "web";if(e.equals("pdf")||e.equals("md")||e.equals("txt")||e.equals("docx"))return "document";if(e.equals("png")||e.equals("jpg")||e.equals("jpeg")||e.equals("webp")||e.equals("gif"))return "image";return "file";}
    private static String familyForPurpose(String p,String name){if("game".equals(p)||isRom(name))return "game";if("garden".equals(p))return "garden";if("library".equals(p))return "library";if("image".equals(p))return "images";if("kernel".equals(p))return "kernel";return "project";}
    private static String normalizeZipPath(String s){if(s==null)return "";String n=s.replace('\\','/');while(n.startsWith("/"))n=n.substring(1);if(n.isEmpty())return "";String[] parts=n.split("/");for(String p:parts)if(p.equals("..")||p.equals("."))throw new IllegalArgumentException("Unsafe ZIP path: "+s);if(n.indexOf('\0')>=0)throw new IllegalArgumentException("Unsafe ZIP path");return n;}
    private static File safeTarget(File root,String rel)throws Exception{File r=root.getCanonicalFile(),t=new File(r,rel).getCanonicalFile();if(!t.getPath().startsWith(r.getPath()+File.separator))throw new IllegalArgumentException("ZIP path escapes project root");return t;}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);}f.delete();}
    private static String versionToken(String p){Matcher m=VERSION.matcher(p);String last="";while(m.find())last=m.group(1).toUpperCase(Locale.US);return last;}
    private static String inferFamily(String p){String x=p.toLowerCase(Locale.US);if(x.contains("garden_immortal")||x.contains("garden-immortal")||x.contains("first_light")||x.contains("firstlight"))return "garden-immortals";if(x.contains("home_base")||x.contains("home-base")||x.contains("farwater")||x.contains("grovenaut")||x.contains("thunderforge"))return "thunderforge-homebase";if(x.contains("cantus")||x.contains("ademic")||x.contains("adamic"))return "cantus";if(x.contains("chrono"))return "chrono-compass";if(x.contains("civis"))return "civis";if(x.contains("patent"))return "patent";if(x.contains("constructor"))return "constructor";if(x.contains("kernel"))return "kernel";return "unplaced";}
    private static String inferArtifactKind(String p){String x=p.toLowerCase(Locale.US);if(x.endsWith(".sh")||x.endsWith(".py")||x.endsWith(".js")||x.endsWith(".java")||x.endsWith(".kt"))return "source";if(x.endsWith(".zip"))return "archive";if(x.contains("backup")||x.contains("pre-r")||x.contains("before-"))return "backup";if(x.contains("manifest")||x.contains("sha256"))return "manifest";if(x.contains("log"))return "log";if(x.contains("save"))return "save";return "file";}
    private static class CopyResult{final long bytes;final String sha256;CopyResult(long b,String s){bytes=b;sha256=s;}}
    private static class FileInfo{final String rel,name;final long size;final String sha,mime,kind;FileInfo(String r,String n,long z,String s,String m,String k){rel=r;name=n;size=z;sha=s;mime=m;kind=k;}}
}
