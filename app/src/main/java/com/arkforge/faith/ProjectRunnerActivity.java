package com.arkforge.faith;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Sandboxed runner for imported/bundled HTML projects.
 * No JavascriptInterface is attached here. Requests outside project.r10.local are blocked.
 */
public class ProjectRunnerActivity extends Activity {
    private WebView web;
    private R10Database db;
    private File root;
    private String projectId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId=getIntent().getStringExtra("project_id"); db=new R10Database(this);
        try {
            JSONObject p=db.getProject(projectId); if(p==null)throw new IllegalArgumentException("Project not found");
            root=new File(p.optString("root_path")).getCanonicalFile(); if(!root.isDirectory())throw new IllegalArgumentException("Project root missing");
            File entry=findEntry(root); if(entry==null)throw new IllegalArgumentException("No index.html/HTML entry found");
            web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);s.setMediaPlaybackRequiresUserGesture(true);
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req){return serve(req.getUrl());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,String url){return serve(Uri.parse(url));}
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){Uri u=req.getUrl();if(isProject(u))return false;openExternal(u);return true;}
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){Uri u=Uri.parse(url);if(isProject(u))return false;openExternal(u);return true;}
            });
            String rel=relative(root,entry); web.loadUrl("https://project.r10.local/"+Uri.encode(projectId)+"/"+encodePath(rel));
            db.log("runner","open","PASS",projectId+" · "+rel+" · isolated bridge=false");
        } catch(Exception e){db.log("runner","open","FAIL",String.valueOf(e.getMessage()));finish();}
    }

    public static File findEntry(File root) {
        if(root==null||!root.isDirectory())return null;
        File direct=new File(root,"index.html");if(direct.isFile())return direct;
        File[] list=root.listFiles();if(list==null)return null;
        for(File f:list)if(f.isDirectory()){File x=new File(f,"index.html");if(x.isFile())return x;}
        for(File f:list)if(f.isFile()&&f.getName().toLowerCase(Locale.US).endsWith(".html"))return f;
        return null;
    }

    private WebResourceResponse serve(Uri uri){
        try{
            if(!isProject(uri))return blocked();
            java.util.List<String> seg=uri.getPathSegments();if(seg.size()<2||!projectId.equals(seg.get(0)))return blocked();
            StringBuilder rel=new StringBuilder();for(int i=1;i<seg.size();i++){if(i>1)rel.append('/');rel.append(seg.get(i));}
            File target=new File(root,rel.toString()).getCanonicalFile();String prefix=root.getPath()+File.separator;if(!target.getPath().startsWith(prefix)||!target.isFile())return notFound();
            String mime=mime(target.getName());String enc=isTextMime(mime)?"UTF-8":null;
            if ("text/html".equals(mime)) {
                if (target.length() > 16L*1024*1024) return new WebResourceResponse(mime,enc,new FileInputStream(target));
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream hin=new FileInputStream(target)){byte[] buf=new byte[32768];int n;while((n=hin.read(buf))!=-1)out.write(buf,0,n);}String html=new String(out.toByteArray(),StandardCharsets.UTF_8);
                String csp="<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' blob:; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self'; worker-src 'self' blob:; frame-src 'self'; object-src 'none'; base-uri 'self'\">";
                int head=html.toLowerCase(Locale.US).indexOf("<head>"); if(head>=0)html=html.substring(0,head+6)+csp+html.substring(head+6); else html=csp+html;
                return new WebResourceResponse(mime,enc,new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
            }
            InputStream in=new FileInputStream(target);return new WebResourceResponse(mime,enc,in);
        }catch(Exception e){return notFound();}
    }
    private boolean isProject(Uri u){return u!=null&&"https".equalsIgnoreCase(u.getScheme())&&"project.r10.local".equalsIgnoreCase(u.getHost());}
    private WebResourceResponse blocked(){java.util.HashMap<String,String> h=new java.util.HashMap<>();h.put("Cache-Control","no-store");return new WebResourceResponse("text/plain","UTF-8",403,"Blocked by R10 project sandbox",h,new ByteArrayInputStream("External subresource blocked by R10 project sandbox.".getBytes(StandardCharsets.UTF_8)));}
    private WebResourceResponse notFound(){java.util.HashMap<String,String> h=new java.util.HashMap<>();h.put("Cache-Control","no-store");return new WebResourceResponse("text/plain","UTF-8",404,"Not found",h,new ByteArrayInputStream("Not found".getBytes(StandardCharsets.UTF_8)));}
    private void openExternal(Uri uri){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));db.log("runner","external-link","PASS",String.valueOf(uri));}catch(Exception e){db.log("runner","external-link","FAIL",String.valueOf(uri));}}
    private static String relative(File r,File f)throws Exception{return f.getCanonicalPath().substring(r.getCanonicalPath().length()+1).replace(File.separatorChar,'/');}
    private static String encodePath(String p){StringBuilder b=new StringBuilder();for(String s:p.split("/")){if(b.length()>0)b.append('/');b.append(Uri.encode(s));}return b.toString();}
    private static String mime(String name){String m=URLConnection.guessContentTypeFromName(name);if(m!=null)return m;String ext=MimeTypeMap.getFileExtensionFromUrl(name);m=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);return m==null?"application/octet-stream":m;}
    private static boolean isTextMime(String m){return m.startsWith("text/")||m.contains("javascript")||m.contains("json")||m.contains("xml")||m.contains("svg");}
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null)web.destroy();if(db!=null)db.close();super.onDestroy();}
}
