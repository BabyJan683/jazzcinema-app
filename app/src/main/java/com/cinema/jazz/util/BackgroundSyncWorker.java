package com.cinema.jazz.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.*;
import com.cinema.jazz.Constants;
import com.cinema.jazz.MainActivity;
import com.cinema.jazz.R;
import com.cinema.jazz.control.RemoteConfigManager;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.CachedMovieEntity;
import com.cinema.jazz.db.CachedShortEntity;
import com.cinema.jazz.db.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * v8.7 — Background sync worker (like WhatsApp / Facebook).
 *
 * Runs every 15 min via WorkManager even when the app is closed.
 * Each run:
 *   1. Fetches fresh remote config from GitHub
 *   2. Connects via DatabaseManager (primary → backups)
 *   3. Finds NEW movies/shorts added since last sync
 *   4. Pre-caches them into Room (so offline mode always has fresh content)
 *   5. Shows a rich notification if new content found
 *   6. Updates last-sync timestamp
 *
 * Tap notification → opens app directly to home screen.
 */
public class BackgroundSyncWorker extends Worker {

    private static final String CHANNEL_ID   = "jazz_sync_channel";
    private static final String CHANNEL_NAME = "New Content";
    private static final int    NOTIF_ID     = 7001;
    private static final String WORK_NAME    = "jazz_bg_sync";

    public BackgroundSyncWorker(@NonNull Context ctx, @NonNull WorkerParameters p) {
        super(ctx, p);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx   = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences("jazz_settings", Context.MODE_PRIVATE);
        boolean notificationsOn = prefs.getBoolean("notifications", true);
        long lastSync = prefs.getLong(Constants.PREF_LAST_SYNC, 0L);

        // Step 1 — refresh GitHub remote config silently
        try { RemoteConfigManager.fetch(ctx); } catch (Exception ignored) {}

        List<NewMovie> newMovies = new ArrayList<>();
        int newShorts = 0;

        // Step 2 — connect via DatabaseManager (primary → backups)
        try {
            Connection conn = DatabaseManager.getConnection(ctx);

            // Step 3a — fetch NEW movies since last sync (with titles for notification)
            String movieSql = buildCountSql("Movies", "id,title", lastSync);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(movieSql)) {
                while (rs.next()) {
                    newMovies.add(new NewMovie(rs.getInt("id"), rs.getString("title")));
                }
            } catch (Exception ignored) {}

            // Step 3b — count NEW shorts since last sync
            String shortSql = buildCountSql(RemoteConfigManager.tiktokTable(), "COUNT(*) AS cnt", lastSync);
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(shortSql)) {
                if (rs.next()) newShorts = rs.getInt("cnt");
            } catch (Exception ignored) {}

            // Step 4 — pre-cache new movies into Room
            if (!newMovies.isEmpty()) precacheNewMovies(ctx, conn, lastSync);

            // Step 4b — pre-cache new shorts into Room
            if (newShorts > 0) precacheNewShorts(ctx, conn, lastSync);

