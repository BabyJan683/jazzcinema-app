package com.cinema.jazz.db;
import androidx.room.*;
@Entity(tableName = "cached_movies")
public class CachedMovieEntity {
    @PrimaryKey public int    id;
    public String             title, category, thumbnailUrl, driveUrl, playUrl, createdAt;
    public int                releaseYear;
    public long               cachedAt;
}
