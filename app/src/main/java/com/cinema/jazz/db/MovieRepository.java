package com.cinema.jazz.db;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.cinema.jazz.control.RemoteConfigManager;
import com.cinema.jazz.model.Movie;
import java.sql.*;
import java.util.*;

/**
 * v8.7 — Movie data layer with:
 *  • Multi-DB failover via DatabaseManager (primary → backup 1 → backup 2 …)
 *  • Offline mode: serves Room cache when all DBs unreachable
 *  • "🆕 Recently Added" category always shown first (sorted by created_at DESC)
 *  • "🏆 Latest Movies" category — top 20 by release_year DESC
 *  • All other categories follow in fixed order
 */
public class MovieRepository {
    public interface Callback { void onResult(Map<String, List<Movie>> data, String error); }

    public static boolean hasInternet(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo i = cm != null ? cm.getActiveNetworkInfo() : null;
            return i != null && i.isConnected();
        } catch (Throwable t) { return false; }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main load: serves Room cache immediately, then refreshes from MySQL (multi-DB).
     * If all DBs fail and cache exists → shows offline data with "Offline Mode" notice.
     */
    public static void fetchWithCache(Context ctx, Callback onCache, Callback onFresh) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(ctx);
            List<CachedMovieEntity> cached = db.cachedMovieDao().getAll();

            // Serve stale cache immediately so UI loads fast
            if (!cached.isEmpty())
                onCache.onResult(buildMap(toMovies(cached)), null);

            if (!hasInternet(ctx)) {
                if (cached.isEmpty())
                    onFresh.onResult(null, "📡 No internet. Connect to load movies.");
                else
                    onFresh.onResult(buildMap(toMovies(cached)), "📵 Offline mode — showing cached data");
                return;
            }

