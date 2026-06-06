package com.cinema.jazz.db;

import android.content.Context;
import androidx.room.*;

@Database(
    entities = {
        DownloadEntity.class,
        CachedMovieEntity.class,
        CachedShortEntity.class,
        WatchHistoryEntity.class
    },
    version = 6,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INST;

    public abstract DownloadDao     downloadDao();
    public abstract CachedMovieDao  cachedMovieDao();
    public abstract CachedShortDao  cachedShortDao();
    public abstract WatchHistoryDao watchHistoryDao();

    public static AppDatabase getInstance(Context ctx) {
        if (INST == null) synchronized (AppDatabase.class) {
            if (INST == null) INST = Room.databaseBuilder(
                    ctx.getApplicationContext(), AppDatabase.class, "jazz_cinema.db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
        }
        return INST;
    }
}
