package com.cinema.jazz.auth;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

/**
 * Lightweight Supabase REST + Auth client using only java.net.
 * Supports: Auth, Jazz_Clients CRUD, packages, subscriptions, data usage.
 */
public class SupabaseClient {

    private static final String BASE_URL = "https://kyuvvglwsrewbhlrhhdb.supabase.co";
    private static final String ANON_KEY = "sb_publishable_O8MAhyvMJaBEhJQBmASFrA_KkkGnlvk";

    // ── Auth endpoints ────────────────────────────────────────────────────────
    public static JSONObject signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email); body.put("password", password);
        return post("/auth/v1/signup", body.toString(), null);
    }

    public static JSONObject signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email); body.put("password", password);
        return post("/auth/v1/token?grant_type=password", body.toString(), null);
    }

    public static JSONObject signOut(String accessToken) throws Exception {
        return post("/auth/v1/logout", "{}", accessToken);
    }

    // ── Jazz_Clients ──────────────────────────────────────────────────────────
    public static JSONObject insertClient(String email, String userId, String accessToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("id", userId); body.put("email", email);
        body.put("status", "Pending"); body.put("plan", "Free");
        return postTable("/rest/v1/Jazz_Clients", body.toString(), accessToken);
    }

    public static JSONObject fetchClient(String userId, String accessToken) throws Exception {
        return getTable("/rest/v1/Jazz_Clients?id=eq." + URLEncoder.encode(userId, "UTF-8") + "&select=*", accessToken);
    }

    public static JSONObject incrementDailyWatch(String userId, int newCount, String accessToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("daily_watch_count", newCount);
        body.put("daily_watch_date", java.time.LocalDate.now().toString());
        return patchTable("/rest/v1/Jazz_Clients?id=eq." + URLEncoder.encode(userId, "UTF-8"), body.toString(), accessToken);
    }

    // ── Data Usage ────────────────────────────────────────────────────────────
    /**
     * Upsert data usage record for the user.
     * @param usedBytes total bytes used this billing period
     */
    public static JSONObject upsertDataUsage(String userId, long usedBytes, String accessToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("user_id", userId);
        body.put("used_bytes", usedBytes);
        body.put("updated_at", java.time.Instant.now().toString());
        URL url = new URL(BASE_URL + "/rest/v1/user_data_usage");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation");
        if (accessToken != null) c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes("UTF-8")); }
        return readResponse(c);
    }

    /** Fetch data usage for this user. */
    public static JSONObject fetchDataUsage(String userId, String accessToken) throws Exception {
        return getTable("/rest/v1/user_data_usage?user_id=eq." + URLEncoder.encode(userId, "UTF-8") + "&select=*", accessToken);
    }

    /** Fetch the user's active subscription (if any). */
    public static JSONObject fetchActiveSubscription(String userId, String accessToken) throws Exception {
        String today = java.time.LocalDate.now().toString();
        return getTable("/rest/v1/user_subscriptions?user_id=eq." + URLEncoder.encode(userId, "UTF-8")
            + "&expires_at=gte." + today + "&select=*,packages(*)", accessToken);
    }

    /** Fetch all available packages. */
    public static JSONObject fetchPackages(String accessToken) throws Exception {
        return getTable("/rest/v1/packages?select=*&order=data_gb.asc", accessToken);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────
    private static JSONObject post(String path, String jsonBody, String token) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", ANON_KEY);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) { os.write(jsonBody.getBytes("UTF-8")); }
        return readResponse(c);
    }

    private static JSONObject postTable(String path, String jsonBody, String token) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Prefer", "return=representation");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) { os.write(jsonBody.getBytes("UTF-8")); }
        return readResponse(c);
    }

    private static JSONObject patchTable(String path, String jsonBody, String token) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("PATCH");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Prefer", "return=representation");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(15000);
        try (OutputStream os = c.getOutputStream()) { os.write(jsonBody.getBytes("UTF-8")); }
        return readResponse(c);
    }

    private static JSONObject getTable(String path, String token) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setRequestProperty("apikey", ANON_KEY);
        c.setRequestProperty("Accept", "application/json");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        return readResponse(c);
    }

    private static JSONObject readResponse(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line; while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        String body = sb.toString();
        if (body.startsWith("[")) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("data", new org.json.JSONArray(body));
            wrapper.put("code", code);
            return wrapper;
        }
        JSONObject result = body.isEmpty() ? new JSONObject() : new JSONObject(body);
        result.put("_httpCode", code);
        return result;
    }
}
