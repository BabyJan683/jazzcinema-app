package com.cinema.jazz.util;

import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.core.app.*;
import androidx.core.app.NotificationCompat;
import com.cinema.jazz.R;

/**
 * Download notifications — app icon, Pause + Cancel buttons on progress,
 * Resume + Cancel buttons when paused, top-priority placement.
 */
public class DownloadNotificationManager {

    private static final String CHANNEL_ID   = "jazz_downloads";
    private static final String CHANNEL_NAME = "Jazz Cinema Downloads";

    public static final String ACTION_PAUSE  = "com.cinema.jazz.DOWNLOAD_PAUSE";
    public static final String ACTION_RESUME = "com.cinema.jazz.DOWNLOAD_RESUME";
    public static final String ACTION_CANCEL = "com.cinema.jazz.DOWNLOAD_CANCEL";
    public static final String EXTRA_DL_ID  = "dl_id";

    /** Call once on app start. Idempotent. */
    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Active & completed downloads");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(true);
            getManager(ctx).createNotificationChannel(ch);
        }
    }

    /** Show or update progress notification — app icon, Pause + Cancel buttons. */
    public static void notifyProgress(Context ctx, int id, String title,
                                      int pct, long downloadedBytes,
                                      long totalBytes, long speedBps) {
        String sizeText  = DownloadEngine.formatSize(downloadedBytes)
                + (totalBytes > 0 ? " / " + DownloadEngine.formatSize(totalBytes) : "");
        String speedText = speedBps > 0 ? "  •  " + DownloadEngine.formatSpeed(speedBps) : "";

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⬇ " + title)
            .setContentText(sizeText + speedText)
            .setProgress(100, pct, pct == 0)
            .setSubText(pct + "%")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_pause, "Pause",  actionIntent(ctx, ACTION_PAUSE,  id))
            .addAction(android.R.drawable.ic_delete,      "Cancel", actionIntent(ctx, ACTION_CANCEL, id));

        getManager(ctx).notify(notifId(id), b.build());
    }

    /** Show paused notification — app icon, Resume + Cancel buttons. */
    public static void notifyPaused(Context ctx, int id, String title) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏸ Paused: " + title)
            .setContentText("Tap Resume to continue downloading.")
            .setOngoing(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_media_play, "Resume", actionIntent(ctx, ACTION_RESUME, id))
            .addAction(android.R.drawable.ic_delete,     "Cancel", actionIntent(ctx, ACTION_CANCEL, id));

        getManager(ctx).cancel(notifId(id));
        getManager(ctx).notify(notifId(id), b.build());
    }

    /** Replace progress notification with "Download complete" banner. */
    public static void notifyComplete(Context ctx, int id, String title) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✅ Download complete")
            .setContentText(title + " is ready to watch offline.")
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        getManager(ctx).cancel(notifId(id));
        getManager(ctx).notify(notifId(id) + 10000, b.build());
    }

    /** On error, show a paused notification with Resume button. */
    public static void notifyFailed(Context ctx, int id, String title, String reason) {
        notifyPaused(ctx, id, title);
    }

    /** Cancel (remove) an in-progress notification. */
    public static void cancel(Context ctx, int id) {
        getManager(ctx).cancel(notifId(id));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static PendingIntent actionIntent(Context ctx, String action, int dlId) {
        Intent i = new Intent(action).setPackage(ctx.getPackageName());
        i.putExtra(EXTRA_DL_ID, dlId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, dlId, i, flags);
    }

    private static NotificationManager getManager(Context ctx) {
        return (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static int notifId(int entityId) { return 7000 + entityId; }
}