            conn.close();
        } catch (Exception e) {
            // All DBs failed — retry in 5 min
            return Result.retry();
        }

        // Step 5 — update last sync time
        prefs.edit().putLong(Constants.PREF_LAST_SYNC, System.currentTimeMillis()).apply();

        // Step 6 — notify if new content found
        if (notificationsOn && (!newMovies.isEmpty() || newShorts > 0)) {
            showNotification(ctx, newMovies, newShorts);
        }

        return Result.success();
    }

    // ── Pre-caching ───────────────────────────────────────────────────────────

    private void precacheNewMovies(Context ctx, Connection conn, long lastSync) {
        try {
            String sql = "SELECT id,category,title,thumbnail_url,drive_url,play_url,release_year,created_at"
                       + " FROM Movies";
            if (lastSync > 0)
                sql += " WHERE created_at > FROM_UNIXTIME(" + (lastSync / 1000) + ")";
            sql += " ORDER BY created_at DESC";

            List<CachedMovieEntity> toSave = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                long now = System.currentTimeMillis();
                while (rs.next()) {
                    CachedMovieEntity e = new CachedMovieEntity();
                    e.id           = rs.getInt("id");
                    e.category     = s(rs.getString("category"));
                    e.title        = s(rs.getString("title"));
                    e.thumbnailUrl = s(rs.getString("thumbnail_url"));
                    e.driveUrl     = s(rs.getString("drive_url"));
                    e.playUrl      = s(rs.getString("play_url"));
                    e.releaseYear  = rs.getInt("release_year");
                    e.createdAt    = s(rs.getString("created_at"));
                    e.cachedAt     = now;
                    toSave.add(e);
                }
            }
            if (!toSave.isEmpty())
                AppDatabase.getInstance(ctx).cachedMovieDao().insertAll(toSave);
        } catch (Exception ignored) {}
    }

    private void precacheNewShorts(Context ctx, Connection conn, long lastSync) {
        try {
            String table = RemoteConfigManager.tiktokTable();
            String sql = "SELECT id,title,drive_url,category,release_year FROM " + table;
            if (lastSync > 0)
                sql += " WHERE created_at > FROM_UNIXTIME(" + (lastSync / 1000) + ")";
            sql += " LIMIT 50";

            List<CachedShortEntity> toSave = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                long now = System.currentTimeMillis();
                while (rs.next()) {
                    CachedShortEntity e = new CachedShortEntity();
                    e.id          = rs.getInt("id");
                    e.title       = s(rs.getString("title"));
                    e.driveUrl    = s(rs.getString("drive_url"));
                    e.category    = s(rs.getString("category"));
                    e.releaseYear = rs.getInt("release_year");
                    e.cachedAt    = now;
                    toSave.add(e);
                }
            }
            if (!toSave.isEmpty())
                AppDatabase.getInstance(ctx).cachedShortDao().insertAll(toSave);
        } catch (Exception ignored) {}
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void showNotification(Context ctx, List<NewMovie> movies, int shorts) {
        NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("New movies and shorts alerts");
            nm.createNotificationChannel(ch);
        }

        // Build rich message with movie titles (up to 3)
        StringBuilder bigText = new StringBuilder();
        if (!movies.isEmpty()) {
            bigText.append("🎬 ").append(movies.size()).append(" new movie(s):\n");
            for (int i = 0; i < Math.min(3, movies.size()); i++)
                bigText.append("  • ").append(movies.get(i).title).append("\n");
            if (movies.size() > 3)
                bigText.append("  + ").append(movies.size() - 3).append(" more\n");
        }
        if (shorts > 0)
            bigText.append("🎵 ").append(shorts).append(" new short(s) added!");

        String shortText = "";
        if (!movies.isEmpty()) shortText += movies.size() + " new movie(s)  ";
        if (shorts > 0)        shortText += shorts + " new short(s)";

        // Tap notification → open app
        Intent tapIntent = new Intent(ctx, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jazz_logo)
            .setContentTitle("Jazz Cinema — New Content! 🎬")
            .setContentText(shortText.trim())
            .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText.toString().trim()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true);

        nm.notify(NOTIF_ID, nb.build());
    }

    // ── WorkManager schedule helper ───────────────────────────────────────────

    public static void schedule(Context ctx) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BackgroundSyncWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildCountSql(String table, String cols, long lastSync) {
        String sql = "SELECT " + cols + " FROM " + table;
        if (lastSync > 0)
            sql += " WHERE created_at > FROM_UNIXTIME(" + (lastSync / 1000) + ")";
        return sql;
    }

    private static String s(String v) { return v != null ? v : ""; }

    private static class NewMovie {
        final int id; final String title;
        NewMovie(int id, String title) { this.id = id; this.title = title != null ? title : ""; }
    }
}
