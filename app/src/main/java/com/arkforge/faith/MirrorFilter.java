package com.arkforge.faith;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MirrorFilter {
    private static final int TEXT_SAMPLE_BYTES=512*1024;
    private static final long MAX_TEXT_FILE=16L*1024*1024;

    private static final String[] FAMILY={
            "dad","father","daddy","mom","mother","family","parent","parents","cousin","aunt","uncle","brother","sister"
    };
    private static final String[] DAD={"dad","father","daddy"};
    private static final String[] CONFLICT={
            "angry","anger","mad","furious","hate","hated","fight","fighting","argument","arguing","drama",
            "betray","betrayed","betrayal","accuse","accused","accusation","threat","threaten","threatened",
            "stole","steal","conflict","controlling","control me","against me","hurt me","won't listen","wont listen"
    };
    private static final String[] EXIT={
            "escape","get away","get out","leave home","leave town","move out","moving out","run away","flee",
            "relocate","relocation","shelter","need to leave","plan to leave","planning to leave","go somewhere else",
            "get far away","start over somewhere","one way ticket"
    };
    private static final String[] HEAT={
            "i hate","never again","i'm done","im done","can't take this","cant take this","everything is ruined",
            "destroyed everything","no one listens","nobody listens","i'm trapped","im trapped"
    };

    private MirrorFilter(){}

    public static JSONObject sweep(Context context,R10Database helper,boolean rescan)throws Exception{
        SQLiteDatabase db=helper.getWritableDatabase();
        long classified=0,archive=0,review=0,present=0,textRead=0,unreadable=0;

        String sql="SELECT p.id,p.source_uri,p.relative_path,p.name,p.mime,p.size,p.category "+
                "FROM phone_intake_files p LEFT JOIN mirror_triage m ON m.file_id=p.id "+
                "WHERE p.category<>'directory' "+(rescan?"":"AND (m.file_id IS NULL OR m.user_override=0) ")+
                "ORDER BY p.id";
        Cursor c=db.rawQuery(sql,null);
        db.beginTransaction();
        try{
            while(c.moveToNext()){
                long id=c.getLong(0);
                String uri=c.getString(1),path=c.getString(2),name=c.getString(3),mime=c.getString(4);
                long size=c.getLong(5);
                String category=c.getString(6);

                String metadata=((path==null?"":path)+" "+(name==null?"":name)).toLowerCase(Locale.US);
                String sample="";
                if(isTextLike(name,mime,category)&&size>=0&&size<=MAX_TEXT_FILE){
                    try{sample=readSample(context,uri,TEXT_SAMPLE_BYTES);textRead++;}
                    catch(Exception e){unreadable++;}
                }

                Triage t=classify(metadata,sample);
                ContentValues v=new ContentValues();
                v.put("file_id",id);v.put("disposition",t.disposition);v.put("labels",new JSONArray(t.labels).toString());
                v.put("score",t.score);v.put("reason",t.reason);v.put("excerpt",t.excerpt);
                v.put("classified_at",R10Database.now());v.put("user_override",0);
                // Preserve an existing manual reflection even during an explicit rescan.
                db.insertWithOnConflict("mirror_triage",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                if(rescan){
                    db.update("mirror_triage",v,"file_id=? AND user_override=0",new String[]{String.valueOf(id)});
                }

                classified++;
                if("MIRROR_ARCHIVE".equals(t.disposition))archive++;
                else if("REVIEW".equals(t.disposition))review++;
                else present++;

                if(classified%2000==0){
                    db.setTransactionSuccessful();db.endTransaction();db.beginTransaction();
                }
            }
            db.setTransactionSuccessful();
        }finally{
            try{db.endTransaction();}catch(Exception ignored){}
            c.close();
        }

        helper.log("mirror","sweep","PASS","classified="+classified+" archive="+archive+" review="+review+" present="+present);
        JSONObject out=helper.mirrorSummary();
        out.put("classified_this_run",classified);out.put("archive_this_run",archive);
        out.put("review_this_run",review);out.put("present_this_run",present);
        out.put("text_samples_read",textRead);out.put("unreadable_text_candidates",unreadable);
        out.put("policy","Past entries are evidence, not present authority. Mirror filtering hides selected historical heat from the default present view without deleting the source.");
        return out;
    }

    private static Triage classify(String metadata,String sample){
        String text=(metadata+" "+(sample==null?"":sample)).toLowerCase(Locale.US);
        int family=count(text,FAMILY),dad=count(text,DAD),conflict=count(text,CONFLICT),exit=count(text,EXIT),heat=count(text,HEAT);
        List<String> labels=new ArrayList<>();
        int score=0;

        if(dad>0&&conflict>=2){labels.add("DAD_CONFLICT");score+=6;}
        if(family>0&&conflict>=3){labels.add("FAMILY_CONFLICT");score+=5;}
        if(exit>=2){labels.add("EXIT_RELOCATION_PLANNING");score+=5;}
        if(heat>=2||conflict>=6){labels.add("HIGH_EMOTION");score+=3;}

        String disposition="PRESENT";
        if(labels.contains("DAD_CONFLICT")||labels.contains("FAMILY_CONFLICT")||labels.contains("EXIT_RELOCATION_PLANNING"))
            disposition="MIRROR_ARCHIVE";
        else if(labels.contains("HIGH_EMOTION"))disposition="REVIEW";

        String reason;
        if(labels.isEmpty())reason="No selected mirror pattern detected.";
        else reason="Historical mirror signals: "+String.join(", ",labels)+". Source preserved; classification is reversible.";

        String excerpt=compact(sample,420);
        return new Triage(disposition,labels,score,reason,excerpt);
    }

    private static int count(String text,String[] terms){
        int n=0;
        for(String term:terms){
            int from=0;
            while(true){
                int i=text.indexOf(term,from);if(i<0)break;n++;from=i+Math.max(1,term.length());
                if(n>30)return n;
            }
        }
        return n;
    }

    private static boolean isTextLike(String name,String mime,String category){
        String n=name==null?"":name.toLowerCase(Locale.US);
        String m=mime==null?"":mime.toLowerCase(Locale.US);
        if(m.startsWith("text/")||m.contains("json")||m.contains("xml")||m.contains("javascript"))return true;
        if("source-code".equals(category)||"document".equals(category)) {
            String[] ex={".txt",".md",".json",".jsonl",".ndjson",".csv",".log",".html",".htm",".xml",".yaml",".yml",".toml",".ini",".cfg",".js",".ts",".py",".java",".kt",".sh"};
            for(String x:ex)if(n.endsWith(x))return true;
        }
        return false;
    }

    private static String readSample(Context context,String uriString,int limit)throws Exception{
        if(uriString==null||uriString.isEmpty())return "";
        Uri uri=Uri.parse(uriString);
        InputStream raw;
        if("file".equalsIgnoreCase(uri.getScheme())){
            raw=new FileInputStream(new File(uri.getPath()));
        }else{
            raw=context.getContentResolver().openInputStream(uri);
        }
        if(raw==null)return "";
        try(InputStream in=new BufferedInputStream(raw)){
            byte[] buf=new byte[32768];java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            int total=0,r;
            while(total<limit&&(r=in.read(buf,0,Math.min(buf.length,limit-total)))!=-1){
                out.write(buf,0,r);total+=r;
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }

    private static String compact(String s,int max){
        if(s==null)return "";
        String x=s.replaceAll("\\s+"," ").trim();
        return x.length()<=max?x:x.substring(0,max)+"…";
    }

    private static final class Triage{
        final String disposition,reason,excerpt;final List<String> labels;final int score;
        Triage(String d,List<String> l,int s,String r,String e){disposition=d;labels=l;score=s;reason=r;excerpt=e;}
    }
}
