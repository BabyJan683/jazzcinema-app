package com.cinema.jazz.db;
import androidx.room.*;
import java.util.List;
@Dao
public interface CachedMovieDao {
    @Query("SELECT * FROM cached_movies ORDER BY cachedAt DESC") List<CachedMovieEntity> getAll();
    @Query("SELECT COUNT(*) FROM cached_movies") int count();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertAll(List<CachedMovieEntity> list);
    @Query("DELETE FROM cached_movies") void clearAll();
}
