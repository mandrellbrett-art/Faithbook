package com.arkforge.faith;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-initiated parser for public-domain Bible text files (especially Project Gutenberg KJV
 * and Challoner Douay-Rheims plain-text exports). The original selected bytes are preserved
 * as a managed Library project. Parsing never rewrites the selected source.
 */
public final class BibleCorpusImporter {
    private static final long MAX_TEXT_BYTES = 64L * 1024 * 1024;
    private static final Pattern BOOK_LINE = Pattern.compile("(?i)^\\s*(?:book\\s+)?(\\d{1,3})\\s+(.+?)\\s*$");
    private static final Pattern DR_BOOK_LINE = Pattern.compile("(?i)^\\s*Book\\s+(\\d{1,3})\\s+(.+?)\\s*$");
    private static final Pattern TRIPLE = Pattern.compile("^\\s*(\\d{1,3})[:.]([0-9]{1,3})[:.]([0-9]{1,3})[\\s.]+(.+?)\\s*$");
    private static final Pattern CV_DOT = Pattern.compile("^\\s*(\\d{1,3}):([0-9]{1,3})\\.\\s*(.+?)\\s*$");
    private static final Pattern CV_SPACE = Pattern.compile("^\\s*(\\d{1,3}):([0-9]{1,3})\\s+(.+?)\\s*$");
    private static final Pattern CHAPTER = Pattern.compile("(?i)^\\s*(.+?)\\s+Chapter\\s+(\\d+)\\s*$");

    private BibleCorpusImporter() {}

    public static JSONObject importText(Context context, R10Database db, Uri uri) throws Exception {
        String name=displayName(context,uri);
        String lower=name.toLowerCase(Locale.US);
        if(!(lower.endsWith(".txt")||lower.endsWith(".text")||lower.endsWith(".md")))
            throw new IllegalArgumentException("Bible corpus import currently accepts UTF-8 plain text (.txt/.text/.md). Download the Project Gutenberg Plain Text version or export your own text file.");

        File root=new File(context.getFilesDir(),"projects/bible-corpus-"+safeName(stripExt(name))+"-"+shortId());
        if(!root.mkdirs()&&!root.isDirectory())throw new IllegalStateException("Could not create Bible corpus storage");
        File exact=new File(root,safeName(name));
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        long bytes=0;
        try(InputStream in=new BufferedInputStream(require(context.getContentResolver().openInputStream(uri),"Could not open selected Bible text"));
            FileOutputStream out=new FileOutputStream(exact)){
            byte[] buf=new byte[65536];int r;
            while((r=in.read(buf))!=-1){bytes+=r;if(bytes>MAX_TEXT_BYTES)throw new IllegalArgumentException("Bible text exceeds 64 MB safety limit");out.write(buf,0,r);digest.update(buf,0,r);}
        }catch(Exception e){deleteTree(root);throw e;}
        String sha=hex(digest.digest());

        Detection det=detect(exact,name);
        String projectId=db.createProject(det.title,"Public-domain/user-supplied Bible comparison corpus. Exact selected bytes preserved; parsed verses are a searchable comparison layer, not a replacement for the user-owned third-party scan.","library",root.getAbsolutePath(),uri.toString());
        db.addManagedFile(projectId,exact.getName(),exact.getName(),bytes,sha,"text/plain","document",uri.toString());
        db.updateProjectStatus(projectId,"bible-corpus");

        String corpusId=db.beginBibleCorpus(det.title,det.translation,uri.toString(),sha,det.license,det.publicDomain);
        int verses=parseIntoDb(exact,db,corpusId,det);
        db.finishBibleCorpus(corpusId,verses);
        db.log("bible","corpus-import","PASS",det.translation+" · "+verses+" verses · sha256="+sha);

        JSONObject out=new JSONObject();
        out.put("ok",true);out.put("id",projectId);out.put("project_id",projectId);out.put("corpus_id",corpusId);
        out.put("name",name);out.put("title",det.title);out.put("translation",det.translation);out.put("verses",verses);
        out.put("bytes",bytes);out.put("sha256",sha);out.put("source_uri",uri.toString());out.put("public_domain",det.publicDomain);
        out.put("message","Exact source preserved; searchable verse layer created. Review parser output before treating it as authoritative, especially footnotes/formatting from old e-texts.");
        return out;
    }

