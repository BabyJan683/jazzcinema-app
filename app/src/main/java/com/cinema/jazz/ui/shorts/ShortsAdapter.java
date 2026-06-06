package com.cinema.jazz.ui.shorts;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.SparseArray;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import com.cinema.jazz.R;
import com.cinema.jazz.util.JazzDriveResolver;
import java.util.*;
import java.util.concurrent.*;

/**
 * Shorts adapter with:
 *  • CDN URL pre-resolution cache — all Jazz Drive URLs resolved in background
 *    the moment videos are appended. Playback is instant: no wait at swipe time.
 *  • TikTok-style bottom time bar (position / duration) shown on tap.
 *  • Gallery download via MediaStore (Android 10+) or DownloadManager (older).
 *  • Share button removed. Like/Unlike and Download active.
 */
public class ShortsAdapter extends RecyclerView.Adapter<ShortsAdapter.ShortHolder> {

    private static final String PREFS_LIKES = "jazz_shorts_likes";
    private static final String KEY_LIKES   = "liked_ids";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // CDN URL cache: Jazz Drive share URL → resolved CDN URL
    private static final Map<String, String> CDN_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<String, String>() {
            @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) {
                return size() > 100; // keep last 100 resolved URLs
            }
        });

    private static final ExecutorService RESOLVER_POOL = Executors.newFixedThreadPool(4);

    private final List<ShortVideo>         items       = new ArrayList<>();
    private final SparseArray<ShortHolder> liveHolders = new SparseArray<>();
    private final Context                  ctx;

    public ShortsAdapter(Context ctx) { this.ctx = ctx; }

    // ── data ──────────────────────────────────────────────────────────────────

    /**
     * Add videos and immediately kick off background CDN resolution for all Jazz Drive
     * URLs so that by the time the user swipes to them, the CDN URL is already cached.
     */
    public void appendVideos(List<ShortVideo> videos) {
        int start = items.size();
        items.addAll(videos);
        notifyItemRangeInserted(start, videos.size());
        preResolveCdnUrls(videos);
    }

    /** Pre-resolve Jazz Drive URLs in the background thread pool. */
    private void preResolveCdnUrls(List<ShortVideo> videos) {
        for (ShortVideo v : videos) {
            if (v.isJazzShareUrl() && !CDN_CACHE.containsKey(v.driveUrl)) {
                final String driveUrl = v.driveUrl;
                RESOLVER_POOL.execute(() ->
                    JazzDriveResolver.resolve(driveUrl, new JazzDriveResolver.ResolveCallback() {
                        @Override public void onResolved(String cdnUrl) {
                            CDN_CACHE.put(driveUrl, cdnUrl);
                        }
                        @Override public void onError(String msg) { /* will retry at play time */ }
                    })
                );
            }
        }
    }

    public ShortVideo getItem(int pos) { return items.get(pos); }
    @Override public int getItemCount() { return items.size(); }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @NonNull @Override
    public ShortHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_short, parent, false);
        return new ShortHolder(v);
    }

    @Override public void onBindViewHolder(@NonNull ShortHolder h, int pos) {
        liveHolders.put(pos, h);
        h.bind(items.get(pos));
    }

    @Override public void onViewRecycled(@NonNull ShortHolder h) {
        int pos = h.getAdapterPosition();
        if (pos != RecyclerView.NO_ID) liveHolders.remove(pos);
        h.releasePlayer();
    }

    // ── playback control ──────────────────────────────────────────────────────

    public void playAt(int pos) {
        ShortHolder h = liveHolders.get(pos);
        if (h != null) h.play();
    }

    public void pauseAll() {
        for (int i = 0; i < liveHolders.size(); i++) liveHolders.valueAt(i).pause();
    }

    public void preBufferAt(int pos) {
        ShortHolder h = liveHolders.get(pos);
        if (h != null) h.prepareIfNeeded();
    }

    public void releaseAll() {
        for (int i = 0; i < liveHolders.size(); i++) liveHolders.valueAt(i).releasePlayer();
        liveHolders.clear();
    }

    // ── likes ─────────────────────────────────────────────────────────────────

    private boolean isLiked(int id) {
        return ctx.getSharedPreferences(PREFS_LIKES, Context.MODE_PRIVATE)
            .getStringSet(KEY_LIKES, Collections.emptySet()).contains(String.valueOf(id));
    }

    private void toggleLike(int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_LIKES, Context.MODE_PRIVATE);
        Set<String> likes = new HashSet<>(p.getStringSet(KEY_LIKES, Collections.emptySet()));
        if (!likes.remove(String.valueOf(id))) likes.add(String.valueOf(id));
        p.edit().putStringSet(KEY_LIKES, likes).apply();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class ShortHolder extends RecyclerView.ViewHolder {
        final PlayerView  playerView;
        final ProgressBar pbBuffer;
        final TextView    txtTitle, txtMeta, txtLikes, txtTimeCurrent, txtTimeDuration;
        final ImageView   btnLike, btnSave, icPauseIndicator;
        final SeekBar     seekBarTime;
        final View        tapOverlay, layoutTimeBar;

        ExoPlayer  player;
        ShortVideo currentVideo;
        boolean    playerPrepared = false;
        boolean    manuallyPaused = false;
        boolean    timeBarVisible = false;

        // Handler to update time bar every second
        final Handler timeHandler = new Handler(Looper.getMainLooper());
        final Runnable timeUpdater = new Runnable() {
            @Override public void run() {
                if (player != null && player.isPlaying()) {
                    long pos  = player.getCurrentPosition();
                    long dur  = player.getDuration();
                    if (dur > 0) {
                        seekBarTime.setMax((int)(dur / 1000));
                        seekBarTime.setProgress((int)(pos / 1000));
                        txtTimeCurrent.setText(formatTime(pos));
                        txtTimeDuration.setText(formatTime(dur));
                    }
                }
                timeHandler.postDelayed(this, 500);
            }
        };

        ShortHolder(@NonNull View v) {
            super(v);
            playerView       = v.findViewById(R.id.player_view);
            pbBuffer         = v.findViewById(R.id.pb_buffer);
            txtTitle         = v.findViewById(R.id.txt_title);
            txtMeta          = v.findViewById(R.id.txt_meta);
            txtLikes         = v.findViewById(R.id.txt_likes);
            btnLike          = v.findViewById(R.id.btn_like);
            btnSave          = v.findViewById(R.id.btn_save);
            icPauseIndicator = v.findViewById(R.id.ic_pause_indicator);
            tapOverlay       = v.findViewById(R.id.tap_overlay);
            layoutTimeBar    = v.findViewById(R.id.layout_time_bar);
            seekBarTime      = v.findViewById(R.id.seek_bar_time);
            txtTimeCurrent   = v.findViewById(R.id.txt_time_current);
            txtTimeDuration  = v.findViewById(R.id.txt_time_duration);

            // SeekBar drag to seek
            seekBarTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && player != null) player.seekTo(progress * 1000L);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        void bind(ShortVideo video) {
            currentVideo   = video;
            playerPrepared = false;
            manuallyPaused = false;
            timeBarVisible = false;
            layoutTimeBar.setVisibility(View.GONE);
            stopTimeUpdater();

            txtTitle.setText(video.title != null ? video.title : "");
            String meta = "";
            if (video.category != null && !video.category.isEmpty()) meta += video.category;
            if (video.releaseYear > 0) meta += (meta.isEmpty() ? "" : "  •  ") + video.releaseYear;
            txtMeta.setText(meta);
            refreshLikeUI(video);

            // Tap: toggle pause/play AND toggle time bar (TikTok style)
            tapOverlay.setOnClickListener(vv -> {
                if (player == null) return;
                if (player.isPlaying()) {
                    player.pause();
                    manuallyPaused = true;
                    showPauseIcon(true);
                } else {
                    player.play();
                    manuallyPaused = false;
                    showPauseIcon(false);
                }
                // Toggle time bar visibility on each tap
                timeBarVisible = !timeBarVisible;
                layoutTimeBar.setVisibility(timeBarVisible ? View.VISIBLE : View.GONE);
                if (timeBarVisible) startTimeUpdater();
                else stopTimeUpdater();
            });

            btnLike.setOnClickListener(vv -> {
                toggleLike(video.id);
                refreshLikeUI(video);
                btnLike.animate().scaleX(1.4f).scaleY(1.4f).setDuration(120)
                    .withEndAction(() ->
                        btnLike.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
            });

            btnSave.setOnClickListener(vv -> startDownload(video));
            buildPlayer(video);
        }

        // ── player ────────────────────────────────────────────────────────────

        void buildPlayer(ShortVideo video) {
            releasePlayer();
            pbBuffer.setVisibility(View.VISIBLE);

            String cached = CDN_CACHE.get(video.driveUrl);

            if (video.isJazzShareUrl()) {
                if (cached != null) {
                    // Already resolved — instant playback, no network wait
                    initExoPlayer(cached);
                } else {
                    // Not yet resolved — resolve now in background
                    RESOLVER_POOL.execute(() ->
                        JazzDriveResolver.resolve(video.driveUrl, new JazzDriveResolver.ResolveCallback() {
                            @Override public void onResolved(String cdnUrl) {
                                CDN_CACHE.put(video.driveUrl, cdnUrl);
                                MAIN.post(() -> {
                                    if (currentVideo == null ||
                                        !currentVideo.driveUrl.equals(video.driveUrl)) return;
                                    initExoPlayer(cdnUrl);
                                });
                            }
                            @Override public void onError(String msg) {
                                MAIN.post(() -> {
                                    if (currentVideo == null ||
                                        !currentVideo.driveUrl.equals(video.driveUrl)) return;
                                    pbBuffer.setVisibility(View.GONE);
                                    Toast.makeText(ctx, "Load error: " + msg,
                                        Toast.LENGTH_SHORT).show();
                                });
                            }
                        })
                    );
                }
            } else {
                String url = video.streamUrl();
                if (url.isEmpty()) { pbBuffer.setVisibility(View.GONE); return; }
                initExoPlayer(url);
            }
        }

        private void initExoPlayer(String url) {
            player = new ExoPlayer.Builder(ctx).build();
            playerView.setPlayer(player);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) {
                        pbBuffer.setVisibility(View.VISIBLE);
                    } else {
                        pbBuffer.setVisibility(View.GONE);
                        if (state == Player.STATE_READY) playerPrepared = true;
                    }
                }
                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    showPauseIcon(!isPlaying && !pbBuffer.isShown() && player != null
                        && player.getPlaybackState() == Player.STATE_READY);
                    if (isPlaying && timeBarVisible) startTimeUpdater();
                    else if (!isPlaying) stopTimeUpdater();
                }
            });
            player.prepare();
        }

        void prepareIfNeeded() {
            if (player == null && currentVideo != null) buildPlayer(currentVideo);
        }

        void play() {
            if (player == null) return;
            manuallyPaused = false;
            player.setPlayWhenReady(true);
            showPauseIcon(false);
        }

        void pause() {
            if (player == null) return;
            player.setPlayWhenReady(false);
            stopTimeUpdater();
        }

        void releasePlayer() {
            stopTimeUpdater();
            if (player != null) { player.release(); player = null; playerPrepared = false; }
        }

        // ── time bar ──────────────────────────────────────────────────────────

        private void startTimeUpdater() { timeHandler.removeCallbacks(timeUpdater); timeHandler.post(timeUpdater); }
        private void stopTimeUpdater()  { timeHandler.removeCallbacks(timeUpdater); }

        private String formatTime(long ms) {
            long s = ms / 1000;
            return String.format("%d:%02d", s / 60, s % 60);
        }

        // ── download ──────────────────────────────────────────────────────────

        private void startDownload(ShortVideo video) {
            if (video.isJazzShareUrl()) {
                String cached = CDN_CACHE.get(video.driveUrl);
                if (cached != null) {
                    enqueueDownload(video, cached);
                } else {
                    Toast.makeText(ctx, "Preparing download…", Toast.LENGTH_SHORT).show();
                    RESOLVER_POOL.execute(() ->
                        JazzDriveResolver.resolve(video.driveUrl, new JazzDriveResolver.ResolveCallback() {
                            @Override public void onResolved(String cdnUrl) {
                                CDN_CACHE.put(video.driveUrl, cdnUrl);
                                MAIN.post(() -> enqueueDownload(video, cdnUrl));
                            }
                            @Override public void onError(String msg) {
                                MAIN.post(() -> Toast.makeText(ctx,
                                    "Download error: " + msg, Toast.LENGTH_LONG).show());
                            }
                        })
                    );
                }
            } else {
                String url = video.streamUrl();
                if (url.isEmpty()) {
                    Toast.makeText(ctx, "No download URL.", Toast.LENGTH_SHORT).show();
                    return;
                }
                enqueueDownload(video, url);
            }
        }

        /**
         * Download to gallery (DCIM/JazzCinema) so it appears in the Photos/Gallery app.
         * Android 10+ uses MediaStore; older uses DownloadManager into DCIM.
         */
        private void enqueueDownload(ShortVideo video, String url) {
            String title = video.title != null ? video.title : "JazzCinema Short";
            String safeName = title.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".mp4";

            try {
                DownloadManager dm =
                    (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) {
                    Toast.makeText(ctx, "Download not supported.", Toast.LENGTH_SHORT).show();
                    return;
                }
                DownloadManager.Request req =
                    new DownloadManager.Request(Uri.parse(url))
                        .setTitle(title)
                        .setDescription("Jazz Cinema · Saving to Gallery")
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DCIM, "JazzCinema/" + safeName)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                        .setMimeType("video/mp4");
                req.allowScanningByMediaScanner();
                dm.enqueue(req);
                Toast.makeText(ctx, "Saving to DCIM/JazzCinema...",
                    Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(ctx, "Download error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        }

        // ── UI helpers ────────────────────────────────────────────────────────

        private void refreshLikeUI(ShortVideo video) {
            boolean liked = isLiked(video.id);
            btnLike.setColorFilter(liked ? 0xFFEF233C : 0xFFFFFFFF);
            txtLikes.setText(liked ? "Unlike" : "Like");
        }

        private void showPauseIcon(boolean show) {
            if (icPauseIndicator == null) return;
            if (show) {
                icPauseIndicator.setVisibility(View.VISIBLE);
                icPauseIndicator.animate().alpha(1f).setDuration(150).start();
            } else {
                icPauseIndicator.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> icPauseIndicator.setVisibility(View.GONE)).start();
            }
        }
    }
}
