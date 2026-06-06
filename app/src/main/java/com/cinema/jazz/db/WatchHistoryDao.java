package com.cinema.jazz.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WatchHistoryDao {

    /** Insert or update (re-watching a movie bumps its watchedAt timestamp). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(WatchHistoryEntity entry);

    /** Returns up to 20 most recently watched movies, newest first. */
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 20")
    List<WatchHistoryEntity> getRecent();

    /** Total count of watched movies. */
    @Query("SELECT COUNT(*) FROM watch_history")
    int count();

    /** Remove a single entry. */
    @Query("DELETE FROM watch_history WHERE movieId = :id")
    void remove(int id);

    /** Wipe all history. */
    @Query("DELETE FROM watch_history")
    void clearAll();
}
