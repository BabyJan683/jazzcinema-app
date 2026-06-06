package com.cinema.jazz.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import java.util.concurrent.Executors;

/**
 * Handles Pause / Resume / Cancel actions from download notifications.
 */
public class DownloadActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        int dlId = intent.getIntExtra(DownloadNotificationManager.EXTRA_DL_ID, -1);
        if (dlId < 0) return;

        switch (intent.getAction()) {
            case DownloadNotificationManager.ACTION_PAUSE:
                handlePause(ctx, dlId);
                break;
            case DownloadNotificationManager.ACTION_CANCEL:
                handleCancel(ctx, dlId);
                break;
            case DownloadNotificationManager.ACTION_RESUME:
                // Just cancel the paused notification — user will tap Resume in Downloads tab
                DownloadNotificationManager.cancel(ctx, dlId);
                break;
        }
    }

    private void handlePause(Context ctx, int dlId) {
        DownloadEngine.pauseDownload(dlId);
        Executors.newSingleThreadExecutor().execute(() -> {
            DownloadEntity e = AppDatabase.getInstance(ctx).downloadDao().getById(dlId);
            if (e != null) {
                e.status = "paused";
                AppDatabase.getInstance(ctx).downloadDao().update(e);
                DownloadNotificationManager.notifyPaused(ctx, dlId, e.title);
            }
        });
    }

    private void handleCancel(Context ctx, int dlId) {
        DownloadEngine.pauseDownload(dlId);
        DownloadNotificationManager.cancel(ctx, dlId);
        Executors.newSingleThreadExecutor().execute(() -> {
            DownloadEntity e = AppDatabase.getInstance(ctx).downloadDao().getById(dlId);
            if (e != null) {
                e.status = "failed";
                AppDatabase.getInstance(ctx).downloadDao().update(e);
            }
        });
    }
}
