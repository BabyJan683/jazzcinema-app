package com.cinema.jazz.db;
import androidx.room.*;
import java.util.List;
@Dao
public interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC") List<DownloadEntity> getAll();
    @Query("SELECT * FROM downloads WHERE status='completed' ORDER BY downloadedAt DESC") List<DownloadEntity> getOffline();
    @Query("SELECT COUNT(*) FROM downloads") int count();
    @Insert(onConflict = OnConflictStrategy.REPLACE) long insert(DownloadEntity e);
    @Update void update(DownloadEntity e);
    @Delete void delete(DownloadEntity e);
    @Query("DELETE FROM downloads WHERE id=:id") void deleteById(int id);
    @Query("SELECT * FROM downloads WHERE id=:id LIMIT 1") DownloadEntity getById(int id);
}