    private static Detection detect(File f,String name)throws Exception{
        StringBuilder sample=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line;int n=0;while((line=r.readLine())!=null&&n++<500)sample.append(line).append('\n');}
        String s=sample.toString().toLowerCase(Locale.US);
        if(s.contains("douay-rheims")||s.contains("rheims version")||s.contains("challoner"))
            return new Detection("Douay-Rheims Bible — Challoner Revision","Douay-Rheims (Challoner)","Public domain in the USA; source-file distribution terms still apply.",true,true);
        if(s.contains("king james")||s.contains("authorized version")||name.toLowerCase(Locale.US).contains("kjv"))
            return new Detection("King James Bible","King James Version","Public domain in the USA; source-file distribution terms still apply.",false,true);
        return new Detection(stripExt(name),"USER_SUPPLIED_PUBLIC_DOMAIN_OR_LICENSED_TEXT","User must verify rights/license for this selected corpus.",false,false);
    }

    private static int parseIntoDb(File f,R10Database db,String corpusId,Detection det)throws Exception{
        HashMap<Integer,String> names=new HashMap<>();
        String[] fallback=det.douay?DOUAY_ORDER:KJV_ORDER;
        for(int i=0;i<fallback.length;i++)names.put(i+1,fallback[i]);
        int currentBookNo=0,currentChapter=0,count=0;
        String currentBook="";
        Verse pending=null;
        boolean blankAfterVerse=false;
        JSONArray batch=new JSONArray();

        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
            String line;
            while((line=r.readLine())!=null){
                String trimmed=line.trim();
                if(trimmed.isEmpty()){ if(pending!=null) blankAfterVerse=true; continue; }

                Matcher bm=DR_BOOK_LINE.matcher(trimmed);
                if(bm.matches()){
                    if(pending!=null){batch=flushPending(pending,batch,db,corpusId);count++;pending=null;}
                    currentBookNo=parseInt(bm.group(1));currentBook=cleanBookName(bm.group(2));
                    if(!currentBook.isEmpty())names.put(currentBookNo,currentBook);
                    currentChapter=0;blankAfterVerse=false;continue;
                }

                Matcher ch=CHAPTER.matcher(trimmed);
                if(ch.matches()){
                    if(pending!=null){batch=flushPending(pending,batch,db,corpusId);count++;pending=null;}
                    currentBook=cleanBookName(ch.group(1));currentChapter=parseInt(ch.group(2));blankAfterVerse=false;continue;
                }

                Matcher t=TRIPLE.matcher(trimmed);
                if(t.matches()){
                    if(pending!=null){batch=flushPending(pending,batch,db,corpusId);count++;}
                    int bno=parseInt(t.group(1)),chap=parseInt(t.group(2)),verse=parseInt(t.group(3));
                    currentBookNo=bno;currentChapter=chap;currentBook=names.getOrDefault(bno,"Book "+bno);
                    pending=new Verse(bno,currentBook,chap,verse,t.group(4));blankAfterVerse=false;continue;
                }

                Matcher cv=CV_DOT.matcher(trimmed); if(!cv.matches())cv=CV_SPACE.matcher(trimmed);
                if(cv.matches() && currentBookNo>0){
                    if(pending!=null){batch=flushPending(pending,batch,db,corpusId);count++;}
                    int chap=parseInt(cv.group(1)),verse=parseInt(cv.group(2));currentChapter=chap;
                    if(currentBook.isEmpty())currentBook=names.getOrDefault(currentBookNo,"Book "+currentBookNo);
                    pending=new Verse(currentBookNo,currentBook,chap,verse,cv.group(3));blankAfterVerse=false;continue;
                }

                // Some KJV plaintexts provide "Book 01 Genesis" without the word Book after normalization.
                Matcher generic=BOOK_LINE.matcher(trimmed);
                if(generic.matches() && trimmed.toLowerCase(Locale.US).startsWith("book ")){
                    currentBookNo=parseInt(generic.group(1));currentBook=cleanBookName(generic.group(2));names.put(currentBookNo,currentBook);continue;
                }

                // Continue a wrapped verse only until the first blank line. Footnotes/intros after a blank are not merged.
                if(pending!=null && !blankAfterVerse && !looksLikeHeading(trimmed)) pending.text.append(' ').append(trimmed);
            }
        }
        if(pending!=null){batch=flushPending(pending,batch,db,corpusId);count++;}
        if(batch.length()>0)db.addBibleVerseBatch(corpusId,batch);
        if(count<100) throw new IllegalArgumentException("The selected text did not parse as a Bible corpus (only "+count+" verse records detected). Exact bytes remain in the imported project, but the corpus index was not reliable.");
        return count;
    }

    private static JSONArray flushPending(Verse v,JSONArray batch,R10Database db,String corpusId)throws Exception{
        JSONObject o=new JSONObject();o.put("book_no",v.bookNo);o.put("book",v.book);o.put("chapter",v.chapter);o.put("verse",v.verse);o.put("text",v.text.toString().trim());batch.put(o);
        if(batch.length()>=500){db.addBibleVerseBatch(corpusId,batch);return new JSONArray();}return batch;
    }

    private static boolean looksLikeHeading(String s){String x=s.toLowerCase(Locale.US);return x.startsWith("book ")||x.startsWith("the book of ")||x.startsWith("the gospel")||x.startsWith("chapter ")||x.contains(" chapter ")||x.startsWith("***")||x.startsWith("project gutenberg");}
    private static String cleanBookName(String s){String x=s.replaceAll("(?i)^the\\s+book\\s+of\\s+","").replaceAll("(?i)^the\\s+gospel\\s+according\\s+to\\s+","").replaceAll("(?i)^the\\s+","").trim();return x.replaceAll("\\s+"," ");}
    private static int parseInt(String s){try{return Integer.parseInt(s.replaceFirst("^0+","").isEmpty()?"0":s.replaceFirst("^0+",""));}catch(Exception e){return 0;}}
    private static String displayName(Context context,Uri uri){try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.isBlank())return n;}}catch(Exception ignored){}String last=uri.getLastPathSegment();return last==null?"bible.txt":last;}
    private static String safeName(String s){String x=(s==null?"bible":s).replaceAll("[^A-Za-z0-9._() +\\-\\[\\]]","_").trim();return x.isEmpty()?"bible":x;}
    private static String stripExt(String s){int i=s.lastIndexOf('.');return i>0?s.substring(0,i):s;}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,8);}
    private static InputStream require(InputStream in,String msg){if(in==null)throw new IllegalArgumentException(msg);return in;}
    private static String hex(byte[] h){StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.US,"%02x",x));return b.toString();}
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);}f.delete();}

    private static final class Detection{final String title,translation,license;final boolean douay,publicDomain;Detection(String a,String b,String c,boolean d,boolean p){title=a;translation=b;license=c;douay=d;publicDomain=p;}}
    private static final class Verse{final int bookNo,chapter,verse;final String book;final StringBuilder text;Verse(int b,String k,int c,int v,String t){bookNo=b;book=k;chapter=c;verse=v;text=new StringBuilder(t==null?"":t.trim());}}

    private static final String[] KJV_ORDER={
            "Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth","1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra","Nehemiah","Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon","Isaiah","Jeremiah","Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos","Obadiah","Jonah","Micah","Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi","Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians","Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation"};

    private static final String[] DOUAY_ORDER={
            "Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Josue","Judges","Ruth","1 Samuel","2 Samuel","3 Kings","4 Kings","1 Paralipomenon","2 Paralipomenon","1 Esdras","Nehemias","Tobias","Judith","Esther","Job","Psalms","Proverbs","Ecclesiastes","Canticle of Canticles","Wisdom","Ecclesiasticus","Isaias","Jeremias","Lamentations","Baruch","Ezechiel","Daniel","Osee","Joel","Amos","Abdias","Jonas","Micheas","Nahum","Habacuc","Sophonias","Aggeus","Zacharias","Malachias","1 Machabees","2 Machabees","Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians","Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Apocalypse"};
}
