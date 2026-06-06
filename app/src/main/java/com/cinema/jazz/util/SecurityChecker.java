package com.cinema.jazz.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import java.util.Arrays;
import java.util.List;

/**
 * Detects network interception tools (HTTP Canary, SSL Capture) and blocks the app if found.
 * Call SecurityChecker.check(activity) in MainActivity.onCreate() before anything else.
 */
public class SecurityChecker {

    private static final List<String> BLOCKED_PACKAGES = Arrays.asList(
        "com.guoshi.httpcanary",
        "com.guoshi.httpcanary.premium",
        "app.greyshirts.sslcapture",
        "com.proxyman.NSProxy",
        "com.streamassist.streamassist"
    );

    /** Returns true if a dangerous interception app is installed. */
    public static boolean isDangerousAppInstalled(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        for (String pkg : BLOCKED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true; // found
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return false;
    }

    /**
     * If a proxy/interception app is detected, show a blocking dialog and return true.
     * The activity should finish/exit if this returns true.
     */
    public static boolean checkAndBlock(Activity activity) {
        if (!isDangerousAppInstalled(activity)) return false;

        new AlertDialog.Builder(activity)
            .setTitle("Security Alert")
            .setMessage(
                "A network interception app (HTTP Canary / SSL Capture) is installed on your device.\n\n" +
                "Please uninstall it first, then reopen Jazz Cinema.\n\n" +
                "Blocked apps:\n• com.guoshi.httpcanary\n• app.greyshirts.sslcapture"
            )
            .setCancelable(false)
            .setPositiveButton("Exit App", (d, w) -> activity.finishAffinity())
            .show();
        return true;
    }
}
