package com.cinema.jazz.db;
import androidx.room.*;

@Entity(tableName = "downloads")
public class DownloadEntity {
    @PrimaryKey(autoGenerate = true) public int id;
    public String title, driveUrl, thumbUrl, category, status;
    public float  progress;
    public long   downloadedBytes;
    public long   totalBytes;
    public long   speedBps;
    public long   downloadedAt;
    public String filePath;
    public String language;

    /** For HLS: index of the last successfully saved segment (resume from here). */
    public int    resumeSegmentIndex;
    /** For direct MP4: byte offset to resume from (HTTP Range). */
    public long   resumeByteOffset;
}
