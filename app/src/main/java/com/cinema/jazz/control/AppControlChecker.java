package com.cinema.jazz.control;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;

/**
 * v8.7 — Reads RemoteConfig from GitHub and returns app control status.
 * Called from SplashActivity. Network work runs on background thread.
 */
public class AppControlChecker {

    public static class ControlStatus {
        public boolean isActive        = true;
        public boolean maintenanceMode = false;
        public boolean forceUpdate     = false;
        public String  blockedMessage  = "";
        public String  maintenanceMsg  = "";
        public String  updateMessage   = "";
    }

    public interface Callback {
        void onResult(ControlStatus status);
        void onError();
    }

    public static void check(Context ctx, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                RemoteConfigManager.fetch(ctx);

                ControlStatus s    = new ControlStatus();
                s.isActive        = RemoteConfigManager.isActive();
                s.maintenanceMode = RemoteConfigManager.maintenanceMode();
                s.forceUpdate     = RemoteConfigManager.forceUpdate();
                s.blockedMessage  = RemoteConfigManager.blockedMessage();
                s.maintenanceMsg  = RemoteConfigManager.maintenanceMsg();
                s.updateMessage   = RemoteConfigManager.updateMessage();

                new Handler(Looper.getMainLooper()).post(() -> cb.onResult(s));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(cb::onError);
            }
        });
    }
}
