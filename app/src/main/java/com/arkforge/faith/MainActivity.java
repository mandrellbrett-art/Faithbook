package com.arkforge.faith;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private static final int REQ_BACKUP_EXPORT = 1002;
    private WebView web;
    private R10Database db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String pendingImportKind = "project";

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
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { startActivityForResult(i, REQ_PICK); }
            catch (ActivityNotFoundException e) { callback("onNativeError", error("No Android document picker is available.")); }
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

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
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
