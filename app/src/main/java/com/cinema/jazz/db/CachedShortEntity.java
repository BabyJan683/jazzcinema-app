package com.cinema.jazz.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room entity — stores Shorts/TikTok videos for offline playback. */
@Entity(tableName = "cached_shorts")
public class CachedShortEntity {
    @PrimaryKey
    public int    id;
    public String title;
    public String driveUrl;
    public String category;
    public int    releaseYear;
    public long   cachedAt;
}
