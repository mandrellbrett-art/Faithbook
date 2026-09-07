package com.arkforge.faith;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class NetworkTools {
    private static final int MAX_BYTES=2*1024*1024;
    private NetworkTools(){}

    public static JSONObject safeFetch(String raw) throws Exception {
        URI uri=new URI(raw==null?"":raw.trim());
        if(!"https".equalsIgnoreCase(uri.getScheme()))throw new IllegalArgumentException("Argus fetch accepts HTTPS only");
        if(uri.getUserInfo()!=null)throw new IllegalArgumentException("URLs with embedded credentials are blocked");
        String host=uri.getHost();if(host==null||host.isBlank())throw new IllegalArgumentException("URL host is missing");
        rejectPrivateHost(host);
        HttpURLConnection c=(HttpURLConnection)new URL(uri.toString()).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","Thunderforge-R10-Standalone/10");c.setRequestProperty("Accept","text/plain,text/html,application/json,application/xml;q=0.8,*/*;q=0.3");
        int status=c.getResponseCode();
        if(status>=300&&status<400){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IllegalArgumentException("Redirect without Location");URI redirected=uri.resolve(loc);if(!"https".equalsIgnoreCase(redirected.getScheme()))throw new IllegalArgumentException("Redirect to non-HTTPS URL blocked");rejectPrivateHost(redirected.getHost());return safeFetch(redirected.toString());}
        InputStream in=status>=400?c.getErrorStream():c.getInputStream();if(in==null)throw new IllegalArgumentException("Remote server returned no body");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>MAX_BYTES)throw new IllegalArgumentException("Remote response exceeds 2 MB safety limit");b.write(buf,0,n);}in.close();String type=c.getContentType();String finalUrl=c.getURL().toString();c.disconnect();JSONObject o=new JSONObject();o.put("ok",status>=200&&status<300);o.put("status",status);o.put("url",finalUrl);o.put("content_type",type==null?"":type);o.put("bytes",total);o.put("text",new String(b.toByteArray(),StandardCharsets.UTF_8));return o;
    }

    private static void rejectPrivateHost(String host)throws Exception{
        if(host==null)throw new IllegalArgumentException("Host missing");String h=host.toLowerCase(Locale.US);if(h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal"))throw new IllegalArgumentException("Private/local hosts are blocked");
        InetAddress[] all=InetAddress.getAllByName(host);if(all.length==0)throw new IllegalArgumentException("Host did not resolve");for(InetAddress a:all){if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())throw new IllegalArgumentException("Private/local network addresses are blocked");byte[] x=a.getAddress();if(a instanceof Inet4Address){int p=x[0]&255,q=x[1]&255;if(p==0||p==10||p==127||p>=224||(p==169&&q==254)||(p==172&&q>=16&&q<=31)||(p==192&&q==168)||(p==100&&q>=64&&q<=127))throw new IllegalArgumentException("Private/special IPv4 ranges are blocked");}else if(a instanceof Inet6Address){int p=x[0]&255;if((p&0xfe)==0xfc)throw new IllegalArgumentException("Unique-local IPv6 ranges are blocked");}}
    }
}
