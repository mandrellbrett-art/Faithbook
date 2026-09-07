package com.arkforge.faith;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Download-once, app-private reader store for the CC0 Original Douay-Rheims dataset.
 * It is intentionally separate from user-imported corpora and never rewrites the source JSON.
 */
public final class PublicBibleStore {
    private static final String SOURCE_ID="original-douay-rheims-1582-1610-cc0";
    private static final String BASE="https://raw.githubusercontent.com/janvier-s/original-douay-rheims/main/bible/raw/";
    private static final long MAX_BOOK_BYTES=8L*1024L*1024L;
    private static final String[] SLUGS=new String[]{
            "genesis",
            "exodus",
            "leviticus",
            "numbers",
            "deuteronomy",
            "josue",
            "judges",
            "ruth",
            "1-kings",
            "2-kings",
            "3-kings",
            "4-kings",
            "1-paralipomenon",
            "2-paralipomenon",
            "1-esdras",
            "2-esdras",
            "tobias",
            "judith",
            "esther",
            "1-machabees",
            "2-machabees",
            "job",
            "psalms",
            "proverbs",
            "ecclesiastes",
            "canticle-of-canticles",
            "wisdom",
            "ecclesiasticus",
            "isaie",
            "jeremie",
            "lamentations",
            "baruch",
            "ezechiel",
            "daniel",
            "osee",
            "joel",
            "amos",
            "abdias",
            "jonas",
            "micheas",
            "nahum",
            "habacuc",
            "sophonias",
            "aggeus",
            "zacharias",
            "malachie",
            "matthew",
            "mark",
            "luke",
            "john",
            "acts",
            "romans",
            "1-corinthians",
            "2-corinthians",
            "galatians",
            "ephesians",
            "philippians",
            "colossians",
            "1-thessalonians",
            "2-thessalonians",
            "1-timothy",
            "2-timothy",
            "titus",
            "philemon",
            "hebrews",
            "james",
            "1-peter",
            "2-peter",
            "1-john",
            "2-john",
            "3-john",
            "jude",
            "apocalypse"
    };
    private static final String[] NAMES=new String[]{
            "Genesis",
            "Exodus",
            "Leviticus",
            "Numbers",
            "Deuteronomy",
            "Joshua",
            "Judges",
            "Ruth",
            "1 Samuel",
            "2 Samuel",
            "1 Kings",
            "2 Kings",
            "1 Chronicles",
            "2 Chronicles",
            "Ezra",
            "Nehemiah",
            "Tobit",
            "Judith",
            "Esther",
            "1 Maccabees",
            "2 Maccabees",
            "Job",
            "Psalms",
            "Proverbs",
            "Ecclesiastes",
            "Song of Songs",
            "Wisdom",
            "Sirach",
            "Isaiah",
            "Jeremiah",
            "Lamentations",
            "Baruch",
            "Ezekiel",
            "Daniel",
            "Hosea",
            "Joel",
            "Amos",
            "Obadiah",
            "Jonah",
            "Micah",
            "Nahum",
            "Habakkuk",
            "Zephaniah",
            "Haggai",
            "Zechariah",
            "Malachi",
            "Matthew",
            "Mark",
            "Luke",
            "John",
            "Acts",
            "Romans",
            "1 Corinthians",
            "2 Corinthians",
            "Galatians",
            "Ephesians",
            "Philippians",
            "Colossians",
            "1 Thessalonians",
            "2 Thessalonians",
            "1 Timothy",
            "2 Timothy",
            "Titus",
            "Philemon",
            "Hebrews",
            "James",
            "1 Peter",
            "2 Peter",
            "1 John",
            "2 John",
            "3 John",
            "Jude",
            "Revelation"
    };
    private static final Set<String> ALLOWED=new HashSet<>();
    private static final Map<String,String> DISPLAY=new HashMap<>();
    private static volatile boolean installing=false;
    static { for(int i=0;i<SLUGS.length;i++){ALLOWED.add(SLUGS[i]);DISPLAY.put(SLUGS[i],NAMES[i]);} }

    private PublicBibleStore() {}
    private static File dir(Context c){File d=new File(c.getFilesDir(),"public-scripture/"+SOURCE_ID);if(!d.exists())d.mkdirs();return d;}
    private static File statusFile(Context c){return new File(dir(c),"install-status.json");}
    private static File bookFile(Context c,String slug){return new File(dir(c),slug+".json");}
    private static void requireSlug(String slug){if(slug==null||!ALLOWED.contains(slug))throw new IllegalArgumentException("Unknown or non-canonical Bible book slug");}

