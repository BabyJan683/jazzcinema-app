package com.cinema.jazz.ui.shorts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import com.cinema.jazz.control.RemoteConfigManager;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.CachedShortEntity;
import com.cinema.jazz.db.DatabaseManager;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * v8.7 — Shorts data layer with:
 *  • Multi-DB failover via DatabaseManager (primary → backup 1 → backup 2 …)
 *  • Offline mode: serves locally cached shorts when all DBs unreachable
 *  • Seen-video tracking so same video isn't repeated until all are watched
 *  • Persists newly fetched shorts into Room for offline access
 */
public class ShortsRepository {

    private static final int    PAGE_SIZE  = 20;
    private static final String PREFS_NAME = "jazz_shorts_seen";
    private static final String KEY_SEEN   = "seen_ids";
    private static final int    MAX_SEEN   = 1000;

    public interface Callback {
        void onResult(List<ShortVideo> videos);
        void onError(String message);
    }

    // ── Main fetch — online first, offline fallback ───────────────────────────

    public static void fetchNext(Context ctx, Callback cb) {
        Set<String> seen = getSeenIds(ctx);
        Executors.newSingleThreadExecutor().execute(() -> {

            if (hasInternet(ctx)) {
                // Try live DB (primary → backups)
                try {
                    String table = RemoteConfigManager.tiktokTable();
                    String sc = seen.isEmpty() ? "" :
                        " AND id NOT IN (" + String.join(",", seen) + ")";
                    String sql = "SELECT id, title, drive_url, category, release_year"
                               + " FROM " + table
                               + " WHERE drive_url IS NOT NULL AND drive_url <> ''"
                               + sc + " ORDER BY RAND() LIMIT " + PAGE_SIZE;

                    try (Connection conn = DatabaseManager.getTikTokConnection(ctx);
                         PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {

                        List<ShortVideo> list = new ArrayList<>();
                        while (rs.next()) {
                            list.add(new ShortVideo(
                                rs.getInt("id"),
                                rs.getString("title"),
                                rs.getString("drive_url"),
                                rs.getString("category"),
                                rs.getInt("release_year")
                            ));
                        }

                        if (list.isEmpty() && !seen.isEmpty()) {
                            resetSeen(ctx);
                            fetchNext(ctx, cb);
                            return;
                        }

                        // Persist to offline cache
                        persistShorts(ctx, list);

                        post(cb, list, null);
                        return;
                    }
                } catch (Exception e) {
                    // All DBs failed — fall through to offline cache
                }
            }

            // ── Offline fallback ──────────────────────────────────────────────
            List<CachedShortEntity> cached = AppDatabase.getInstance(ctx)
                                                        .cachedShortDao().getAll();
            if (cached.isEmpty()) {
                post(cb, null, "📡 No internet and no offline shorts available.");
                return;
            }

            // Filter out already-seen, shuffle, take PAGE_SIZE
            List<ShortVideo> offlineList = new ArrayList<>();
            for (CachedShortEntity e : cached) {
                if (!seen.contains(String.valueOf(e.id)))
                    offlineList.add(new ShortVideo(e.id, e.title, e.driveUrl, e.category, e.releaseYear));
            }
            if (offlineList.isEmpty()) {
                resetSeen(ctx);   // All watched — reset and replay
                for (CachedShortEntity e : cached)
                    offlineList.add(new ShortVideo(e.id, e.title, e.driveUrl, e.category, e.releaseYear));
            }
            Collections.shuffle(offlineList);
            post(cb, offlineList.subList(0, Math.min(PAGE_SIZE, offlineList.size())), null);
        });
    }

    // ── Seen tracking ─────────────────────────────────────────────────────────

    public static void markSeen(Context ctx, int videoId) {
        SharedPreferences p    = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String>       seen = new HashSet<>(p.getStringSet(KEY_SEEN, Collections.emptySet()));
        seen.add(String.valueOf(videoId));
        if (seen.size() > MAX_SEEN) {
            List<String> l = new ArrayList<>(seen);
            seen = new HashSet<>(l.subList(l.size() - MAX_SEEN, l.size()));
        }
        p.edit().putStringSet(KEY_SEEN, seen).apply();
    }

    // ── Offline cache helpers ─────────────────────────────────────────────────

    private static void persistShorts(Context ctx, List<ShortVideo> list) {
        try {
            List<CachedShortEntity> entities = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (ShortVideo v : list) {
                CachedShortEntity e = new CachedShortEntity();
                e.id = v.id; e.title = v.title; e.driveUrl = v.driveUrl;
                e.category = v.category; e.releaseYear = v.releaseYear;
                e.cachedAt = now;
                entities.add(e);
            }
            AppDatabase.getInstance(ctx).cachedShortDao().insertAll(entities);
        } catch (Throwable ignored) {}
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static boolean hasInternet(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo i = cm != null ? cm.getActiveNetworkInfo() : null;
            return i != null && i.isConnected();
        } catch (Throwable t) { return false; }
    }

    private static void post(Callback cb, List<ShortVideo> list, String err) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (err != null) cb.onError(err);
            else cb.onResult(list);
        });
    }

    private static Set<String> getSeenIds(Context ctx) {
        return new HashSet<>(ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .getStringSet(KEY_SEEN, Collections.emptySet()));
    }

    private static void resetSeen(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().remove(KEY_SEEN).apply();
    }
}
