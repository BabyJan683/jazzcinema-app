package com.cinema.jazz.model;

/**
 * Song model — matches columns in the "songs" MySQL table.
 *
 * TABLE NAME (must be same everywhere):
 *   Database  →  songs
 *   config.json "extra_sources" → "table": "songs"
 *   SongsRepository.java → TABLE = "songs"  ← all three identical ✅
 *
 * SQL to create table in your MySQL database:
 *
 *   CREATE TABLE songs (
 *     id           INT AUTO_INCREMENT PRIMARY KEY,
 *     title        VARCHAR(255) NOT NULL,
 *     artist       VARCHAR(255),
 *     category     VARCHAR(100),
 *     drive_url    TEXT,
 *     thumbnail_url TEXT,
 *     release_year  INT DEFAULT 0,
 *     duration_sec  INT DEFAULT 0,
 *     created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 *   );
 */
public class Song {
    public int    id;
    public String title;
    public String artist;
    public String category;
    public String driveUrl;
    public String thumbnailUrl;
    public int    releaseYear;
    public int    durationSec;

    public Song(int id, String title, String artist, String category,
                String driveUrl, String thumbnailUrl, int releaseYear, int durationSec) {
        this.id           = id;
        this.title        = title;
        this.artist       = artist;
        this.category     = category;
        this.driveUrl     = driveUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.releaseYear  = releaseYear;
        this.durationSec  = durationSec;
    }

    /** Returns duration formatted as "m:ss" */
    public String durationFormatted() {
        if (durationSec <= 0) return "";
        return (durationSec / 60) + ":" + String.format("%02d", durationSec % 60);
    }
}