            // Try live fetch (primary → backups)
            Map<String, List<Movie>> fresh = fetchMySQL(ctx, null);
            if (fresh == null) {
                if (cached.isEmpty())
                    onFresh.onResult(null, "❌ Could not connect to any database.");
                else
                    onFresh.onResult(buildMap(toMovies(cached)),
                        "⚠️ DB unreachable — showing cached data (" + DatabaseManager.getMovieDbLabel(ctx) + ")");
                return;
            }
            persistToCache(ctx, fresh);
            onFresh.onResult(fresh, null);
        }).start();
    }

    /**
     * Refresh: fetches only movies newer than newest cached entry.
     * Falls back to offline cache message if no internet.
     */
    public static void fetchNewOnly(Context ctx, Callback onResult) {
        new Thread(() -> {
            if (!hasInternet(ctx)) {
                onResult.onResult(Collections.emptyMap(), "📡 No internet connection.");
                return;
            }
            AppDatabase db = AppDatabase.getInstance(ctx);
            List<CachedMovieEntity> cached = db.cachedMovieDao().getAll();
            String newestDate = null;
            for (CachedMovieEntity e : cached) {
                if (e.createdAt != null && !e.createdAt.isEmpty())
                    if (newestDate == null || e.createdAt.compareTo(newestDate) > 0)
                        newestDate = e.createdAt;
            }
            Map<String, List<Movie>> fresh = fetchMySQL(ctx, newestDate);
            if (fresh == null || fresh.isEmpty()) {
                onResult.onResult(Collections.emptyMap(), null);
                return;
            }
            persistToCache(ctx, fresh);
            onResult.onResult(fresh, null);
        }).start();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static Map<String, List<Movie>> fetchMySQL(Context ctx, String newerThan) {
        String sql = "SELECT id,category,title,thumbnail_url,drive_url,play_url,release_year,created_at"
                   + " FROM Movies";
        if (newerThan != null && !newerThan.isEmpty())
            sql += " WHERE created_at > '" + newerThan.replace("'", "''") + "'";
        sql += " ORDER BY created_at DESC";

        try (Connection c = DatabaseManager.getConnection(ctx);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            List<Movie> all = new ArrayList<>();
            while (rs.next()) {
                Movie m = new Movie();
                m.id           = rs.getInt("id");
                m.category     = s(rs.getString("category"));
                m.title        = s(rs.getString("title"));
                m.thumbnailUrl = s(rs.getString("thumbnail_url"));
                m.driveUrl     = s(rs.getString("drive_url"));
                m.playUrl      = s(rs.getString("play_url"));
                m.releaseYear  = rs.getInt("release_year");
                m.createdAt    = s(rs.getString("created_at"));
                all.add(m);
            }
            return all.isEmpty() ? Collections.emptyMap() : buildMap(all);
        } catch (Throwable t) { return null; }
    }

    private static void persistToCache(Context ctx, Map<String, List<Movie>> fresh) {
        AppDatabase db = AppDatabase.getInstance(ctx);
        List<CachedMovieEntity> save = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (List<Movie> list : fresh.values())
            for (Movie m : list) {
                if (seen.contains(m.id)) continue;
                seen.add(m.id);
                CachedMovieEntity e = new CachedMovieEntity();
                e.id = m.id; e.title = m.title; e.category = m.category;
                e.thumbnailUrl = m.thumbnailUrl; e.driveUrl = m.driveUrl;
                e.playUrl = m.playUrl; e.releaseYear = m.releaseYear;
                e.createdAt = m.createdAt; e.cachedAt = System.currentTimeMillis();
                save.add(e);
            }
        db.cachedMovieDao().clearAll();
        db.cachedMovieDao().insertAll(save);
    }

    /**
     * Build display map — order:
     *  🆕 Recently Added  — top 20 by created_at DESC (newest first)
     *  🏆 Latest Movies   — top 20 by release_year DESC
     *  South, Bollywood, Hollywood, Pakistani, Turkish, Urdu Dubbed, Trending
     *  … any other categories
     */
    private static Map<String, List<Movie>> buildMap(List<Movie> all) {
        // Sort master list by created_at DESC (most recently added = first)
        List<Movie> byDate = new ArrayList<>(all);
        byDate.sort((a, b) -> {
            if (a.createdAt == null && b.createdAt == null) return 0;
            if (a.createdAt == null) return 1;
            if (b.createdAt == null) return -1;
            return b.createdAt.compareTo(a.createdAt);
        });

        // Sort by release year DESC for "Latest Movies"
        List<Movie> byYear = new ArrayList<>(all);
        byYear.sort((a, b) -> Integer.compare(b.releaseYear, a.releaseYear));

        // Group by category
        Map<String, List<Movie>> byCat = new LinkedHashMap<>();
        for (Movie m : byDate) {
            byCat.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);
        }

        Map<String, List<Movie>> ord = new LinkedHashMap<>();

        // 1. Recently Added — top 20 newest (by created_at)
        ord.put("🆕 Recently Added",
            new ArrayList<>(byDate.subList(0, Math.min(20, byDate.size()))));

        // 2. Latest Movies — top 20 highest release year
        List<Movie> latestDedup = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (Movie m : byYear) {
            if (seenIds.add(m.id)) latestDedup.add(m);
            if (latestDedup.size() >= 20) break;
        }
        if (!latestDedup.isEmpty())
            ord.put("🏆 Latest Movies", latestDedup);

        // 3. Fixed known categories
        for (String c : new String[]{"South","Bollywood","Hollywood","Pakistani","Turkish","Urdu Dubbed","Trending"}) {
            List<Movie> l = caseFind(byCat, c);
            if (l != null && !l.isEmpty()) ord.put(emoji(c) + " " + c, l);
        }

        // 4. Any remaining categories not already included
        for (Map.Entry<String, List<Movie>> e : byCat.entrySet()) {
            boolean dup = false;
            for (String k : ord.keySet())
                if (k.toLowerCase().endsWith(e.getKey().toLowerCase())) { dup = true; break; }
            if (!dup && !e.getValue().isEmpty())
                ord.put(emoji(e.getKey()) + " " + e.getKey(), e.getValue());
        }
        return ord;
    }

    private static List<Movie> toMovies(List<CachedMovieEntity> list) {
        List<Movie> out = new ArrayList<>();
        for (CachedMovieEntity e : list) {
            Movie m = new Movie();
            m.id = e.id; m.title = e.title; m.category = e.category;
            m.thumbnailUrl = e.thumbnailUrl; m.driveUrl = e.driveUrl;
            m.playUrl = e.playUrl; m.releaseYear = e.releaseYear; m.createdAt = e.createdAt;
            out.add(m);
        }
        return out;
    }

    private static List<Movie> caseFind(Map<String, List<Movie>> m, String key) {
        for (Map.Entry<String, List<Movie>> e : m.entrySet())
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
    }

    private static String s(String v) { return v != null ? v : ""; }

    private static String emoji(String c) {
        if (c == null) return "🎬";
        switch (c.toLowerCase()) {
            case "south": case "south indian": return "🎭";
            case "bollywood":   return "🎵";
            case "hollywood":   return "🌟";
            case "pakistani":   return "🇵🇰";
            case "turkish":     return "🌙";
            case "trending":    return "🔥";
            case "urdu dubbed": return "🎙";
            default:            return "🎬";
        }
    }
}
