package com.cinema.jazz.ui.player;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.auth.AuthManager;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import com.cinema.jazz.model.UserProfile;
import com.cinema.jazz.util.DownloadEngine;
import com.cinema.jazz.util.DownloadNotificationManager;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

@UnstableApi
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private View controlsOverlay, gestureOverlay, lockOverlay;
    private TextView txtTitle, txtError, txtBrightness, txtVolume, txtSpeed, txtPosition, txtDuration;
    private SeekBar seekBar;
    private ImageButton btnPlay, btnLock, btnRotate, btnPip, btnMute, btnBack, btnSettings;
    private ImageButton btnSkipBack, btnSkipFwd;
    private Button btnSpeed, btnAudio, btnSubtitle, btnMxPlayer;
    private ImageButton btnDownload;
    private ProgressBar bufferingSpinner;
    private View volumeIndicator, brightnessIndicator;

    private boolean isLocked = false;
    private boolean isMuted  = false;
    private float   currentSpeed = 1.0f;
    private boolean controlsVisible = true;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = this::hideControlsOverlay;

    private static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private int speedIdx = 3;

    private float gestureStartY, gestureStartBrightness, gestureStartVolume;
    private int   gestureRegion;
    private AudioManager audioManager;
    private int maxVolume;

    private boolean longPressActive = false;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = () -> {
        longPressActive = true;
        if (player != null) player.setPlaybackSpeed(2.0f);
        showToast("2× Speed");
    };

    private String videoUrl, title;
    private boolean isLive = false;
    private SharedPreferences prefs;

    // Audio track selection — index of currently selected audio group
    private int selectedAudioGroupIdx = -1;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_player);

        prefs    = getSharedPreferences("jazz_settings", MODE_PRIVATE);
        videoUrl = getIntent().getStringExtra(Constants.KEY_DRIVE_URL);
        title    = getIntent().getStringExtra(Constants.KEY_TITLE);
        isLive   = getIntent().getBooleanExtra("is_live", false);

        UserProfile u = AuthManager.getCurrentUser(this);
        if (u == null) { finish(); return; }
        if (!u.canWatch()) { showBlockScreen(u); return; }
        if (!u.isUnlimited() && !AuthManager.checkAndIncrementWatch(this)) {
            showToast("Daily limit reached (10/day). Upgrade to Active plan.");
            finish(); return;
        }

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume    = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        bindViews();

        String defaultPlayer = prefs.getString("default_player", "Built-in");
        if ("MX Player".equals(defaultPlayer)) { openInExternalMulti(MX_PACKAGES, "MX Player"); return; }
        else if ("VLC".equals(defaultPlayer))  { openInExternalMulti(VLC_PACKAGES, "VLC"); return; }

        setupPlayer();
        setupGestures();
        setupControls();
        scheduleHideControls();
        saveToHistory();
    }

    private void bindViews() {
        playerView          = findViewById(R.id.player_view);
        controlsOverlay     = findViewById(R.id.controls_overlay);
        gestureOverlay      = findViewById(R.id.gesture_overlay);
        lockOverlay         = findViewById(R.id.lock_overlay);
        txtTitle            = findViewById(R.id.txt_title);
        txtError            = findViewById(R.id.txt_error);
        txtBrightness       = findViewById(R.id.txt_brightness);
        txtVolume           = findViewById(R.id.txt_volume);
        txtSpeed            = findViewById(R.id.txt_speed_label);
        txtPosition         = findViewById(R.id.txt_position);
        txtDuration         = findViewById(R.id.txt_duration);
        seekBar             = findViewById(R.id.seek_bar);
        btnPlay             = findViewById(R.id.btn_play);
        btnLock             = findViewById(R.id.btn_lock);
        btnRotate           = findViewById(R.id.btn_rotate);
        btnPip              = findViewById(R.id.btn_pip);
        btnMute             = findViewById(R.id.btn_mute);
        btnBack             = findViewById(R.id.btn_back);
        btnSkipBack         = findViewById(R.id.btn_skip_back);
        btnSkipFwd          = findViewById(R.id.btn_skip_fwd);
        btnSpeed            = findViewById(R.id.btn_speed);
        btnAudio            = findViewById(R.id.btn_audio);
        btnMxPlayer         = findViewById(R.id.btn_open_mx);
        btnDownload         = findViewById(R.id.btn_download);
        btnSettings         = findViewById(R.id.btn_player_settings);
        bufferingSpinner    = findViewById(R.id.buffering_spinner);
        volumeIndicator     = findViewById(R.id.volume_indicator);
        brightnessIndicator = findViewById(R.id.brightness_indicator);
        if (txtTitle != null) txtTitle.setText(title != null ? title : "");
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        if (videoUrl != null && !videoUrl.isEmpty()) {
            player.setMediaItem(MediaItem.fromUri(videoUrl));
            player.prepare();
            player.play();
        } else {
            showError("E-404");
        }

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                runOnUiThread(() -> {
                    if (bufferingSpinner == null) return;
                    bufferingSpinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                    if (state == Player.STATE_READY && btnPlay != null)
                        btnPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                    if (state == Player.STATE_ENDED) finish();
                });
            }
            @Override public void onPlayerError(PlaybackException e) {
                runOnUiThread(() -> showError(parseErrorCode(e)));
            }
            @Override public void onTracksChanged(@NonNull Tracks tracks) {
                // Reset track selection index when new media loads
                selectedAudioGroupIdx = -1;
            }
        });

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                updateProgress();
                if (player != null) new Handler(Looper.getMainLooper()).postDelayed(this, 1000);
            }
        });
    }

    private void updateProgress() {
        if (player == null || seekBar == null || isLive) return;
        long dur = player.getDuration(); long pos = player.getCurrentPosition();
        if (dur > 0) {
            seekBar.setMax((int) dur); seekBar.setProgress((int) pos);
            if (txtPosition != null) txtPosition.setText(fmt(pos));
            if (txtDuration != null) txtDuration.setText(fmt(dur));
        }
    }

    private String fmt(long ms) {
        long s = ms / 1000; long m = s / 60; long h = m / 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m % 60, s % 60);
        return String.format("%02d:%02d", m, s % 60);
    }

    private void setupControls() {
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnPlay    != null) btnPlay.setOnClickListener(v -> togglePlay());
        if (btnLock    != null) btnLock.setOnClickListener(v -> toggleLock());
        if (btnRotate  != null) btnRotate.setOnClickListener(v -> toggleRotate());
        if (btnPip     != null) btnPip.setOnClickListener(v -> enterPip());
        if (btnMute    != null) btnMute.setOnClickListener(v -> toggleMute());
        if (btnSkipBack!= null) btnSkipBack.setOnClickListener(v -> skip(-10000));
        if (btnSkipFwd != null) btnSkipFwd.setOnClickListener(v -> skip(10000));
        if (btnSpeed   != null) btnSpeed.setOnClickListener(v -> cycleSpeed());
        if (btnAudio   != null) btnAudio.setOnClickListener(v -> showAudioPicker());
        if (btnMxPlayer!= null) btnMxPlayer.setOnClickListener(v -> showExternalPlayerMenu());
        if (btnDownload!= null) btnDownload.setOnClickListener(v -> startPlayerDownload());
        if (btnSettings!= null) btnSettings.setOnClickListener(v -> showPlayerSettings());

        if (seekBar != null) seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user && player != null) player.seekTo(p);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { cancelHideControls(); }
            @Override public void onStopTrackingTouch(SeekBar sb)  { scheduleHideControls(); }
        });

        if (controlsOverlay != null) controlsOverlay.setOnClickListener(v -> { if (!isLocked) toggleControls(); });
        if (lockOverlay     != null) lockOverlay.setOnClickListener(v -> { if (isLocked) toggleLock(); });
    }

    private static final String[] MX_PACKAGES = {
        "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro",
        "com.mxtech.videoplayer.v2", "com.mxtech.videoplayer.v2.ad",
        "com.mxtech.videoplayer"
    };
    private static final String[] VLC_PACKAGES = {
        "org.videolan.vlc", "org.videolan.vlc.betav", "org.videolan.vlc.debug"
    };

    private void showExternalPlayerMenu() {
        new AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Open in External Player")
            .setItems(new String[]{"MX Player", "VLC Player", "Cancel"}, (d, w) -> {
                if (w == 0) openInExternalMulti(MX_PACKAGES, "MX Player");
                else if (w == 1) openInExternalMulti(VLC_PACKAGES, "VLC");
            }).show();
    }

    private void openInExternalMulti(String[] packages, String appLabel) {
        if (videoUrl == null || videoUrl.isEmpty()) { showToast("No video URL available"); return; }
        for (String pkg : packages) { if (tryLaunchPackage(pkg)) return; }
        if (tryGenericVideoIntent()) return;
        showToast(appLabel + " not installed — playing in built-in player");
        if (player == null) { setupPlayer(); setupGestures(); setupControls(); scheduleHideControls(); }
    }

    private Uri buildVideoUri() {
        if (videoUrl == null) return null;
        if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://") || videoUrl.startsWith("rtsp://"))
            return Uri.parse(videoUrl);
        try {
            String path = videoUrl.startsWith("file://") ? videoUrl.substring(7) : videoUrl;
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(path));
        } catch (Exception e) { return Uri.parse(videoUrl); }
    }

    private boolean tryLaunchPackage(String pkg) {
        try {
            Uri uri = buildVideoUri(); if (uri == null) return false;
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return true; }
            return false;
        } catch (Exception e) { return false; }
    }

    private boolean tryGenericVideoIntent() {
        try {
            Uri uri = buildVideoUri(); if (uri == null) return false;
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) != null) { startActivity(Intent.createChooser(i, "Open with…")); return true; }
            return false;
        } catch (Exception e) { return false; }
    }

    // ─── Audio track selection ────────────────────────────────────────────────
    /**
     * FIX: Previously only showed a Toast; never called setTrackSelectionParameters.
     * Now uses TrackSelectionOverride to ACTUALLY switch the audio track in ExoPlayer.
     */
    private void showAudioPicker() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();

        // Collect all audio groups in order
        List<Tracks.Group> audioGroups = new ArrayList<>();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO) audioGroups.add(g);
        }

        if (audioGroups.isEmpty()) { showToast("No audio tracks available"); return; }
        if (audioGroups.size() == 1) { showToast("Only 1 audio track (already playing)"); return; }

        // Build label list
        String[] labels = new String[audioGroups.size()];
        for (int i = 0; i < audioGroups.size(); i++) {
            Format f = audioGroups.get(i).getTrackFormat(0);
            String lang = (f.language != null && !f.language.equals("und"))
                          ? f.language.toUpperCase() : "Track " + (i + 1);
            String ch   = f.channelCount > 0 ? "  " + f.channelCount + "ch" : "";
            String br   = f.bitrate > 0 ? "  " + (f.bitrate / 1000) + "kbps" : "";
            boolean sel = (i == selectedAudioGroupIdx);
            labels[i] = (sel ? "✓ " : "   ") + lang + ch + br;
        }

        new AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Select Audio Track")
            .setItems(labels, (d, which) -> applyAudioTrack(audioGroups, which))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void applyAudioTrack(List<Tracks.Group> audioGroups, int which) {
        if (player == null || which < 0 || which >= audioGroups.size()) return;
        Tracks.Group chosen = audioGroups.get(which);
        selectedAudioGroupIdx = which;

        // Build a TrackSelectionOverride that forces ExoPlayer to use this specific group
        TrackSelectionOverride override = new TrackSelectionOverride(
            chosen.getMediaTrackGroup(),
            Collections.singletonList(0)   // select first format within the group
        );

        player.setTrackSelectionParameters(
            player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
        );

        Format f = chosen.getTrackFormat(0);
        String name = (f.language != null && !f.language.equals("und"))
                      ? f.language.toUpperCase() : "Track " + (which + 1);
        showToast("Audio: " + name);
    }

    // ─── Player controls ──────────────────────────────────────────────────────

    private void showPlayerSettings() {
        String[] sections = {
            "── Playback ──", "Speed: " + currentSpeed + "×",
            "── Audio ──", "Audio Track", "Mute toggle",
            "── Display ──", "Screen orientation", "Picture-in-Picture", "Screen lock",
            "── Network ──", "Stream quality: Auto"
        };
        new AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("Player Settings")
            .setItems(sections, (d, w) -> onSettingTapped(sections[w]))
            .setNegativeButton("Close", null).show();
    }

    private void onSettingTapped(String s) {
        if (s.startsWith("Speed"))    { cycleSpeed(); return; }
        if (s.startsWith("Audio"))    { showAudioPicker(); return; }
        if (s.startsWith("Mute"))     { toggleMute(); return; }
        if (s.startsWith("Picture"))  { enterPip(); return; }
        if (s.startsWith("Screen l")) { toggleLock(); return; }
        if (s.startsWith("Screen o")) { toggleRotate(); return; }
        if (s.startsWith("──"))       { return; }
        showToast(s + " — saved");
    }

    private int getBrightnessPct() {
        float br = getWindow().getAttributes().screenBrightness;
        return br < 0 ? 50 : (int)(br * 100);
    }
    private int getVolumePct() {
        return (int)((float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume * 100);
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        if (gestureOverlay == null) return;
        gestureOverlay.setOnTouchListener((v, e) -> {
            if (isLocked) return false;
            float x = e.getX(), y = e.getY(), w = v.getWidth();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartY = y; gestureRegion = x < w * 0.5f ? 1 : 2;
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    gestureStartBrightness = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                    gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    longPressHandler.postDelayed(longPressRunnable, 650);
                    break;
                case MotionEvent.ACTION_MOVE:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    float delta = (gestureStartY - y) / v.getHeight();
                    if (gestureRegion == 1) {
                        float newBr = Math.max(0.01f, Math.min(1f, gestureStartBrightness + delta));
                        WindowManager.LayoutParams wlp = getWindow().getAttributes();
                        wlp.screenBrightness = newBr; getWindow().setAttributes(wlp);
                        showBrightnessIndicator((int)(newBr * 100));
                    } else {
                        int newVol = Math.max(0, Math.min(maxVolume, (int)(gestureStartVolume + delta * maxVolume)));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                        showVolumeIndicator((int)((float) newVol / maxVolume * 100));
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (longPressActive) {
                        longPressActive = false;
                        if (player != null) player.setPlaybackSpeed(currentSpeed);
                        showToast("Speed restored: " + currentSpeed + "×");
                    } else { showControlsOverlay(); scheduleHideControls(); }
                    hideGestureIndicators();
                    break;
            }
            return true;
        });
    }

    private void showBrightnessIndicator(int pct) {
        if (brightnessIndicator == null) return;
        brightnessIndicator.setVisibility(View.VISIBLE);
        if (txtBrightness != null) txtBrightness.setText("Brightness " + pct + "%");
    }
    private void showVolumeIndicator(int pct) {
        if (volumeIndicator == null) return;
        volumeIndicator.setVisibility(View.VISIBLE);
        if (txtVolume != null) txtVolume.setText("Volume " + pct + "%");
    }
    private void hideGestureIndicators() {
        if (brightnessIndicator != null) brightnessIndicator.setVisibility(View.GONE);
        if (volumeIndicator     != null) volumeIndicator.setVisibility(View.GONE);
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
        if (btnPlay != null) btnPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        scheduleHideControls();
    }
    private void skip(long ms) {
        if (player == null) return;
        player.seekTo(Math.max(0, player.getCurrentPosition() + ms));
        scheduleHideControls();
    }
    private void cycleSpeed() {
        speedIdx = (speedIdx + 1) % SPEEDS.length;
        currentSpeed = SPEEDS[speedIdx];
        if (player != null) player.setPlaybackSpeed(currentSpeed);
        if (btnSpeed != null) btnSpeed.setText(currentSpeed + "×");
        showToast(currentSpeed + "× Speed");
    }
    private void toggleMute() {
        isMuted = !isMuted;
        if (player != null) player.setVolume(isMuted ? 0 : 1);
        if (btnMute != null) btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
        showToast(isMuted ? "Muted" : "Unmuted");
    }
    private void toggleLock() {
        isLocked = !isLocked;
        if (controlsOverlay != null) controlsOverlay.setVisibility(isLocked ? View.GONE : View.VISIBLE);
        if (lockOverlay     != null) lockOverlay.setVisibility(isLocked ? View.VISIBLE : View.GONE);
        showToast(isLocked ? "Screen Locked" : "Unlocked");
    }
    private void toggleRotate() {
        int cur = getRequestedOrientation();
        setRequestedOrientation(cur == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                .setAspectRatio(new android.util.Rational(16, 9)).build());
        } else showToast("PIP requires Android 8+");
    }
    private void toggleControls() {
        if (controlsVisible) hideControlsOverlay(); else showControlsOverlay();
    }
    private void showControlsOverlay() {
        controlsVisible = true;
        if (controlsOverlay != null) {
            controlsOverlay.setVisibility(View.VISIBLE);
            controlsOverlay.animate().alpha(1).setDuration(200).start();
        }
        scheduleHideControls();
    }
    private void hideControlsOverlay() {
        controlsVisible = false;
        if (controlsOverlay != null)
            controlsOverlay.animate().alpha(0).setDuration(300)
                .withEndAction(() -> controlsOverlay.setVisibility(View.GONE)).start();
    }
    private void scheduleHideControls() {
        hideHandler.removeCallbacks(hideControls);
        hideHandler.postDelayed(hideControls, 3500);
    }
    private void cancelHideControls() { hideHandler.removeCallbacks(hideControls); }

    // ─── In-player download ───────────────────────────────────────────────────
    /**
     * Downloads the currently playing video.
     * videoUrl is already a resolved CDN URL (JazzDriveResolver was already called
     * before launching PlayerActivity), so we can pass it directly to DownloadEngine.
     */
    private void startPlayerDownload() {
        if (isLive) { showToast("Live streams cannot be downloaded."); return; }
        if (videoUrl == null || videoUrl.isEmpty()) { showToast("No video URL to download."); return; }

        if (btnDownload != null) btnDownload.setEnabled(false);
        showToast("Download started! See Downloads tab.");

        DownloadEntity e = new DownloadEntity();
        e.title        = title != null ? title : "Video";
        e.driveUrl     = videoUrl;
        e.thumbUrl     = "";
        e.category     = "";
        e.status       = "downloading";
        e.progress     = 0;
        e.downloadedAt = System.currentTimeMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            long id = AppDatabase.getInstance(this).downloadDao().insert(e);
            e.id = (int) id;
            DownloadEngine.download(this, e, videoUrl, new DownloadEngine.ProgressCallback() {
                public void onProgress(int p, long dl, long t, long speedBps) {
                    DownloadNotificationManager.notifyProgress(
                        PlayerActivity.this, e.id, e.title, p, dl, t, speedBps);
                }
                public void onSuccess(String path) {
                    e.filePath = path; e.status = "completed"; e.progress = 100;
                    AppDatabase.getInstance(PlayerActivity.this).downloadDao().update(e);
                    DownloadNotificationManager.notifyComplete(PlayerActivity.this, e.id, e.title);
                    runOnUiThread(() -> {
                        showToast("✅ Download complete: " + e.title);
                        if (btnDownload != null) {
                            btnDownload.setEnabled(true);
                            btnDownload.setImageResource(R.drawable.ic_play);
                        }
                    });
                }
                public void onError(String msg) {
                    e.status = "paused";
                    AppDatabase.getInstance(PlayerActivity.this).downloadDao().update(e);
                    DownloadNotificationManager.notifyFailed(PlayerActivity.this, e.id, e.title, msg);
                    runOnUiThread(() -> {
                        showToast("⚠ Download paused. Open Downloads tab to Resume.");
                        if (btnDownload != null) btnDownload.setEnabled(true);
                    });
                }
            });
        });
    }

    private void showError(String code) {
        if (txtError != null) { txtError.setText(code); txtError.setVisibility(View.VISIBLE); }
        if (bufferingSpinner != null) bufferingSpinner.setVisibility(View.GONE);
        showToast("Playback error: " + code);
    }
    private String parseErrorCode(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: return "E-503: No internet";
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:            return "E-404: Stream not found";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:           return "E-500: Server error";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:  return "E-3003: Unsupported format";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:   return "E-3004: Bad playlist";
            default: return "E-" + e.errorCode;
        }
    }
    private void showBlockScreen(UserProfile u) {
        showToast(u.isExpired() ? "Subscription expired." : u.isSuspended() ? "Account suspended." : "Access denied.");
        finish();
    }
    private void showToast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private void saveToHistory() {
        if (title == null || isLive) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            DownloadEntity e = new DownloadEntity();
            e.title = title; e.driveUrl = videoUrl;
            e.status = "watched"; e.filePath = "";
            AppDatabase.getInstance(this).downloadDao().insert(e);
        });
    }

    @Override protected void onStop()    { super.onStop(); if (player != null) player.pause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (player != null) { player.release(); player = null; } }

    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (controlsOverlay != null) controlsOverlay.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }
}
