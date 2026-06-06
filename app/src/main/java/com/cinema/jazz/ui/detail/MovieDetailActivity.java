package com.cinema.jazz.ui.detail;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.auth.AuthManager;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import com.cinema.jazz.model.UserProfile;
import com.cinema.jazz.ui.player.PlayerActivity;
import com.cinema.jazz.util.DownloadEngine;
import com.cinema.jazz.util.DownloadNotificationManager;
import com.cinema.jazz.util.JazzDriveResolver;
import com.cinema.jazz.util.TmdbHelper;
import com.cinema.jazz.util.WhatsAppHelper;
import java.util.List;
import java.util.concurrent.Executors;

public class MovieDetailActivity extends AppCompatActivity {
    private static final int NOTIF_PERM_REQ = 201;

    private String title, driveUrl, playUrl, thumbUrl, category;
    private int year;
    private final Handler mainH = new Handler(Looper.getMainLooper());
    private TextView txtOverview;
    private LinearLayout castContainer;
    private View btnDownloadNow;
    private ProgressDialog loader;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_movie_detail);
        title    = getIntent().getStringExtra(Constants.KEY_TITLE);
        driveUrl = getIntent().getStringExtra(Constants.KEY_DRIVE_URL);
        playUrl  = getIntent().getStringExtra(Constants.KEY_PLAY_URL);
        thumbUrl = getIntent().getStringExtra(Constants.KEY_THUMB_URL);
        category = getIntent().getStringExtra(Constants.KEY_CATEGORY);
        year     = getIntent().getIntExtra(Constants.KEY_YEAR, 0);

        ImageView hero = findViewById(R.id.img_hero);
        if (hero != null && thumbUrl != null) Glide.with(this).load(thumbUrl).centerCrop().into(hero);
        setText(R.id.txt_title, title);
        setText(R.id.txt_category, category != null ? category : "");
        setText(R.id.txt_year, year > 0 ? String.valueOf(year) : "");
        setText(R.id.txt_meta, buildMeta());

        txtOverview    = findViewById(R.id.txt_overview);
        castContainer  = findViewById(R.id.cast_container);
        btnDownloadNow = findViewById(R.id.btn_download_now);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> onBackPressed());
        View play = findViewById(R.id.btn_play_now);
        if (play != null) play.setOnClickListener(v -> startPlay());
        View wl   = findViewById(R.id.btn_watchlist);
        if (wl   != null) wl.setOnClickListener(v -> addToWatchList());
        if (btnDownloadNow != null) btnDownloadNow.setOnClickListener(v -> requestNotifThenDownload());
        View wa   = findViewById(R.id.btn_whatsapp);
        if (wa   != null) wa.setOnClickListener(v -> WhatsAppHelper.showSubscribeDialog(this));
        loadTmdbInfo();
    }

    // ── Notification permission (Android 13+) ────────────────────────────────

    private void requestNotifThenDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERM_REQ);
                return;
            }
        }
        startDownload();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == NOTIF_PERM_REQ) startDownload(); // proceed even if denied
    }

    // ── Play logic ────────────────────────────────────────────────────────────

    private void startPlay() {
        UserProfile u = AuthManager.getCurrentUser(this);
        if (u == null || !u.canWatch()) { toast("Access denied. Check your account status."); return; }
        if (u.isPending() && !AuthManager.checkAndIncrementWatch(this)) {
            toast("Daily limit reached (10/day). Contact Faizan Mobile Shop to upgrade."); return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            DownloadEntity dl = findCompleted();
            if (dl != null && dl.filePath != null && !dl.filePath.isEmpty()) {
                mainH.post(() -> launchPlayer("file://" + dl.filePath)); return;
            }
            String direct = pickDirectUrl();
            if (direct != null) { mainH.post(() -> launchPlayer(direct)); return; }

            String share = isJazzShare(driveUrl) ? driveUrl : isJazzShare(playUrl) ? playUrl : null;
            if (share == null) { mainH.post(() -> toast("No stream URL available.")); return; }
            mainH.post(this::showLoader);
            JazzDriveResolver.resolve(share, new JazzDriveResolver.ResolveCallback() {
                public void onResolved(String url) { mainH.post(() -> { dismissLoader(); launchPlayer(url); }); }
                public void onError(String msg)    { mainH.post(() -> { dismissLoader(); showPlayError(msg); }); }
            });
        });
    }

    // ── Download logic ────────────────────────────────────────────────────────

    private void startDownload() {
        UserProfile u = AuthManager.getCurrentUser(this);
        if (u == null || !u.canWatch()) { toast("Access denied. Check your account status."); return; }
        if (u.isPending() && !AuthManager.checkAndIncrementWatch(this)) {
            toast("Daily limit reached (10/day). Upgrade to Active plan to download more."); return;
        }

        String direct = pickDirectUrl();
        if (direct != null) { doDownload(direct); return; }

        String share = isJazzShare(driveUrl) ? driveUrl : isJazzShare(playUrl) ? playUrl : null;
        if (share == null) { toast("No downloadable URL for this movie."); return; }

        if (btnDownloadNow != null) btnDownloadNow.setEnabled(false);
        toast("Preparing download…");
        Executors.newSingleThreadExecutor().execute(() ->
            JazzDriveResolver.resolve(share, new JazzDriveResolver.ResolveCallback() {
                public void onResolved(String url) { mainH.post(() -> doDownload(url)); }
                public void onError(String msg) {
                    String fallback = playUrl != null && playUrl.startsWith("http") ? playUrl
                                    : driveUrl != null && driveUrl.startsWith("http") ? driveUrl : null;
                    mainH.post(() -> {
                        if (btnDownloadNow != null) btnDownloadNow.setEnabled(true);
                        if (fallback != null) { toast("Trying direct stream download…"); doDownload(fallback); }
                        else new AlertDialog.Builder(MovieDetailActivity.this, R.style.DarkDialog)
                                .setTitle("Download Failed")
                                .setMessage("Could not get stream URL.\n\nReason: " + msg)
                                .setPositiveButton("OK", null).show();
                    });
                }
            }));
    }

    private void doDownload(String url) {
        DownloadEntity e = new DownloadEntity();
        e.title       = title;
        e.driveUrl    = driveUrl != null ? driveUrl : "";
        e.thumbUrl    = thumbUrl != null ? thumbUrl : "";
        e.category    = category != null ? category : "";
        e.status      = "downloading";
        e.progress    = 0;
        e.downloadedAt= System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            long id = AppDatabase.getInstance(this).downloadDao().insert(e);
            e.id = (int) id;
            toast("Download started! Check notification bar.");

            DownloadEngine.download(this, e, url, new DownloadEngine.ProgressCallback() {
                public void onProgress(int p, long dl, long t, long spd) {
                    // Update notification bar with live progress
                    DownloadNotificationManager.notifyProgress(
                        MovieDetailActivity.this, e.id, e.title, p, dl, t, spd);
                }
                public void onSuccess(String path) {
                    e.filePath = path; e.status = "completed"; e.progress = 100;
                    AppDatabase.getInstance(MovieDetailActivity.this).downloadDao().update(e);
                    DownloadNotificationManager.notifyComplete(MovieDetailActivity.this, e.id, e.title);
                    mainH.post(() -> toast("✅ Download complete: " + title));
                }
                public void onError(String msg) {
                    e.status = "paused";
                    AppDatabase.getInstance(MovieDetailActivity.this).downloadDao().update(e);
                    DownloadNotificationManager.notifyFailed(MovieDetailActivity.this, e.id, e.title, msg);
                    mainH.post(() -> toast("⚠ Download paused. Tap Resume in Downloads tab."));
                }
            });
            mainH.post(() -> { if (btnDownloadNow != null) btnDownloadNow.setEnabled(true); });
        });
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void loadTmdbInfo() {
        if (title == null) return;
        TmdbHelper.fetch(this, title, info -> mainH.post(() -> {
            if (isFinishing() || info == null) return;
            if (txtOverview != null && !info.overview.isEmpty()) {
                txtOverview.setText(info.overview); txtOverview.setVisibility(View.VISIBLE);
            }
            if (castContainer != null && !info.cast.isEmpty()) {
                castContainer.setVisibility(View.VISIBLE); castContainer.removeAllViews();
                for (TmdbHelper.Cast c : info.cast) addCastCard(c);
            }
        }));
    }

    private void addCastCard(TmdbHelper.Cast c) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_cast, castContainer, false);
        ImageView photo = card.findViewById(R.id.img_cast_photo);
        TextView name   = card.findViewById(R.id.txt_cast_name);
        TextView role   = card.findViewById(R.id.txt_cast_role);
        if (c.photoUrl != null) Glide.with(this).load(c.photoUrl).circleCrop().into(photo);
        if (name != null) name.setText(c.name);
        if (role != null) role.setText(c.character);
        castContainer.addView(card);
    }

    private void showLoader() {
        loader = new ProgressDialog(this);
        loader.setMessage("Loading stream…\nPlease wait");
        loader.setCancelable(false); loader.show();
    }
    private void dismissLoader() { if (loader != null && loader.isShowing()) { loader.dismiss(); loader = null; } }

    private void launchPlayer(String url) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(Constants.KEY_TITLE, title);
        i.putExtra(Constants.KEY_DRIVE_URL, url);
        startActivity(i);
    }

    private void showPlayError(String msg) {
        new AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Cannot Play").setMessage("Stream error: " + msg)
            .setPositiveButton("OK", null).show();
    }

    private void addToWatchList() {
        DownloadEntity e = new DownloadEntity();
        e.title = title; e.driveUrl = driveUrl != null ? driveUrl : "";
        e.thumbUrl = thumbUrl != null ? thumbUrl : "";
        e.category = category != null ? category : "";
        e.status = "saved"; e.downloadedAt = System.currentTimeMillis();
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).downloadDao().insert(e);
            mainH.post(() -> toast("Added to Watch List"));
        });
    }

    private DownloadEntity findCompleted() {
        try {
            List<DownloadEntity> all = AppDatabase.getInstance(this).downloadDao().getAll();
            for (DownloadEntity d : all)
                if (d.title != null && d.title.equals(title) && "completed".equals(d.status)) return d;
        } catch (Exception ignored) {}
        return null;
    }

    private String pickDirectUrl() {
        if (playUrl != null && playUrl.startsWith("http") && !isJazzShare(playUrl)) return playUrl;
        if (driveUrl != null && driveUrl.startsWith("http") && !isJazzShare(driveUrl)) return driveUrl;
        return null;
    }

    private boolean isJazzShare(String u) { return u != null && u.contains("jazzdrive.com.pk/share/f/"); }

    private String buildMeta() {
        StringBuilder sb = new StringBuilder();
        if (category != null && !category.isEmpty()) sb.append(category);
        if (year > 0) { if (sb.length() > 0) sb.append("  •  "); sb.append(year); }
        return sb.toString();
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text != null ? text : "");
    }

    private void toast(String msg) { mainH.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()); }
}
