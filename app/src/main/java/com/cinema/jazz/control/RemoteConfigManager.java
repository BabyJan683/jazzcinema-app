package com.cinema.jazz.control;

import android.content.Context;
import android.content.SharedPreferences;
import com.cinema.jazz.BuildConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * v8.8 Remote Config — GitHub hosted, token-authenticated.
 *
 * CONFIG REPO: https://github.com/BabyJan683/jazzcinema-config
 * CONFIG FILE: config.json (edit directly on GitHub to update live)
 *
 * SUPPORTS UP TO 20+ DATABASES:
 *   • "database"          → Primary Movies DB
 *   • "db_backups"        → Array of up to 20 backup Movie DBs
 *   • "tiktok_database"   → Primary TikTok/Shorts DB
 *   • "tiktok_db_backups" → Array of up to 20 backup TikTok DBs
 *   • "extra_sources"     → Songs, Live TV, and other custom DBs
 *
 * BOTH flat keys (legacy) and nested objects (new) are supported.
 * Cache: 15 min — works fully offline with last known config.
 */
public class RemoteConfigManager {

    private static final String PREFS    = "jazz_remote_cfg";
    private static final String KEY_JSON = "cfg_json";
    private static final String KEY_TIME = "cfg_time";
    private static final long   CACHE_MS = 15 * 60 * 1000L;

    private static JSONObject current = null;

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH
    // ─────────────────────────────────────────────────────────────────────────

    /** Fetch config from GitHub (or local cache). Call from background thread. */
    public static JSONObject fetch(Context ctx) {
        SharedPreferences p   = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long              age = System.currentTimeMillis() - p.getLong(KEY_TIME, 0);

        // Serve from 15-min cache if still fresh
        if (age < CACHE_MS && p.contains(KEY_JSON)) {
            try {
                current = new JSONObject(p.getString(KEY_JSON, "{}"));
                return current;
            } catch (Exception ignored) {}
        }

        // Fetch from GitHub
        try {
            HttpURLConnection conn =
                (HttpURLConnection) new URL(BuildConfig.CONFIG_URL).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cache-Control", "no-cache");

            String token = BuildConfig.GITHUB_TOKEN;
            if (token != null && !token.isEmpty())
                conn.setRequestProperty("Authorization", "token " + token);

            if (conn.getResponseCode() == 200) {
                BufferedReader br =
                    new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String json = sb.toString();
                current = new JSONObject(json);
                p.edit()
                 .putString(KEY_JSON, json)
                 .putLong(KEY_TIME, System.currentTimeMillis())
                 .apply();
                return current;
            }
        } catch (Exception ignored) {}

        // Fallback: stale local cache
        try {
            String cached = p.getString(KEY_JSON, null);
            if (cached != null) {
                current = new JSONObject(cached);
                return current;
            }
        } catch (Exception ignored) {}

        // Last resort: hardcoded safe defaults
        try { current = new JSONObject(safeDefaults()); }
        catch (Exception e) { current = new JSONObject(); }
        return current;
    }

    /** Force re-fetch ignoring the 15-min cache (e.g. Settings "Refresh" button). */
    public static void forceRefresh(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().remove(KEY_TIME).apply();
        current = null;
        new Thread(() -> fetch(ctx)).start();
    }

