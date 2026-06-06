package com.cinema.jazz.db;

import android.content.Context;
import android.content.SharedPreferences;
import com.cinema.jazz.control.RemoteConfigManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * v8.8 — Multi-Database Failover Manager (20+ DB support).
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  MOVIES  (getConnection)                                     │
 * │    1. Primary "database" from config.json                    │
 * │    2. Backup 1..N from "db_backups" array (up to 20+)        │
 * │                                                              │
 * │  TIKTOK / SHORTS  (getTikTokConnection)                      │
 * │    1. Primary "tiktok_database" from config.json             │
 * │    2. Backup 1..N from "tiktok_db_backups" array (up to 20+) │
 * │                                                              │
 * │  EXTRA  (getExtraConnection)                                 │
 * │    Reads from "extra_sources" array filtered by type         │
 * │    e.g. type="songs", type="livetv"                         │
 * └──────────────────────────────────────────────────────────────┘
 *
 * HOW TO ADD DATABASES — just edit config.json on GitHub:
 *
 *   "db_backups": [
 *     { "host": "...", "name": "...", "user": "...", "password": "...", "port": 3306 },
 *     { "host": "...", "name": "...", "user": "...", "password": "...", "port": 3306 },
 *     ...up to 20+
 *   ]
 *
 *   "tiktok_db_backups": [
 *     { "host": "...", "name": "...", "user": "...", "password": "...", "port": 3306, "table": "Tiktok" }
 *   ]
 *
 *   "extra_sources": [
 *     { "type": "songs",  "host": "...", "name": "...", "user": "...", "password": "...", "port": 3306, "table": "songs" },
 *     { "type": "livetv", "host": "...", "name": "...", "user": "...", "password": "...", "port": 3306, "table": "channels" }
 *   ]
 *
 * NO REBUILD NEEDED — changes apply live on next app launch.
 */
public class DatabaseManager {

    private static final String PREFS            = "jazz_db_mgr";
    private static final String KEY_MOVIE_LABEL  = "movie_db_label";
    private static final String KEY_TIKTOK_LABEL = "tiktok_db_label";
    private static final int    TIMEOUT_MS       = 12_000;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC STATUS (for Settings screen)
    // ─────────────────────────────────────────────────────────────────────────

    /** Label of the currently active Movies DB ("Primary", "Backup 3", "Offline") */
    public static String getMovieDbLabel(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getString(KEY_MOVIE_LABEL, "Unknown");
    }

