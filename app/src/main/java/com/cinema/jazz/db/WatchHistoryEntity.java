package com.cinema.jazz.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity — stores recently watched movies for "⏯ Continue Watching" row.
 * One row per movie (upsert on re-watch updates the timestamp).
 */
@Entity(tableName = "watch_history")
public class WatchHistoryEntity {
    @PrimaryKey
    public int    movieId;
    public String title;
    public String thumbnailUrl;
    public String driveUrl;
    public String playUrl;
    public String category;
    public int    releaseYear;
    /** System.currentTimeMillis() of last watch — used for ordering. */
    public long   watchedAt;
}
