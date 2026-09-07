package com.arkforge.faith;

import android.content.Context;
import android.net.Uri;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private BackupManager() {}

    public static JSONObject exportToUri(Context context, R10Database db, Uri uri) throws Exception {
        JSONObject manifest = db.exportJson();
        long files = 0, bytes = 0;
        try (OutputStream raw = context.getContentResolver().openOutputStream(uri, "wt");
             ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(requireOut(raw)))) {
            byte[] json = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
            zout.putNextEntry(new ZipEntry("r10-backup.json")); zout.write(json); zout.closeEntry();
            File projects = new File(context.getFilesDir(), "projects");
            if (projects.isDirectory()) {
                Counter c = new Counter();
                addTree(zout, projects, projects, c);
                files = c.files; bytes = c.bytes;
            }
        }
        db.log("backup", "export", "PASS", "managed_files=" + files + " managed_bytes=" + bytes + " -> " + uri);
        JSONObject out = new JSONObject(); out.put("ok", true); out.put("managed_files", files); out.put("managed_bytes", bytes); out.put("uri", uri.toString()); return out;
    }

    public static JSONObject restoreFromUri(Context context, R10Database db, Uri uri) throws Exception {
        File stage = new File(context.getCacheDir(), "restore-" + System.nanoTime());
        if (!stage.mkdirs() && !stage.isDirectory()) throw new IllegalStateException("Could not create restore stage");
        JSONObject backup = null; long extracted = 0; int entries = 0;
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zin = new ZipInputStream(new BufferedInputStream(requireIn(raw)))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries++; if (entries > 100000) throw new IllegalArgumentException("Backup has too many entries");
                String n = safePath(e.getName());
                if (e.isDirectory()) { zin.closeEntry(); continue; }
                if ("r10-backup.json".equals(n)) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream(); copy(zin,b,32L*1024*1024); backup = new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8));
                } else if (n.startsWith("managed/")) {
                    String rel = n.substring("managed/".length()); File target = safeTarget(stage, rel); File parent=target.getParentFile(); if(parent!=null&&!parent.mkdirs()&&!parent.isDirectory())throw new IllegalStateException("Restore folder create failed");
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){extracted += copy(zin,out,4L*1024*1024*1024-extracted);} 
                }
                zin.closeEntry();
            }
        }
        if (backup == null) { deleteTree(stage); throw new IllegalArgumentException("Backup ZIP is missing r10-backup.json"); }
        JSONObject merged = db.restoreJson(backup);
        // Restore managed bytes additively. Existing files are never overwritten.
        File dst = new File(context.getFilesDir(), "projects"); dst.mkdirs(); Counter moved=new Counter(); mergeTree(stage,dst,moved);
        deleteTree(stage);
        db.log("backup","managed-restore","PASS","files_added="+moved.files+" bytes="+moved.bytes+" originals_preserved=true");
        merged.put("managed_files_added",moved.files);merged.put("managed_bytes_added",moved.bytes);merged.put("ok",true);return merged;
    }

    private static void addTree(ZipOutputStream z, File root, File here, Counter c) throws Exception {
        File[] list=here.listFiles(); if(list==null)return;
        for(File f:list){if(f.isDirectory()){addTree(z,root,f,c);continue;}String rel=f.getCanonicalPath().substring(root.getCanonicalPath().length()+1).replace(File.separatorChar,'/');ZipEntry e=new ZipEntry("managed/"+rel);z.putNextEntry(e);try(InputStream in=new BufferedInputStream(new FileInputStream(f))){c.bytes+=copy(in,z,-1);}z.closeEntry();c.files++;}
    }
    private static void mergeTree(File src,File dst,Counter c)throws Exception{File[] list=src.listFiles();if(list==null)return;for(File s:list){File d=new File(dst,s.getName());if(s.isDirectory()){if(!d.mkdirs()&&!d.isDirectory())throw new IllegalStateException("Restore directory failed");mergeTree(s,d,c);}else if(!d.exists()){try(InputStream in=new FileInputStream(s);OutputStream out=new FileOutputStream(d)){c.bytes+=copy(in,out,-1);}c.files++;}}}
    private static long copy(InputStream in,OutputStream out,long max)throws Exception{byte[] b=new byte[65536];long n=0;int r;while((r=in.read(b))!=-1){n+=r;if(max>=0&&n>max)throw new IllegalArgumentException("Backup size safety limit exceeded");out.write(b,0,r);}return n;}
    private static String safePath(String s){String n=(s==null?"":s).replace('\\','/');while(n.startsWith("/"))n=n.substring(1);for(String p:n.split("/"))if(p.equals("..")||p.equals("."))throw new IllegalArgumentException("Unsafe backup path");return n;}
    private static File safeTarget(File root,String rel)throws Exception{File r=root.getCanonicalFile(),t=new File(r,rel).getCanonicalFile();if(!t.getPath().startsWith(r.getPath()+File.separator))throw new IllegalArgumentException("Backup path escapes restore stage");return t;}
    private static InputStream requireIn(InputStream i){if(i==null)throw new IllegalArgumentException("Could not read selected backup");return i;}
    private static OutputStream requireOut(OutputStream o){if(o==null)throw new IllegalArgumentException("Could not create selected backup destination");return o;}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);}f.delete();}
    private static class Counter{long files,bytes;}
}
