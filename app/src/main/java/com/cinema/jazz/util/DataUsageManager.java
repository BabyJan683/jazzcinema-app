package com.cinema.jazz.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.cinema.jazz.Constants;
import java.time.LocalDate;

/**
 * Tracks data usage for the Jazz Cinema app.
 * - Movie/series streaming: COUNTED
 * - App general internet usage: COUNTED
 * - Live TV: NOT counted
 * - Downloads: COUNTED
 *
 * Data resets automatically when a new monthly package is activated.
 * Package is stored in SharedPreferences and synced from Supabase on login.
 */
public class DataUsageManager {

    private static final String PREFS = "jazz_data_usage";

    /** Add bytes used during streaming or download (non-Live-TV). */
    public static void addUsedBytes(Context ctx, long bytes) {
        if (bytes <= 0) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long current = p.getLong(Constants.PREF_DATA_USED_BYTES, 0L);
        p.edit().putLong(Constants.PREF_DATA_USED_BYTES, current + bytes).apply();
    }

    /** Get total bytes used this billing period. */
    public static long getUsedBytes(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(Constants.PREF_DATA_USED_BYTES, 0L);
    }

    /** Get package total in bytes (e.g. 5GB → 5*1024*1024*1024). */
    public static long getPackageBytes(Context ctx) {
        long gb = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(Constants.PREF_DATA_PACKAGE_GB, 0L);
        return gb * 1024L * 1024L * 1024L;
    }

    /** Get package size in GB (0 if no package). */
    public static long getPackageGB(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(Constants.PREF_DATA_PACKAGE_GB, 0L);
    }

    /** Called when admin/server sets a new package for the user. */
    public static void setPackage(Context ctx, long packageGb) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit()
            .putLong(Constants.PREF_DATA_PACKAGE_GB, packageGb)
            .putLong(Constants.PREF_DATA_USED_BYTES, 0L)
            .putString(Constants.PREF_DATA_RESET_DATE, LocalDate.now().toString())
            .apply();
    }

    /** Reset usage (e.g. monthly renewal). */
    public static void resetUsage(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(Constants.PREF_DATA_USED_BYTES, 0L)
            .putString(Constants.PREF_DATA_RESET_DATE, LocalDate.now().toString())
            .apply();
    }

    /** True if user has no package or has exceeded their package limit. */
    public static boolean isLimitExceeded(Context ctx) {
        long pkg = getPackageBytes(ctx);
        if (pkg <= 0) return false; // no package set = unlimited (free)
        return getUsedBytes(ctx) >= pkg;
    }

    /** Returns human-readable used string, e.g. "1.23 GB" */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** 0.0–1.0 progress for progress bar. Returns 0 if no package. */
    public static float getUsageProgress(Context ctx) {
        long pkg = getPackageBytes(ctx);
        if (pkg <= 0) return 0f;
        return Math.min(1f, (float) getUsedBytes(ctx) / pkg);
    }

    /** Get the reset date string. */
    public static String getResetDate(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(Constants.PREF_DATA_RESET_DATE, "");
    }
}
