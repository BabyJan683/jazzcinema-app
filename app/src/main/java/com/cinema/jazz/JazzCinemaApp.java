package com.cinema.jazz;

import android.app.Application;
import com.cinema.jazz.util.BackgroundSyncWorker;
import com.cinema.jazz.util.DownloadNotificationManager;

public class JazzCinemaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Create download notification channel
        DownloadNotificationManager.createChannel(this);
        // v8.7 — start background sync (like WhatsApp / Facebook)
        // Runs every 15 min even when app is closed, notifies on new movies/shorts
        BackgroundSyncWorker.schedule(this);
    }
}
