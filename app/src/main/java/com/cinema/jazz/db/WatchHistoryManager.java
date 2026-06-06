package com.cinema.jazz.db;

import android.content.Context;
import com.cinema.jazz.model.Movie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * v8.7 — Watch History Manager.
 *
 * Records every movie the user opens. Powers the "⏯ Continue Watching" row
 * in HomeFragment. Works 100% offline — stored in local Room DB.
 *
 * Usage:
 *   // In MovieDetailActivity.onCreate():
 *   WatchHistoryManager.record(this, movieId, title, thumbUrl, driveUrl, playUrl, category, year);
 *
 *   // In HomeFragment:
 *   WatchHistoryManager.getRecent(ctx, movies -> buildContinueWatchingRow(movies));
 */
public class WatchHistoryManager {

    public interface Callback { void onResult(List<Movie> movies); }

    /** Save a movie to watch history (background thread — safe to call from main thread). */
    public static void record(Context ctx, int movieId, String title,
                              String thumbUrl, String driveUrl, String playUrl,
                              String category, int year) {
        Executors.newSingleThreadExecutor().execute(() -> {
            WatchHistoryEntity e = new WatchHistoryEntity();
            e.movieId      = movieId;
            e.title        = title != null ? title : "";
            e.thumbnailUrl = thumbUrl != null ? thumbUrl : "";
            e.driveUrl     = driveUrl != null ? driveUrl : "";
            e.playUrl      = playUrl != null ? playUrl : "";
            e.category     = category != null ? category : "";
            e.releaseYear  = year;
            e.watchedAt    = System.currentTimeMillis();
            AppDatabase.getInstance(ctx).watchHistoryDao().save(e);
        });
    }

    /** Get up to 20 most recently watched movies, newest first (background thread). */
    public static void getRecent(Context ctx, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<WatchHistoryEntity> rows =
                AppDatabase.getInstance(ctx).watchHistoryDao().getRecent();
            List<Movie> out = new ArrayList<>();
            for (WatchHistoryEntity e : rows) {
                Movie m = new Movie();
                m.id           = e.movieId;
                m.title        = e.title;
                m.thumbnailUrl = e.thumbnailUrl;
                m.driveUrl     = e.driveUrl;
                m.playUrl      = e.playUrl;
                m.category     = e.category;
                m.releaseYear  = e.releaseYear;
                out.add(m);
            }
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> cb.onResult(out));
        });
    }

    /** Clear all watch history (e.g. from Settings). */
    public static void clearAll(Context ctx) {
        Executors.newSingleThreadExecutor().execute(() ->
            AppDatabase.getInstance(ctx).watchHistoryDao().clearAll());
    }

    /** Remove a single movie from history. */
    public static void remove(Context ctx, int movieId) {
        Executors.newSingleThreadExecutor().execute(() ->
            AppDatabase.getInstance(ctx).watchHistoryDao().remove(movieId));
    }
}
