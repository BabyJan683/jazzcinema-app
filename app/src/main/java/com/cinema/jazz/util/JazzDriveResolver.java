package com.cinema.jazz.util;
import android.util.Log;
import org.json.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;
public class JazzDriveResolver {
    public interface ResolveCallback{void onResolved(String url);void onError(String msg);}
    private static final String TAG="JazzDriveResolver",BASE="https://cloud.jazzdrive.com.pk";
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36";
    public static void resolve(String shareUrl,ResolveCallback cb){
        if(shareUrl==null||shareUrl.isEmpty()){cb.onError("No share URL");return;}
        String token=extractToken(shareUrl);
        if(token==null||token.isEmpty()){cb.onError("Invalid share URL");return;}
        OkHttpClient client=buildClient();
        String loginJson="{\"data\":{\"accesstoken\":\""+token+"\"}}";
        Request loginReq=new Request.Builder().url(BASE+"/sapi/link/login?action=login")
            .post(RequestBody.create(loginJson,MediaType.parse("application/json;charset=UTF-8")))
            .header("Accept","application/json,*/*").header("Content-Type","application/json;charset=UTF-8")
            .header("Origin",BASE).header("Referer",BASE+"/share/f/"+token).header("User-Agent",UA).build();
        String sessionCookie,validationKey,folderId;
        try(Response r=client.newCall(loginReq).execute()){
            String body=r.body()!=null?r.body().string():"";
            Log.d(TAG,"Login["+r.code()+"]:"+body.substring(0,Math.min(200,body.length())));
            sessionCookie=extractCookie(r,"JSESSIONID");
            if(body.isEmpty()||body.trim().startsWith("<")){cb.onError("Jazz Drive blocked ("+r.code()+")");return;}
            try{JSONObject root=new JSONObject(body);
                if(root.has("status")&&!"success".equalsIgnoreCase(root.optString("status")))
                    {cb.onError("JazzDrive: "+root.optString("message",root.optString("msg","Login failed")));return;}
                JSONObject data=root.getJSONObject("data");
                validationKey=data.getString("validationkey");
                folderId=data.has("link")?data.getJSONObject("link").optString("folderid",""):data.optString("folderid","");
                if(folderId.isEmpty()||folderId.equals("-1")){cb.onError("JazzDrive: folderid missing");return;}
            }catch(Exception e){cb.onError("JazzDrive login parse: "+e.getMessage());return;}
        }catch(Exception e){cb.onError("Network(login): "+e.getMessage());return;}
        String fields="{\"data\":{\"fields\":[\"name\",\"url\",\"playbackurl\",\"videometadata\"]}}";
        String mediaUrl=BASE+"/sapi/media?action=get&folderid="+folderId+"&shared=true&validationkey="+validationKey;
        Request.Builder mb=new Request.Builder().url(mediaUrl)
            .post(RequestBody.create(fields,MediaType.parse("application/json;charset=UTF-8")))
            .header("Accept","application/json,*/*").header("Content-Type","application/json;charset=UTF-8")
            .header("Origin",BASE).header("Referer",BASE+"/share/f/"+token).header("User-Agent",UA);
        if(sessionCookie!=null&&!sessionCookie.isEmpty())mb.header("Cookie","JSESSIONID="+sessionCookie);
        try(Response r=client.newCall(mb.build()).execute()){
            String body=r.body()!=null?r.body().string():"";
            Log.d(TAG,"Media["+r.code()+"]:"+body.substring(0,Math.min(300,body.length())));
            if(body.isEmpty()||body.trim().startsWith("<")){cb.onError("Jazz Drive rate limited.");return;}
            try{JSONArray media=new JSONObject(body).getJSONObject("data").getJSONArray("media");
                if(media.length()==0){cb.onError("No files in shared folder");return;}
                for(int i=0;i<media.length();i++){JSONObject item=media.getJSONObject(i);
                    String url=item.optString("url",""),pb=item.optString("playbackurl","");
                    if(url.startsWith("http")&&url.contains("jazzdrive")){cb.onResolved(url);return;}
                    if(pb.startsWith("http")&&pb.contains("jazzdrive")){cb.onResolved(pb);return;}
                    if(url.startsWith("http")){cb.onResolved(url);return;}}
                cb.onError("No playable URL found");
            }catch(Exception e){cb.onError("Media parse: "+e.getMessage());}
        }catch(Exception e){cb.onError("Network(media): "+e.getMessage());}
    }
    private static String extractToken(String url){if(url==null)return null;int i=url.indexOf("/share/f/");if(i<0)return null;String t=url.substring(i+9);int q=t.indexOf('?');if(q>=0)t=t.substring(0,q);int s=t.indexOf('/');if(s>=0)t=t.substring(0,s);return t.trim();}
    private static String extractCookie(Response r,String name){for(String h:r.headers("Set-Cookie")){String[]ps=h.split(";");if(ps.length>0&&ps[0].trim().startsWith(name+"="))return ps[0].trim().substring(name.length()+1);}return null;}
    private static OkHttpClient buildClient(){try{TrustManager[]t={new X509TrustManager(){public void checkClientTrusted(X509Certificate[]c,String a){}public void checkServerTrusted(X509Certificate[]c,String a){}public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}}};SSLContext sc=SSLContext.getInstance("TLS");sc.init(null,t,new java.security.SecureRandom());return new OkHttpClient.Builder().sslSocketFactory(sc.getSocketFactory(),(X509TrustManager)t[0]).hostnameVerifier((h,s)->true).followRedirects(true).followSslRedirects(true).connectTimeout(20,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build();}catch(Exception e){return new OkHttpClient();}}
}
