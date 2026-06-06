package com.cinema.jazz.db;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import com.cinema.jazz.model.Song;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * SongsRepository — fetches songs from the database.
 *
 * ══════════════════════════════════════════════════════════════
 *  TABLE NAME — must be IDENTICAL in all 3 places:
 *
 *  1. MySQL Database:
 *       CREATE TABLE songs ( id INT AUTO_INCREMENT PRIMARY KEY,
 *         title VARCHAR(255), artist VARCHAR(255),
 *         category VARCHAR(100), drive_url TEXT,
 *         thumbnail_url TEXT, release_year INT DEFAULT 0,
 *         duration_sec INT DEFAULT 0 );
 *
 *  2. GitHub config.json (BabyJan683/jazzcinema-config):
 *       "extra_sources": [
 *         { "type": "songs", "table": "songs",
 *           "host": "...", "name": "...",
 *           "user": "...", "password": "..." }
 *       ]
 *
 *  3. This file (app source):
 *       private static final String TABLE = "songs";  ← HERE
 *
 *  Change the table name in config.json and it updates everywhere.
 * ══════════════════════════════════════════════════════════════
 *
 * DatabaseManager.getExtraConnection("songs") handles:
 *   • Uses the DB credentials from config.json "extra_sources" type=songs
 *   • Falls back to backup song DBs in order
 *   • No rebuild needed when you add/change DB in config.json
 */
public class SongsRepository {

    // ── Table name — read from config.json at runtime ─────────────────────────
    // Change "songs" in config.json → "extra_sources[type=songs].table"
    // and this will pick it up automatically. No rebuild needed.
    private static final String DEFAULT_TABLE = "songs";
    private static final int    PAGE_SIZE     = 30;

    public interface Callback {
        void onResult(List<Song> songs);
        void onError(String message);
    }

    // ── Fetch all songs (or by category) ─────────────────────────────────────

    /** Fetch all songs, newest first */
    public static void fetchAll(Context ctx, Callback cb) {
        fetchByCategory(ctx, null, cb);
    }

    /** Fetch songs filtered by category. Pass null to get all. */
    public static void fetchByCategory(Context ctx, String category, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!hasInternet(ctx)) {
                post(cb, null, "📡 No internet connection.");
                return;
            }
            try {
                // Gets table name from config.json → extra_sources[type=songs].table
                String table = DatabaseManager.getExtraTable("songs");
                if (table == null || table.isEmpty()) table = DEFAULT_TABLE;

                String sql;
                if (category != null && !category.isEmpty()) {
                    sql = "SELECT id, title, artist, category, drive_url, thumbnail_url,"
                        + " release_year, duration_sec FROM " + table
                        + " WHERE category = ? AND drive_url IS NOT NULL"
                        + " ORDER BY id DESC LIMIT " + PAGE_SIZE;
                } else {
                    sql = "SELECT id, title, artist, category, drive_url, thumbnail_url,"
                        + " release_year, duration_sec FROM " + table
                        + " WHERE drive_url IS NOT NULL"
                        + " ORDER BY id DESC LIMIT " + PAGE_SIZE;
                }

                try (Connection conn = DatabaseManager.getExtraConnection(ctx, "songs");
                     PreparedStatement ps = conn.prepareStatement(sql)) {

                    if (category != null && !category.isEmpty())
                        ps.setString(1, category);

                    try (ResultSet rs = ps.executeQuery()) {
                        List<Song> list = new ArrayList<>();
                        while (rs.next()) {
                            list.add(new Song(
                                rs.getInt("id"),
                                rs.getString("title"),
                                safeStr(rs, "artist"),
                                safeStr(rs, "category"),
                                rs.getString("drive_url"),
                                safeStr(rs, "thumbnail_url"),
                                rs.getInt("release_year"),
                                rs.getInt("duration_sec")
                            ));
                        }
                        if (list.isEmpty())
                            post(cb, null, "No songs found.");
                        else
                            post(cb, list, null);
                    }
                }

            } catch (Exception e) {
                post(cb, null, "❌ Could not load songs: " + e.getMessage());
            }
        });
    }

    /** Search songs by title or artist */
    public static void search(Context ctx, String query, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!hasInternet(ctx)) {
                post(cb, null, "📡 No internet connection.");
                return;
            }
            try {
                String table = DatabaseManager.getExtraTable("songs");
                if (table == null || table.isEmpty()) table = DEFAULT_TABLE;

                String sql = "SELECT id, title, artist, category, drive_url, thumbnail_url,"
                           + " release_year, duration_sec FROM " + table
                           + " WHERE (title LIKE ? OR artist LIKE ?)"
                           + " AND drive_url IS NOT NULL"
                           + " ORDER BY id DESC LIMIT " + PAGE_SIZE;

                try (Connection conn = DatabaseManager.getExtraConnection(ctx, "songs");
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    String q = "%" + query + "%";
                    ps.setString(1, q);
                    ps.setString(2, q);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<Song> list = new ArrayList<>();
                        while (rs.next()) {
                            list.add(new Song(
                                rs.getInt("id"),
                                rs.getString("title"),
                                safeStr(rs, "artist"),
                                safeStr(rs, "category"),
                                rs.getString("drive_url"),
                                safeStr(rs, "thumbnail_url"),
                                rs.getInt("release_year"),
                                rs.getInt("duration_sec")
                            ));
                        }
                        post(cb, list.isEmpty() ? new ArrayList<>() : list, null);
                    }
                }
            } catch (Exception e) {
                post(cb, null, "❌ Search failed: " + e.getMessage());
            }
        });
    }

    /** Fetch list of distinct song categories */
    public static void fetchCategories(Context ctx, java.util.function.Consumer<List<String>> onResult) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String table = DatabaseManager.getExtraTable("songs");
                if (table == null || table.isEmpty()) table = DEFAULT_TABLE;
                String sql = "SELECT DISTINCT category FROM " + table
                           + " WHERE category IS NOT NULL AND category <> ''"
                           + " ORDER BY category ASC";
                try (Connection conn = DatabaseManager.getExtraConnection(ctx, "songs");
                     PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    List<String> cats = new ArrayList<>();
                    while (rs.next()) cats.add(rs.getString("category"));
                    new Handler(Looper.getMainLooper()).post(() -> onResult.accept(cats));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> onResult.accept(new ArrayList<>()));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasInternet(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
            return info != null && info.isConnected();
        } catch (Throwable t) { return false; }
    }

    private static String safeStr(ResultSet rs, String col) {
        try { String v = rs.getString(col); return v != null ? v : ""; }
        catch (Exception e) { return ""; }
    }

    private static void post(Callback cb, List<Song> list, String err) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (err != null) cb.onError(err);
            else             cb.onResult(list);
        });
    }
}