    public static JSONObject status(Context c) throws Exception {
        int count=0;long bytes=0;
        JSONArray installedBooks=new JSONArray();
        for(String slug:SLUGS){File f=bookFile(c,slug);if(f.isFile()&&f.length()>10){count++;bytes+=f.length();installedBooks.put(slug);}}
        JSONObject o=readStatus(c);
        if(!installing && "DOWNLOADING".equals(o.optString("state"))) o.put("state","INTERRUPTED");
        o.put("ok",true);o.put("source_id",SOURCE_ID);o.put("installed_books",count);o.put("total_books",SLUGS.length);o.put("bytes",bytes);o.put("complete",count==SLUGS.length);o.put("installing",installing);o.put("installed_slugs",installedBooks);
        return o;
    }

    public static synchronized JSONObject installAsync(Context c,R10Database db) throws Exception {
        if(installing){JSONObject o=status(c);o.put("accepted",false);o.put("message","Scripture install is already running.");return o;}
        installing=true;
        Context app=c.getApplicationContext();
        JSONObject start=new JSONObject();start.put("state","DOWNLOADING");start.put("current","");start.put("done",0);start.put("total",SLUGS.length);start.put("last_error","");writeStatus(app,start);
        Thread t=new Thread(()->{
            int done=0;
            try{
                for(String slug:SLUGS){
                    File finalFile=bookFile(app,slug);
                    if(finalFile.isFile()&&finalFile.length()>10){done++;updateProgress(app,"DOWNLOADING",slug,done,"");continue;}
                    updateProgress(app,"DOWNLOADING",slug,done,"");
                    downloadOne(app,slug);
                    done++;
                    updateProgress(app,"DOWNLOADING",slug,done,"");
                }
                updateProgress(app,"COMPLETE","",done,"");
                db.log("bible","public-scripture-install","PASS",SOURCE_ID+" · "+done+" canonical books cached");
            }catch(Exception e){
                try{updateProgress(app,"ERROR","",done,String.valueOf(e.getMessage()));}catch(Exception ignored){}
                db.log("bible","public-scripture-install","FAIL",SOURCE_ID+" · "+String.valueOf(e.getMessage()));
            }finally{installing=false;}
        },"arkforge-public-bible-install");
        t.setDaemon(true);t.start();
        JSONObject out=status(c);out.put("accepted",true);out.put("message","Downloading the 73-book CC0 Catholic Scripture foundation to app-private storage.");return out;
    }

    private static void downloadOne(Context c,String slug)throws Exception{
        requireSlug(slug);
        URL u=new URL(BASE+slug+".json");
        if(!"https".equalsIgnoreCase(u.getProtocol())||!"raw.githubusercontent.com".equalsIgnoreCase(u.getHost()))throw new SecurityException("Unexpected Scripture source host");
        HttpURLConnection h=(HttpURLConnection)u.openConnection();
        h.setInstanceFollowRedirects(false);h.setConnectTimeout(15000);h.setReadTimeout(30000);h.setRequestProperty("User-Agent","Arkforge-Faith/15 ScriptureInstaller");
        int code=h.getResponseCode();if(code!=200)throw new IllegalStateException("Scripture source returned HTTP "+code+" for "+slug);
        long declared=h.getContentLengthLong();if(declared>MAX_BOOK_BYTES)throw new IllegalArgumentException("Bible book file exceeds safety limit: "+slug);
        File tmp=new File(dir(c),slug+".json.part");long bytes=0;
        try(InputStream in=new BufferedInputStream(h.getInputStream());BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){byte[] b=new byte[65536];int r;while((r=in.read(b))!=-1){bytes+=r;if(bytes>MAX_BOOK_BYTES)throw new IllegalArgumentException("Bible book file exceeds safety limit: "+slug);out.write(b,0,r);}}
        finally{h.disconnect();}
        JSONObject parsed=readJson(tmp);if(!slug.equals(parsed.optString("book"))||!(parsed.opt("chapters") instanceof JSONArray)){tmp.delete();throw new IllegalArgumentException("Downloaded JSON failed Scripture schema check: "+slug);}
        File fin=bookFile(c,slug);if(fin.exists()&&!fin.delete()){tmp.delete();throw new IllegalStateException("Could not replace cached Scripture book");}
        if(!tmp.renameTo(fin)){copy(tmp,fin);tmp.delete();}
    }

    public static JSONObject bookInfo(Context c,String slug)throws Exception{
        requireSlug(slug);File f=bookFile(c,slug);if(!f.isFile())throw new IllegalStateException("This book is not installed yet. Install the public Scripture foundation first.");
        JSONObject root=readJson(f);JSONObject o=new JSONObject();o.put("ok",true);o.put("slug",slug);o.put("name",DISPLAY.get(slug));o.put("source_title",root.optString("book_title",DISPLAY.get(slug)));o.put("chapter_count",root.optJSONArray("chapters")==null?0:root.optJSONArray("chapters").length());return o;
    }

