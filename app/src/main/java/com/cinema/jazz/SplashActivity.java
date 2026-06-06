package com.cinema.jazz;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.cinema.jazz.auth.AuthManager;
import com.cinema.jazz.control.AppControlChecker;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DatabaseManager;
import com.cinema.jazz.onboarding.OnboardingActivity;
import com.cinema.jazz.control.RemoteConfigManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;

/**
 * v8.7 Splash — multi-DB failover aware, offline fallback with cached counts.
 *
 * Load sequence:
 *   0%   → "Starting Jazz Cinema..."
 *  15%   → "Fetching remote config..."   (GitHub config.json)
 *  35%   → "Connecting to database..."   (tries primary → backups)
 *  60%   → "Loading movies..."           (COUNT from DB or Room cache)
 *  80%   → "Loading Shorts..."           (COUNT from DB or Room cache)
 *  95%   → "Checking app status..."      (AppControlChecker)
 * 100%   → proceed to app
 *
 * Offline behaviour:
 *   If all DBs unreachable → shows cached movie/short counts from Room.
 *   Always proceeds to app — never gets stuck on splash.
 */
public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView    txtStep, txtPercent, txtMovieCount, txtTiktokCount, txtDbStatus;
    private final Handler mainH = new Handler(Looper.getMainLooper());

    private int movieCount  = 0;
    private int tiktokCount = 0;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_splash);

        ImageView imgSplash = findViewById(R.id.img_splash);
        ImageView imgLogo   = findViewById(R.id.img_logo);
        progressBar         = findViewById(R.id.splash_progress);
        txtStep             = findViewById(R.id.txt_splash_step);
        txtPercent          = findViewById(R.id.txt_splash_percent);
        txtMovieCount       = findViewById(R.id.txt_movie_count);
        txtTiktokCount      = findViewById(R.id.txt_tiktok_count);
        txtDbStatus         = findViewById(R.id.txt_db_status);   // optional — hide if not in layout

        if (imgSplash != null) {
            int res = getResources().getIdentifier("splash_bg", "drawable", getPackageName());
            if (res != 0) imgSplash.setImageResource(res);
            else Glide.with(this).load(Constants.SPLASH_IMAGE_URL).centerCrop().into(imgSplash);
        }
        if (imgLogo != null) {
            int r = getResources().getIdentifier("app_logo", "drawable", getPackageName());
            imgLogo.setImageResource(r != 0 ? r : Constants.LOGO_DRAWABLE);
        }

        if (txtMovieCount  != null) txtMovieCount.setVisibility(View.GONE);
        if (txtTiktokCount != null) txtTiktokCount.setVisibility(View.GONE);
        if (txtDbStatus    != null) txtDbStatus.setVisibility(View.GONE);

        startLoadSequence();
    }

    // ── Loading sequence ──────────────────────────────────────────────────────

    private void startLoadSequence() {
        setProgress(0, "Starting Jazz Cinema...");

        // Step 1 — fetch remote config from GitHub
        mainH.postDelayed(() -> {
            setProgress(15, "Fetching remote config...");
            Executors.newSingleThreadExecutor().execute(() -> {
                RemoteConfigManager.fetch(this);
                mainH.post(this::stepConnect);
            });
        }, 400);
    }

    private void stepConnect() {
        if (isFinishing()) return;
        setProgress(35, "Connecting to database...");
        Executors.newSingleThreadExecutor().execute(() -> {
            Connection conn = null;
            boolean online = false;
            try {
                Class.forName("com.mysql.jdbc.Driver");
                conn   = DatabaseManager.getConnection(this);
                online = true;
            } catch (Exception ignored) {}

            final Connection finalConn = conn;
            final boolean    isOnline  = online;
            mainH.post(() -> {
                if (isFinishing()) return;
                String dbLabel = DatabaseManager.getMovieDbLabel(this);
                if (txtDbStatus != null) {
                    txtDbStatus.setVisibility(View.VISIBLE);
                    txtDbStatus.setText(isOnline
                        ? "🟢 " + dbLabel + " database"
                        : "🔴 Offline — using cached data");
                }
                setProgress(60, isOnline ? "Loading movies..." : "Loading cached movies...");
                loadMovieCount(finalConn, isOnline);
            });
        });
    }

    private void loadMovieCount(Connection conn, boolean online) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (online && conn != null) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM movies")) {
                    if (rs.next()) movieCount = rs.getInt(1);
                } catch (Exception ignored) {}
            } else {
                // Offline — read from Room cache
                movieCount = AppDatabase.getInstance(this).cachedMovieDao().count();
            }

            mainH.post(() -> {
                if (isFinishing()) return;
                if (txtMovieCount != null) {
                    txtMovieCount.setVisibility(View.VISIBLE);
                    txtMovieCount.setText("🎬  " + movieCount + " Movies"
                        + (online ? "" : " (offline)"));
                }
                setProgress(80, online ? "Loading Shorts..." : "Loading cached Shorts...");
                loadTiktokCount(conn, online);
            });
        });
    }

    private void loadTiktokCount(Connection conn, boolean online) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (online && conn != null) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM " + RemoteConfigManager.tiktokTable())) {
                    if (rs.next()) tiktokCount = rs.getInt(1);
                } catch (Exception ignored) {}
                finally {
                    try { conn.close(); } catch (Exception ignored) {}
                }
            } else {
                tiktokCount = AppDatabase.getInstance(this).cachedShortDao().count();
            }

            mainH.post(() -> {
                if (isFinishing()) return;
                if (txtTiktokCount != null) {
                    txtTiktokCount.setVisibility(View.VISIBLE);
                    txtTiktokCount.setText("🎵  " + tiktokCount + " Shorts"
                        + (online ? "" : " (offline)"));
                }
                setProgress(95, "Checking app status...");
                mainH.postDelayed(() -> {
                    setProgress(100, "Ready!");
                    mainH.postDelayed(this::checkControl, 350);
                }, 500);
            });
        });
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setProgress(int pct, String step) {
        if (progressBar != null) progressBar.setProgress(pct);
        if (txtStep     != null) txtStep.setText(step);
        if (txtPercent  != null) txtPercent.setText(pct + "%");
    }

    // ── Control check → navigate ──────────────────────────────────────────────

    private void checkControl() {
        if (isFinishing()) return;
        AppControlChecker.check(this, new AppControlChecker.Callback() {
            @Override public void onResult(AppControlChecker.ControlStatus status) {
                if (isFinishing()) return;
                if (!status.isActive)       { open(BlockedActivity.TYPE_BLOCKED,     status.blockedMessage); return; }
                if (status.maintenanceMode) { open(BlockedActivity.TYPE_MAINTENANCE, status.maintenanceMsg); return; }
                if (status.forceUpdate)     { open(BlockedActivity.TYPE_UPDATE,      status.updateMessage);  return; }
                proceedToApp();
            }
            @Override public void onError() { if (!isFinishing()) proceedToApp(); }
        });
    }

    private void open(String type, String msg) {
        Intent i = new Intent(this, BlockedActivity.class);
        i.putExtra("type", type);
        i.putExtra("message", msg);
        startActivity(i);
        finish();
    }

    private void proceedToApp() {
        startActivity(new Intent(this,
            AuthManager.isLoggedIn(this) ? MainActivity.class : OnboardingActivity.class));
        finish();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        mainH.removeCallbacksAndMessages(null);
    }
}
