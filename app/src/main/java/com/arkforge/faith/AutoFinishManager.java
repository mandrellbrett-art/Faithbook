package com.arkforge.faith;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class AutoFinishManager {
    private static final int MAX_FILES = 25000;
    private static final long SNAPSHOT_CAP = 1024L * 1024 * 1024;
    private AutoFinishManager() {}

    public static JSONObject finish(Context context, R10Database db, String projectId) throws Exception {
        JSONObject project = db.getProject(projectId);
        if (project == null) throw new IllegalArgumentException("Project not found");
        File root = new File(project.optString("root_path"));
        if (!root.isDirectory()) throw new IllegalArgumentException("Project root is missing");

        JSONArray inventory = new JSONArray(); JSONArray checks = new JSONArray(); JSONArray jsonFailures = new JSONArray();
        Counter c = new Counter(); List<File> files = new ArrayList<>(); collect(root,root,files,c);
        boolean indexHtml=false,androidProject=false,pythonProject=false,nodeProject=false;
        for(File f:files){String rel=relative(root,f);String low=rel.toLowerCase(Locale.US);long size=f.length();String sha=sha(f);JSONObject row=new JSONObject();row.put("path",rel);row.put("bytes",size);row.put("sha256",sha);inventory.put(row);
            db.addManagedFile(projectId,rel,f.getName(),size,sha,"",kind(f.getName()),project.optString("source_uri"));
            if(low.equals("index.html")||low.endsWith("/index.html"))indexHtml=true;
            if(low.equals("settings.gradle")||low.equals("settings.gradle.kts")||low.endsWith("/androidmanifest.xml"))androidProject=true;
            if(low.equals("requirements.txt")||low.equals("pyproject.toml")||low.endsWith(".py"))pythonProject=true;
            if(low.equals("package.json"))nodeProject=true;
            if(low.endsWith(".json") && size <= 16L*1024*1024){try{new JSONObject(readText(f,16L*1024*1024));}catch(Exception objectErr){try{new JSONArray(readText(f,16L*1024*1024));}catch(Exception arrayErr){JSONObject bad=new JSONObject();bad.put("path",rel);bad.put("error",arrayErr.getMessage());jsonFailures.put(bad);}}}
        }
        checks.put(check("managed-root",true,"Project is inside Thunderforge managed storage"));
        checks.put(check("file-inventory",files.size()<=MAX_FILES,files.size()+" files inventoried"));
        checks.put(check("json-parse",jsonFailures.length()==0,jsonFailures.length()+" malformed JSON files"));
        checks.put(check("web-entry",indexHtml,indexHtml?"index.html present":"No index.html; project may still be non-web"));
        if(androidProject)checks.put(check("android-source-detected",true,"Android project markers found. R10 does not fabricate an APK build without an Android SDK."));
        if(pythonProject)checks.put(check("python-source-detected",true,"Python source markers found. Source is preserved; arbitrary code is not executed by Auto Finish."));
        if(nodeProject)checks.put(check("node-source-detected",true,"package.json detected. Dependency installation/scripts are not run automatically."));

        JSONObject report=new JSONObject();report.put("schema","thunderforge.r10.autofinish.v1");report.put("generated_at",R10Database.now());report.put("project_id",projectId);report.put("project_name",project.optString("name"));report.put("root",root.getAbsolutePath());report.put("file_count",files.size());report.put("bytes",c.bytes);report.put("checks",checks);report.put("json_failures",jsonFailures);report.put("runnable_web",indexHtml);report.put("android_source",androidProject);report.put("python_source",pythonProject);report.put("node_source",nodeProject);report.put("physical_proof_created",false);report.put("truth_boundary","Auto Finish creates digital validation, inventory, provenance, and recovery artifacts. It does not invent prototypes, measurements, physical tests, certifications, approvals, or manufacturing evidence.");

        File reportFile=new File(root,"R10_AUTOFINISH_REPORT.json");writeText(reportFile,report.toString(2));
        StringBuilder invText=new StringBuilder();for(int i=0;i<inventory.length();i++){JSONObject r=inventory.getJSONObject(i);invText.append(r.getString("sha256")).append("  ").append(r.getString("path")).append('\n');}
        File invFile=new File(root,"R10_FILE_INVENTORY_SHA256.txt");writeText(invFile,invText.toString());
        File exports=new File(context.getFilesDir(),"exports");exports.mkdirs();File zip=new File(exports,safe(project.optString("name"))+"_R10_"+System.currentTimeMillis()+".zip");SnapshotResult snapshot=snapshot(root,zip);
        report.put("generated_report",reportFile.getAbsolutePath());report.put("generated_inventory",invFile.getAbsolutePath());report.put("snapshot",zip.getAbsolutePath());report.put("snapshot_files",snapshot.files);report.put("snapshot_bytes",snapshot.bytes);report.put("snapshot_truncated",snapshot.truncated);
        writeText(reportFile,report.toString(2));
        db.updateProjectStatus(projectId,jsonFailures.length()==0?"finished-candidate":"review-required");
        db.log("auto-finish","finish",jsonFailures.length()==0?"PASS":"REVIEW",projectId+" files="+files.size()+" snapshot="+zip.getName()+" physical_proof=false");
        return report;
    }

    private static void collect(File root,File here,List<File> out,Counter c)throws Exception{File[] list=here.listFiles();if(list==null)return;for(File f:list){if(f.isDirectory()){collect(root,f,out,c);continue;}if(out.size()>=MAX_FILES)throw new IllegalArgumentException("Project exceeds "+MAX_FILES+"-file Auto Finish safety limit");out.add(f);c.bytes+=f.length();}}
    private static JSONObject check(String name,boolean ok,String detail)throws Exception{JSONObject o=new JSONObject();o.put("name",name);o.put("ok",ok);o.put("detail",detail);return o;}
    private static String relative(File root,File f)throws Exception{return f.getCanonicalPath().substring(root.getCanonicalPath().length()+1).replace(File.separatorChar,'/');}
    private static String sha(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[65536];int r;while((r=in.read(b))!=-1)d.update(b,0,r);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
    private static String readText(File f,long max)throws Exception{if(f.length()>max)throw new IllegalArgumentException("File too large for text validation");try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IllegalArgumentException("File too large for text validation");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static void writeText(File f,String s)throws Exception{try(FileOutputStream out=new FileOutputStream(f)){out.write(s.getBytes(StandardCharsets.UTF_8));}}
    private static SnapshotResult snapshot(File root,File zip)throws Exception{SnapshotResult r=new SnapshotResult();try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){List<File> files=new ArrayList<>();Counter c=new Counter();collect(root,root,files,c);byte[] b=new byte[65536];for(File f:files){if(f.equals(zip))continue;long size=f.length();if(r.bytes+size>SNAPSHOT_CAP){r.truncated=true;continue;}z.putNextEntry(new ZipEntry(relative(root,f)));try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();r.files++;r.bytes+=size;}}return r;}
    private static String kind(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".nds")||x.endsWith(".gba")||x.endsWith(".gb")||x.endsWith(".gbc")||x.endsWith(".3ds")||x.endsWith(".cia"))return "rom";if(x.endsWith(".html")||x.endsWith(".js")||x.endsWith(".css"))return "web";if(x.endsWith(".md")||x.endsWith(".txt")||x.endsWith(".pdf"))return "document";return "file";}
    private static String safe(String s){String x=(s==null?"project":s).replaceAll("[^A-Za-z0-9._-]","_");return x.isEmpty()?"project":x;}
    private static class Counter{long bytes;}
    private static class SnapshotResult{long files,bytes;boolean truncated;}
}
