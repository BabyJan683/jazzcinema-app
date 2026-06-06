package com.cinema.jazz.auth;

import android.content.Context;
import com.cinema.jazz.model.UserProfile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;

public class AuthManager {

    private static UserProfile current;

    public static UserProfile getCurrentUser(Context ctx) {
        if (current == null) current = UserProfile.load(ctx);
        return current;
    }

    public static boolean isLoggedIn(Context ctx) {
        UserProfile u = getCurrentUser(ctx);
        return u != null && u.accessToken != null && !u.accessToken.isEmpty();
    }

    /** Register new user → Supabase auth + insert Jazz_Clients row */
    public static UserProfile register(String email, String password) throws Exception {
        JSONObject auth = SupabaseClient.signUp(email, password);
        if (auth.has("error") || (auth.has("_httpCode") && auth.getInt("_httpCode") >= 400))
            throw new Exception(auth.optString("error_description", auth.optString("msg","Registration failed")));

        String userId = auth.optJSONObject("user") != null
                ? auth.getJSONObject("user").getString("id") : auth.optString("id","");
        String token  = auth.optString("access_token","");

        // Insert into Jazz_Clients table
        SupabaseClient.insertClient(email, userId, token);

        UserProfile u = new UserProfile();
        u.id = userId; u.email = email; u.status = "Pending";
        u.plan = "Free"; u.accessToken = token;
        u.dailyWatchCount = 0;
        u.dailyWatchDate  = LocalDate.now().toString();
        current = u;
        return u;
    }

    /** Login → Supabase auth + fetch Jazz_Clients profile */
    public static UserProfile login(Context ctx, String email, String password) throws Exception {
        JSONObject auth = SupabaseClient.signIn(email, password);
        if (auth.has("error") || (auth.has("_httpCode") && auth.getInt("_httpCode") >= 400))
            throw new Exception(auth.optString("error_description", auth.optString("message","Login failed")));

        String token  = auth.optString("access_token","");
        String userId = "";
        if (auth.has("user")) userId = auth.getJSONObject("user").optString("id","");

        // Fetch profile from Jazz_Clients
        JSONObject clientResp = SupabaseClient.fetchClient(userId, token);
        JSONArray data = clientResp.optJSONArray("data");
        UserProfile u = new UserProfile();
        u.accessToken = token;
        u.email = email;
        u.id = userId;

        if (data != null && data.length() > 0) {
            JSONObject row = data.getJSONObject(0);
            u.status     = row.optString("status","Pending");
            u.plan       = row.optString("plan","Free");
            u.activeDate = row.optString("active_date","");
            u.expiryDate = row.optString("expiry_date","");
            u.dailyWatchCount = row.optInt("daily_watch_count",0);
            u.dailyWatchDate  = row.optString("daily_watch_date","");
        } else {
            u.status = "Pending"; u.plan = "Free";
        }

        if (u.isBanned())
            throw new Exception("BANNED: Your account has been banned. Contact support.");

        u.save(ctx);
        current = u;
        return u;
    }

    public static void logout(Context ctx) {
        try {
            if (current != null && current.accessToken != null)
                SupabaseClient.signOut(current.accessToken);
        } catch (Exception ignored) {}
        current = null;
        UserProfile.clear(ctx);
    }

    /** Check and increment daily watch counter for Pending users. Returns false if limit hit. */
    public static boolean checkAndIncrementWatch(Context ctx) {
        UserProfile u = getCurrentUser(ctx);
        if (u == null) return false;
        if (u.isUnlimited()) return true;            // Active = unlimited
        if (!u.isPending())  return false;           // Banned/Suspended/Expired

        String today = LocalDate.now().toString();
        if (!today.equals(u.dailyWatchDate)) {       // New day → reset count
            u.dailyWatchCount = 0;
            u.dailyWatchDate  = today;
        }
        if (u.dailyWatchCount >= 10) return false;   // Limit hit
        u.dailyWatchCount++;
        u.save(ctx);
        // Background sync to Supabase
        new Thread(() -> {
            try { SupabaseClient.incrementDailyWatch(u.id, u.dailyWatchCount, u.accessToken); }
            catch (Exception ignored) {}
        }).start();
        return true;
    }
}