    /** Returns the last successful fetch time (ms epoch), or 0 if never fetched. */
    public static long lastFetchTime(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getLong(KEY_TIME, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // APP CONTROL FLAGS
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isBlocked()       { return getBool("blocked",         false); }
    public static boolean maintenanceMode() { return getBool("maintenance_mode", false); }
    public static boolean forceUpdate()     { return getBool("force_update",     false); }
    public static String  blockedMessage()  { return getStr("block_message",    "Access blocked."); }
    public static String  maintenanceMsg()  { return getStr("maintenance_msg",  "Under maintenance."); }
    public static String  updateMessage()   { return getStr("update_message",   "Please update the app."); }
    public static String  noticeUrl()       { return getStr("notice_url",       ""); }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIMARY MOVIES DATABASE  (nested "database" object OR flat keys)
    // ─────────────────────────────────────────────────────────────────────────

    public static String dbHost() {
        JSONObject db = dbObject("database");
        if (db != null) return db.optString("host", "sql12.freesqldatabase.com");
        return getStr("db_host", "sql12.freesqldatabase.com");
    }
    public static int dbPort() {
        JSONObject db = dbObject("database");
        if (db != null) return db.optInt("port", 3306);
        return getInt("db_port", 3306);
    }
    public static String dbName() {
        JSONObject db = dbObject("database");
        if (db != null) return db.optString("name", "sql12824264");
        return getStr("db_name", "sql12824264");
    }
    public static String dbUser() {
        JSONObject db = dbObject("database");
        if (db != null) return db.optString("user", "sql12824264");
        return getStr("db_user", "sql12824264");
    }
    public static String dbPassword() {
        JSONObject db = dbObject("database");
        if (db != null) return db.optString("password", "");
        return getStr("db_password", "zx2faqegWz");
    }

    /** Full JDBC URL for the primary movies DB. */
    public static String jdbcUrl() {
        return buildJdbcUrl(dbHost(), dbPort(), dbName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BACKUP MOVIES DATABASES  (up to 20 in "db_backups" array)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the array of backup movie DBs from config.json "db_backups".
     * Each entry can have: host, port, name, user, password
     * OR legacy flat keys: db_host, db_port, db_name, db_user, db_password
     */
    public static JSONArray dbBackups() {
        if (current == null) return new JSONArray();
        JSONArray arr = current.optJSONArray("db_backups");
        return arr != null ? arr : new JSONArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIMARY TIKTOK / SHORTS DATABASE
    // ─────────────────────────────────────────────────────────────────────────

    public static String tiktokHost() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optString("host", dbHost());
        return getStr("tiktok_host", dbHost());
    }
    public static int tiktokPort() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optInt("port", 3306);
        return getInt("tiktok_port", 3306);
    }
    public static String tiktokName() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optString("name", dbName());
        return getStr("tiktok_name", dbName());
    }
    public static String tiktokUser() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optString("user", dbUser());
        return getStr("tiktok_user", dbUser());
    }
    public static String tiktokPassword() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optString("password", dbPassword());
        return getStr("tiktok_password", dbPassword());
    }
    public static String tiktokTable() {
        JSONObject db = dbObject("tiktok_database");
        if (db != null) return db.optString("table", "Tiktok");
        return getStr("tiktok_table", "Tiktok");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BACKUP TIKTOK DATABASES  (up to 20 in "tiktok_db_backups" array)
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONArray tiktokDbBackups() {
        if (current == null) return new JSONArray();
        JSONArray arr = current.optJSONArray("tiktok_db_backups");
        return arr != null ? arr : new JSONArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRA SOURCES — Songs, Live TV, custom content (in "extra_sources" array)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all extra source DBs.
     * Each entry: { type: "songs"|"livetv"|"custom", host, port, name, user, password, table }
     * Filter by type: getExtraSourcesByType("songs")
     */
    public static JSONArray extraSources() {
        if (current == null) return new JSONArray();
        JSONArray arr = current.optJSONArray("extra_sources");
        return arr != null ? arr : new JSONArray();
    }

    /** Get extra sources filtered by type ("songs", "livetv", "custom", etc.) */
    public static JSONArray getExtraSourcesByType(String type) {
        JSONArray all    = extraSources();
        JSONArray result = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            try {
                JSONObject src = all.getJSONObject(i);
                if (type.equalsIgnoreCase(src.optString("type", "")))
                    result.put(src);
            } catch (Exception ignored) {}
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATURE FLAGS
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean featureEnabled(String name) {
        JSONObject features = (current == null) ? null : current.optJSONObject("features");
        return features != null ? features.optBoolean(name, true) : true;
    }
    public static boolean shortsEnabled()          { return featureEnabled("shorts_enabled"); }
    public static boolean liveTvEnabled()          { return featureEnabled("live_tv_enabled"); }
    public static boolean downloadsEnabled()       { return featureEnabled("downloads_enabled"); }
    public static boolean continueWatchingEnabled(){ return featureEnabled("continue_watching_enabled"); }
    public static boolean backgroundSyncEnabled()  { return featureEnabled("background_sync_enabled"); }

    // ─────────────────────────────────────────────────────────────────────────
    // OFFLINE / SYNC SETTINGS
    // ─────────────────────────────────────────────────────────────────────────

    public static int offlineCacheHours() {
        JSONObject off = (current == null) ? null : current.optJSONObject("offline_settings");
        return off != null ? off.optInt("cache_expiry_hours", 24)
                           : getInt("offline_cache_hours", 24);
    }
    public static int maxCachedMovies() {
        JSONObject off = (current == null) ? null : current.optJSONObject("offline_settings");
        return off != null ? off.optInt("max_cached_movies", 500) : 500;
    }
    public static int maxCachedShorts() {
        JSONObject off = (current == null) ? null : current.optJSONObject("offline_settings");
        return off != null ? off.optInt("max_cached_shorts", 200)
                           : getInt("offline_shorts_count", 200);
    }
    public static int syncIntervalMinutes() {
        JSONObject sync = (current == null) ? null : current.optJSONObject("sync");
        return sync != null ? sync.optInt("interval_minutes", 30) : 30;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static JSONObject dbObject(String key) {
        if (current == null) return null;
        return current.optJSONObject(key);
    }
    private static boolean getBool(String k, boolean d) {
        return current == null ? d : current.optBoolean(k, d);
    }
    private static String getStr(String k, String d) {
        if (current == null) return d;
        String v = current.optString(k, "");
        return v.isEmpty() ? d : v;
    }
    private static int getInt(String k, int d) {
        return current == null ? d : current.optInt(k, d);
    }

    /** Build a JDBC MySQL URL with standard connection parameters. */
    public static String buildJdbcUrl(String host, int port, String name) {
        return "jdbc:mysql://" + host + ":" + port + "/" + name
             + "?useSSL=false&connectTimeout=10000&socketTimeout=20000"
             + "&useUnicode=true&characterEncoding=UTF-8";
    }

    private static String safeDefaults() {
        return "{"
             + "\"blocked\":false,"
             + "\"database\":{\"host\":\"sql12.freesqldatabase.com\",\"port\":3306,"
             +   "\"name\":\"sql12824264\",\"user\":\"sql12824264\",\"password\":\"zx2faqegWz\"},"
             + "\"db_backups\":[],"
             + "\"tiktok_database\":{\"host\":\"sql12.freesqldatabase.com\",\"port\":3306,"
             +   "\"name\":\"sql12824264\",\"user\":\"sql12824264\",\"password\":\"zx2faqegWz\","
             +   "\"table\":\"Tiktok\"},"
             + "\"tiktok_db_backups\":[],"
             + "\"extra_sources\":[],"
             + "\"offline_settings\":{\"cache_expiry_hours\":24,\"max_cached_movies\":500,\"max_cached_shorts\":200},"
             + "\"features\":{\"shorts_enabled\":true,\"live_tv_enabled\":true,"
             +   "\"downloads_enabled\":true,\"continue_watching_enabled\":true,"
             +   "\"background_sync_enabled\":true},"
             + "\"sync\":{\"interval_minutes\":30}"
             + "}";
    }
}