    /** Label of the currently active TikTok DB */
    public static String getTikTokDbLabel(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                  .getString(KEY_TIKTOK_LABEL, "Unknown");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MOVIES CONNECTION — tries primary, then up to 20 backups
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a live JDBC Connection for the Movies database.
     * Tries primary first, then each backup in order.
     * MUST be called from a background thread.
     *
     * @throws Exception if ALL databases fail — caller should use offline Room cache
     */
    public static Connection getConnection(Context ctx) throws Exception {
        ensureDriver();

        // 1. Primary movies DB
        try {
            Connection c = tryConnect(
                RemoteConfigManager.dbHost(),
                RemoteConfigManager.dbPort(),
                RemoteConfigManager.dbName(),
                RemoteConfigManager.dbUser(),
                RemoteConfigManager.dbPassword()
            );
            saveLabel(ctx, KEY_MOVIE_LABEL, "Primary");
            return c;
        } catch (Exception ignored) {}

        // 2. Backup DBs (up to 20+)
        JSONArray backups = RemoteConfigManager.dbBackups();
        for (int i = 0; i < backups.length(); i++) {
            try {
                JSONObject b = backups.getJSONObject(i);
                Connection c = tryConnectFromJson(b);
                saveLabel(ctx, KEY_MOVIE_LABEL, "Backup " + (i + 1));
                return c;
            } catch (Exception ignored) {}
        }

        // All failed
        saveLabel(ctx, KEY_MOVIE_LABEL, "Offline");
        throw new Exception("All movie databases unreachable. Using offline cache.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TIKTOK / SHORTS CONNECTION — tries primary, then up to 20 backups
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a live JDBC Connection for the TikTok/Shorts database.
     * Falls back to the Movies DB connection if no separate TikTok DB is configured.
     * MUST be called from a background thread.
     */
    public static Connection getTikTokConnection(Context ctx) throws Exception {
        ensureDriver();

        // 1. Primary TikTok DB
        try {
            Connection c = tryConnect(
                RemoteConfigManager.tiktokHost(),
                RemoteConfigManager.tiktokPort(),
                RemoteConfigManager.tiktokName(),
                RemoteConfigManager.tiktokUser(),
                RemoteConfigManager.tiktokPassword()
            );
            saveLabel(ctx, KEY_TIKTOK_LABEL, "TikTok Primary");
            return c;
        } catch (Exception ignored) {}

        // 2. TikTok backup DBs (up to 20+)
        JSONArray backups = RemoteConfigManager.tiktokDbBackups();
        for (int i = 0; i < backups.length(); i++) {
            try {
                JSONObject b = backups.getJSONObject(i);
                Connection c = tryConnectFromJson(b);
                saveLabel(ctx, KEY_TIKTOK_LABEL, "TikTok Backup " + (i + 1));
                return c;
            } catch (Exception ignored) {}
        }

        // 3. Fall back to the primary movies DB (same server, different table)
        try {
            Connection c = getConnection(ctx);
            saveLabel(ctx, KEY_TIKTOK_LABEL, "Shared (Movie DB)");
            return c;
        } catch (Exception ignored) {}

        saveLabel(ctx, KEY_TIKTOK_LABEL, "Offline");
        throw new Exception("All TikTok databases unreachable. Using offline cache.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRA SOURCES — Songs, Live TV, custom content
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a connection to an extra source DB by type.
     * Types are defined in config.json "extra_sources" array.
     * Examples: "songs", "livetv", "news"
     * Tries each matching source in order until one connects.
     */
    public static Connection getExtraConnection(Context ctx, String type) throws Exception {
        ensureDriver();
        JSONArray sources = RemoteConfigManager.getExtraSourcesByType(type);
        for (int i = 0; i < sources.length(); i++) {
            try {
                JSONObject src = sources.getJSONObject(i);
                Connection c = tryConnectFromJson(src);
                return c;
            } catch (Exception ignored) {}
        }
        throw new Exception("No working '" + type + "' database found in extra_sources.");
    }

    /**
     * Returns the table name for an extra source type from config.json.
     * e.g. getExtraTable("songs") → "songs" or whatever is set in config
     */
    public static String getExtraTable(String type) {
        JSONArray sources = RemoteConfigManager.getExtraSourcesByType(type);
        if (sources.length() == 0) return type;
        try { return sources.getJSONObject(0).optString("table", type); }
        catch (Exception e) { return type; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private static void ensureDriver() throws Exception {
        try { Class.forName("com.mysql.jdbc.Driver"); }
        catch (Throwable t) { throw new Exception("MySQL JDBC driver not found"); }
    }

    /**
     * Parse a JSONObject (from db_backups, tiktok_db_backups, or extra_sources)
     * and attempt a connection. Supports both new keys (host/name/user/password)
     * and legacy keys (db_host/db_name/db_user/db_password).
     */
    private static Connection tryConnectFromJson(JSONObject b) throws Exception {
        // Try new-style keys first, fall back to legacy
        String host = b.optString("host",     b.optString("db_host", ""));
        int    port = b.optInt   ("port",     b.optInt("db_port", 3306));
        String name = b.optString("name",     b.optString("db_name", ""));
        String user = b.optString("user",     b.optString("db_user", ""));
        String pass = b.optString("password", b.optString("db_password", ""));
        return tryConnect(host, port, name, user, pass);
    }

    private static Connection tryConnect(String host, int port, String name,
                                          String user, String pass) throws Exception {
        if (host == null || host.isEmpty() || name == null || name.isEmpty())
            throw new Exception("Empty DB config — skipping");

        String url = RemoteConfigManager.buildJdbcUrl(host, port, name)
                   + "&connectTimeout=" + TIMEOUT_MS
                   + "&socketTimeout=" + (TIMEOUT_MS + 5000);

        return DriverManager.getConnection(url, user, pass);
    }

    private static void saveLabel(Context ctx, String key, String label) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
           .edit().putString(key, label).apply();
    }
}
