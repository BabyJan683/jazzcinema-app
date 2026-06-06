package com.cinema.jazz.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v8.7 — Restarts background sync when device reboots.
 * WorkManager normally handles this automatically, but this receiver
 * provides an explicit guarantee for devices with aggressive battery
 * management (Xiaomi, Oppo, Vivo, Samsung One UI).
 *
 * Registered in AndroidManifest.xml with RECEIVE_BOOT_COMPLETED permission.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            BackgroundSyncWorker.schedule(ctx);
        }
    }
}
