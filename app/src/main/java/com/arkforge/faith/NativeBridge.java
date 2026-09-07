package com.arkforge.faith;

import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class NativeBridge {
    private final MainActivity activity;
    private final R10Database db;

    public NativeBridge(MainActivity activity,R10Database db){this.activity=activity;this.db=db;}

    @JavascriptInterface public String bootstrap(){
        try{JSONObject o=new JSONObject();o.put("ok",true);o.put("product","HeritageFaith");o.put("version","20.0.0-united-private");o.put("runtime","android-native-webview");o.put("termux_required",false);o.put("localhost_required",false);o.put("port_required",false);o.put("projects",db.listProjects(false));o.put("stats",db.stats());o.put("features",readAssetJson("feature-ledger.json"));o.put("continuity",db.migrationSummary());o.put("continuity_lock",readAssetJson("PUBLIC_RELEASE_BOUNDARY.json"));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}
    }

    @JavascriptInterface public String projects(){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("projects",db.listProjects(false));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String project(String id){try{JSONObject p=db.getProject(id);if(p==null)return MainActivity.error("Project not found").toString();p.put("ok",true);return p.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String projectFiles(String id){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("files",db.listManagedFiles(id,5000));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String archivedProjects(){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("projects",db.listProjects(true));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String createProject(String name,String description,String family){try{String id=db.createProject(name,description,family,"","");JSONObject o=db.getProject(id);o.put("ok",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String records(String type,boolean archived,int limit){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("records",db.listRecords(type,archived,limit));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String addRecord(String type,String title,String body,String metaJson){try{String id=db.addRecord(type,title,body,metaJson);JSONObject o=new JSONObject();o.put("ok",true);o.put("id",id);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String updateRecord(String id,String title,String body,String metaJson){try{db.updateRecord(id,title,body,metaJson);return "{\"ok\":true}";}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String archiveRecord(String id,boolean archived){try{db.setRecordArchived(id,archived);return "{\"ok\":true}";}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String search(String query){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("results",db.search(query,100));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String searchCorpus(String query){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("results",db.searchCorpus(query,250));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String continuityStatus(){try{JSONObject o=db.migrationSummary();o.put("ok",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String searchContinuity(String query){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("results",db.searchMigrationItems(query,250));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String openContinuityItem(long id){try{JSONObject o=db.getMigrationItem(id);if(o==null)throw new IllegalArgumentException("Continuity item not found");String path=o.optString("payload_path");if(path.isEmpty())throw new IllegalArgumentException("This item is reference-only; its original path remains recorded but bytes were not in the bundle.");File f=new File(path);if(!f.isFile())throw new IllegalArgumentException("Imported continuity bytes are missing from managed storage.");activity.openManagedFile(f);return "{\"ok\":true}";}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public void importFile(String kind){activity.pickImport(kind);}
    @JavascriptInterface public void exportBackup(){activity.requestBackupExport();}
    @JavascriptInterface public void runProject(String projectId){activity.runProject(projectId);}

    @JavascriptInterface public String finishProject(String projectId){try{return AutoFinishManager.finish(activity,db,projectId).toString();}catch(Exception e){db.log("auto-finish","finish","FAIL",projectId+" · "+e.getMessage());return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String installGardenFieldLab(){try{return ImportManager.installBundledGarden(activity,db).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String cantus(int limit){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("logs",db.cantusLogs(limit));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String verifyCantus(){try{return db.verifyCantus().toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String constructorAsk(String message){try{JSONObject o=ConstructorCore.ask(db,message);db.log("constructor","ask","PASS",message.length()>200?message.substring(0,200):message);return o.toString();}catch(Exception e){db.log("constructor","ask","FAIL",String.valueOf(e.getMessage()));return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public String bibleAtlasSpec(){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("atlas",readAssetJson("scripture-context-atlas-spec.json"));o.put("books",readAssetJson("bible-book-index.json"));o.put("starter",readAssetJson("bible-atlas-starter.json"));o.put("sources",readAssetJson("bible-source-registry.json"));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String bibleCorpora(){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("corpora",db.listBibleCorpora());return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String bibleChapter(String corpusId,String book,String chapter){try{int c=Integer.parseInt(chapter);JSONObject o=new JSONObject();o.put("ok",true);o.put("verses",db.bibleChapter(corpusId,book,c));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String bibleSearch(String corpusId,String query,int limit){try{JSONObject o=new JSONObject();o.put("ok",true);o.put("results",db.searchBibleVerses(corpusId,query,limit));return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String publicBibleSource(){try{JSONObject o=readAssetJson("public-scripture-source.json");o.put("ok",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String publicBibleStatus(){try{return PublicBibleStore.status(activity).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String installPublicBible(){try{return PublicBibleStore.installAsync(activity,db).toString();}catch(Exception e){db.log("bible","public-scripture-install","FAIL",String.valueOf(e.getMessage()));return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String publicBibleBookInfo(String slug){try{return PublicBibleStore.bookInfo(activity,slug).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String publicBibleChapter(String slug,String chapter){try{return PublicBibleStore.chapter(activity,slug,Integer.parseInt(chapter)).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String publicBibleSearch(String query,int limit){try{return PublicBibleStore.search(activity,query,limit).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public String safeFetch(String url){try{JSONObject o=NetworkTools.safeFetch(url);db.log("argus","https-fetch",o.optBoolean("ok")?"PASS":"HTTP_"+o.optInt("status"),url+" bytes="+o.optLong("bytes"));return o.toString();}catch(Exception e){db.log("argus","https-fetch","BLOCKED",url+" · "+e.getMessage());return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public void openUrl(String url){try{Uri u=Uri.parse(url);String s=u.getScheme();if(!"https".equalsIgnoreCase(s)&&!"http".equalsIgnoreCase(s))throw new IllegalArgumentException("Only http/https URLs accepted here");activity.openExternalUri(u);}catch(Exception e){activity.callback("onNativeError",MainActivity.error(e.getMessage()));}}
    @JavascriptInterface public void dial(String number){activity.openExternalUri(Uri.parse("tel:"+Uri.encode(number==null?"":number)));}
    @JavascriptInterface public void sms(String number,String body){Uri u=Uri.parse("smsto:"+Uri.encode(number==null?"":number));Intent i=new Intent(Intent.ACTION_SENDTO,u);i.putExtra("sms_body",body==null?"":body);activity.runOnUiThread(()->{try{activity.startActivity(i);db.log("comms","sms-handoff","PASS",String.valueOf(number));}catch(Exception e){activity.callback("onNativeError",MainActivity.error("No SMS app is available."));}});}
    @JavascriptInterface public void email(String address,String subject,String body){Uri u=Uri.parse("mailto:"+Uri.encode(address==null?"":address)+"?subject="+Uri.encode(subject==null?"":subject)+"&body="+Uri.encode(body==null?"":body));activity.openExternalUri(u);}
    @JavascriptInterface public void geo(double lat,double lon,String label){String uri="geo:"+lat+","+lon+"?q="+lat+","+lon+"("+Uri.encode(label==null?"Pin":label)+")";activity.openExternalUri(Uri.parse(uri));}

    @JavascriptInterface public String openProjectFile(String projectId,String relativePath){
        try{JSONObject p=db.getProject(projectId);if(p==null)throw new IllegalArgumentException("Project not found");File root=new File(p.optString("root_path")).getCanonicalFile();File f=new File(root,relativePath).getCanonicalFile();if(!f.getPath().startsWith(root.getPath()+File.separator)||!f.isFile())throw new IllegalArgumentException("File is outside project or missing");activity.openManagedFile(f);return "{\"ok\":true}";}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}
    }

    @JavascriptInterface public String stats(){try{return db.stats().toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String featureLedger(){try{return readAssetJson("feature-ledger.json").toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String privateLibrarySources(){try{JSONObject o=readAssetJson("private-library-sources.json");o.put("ok",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String homeBaseUnitySpec(){try{JSONObject o=readAssetJson("homebase-unity-spec.json");o.put("ok",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public String assistantSettings(){try{return AssistantBridgeManager.settings(activity).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String setAssistantSetting(String key,boolean value){try{return AssistantBridgeManager.setSetting(activity,key,value).toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String exportAssistantContext(String mode){try{activity.requestAssistantContextExport(mode);JSONObject o=new JSONObject();o.put("ok",true);o.put("pending",true);o.put("mode",mode);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public String shareAssistantContext(String mode){try{activity.shareAssistantContext(mode);JSONObject o=new JSONObject();o.put("ok",true);o.put("pending",true);o.put("mode",mode);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}

    @JavascriptInterface public String importLibraryFolder(){try{activity.pickLibraryFolder();JSONObject o=new JSONObject();o.put("ok",true);o.put("pending",true);return o.toString();}catch(Exception e){return MainActivity.error(e.getMessage()).toString();}}
    @JavascriptInterface public void printCurrent(){activity.printCurrent();}
    @JavascriptInterface public void shareText(String subject,String text){activity.shareText(subject,text);}

    private JSONObject readAssetJson(String name)throws Exception{StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(activity.getAssets().open(name),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line).append('\n');}return new JSONObject(b.toString());}


}
