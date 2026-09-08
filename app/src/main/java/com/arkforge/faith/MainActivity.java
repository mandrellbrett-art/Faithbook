package com.arkforge.faith;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.content.UriPermission;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_BACKUP_EXPORT = 1002;
    private static final int REQ_ASSISTANT_EXPORT = 1003;
    private static final int REQ_LIBRARY_TREE = 1004;
    private static final int REQ_PHONE_TREE = 1005;
    private WebView web;
    private R10Database db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String pendingImportKind = "project";
    private String pendingAssistantExportMode = "standard";
    private String pendingPhoneMode = PhoneIntakeManager.MODE_INDEX;
    private boolean pendingImportAll = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new R10Database(this);
        web = new WebView(this);
        setContentView(web);

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.getSettings().setAllowFileAccess(true); // bundled android_asset UI only
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "file".equalsIgnoreCase(u.getScheme()) && "android_asset".equals(u.getHost())) return false;
                if (u != null) openExternalUri(u);
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith("file:///android_asset/")) return false;
                try { openExternalUri(Uri.parse(url)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.addJavascriptInterface(new NativeBridge(this, db), "ArkforgeNative");
        web.loadUrl("file:///android_asset/index.html");
        db.log("system", "app-open", "PASS", "Standalone Android runtime; no Termux/localhost/port required");
    }

    public void pickImport(String kind) {
        pendingImportKind = kind == null ? "project" : kind;
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            if ("library-batch".equals(pendingImportKind)) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(i, REQ_PICK); }
            catch (ActivityNotFoundException e) { callback("onNativeError", error("No Android document picker is available.")); }
        });
    }




    public boolean hasAllFilesAccess(){
        return Build.VERSION.SDK_INT<30||Environment.isExternalStorageManager();
    }

    public void requestImportAll(){
        if(hasAllFilesAccess()){runImportAllNow();return;}
        pendingImportAll=true;
        runOnUiThread(()->{
            try{
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:"+getPackageName())));
            }catch(Exception e){
                try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
                catch(Exception x){callback("onNativeError",error("Android could not open All Files Access settings."));}
            }
        });
    }

    private void runImportAllNow(){
        pendingImportAll=false;
        executor.execute(()->{
            try{
                JSONObject result=PhoneIntakeManager.scanAllSharedStorage(this,db);
                JSONArray providers=new JSONArray();

                // Include previously authorized non-local provider trees such as Drive.
                for(UriPermission perm:getContentResolver().getPersistedUriPermissions()){
                    if(!perm.isReadPermission())continue;
                    Uri u=perm.getUri();if(u==null)continue;
                    String auth=u.getAuthority()==null?"":u.getAuthority();
                    if("com.android.externalstorage.documents".equals(auth))continue;
                    try{
                        JSONObject r=PhoneIntakeManager.scanTree(this,db,u,PhoneIntakeManager.MODE_INDEX);
                        JSONObject one=new JSONObject();
                        one.put("uri",u.toString());one.put("authority",auth);
                        one.put("status",r.optString("run_status","COMPLETE"));
                        one.put("files",r.optLong("files_seen_this_run",0));
                        providers.put(one);
                    }catch(Exception ex){
                        JSONObject one=new JSONObject();
                        one.put("uri",u.toString());one.put("authority",auth);
                        one.put("status","REVIEW");one.put("detail",ex.getMessage());providers.put(one);
                    }
                }

                result.put("provider_trees_scanned",providers.length());
                result.put("persisted_provider_runs",providers);
                callback("onImportAll",result);
            }catch(Exception e){
                db.log("phone-intake","import-all","FAIL",e.getClass().getSimpleName()+": "+e.getMessage());
                callback("onNativeError",error(e.getMessage()));
            }
        });
    }

    @Override protected void onResume(){
        super.onResume();
        if(pendingImportAll&&hasAllFilesAccess())runImportAllNow();
    }

    public void pickPhoneTree(String mode){
        pendingPhoneMode=PhoneIntakeManager.MODE_COPY_ALL.equalsIgnoreCase(mode)?PhoneIntakeManager.MODE_COPY_ALL:PhoneIntakeManager.MODE_INDEX;
        runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);try{startActivityForResult(i,REQ_PHONE_TREE);}catch(ActivityNotFoundException e){callback("onNativeError",error("No Android folder picker is available."));}});
    }

    public void pickLibraryFolder() {
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            try { startActivityForResult(i, REQ_LIBRARY_TREE); }
            catch (ActivityNotFoundException e) {
                callback("onNativeError", error("No Android folder picker is available."));
            }
        });
    }

    public void requestBackupExport() {
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "HeritageFaith_Backup_" + System.currentTimeMillis() + ".zip");
            try { startActivityForResult(i, REQ_BACKUP_EXPORT); }
            catch (ActivityNotFoundException e) { callback("onNativeError", error("No Android document creator is available.")); }
        });
    }

    public void requestAssistantContextExport(String mode) {
        pendingAssistantExportMode = "full".equalsIgnoreCase(mode) ? "full" : "standard";
        runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/zip");
            i.putExtra(Intent.EXTRA_TITLE, "HeritageFaith_Assistant_Context_" + System.currentTimeMillis() + ".zip");
            try { startActivityForResult(i, REQ_ASSISTANT_EXPORT); }
            catch (ActivityNotFoundException e) { callback("onNativeError", error("No Android document creator is available.")); }
        });
    }

    public void shareAssistantContext(String mode) {
        final String safeMode = "full".equalsIgnoreCase(mode) ? "full" : "standard";
        executor.execute(() -> {
            try {
                File bundle = AssistantBridgeManager.createShareBundle(this, db, safeMode);
                Uri uri = ManagedFileProvider.uriForFile(this, bundle);
                runOnUiThread(() -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("application/zip");
                        i.putExtra(Intent.EXTRA_SUBJECT, "HeritageFaith Assistant Context");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Share HeritageFaith context"));
                        JSONObject out = new JSONObject();
                        out.put("ok", true);
                        out.put("kind", "assistant-context-share");
                        out.put("mode", safeMode);
                        out.put("bytes", bundle.length());
                        callback("onAssistantShare", out);
                        db.log("assistant-bridge","share","PASS","mode="+safeMode+" bytes="+bundle.length());
                    } catch (Exception e) {
                        callback("onNativeError", error("Could not open Android share sheet: " + e.getMessage()));
                    }
                });
            } catch (Exception e) {
                callback("onNativeError", error("Could not create Assistant Context bundle: " + e.getMessage()));
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;



        if(requestCode==REQ_PHONE_TREE){
            if(data.getData()==null)return;final Uri tree=data.getData();
            try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            final String mode=pendingPhoneMode;executor.execute(()->{try{callback("onPhoneIntake",PhoneIntakeManager.scanTree(this,db,tree,mode));}catch(Exception e){db.log("phone-intake","scan","FAIL",e.getMessage());callback("onNativeError",error(e.getMessage()));}});return;
        }

        if (requestCode == REQ_LIBRARY_TREE) {
            if (data.getData() == null) return;
            final Uri treeUri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(
                        treeUri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            executor.execute(() -> {
                try { callback("onNativeFolderImport", DriveFolderImporter.importTree(this, db, treeUri)); }
                catch (Exception e) {
                    db.log("library", "folder-import", "FAIL",
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                    callback("onNativeError", error(e.getMessage()));
                }
            });
            return;
        }

        if (requestCode == REQ_PICK && "library-batch".equals(pendingImportKind)) {
            final java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i=0;i<clip.getItemCount();i++) uris.add(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            for (Uri u: uris) {
                try {
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(u, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
            }
            executor.execute(() -> {
                JSONObject batch = new JSONObject();
                JSONArray items = new JSONArray();
                int okCount = 0, failCount = 0;
                try {
                    for (Uri u: uris) {
                        try {
                            JSONObject item = ImportManager.importUri(this, db, u, "library");
                            String title = item.optString("name", item.optString("project_name", item.optString("id","Imported book")));
                            JSONObject meta = new JSONObject();
                            meta.put("project_id", item.optString("id"));
                            meta.put("filename", title);
                            meta.put("sha256", item.optString("sha256"));
                            meta.put("bytes", item.optLong("bytes"));
                            meta.put("source_uri", u.toString());
                            meta.put("rights_status", "PRIVATE_USER_OWNED_OR_AUTHORIZED");
                            meta.put("distribution", "PRIVATE_ONLY");
                            db.addRecord("library", title, "Private book imported through Android document provider.", meta.toString());
                            item.put("ok", true); items.put(item); okCount++;
                        } catch (Exception e) {
                            JSONObject fail = error(e.getMessage());
                            try { fail.put("source_uri", String.valueOf(u)); } catch (Exception ignored) {}
                            items.put(fail); failCount++;
                            db.log("library","batch-import","FAIL",String.valueOf(u)+" · "+e.getMessage());
                        }
                    }
                    batch.put("ok", failCount==0);
                    batch.put("imported", okCount);
                    batch.put("failed", failCount);
                    batch.put("items", items);
                    db.log("library","batch-import",failCount==0?"PASS":"PARTIAL","imported="+okCount+" failed="+failCount);
                    callback("onNativeImportBatch", batch);
                } catch (Exception e) {
                    callback("onNativeError", error(e.getMessage()));
                }
            });
            return;
        }

        if (data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        if (requestCode == REQ_PICK) {
            final String kind = pendingImportKind;
            executor.execute(() -> {
                try { callback("onNativeImport", ImportManager.importUri(this, db, uri, kind)); }
                catch (Exception e) { db.log("files", "import", "FAIL", e.getClass().getSimpleName()+": "+e.getMessage()); callback("onNativeError", error(e.getMessage())); }
            });
        } else if (requestCode == REQ_BACKUP_EXPORT) {
            executor.execute(() -> {
                try { callback("onBackupExport", BackupManager.exportToUri(this, db, uri)); }
                catch (Exception e) { db.log("backup", "export", "FAIL", e.getMessage()); callback("onNativeError", error(e.getMessage())); }
            });
        } else if (requestCode == REQ_ASSISTANT_EXPORT) {
            final String mode = pendingAssistantExportMode;
            executor.execute(() -> {
                try { callback("onAssistantExport", AssistantBridgeManager.exportToUri(this, db, uri, mode)); }
                catch (Exception e) { db.log("assistant-bridge", "context-export", "FAIL", e.getMessage()); callback("onNativeError", error(e.getMessage())); }
            });
        }
    }

    public void runProject(String projectId) {
        runOnUiThread(() -> {
            try {
                JSONObject p = db.getProject(projectId);
                if (p == null) throw new IllegalArgumentException("Project not found");
                File root = new File(p.optString("root_path"));
                File entry = ProjectRunnerActivity.findEntry(root);
                if (entry != null) {
                    Intent i = new Intent(this, ProjectRunnerActivity.class); i.putExtra("project_id", projectId); startActivity(i);
                    db.log("projects","open-runner","PASS",projectId+" · "+entry.getName()); return;
                }
                File first = firstFile(root);
                if (first != null) openManagedFile(first);
                else throw new IllegalArgumentException("Project has no runnable/openable file");
            } catch (Exception e) { callback("onNativeError", error(e.getMessage())); }
        });
    }

    public void openManagedFile(File file) {
        runOnUiThread(() -> {
            try {
                Uri uri = ManagedFileProvider.uriForFile(this, file);
                Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri, mime(file.getName()));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Open with"));
                db.log("files","open","PASS",file.getAbsolutePath());
            } catch (Exception e) { callback("onNativeError", error("No compatible Android app could open this file: " + e.getMessage())); }
        });
    }

    public void openExternalUri(Uri uri) {
        runOnUiThread(() -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, uri); startActivity(i);
                db.log("device","external-intent","PASS",String.valueOf(uri));
            } catch (Exception e) { callback("onNativeError", error("No Android app can handle this action.")); }
        });
    }

    public void printCurrent() {
        runOnUiThread(() -> {
            try {
                android.print.PrintManager pm=(android.print.PrintManager)getSystemService(PRINT_SERVICE);
                android.print.PrintDocumentAdapter adapter=web.createPrintDocumentAdapter("HeritageFaith");
                pm.print("HeritageFaith",adapter,new android.print.PrintAttributes.Builder().build());
                db.log("output","print","PASS","Android print service opened");
            } catch(Exception e){ callback("onNativeError", error("Print service unavailable: "+e.getMessage())); }
        });
    }

    public void shareText(String subject,String text) {
        runOnUiThread(() -> {
            try {
                Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,subject==null?"HeritageFaith":subject);i.putExtra(Intent.EXTRA_TEXT,text==null?"":text);startActivity(Intent.createChooser(i,"Share from HeritageFaith"));db.log("output","share","PASS",subject==null?"":subject);
            } catch(Exception e){callback("onNativeError",error("Share action unavailable."));}
        });
    }

    public void callback(String function, JSONObject payload) {
        String js = "window.R10 && window.R10." + function + " && window.R10." + function + "(" + payload.toString() + ");";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    public static JSONObject error(String message) {
        JSONObject o=new JSONObject();try{o.put("ok",false);o.put("error",message==null?"Unknown error":message);}catch(Exception ignored){}return o;
    }

    private static File firstFile(File root){if(root==null||!root.exists())return null;if(root.isFile())return root;File[] a=root.listFiles();if(a==null)return null;for(File f:a)if(f.isFile())return f;for(File f:a)if(f.isDirectory()){File x=firstFile(f);if(x!=null)return x;}return null;}
    private static String mime(String name){String ext=MimeTypeMap.getFileExtensionFromUrl(name).toLowerCase(Locale.US);String m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return m==null?"application/octet-stream":m;}

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        db.log("system","app-close","PASS","Main activity destroyed"); executor.shutdown(); if(web!=null){web.removeJavascriptInterface("ArkforgeNative");web.destroy();} db.close(); super.onDestroy();
    }
}
