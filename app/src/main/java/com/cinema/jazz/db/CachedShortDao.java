package com.cinema.jazz.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CachedShortDao {
    @Query("SELECT * FROM cached_shorts ORDER BY cachedAt DESC")
    List<CachedShortEntity> getAll();

    @Query("SELECT COUNT(*) FROM cached_shorts")
    int count();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedShortEntity> shorts);

    @Query("DELETE FROM cached_shorts")
    void clearAll();
}