    public static JSONObject chapter(Context c,String slug,int chapter)throws Exception{
        requireSlug(slug);if(chapter<1)throw new IllegalArgumentException("Chapter must be 1 or greater");File f=bookFile(c,slug);if(!f.isFile())throw new IllegalStateException("This book is not installed yet.");
        JSONObject root=readJson(f);JSONArray cs=root.optJSONArray("chapters");if(cs==null)throw new IllegalStateException("Book JSON has no chapters");JSONObject found=null;
        for(int i=0;i<cs.length();i++){JSONObject x=cs.getJSONObject(i);if(x.optInt("chapter")==chapter){found=x;break;}}
        if(found==null)throw new IllegalArgumentException("Chapter not found");
        JSONArray src=found.optJSONArray("verses");JSONArray verses=new JSONArray();
        if(src!=null)for(int i=0;i<src.length();i++){JSONObject v=src.getJSONObject(i);JSONObject z=new JSONObject();z.put("book",DISPLAY.get(slug));z.put("book_slug",slug);z.put("chapter",chapter);z.put("verse",v.optInt("verse"));z.put("text",v.optString("text"));z.put("notes",v.optJSONArray("notes")!=null?v.optJSONArray("notes"):new JSONArray());z.put("cross_refs",v.optJSONArray("cross_refs")!=null?v.optJSONArray("cross_refs"):new JSONArray());verses.put(z);}
        JSONObject o=new JSONObject();o.put("ok",true);o.put("source_id",SOURCE_ID);o.put("book",DISPLAY.get(slug));o.put("book_slug",slug);o.put("source_title",root.optString("book_title",DISPLAY.get(slug)));o.put("chapter",chapter);o.put("chapter_count",cs.length());o.put("summary",found.optString("summary",""));o.put("verses",verses);return o;
    }

    public static JSONObject search(Context c,String query,int limit)throws Exception{
        String q=query==null?"":query.trim().toLowerCase(Locale.US);if(q.length()<2)throw new IllegalArgumentException("Search needs at least 2 characters");limit=Math.max(1,Math.min(limit,250));JSONArray hits=new JSONArray();int booksScanned=0;
        for(String slug:SLUGS){File f=bookFile(c,slug);if(!f.isFile())continue;booksScanned++;JSONObject root=readJson(f);JSONArray cs=root.optJSONArray("chapters");if(cs==null)continue;
            for(int ci=0;ci<cs.length()&&hits.length()<limit;ci++){JSONObject ch=cs.getJSONObject(ci);JSONArray vs=ch.optJSONArray("verses");if(vs==null)continue;for(int vi=0;vi<vs.length()&&hits.length()<limit;vi++){JSONObject v=vs.getJSONObject(vi);String text=v.optString("text");if(text.toLowerCase(Locale.US).contains(q)){JSONObject z=new JSONObject();z.put("book",DISPLAY.get(slug));z.put("book_slug",slug);z.put("chapter",ch.optInt("chapter"));z.put("verse",v.optInt("verse"));z.put("text",text);z.put("notes",v.optJSONArray("notes")!=null?v.optJSONArray("notes"):new JSONArray());z.put("cross_refs",v.optJSONArray("cross_refs")!=null?v.optJSONArray("cross_refs"):new JSONArray());hits.put(z);}}}
            if(hits.length()>=limit)break;
        }
        JSONObject o=new JSONObject();o.put("ok",true);o.put("source_id",SOURCE_ID);o.put("query",query);o.put("books_scanned",booksScanned);o.put("results",hits);return o;
    }

    private static JSONObject readStatus(Context c){try{File f=statusFile(c);if(!f.isFile()){JSONObject o=new JSONObject();o.put("state","NOT_INSTALLED");o.put("done",0);o.put("total",SLUGS.length);return o;}return readJson(f);}catch(Exception e){try{JSONObject o=new JSONObject();o.put("state","STATUS_REVIEW");o.put("last_error",String.valueOf(e.getMessage()));return o;}catch(Exception ignored){return new JSONObject();}}}
    private static synchronized void updateProgress(Context c,String state,String current,int done,String err)throws Exception{JSONObject o=new JSONObject();o.put("state",state);o.put("current",current);o.put("done",done);o.put("total",SLUGS.length);o.put("last_error",err==null?"":err);writeStatus(c,o);}
    private static void writeStatus(Context c,JSONObject o)throws Exception{File f=statusFile(c),tmp=new File(f.getParentFile(),"install-status.json.part");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));}if(f.exists())f.delete();if(!tmp.renameTo(f)){copy(tmp,f);tmp.delete();}}
    private static JSONObject readJson(File f)throws Exception{StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line).append('\n');}return new JSONObject(b.toString());}
    private static void copy(File a,File b)throws Exception{try(FileInputStream in=new FileInputStream(a);FileOutputStream out=new FileOutputStream(b)){byte[] buf=new byte[65536];int r;while((r=in.read(buf))!=-1)out.write(buf,0,r);}}
}
