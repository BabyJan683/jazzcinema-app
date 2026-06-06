package com.cinema.jazz.util;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.cinema.jazz.Constants;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
public class TmdbHelper {
    private static final String TAG  = "TmdbHelper";
    private static final String BASE = "https://api.themoviedb.org/3";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build();
    public static class MovieInfo { public String overview=""; public List<Cast> cast=new ArrayList<>(); }
    public static class Cast { public String name,character,photoUrl;
        public Cast(String n,String c,String p){name=n;character=c;photoUrl=p;} }
    public interface Callback { void onResult(MovieInfo info); }
    public static void fetch(Context ctx, String title, Callback cb) {
        if (!isConnected(ctx)) { cb.onResult(null); return; }
        new Thread(()->{
            try {
                String sb = get(BASE+"/search/movie?query="+java.net.URLEncoder.encode(title,"UTF-8")+"&language=en-US&page=1");
                if (sb==null){cb.onResult(null);return;}
                JSONArray res = new JSONObject(sb).getJSONArray("results");
                if (res.length()==0){cb.onResult(new MovieInfo());return;}
                JSONObject f = res.getJSONObject(0);
                MovieInfo info = new MovieInfo();
                info.overview = f.optString("overview","");
                int id = f.getInt("id");
                String cb2 = get(BASE+"/movie/"+id+"/credits?language=en-US");
                if (cb2!=null) {
                    JSONArray ca = new JSONObject(cb2).getJSONArray("cast");
                    for (int i=0;i<Math.min(3,ca.length());i++) {
                        JSONObject c = ca.getJSONObject(i);
                        String p = c.optString("profile_path","");
                        info.cast.add(new Cast(c.optString("name",""),c.optString("character",""),
                            p.isEmpty()?null:Constants.TMDB_IMAGE_BASE+p));
                    }
                }
                cb.onResult(info);
            } catch (Exception e) { Log.e(TAG,e.getMessage()); cb.onResult(null); }
        }).start();
    }
    private static String get(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("accept","application/json")
            .header("Authorization","Bearer "+Constants.TMDB_TOKEN).build();
        try(Response resp=CLIENT.newCall(r).execute()){return resp.body()!=null?resp.body().string():null;}
    }
    private static boolean isConnected(Context ctx) {
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo i=cm!=null?cm.getActiveNetworkInfo():null; return i!=null&&i.isConnected();
    }
}
