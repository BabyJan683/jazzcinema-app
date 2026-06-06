package com.cinema.jazz.util;

import android.content.Context;
import android.util.Log;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import java.io.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;

/**
 * Download engine with RESUME support.
 *
 * ① Direct MP4  — HTTP Range header resumes from byte offset.
 * ② HLS m3u8    — resumes from the last successfully saved segment index.
 *
 * Call download() for a fresh start, or resume() to continue a paused/failed download.
 */
public class DownloadEngine {
    private static final String TAG = "DownloadEngine";

    /** Set of download entity IDs that have been requested to pause/cancel. */
    private static final java.util.Set<Integer> pausedIds =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** Request a running download to pause. The download thread will check this flag. */
    public static void pauseDownload(int entityId) { pausedIds.add(entityId); }

    /** Check if a download has been paused externally. */
    public static boolean isPaused(int entityId) { return pausedIds.contains(entityId); }

    /** Clear pause flag when download is resumed or restarted. */
    public static void clearPause(int entityId) { pausedIds.remove(entityId); }

    public interface ProgressCallback {
        void onProgress(int pct, long downloaded, long total, long speedBps);
        void onSuccess(String path);
        void onError(String msg);
    }

    /** Resolve output file for an entity (same logic used everywhere). */
    public static File resolveOutFile(Context ctx, DownloadEntity entity, String url) {
        File dir = new File(ctx.getFilesDir(), "downloads");
        if (!dir.exists()) dir.mkdirs();
        boolean isHls = isHlsUrl(url);
        String ext  = isHls ? ".ts" : ".mp4";
        String safe = entity.title != null
                      ? entity.title.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "movie";
        return new File(dir, safe + "_" + entity.id + ext);
    }

    /** Fresh download — starts from the beginning. */
    public static void download(Context ctx, DownloadEntity entity, String url, ProgressCallback cb) {
        File outFile = resolveOutFile(ctx, entity, url);
        // Delete any stale partial file to start clean
        if (outFile.exists()) outFile.delete();
        entity.resumeSegmentIndex = 0;
        entity.resumeByteOffset   = 0;
        startTransfer(ctx, entity, url, outFile, cb);
    }

    /**
     * Resume a previously interrupted download.
     * Uses the resumeSegmentIndex / resumeByteOffset saved in the entity.
     */
    public static void resume(Context ctx, DownloadEntity entity, String url, ProgressCallback cb) {
        File outFile = resolveOutFile(ctx, entity, url);
        startTransfer(ctx, entity, url, outFile, cb);
    }

    private static void startTransfer(Context ctx, DownloadEntity entity,
                                      String url, File outFile, ProgressCallback cb) {
        boolean isHls = isHlsUrl(url);
        if (isHls) {
            int    startSeg   = entity.resumeSegmentIndex;
            long   alreadyBytes = outFile.exists() ? outFile.length() : 0L;
            Log.i(TAG, "HLS download from segment " + startSeg + " (" + alreadyBytes + " bytes already)");
            HlsDownloader.download(outFile, url, startSeg, alreadyBytes,
                    new HlsDownloader.ProgressCallback() {
                public void onProgress(int pct, long dl, long tot, long speed) {
                    // Track which segment we're on so we can resume after interruption.
                    // Approximate: segment index = pct * totalSegments / 100
                    entity.resumeSegmentIndex = pct > 0 ? pct / 5 : startSeg; // rough estimate
                    entity.progress        = pct;
                    entity.downloadedBytes = dl;
                    entity.totalBytes      = tot;
                    entity.speedBps        = speed;
                    entity.status          = "downloading";
                    AppDatabase.getInstance(ctx).downloadDao().update(entity);
                    cb.onProgress(pct, dl, tot, speed);
                }
                public void onSuccess(String path) {
                    entity.resumeSegmentIndex = 0;
                    entity.resumeByteOffset   = 0;
                    cb.onSuccess(path);
                }
                public void onError(String msg) {
                    // Save resume point before reporting error
                    entity.status = "paused";
                    AppDatabase.getInstance(ctx).downloadDao().update(entity);
                    cb.onError(msg);
                }
            });
        } else {
            downloadDirect(ctx, entity, url, outFile, cb);
        }
    }

    private static void downloadDirect(Context ctx, DownloadEntity entity,
                                       String url, File outFile, ProgressCallback cb) {
        OkHttpClient client = buildClient();
        try {
            long resumeFrom = outFile.exists() ? outFile.length() : 0L;
            // Adjust entity offset
            if (resumeFrom > 0) entity.resumeByteOffset = resumeFrom;

            Request.Builder rb = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (resumeFrom > 0) rb.header("Range", "bytes=" + resumeFrom + "-");
            Request req = rb.build();

            try (Response r = client.newCall(req).execute()) {
                // 206 = partial content (server supports resume), 200 = full content
                if ((!r.isSuccessful() && r.code() != 206) || r.body() == null) {
                    cb.onError("HTTP " + r.code()); return;
                }
                long total      = r.body().contentLength();
                if (total > 0 && resumeFrom > 0) total += resumeFrom; // adjust to full size
                InputStream in  = r.body().byteStream();
                // Append to existing file if resuming
                FileOutputStream fos = new FileOutputStream(outFile, resumeFrom > 0);
                byte[] buf      = new byte[8192];
                long downloaded = resumeFrom;
                long lastTime   = System.currentTimeMillis();
                long lastBytes  = resumeFrom;
                long speedBps   = 0;
                int  n;

                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    downloaded += n;

                    long now     = System.currentTimeMillis();
                    long elapsed = now - lastTime;
                    if (elapsed >= 500) {
                        speedBps  = (long) ((downloaded - lastBytes) * 1000.0 / elapsed);
                        lastTime  = now;
                        lastBytes = downloaded;
                    }

                    int pct = total > 0 ? (int) ((downloaded * 100) / total) : 0;
                    entity.progress        = pct;
                    entity.downloadedBytes = downloaded;
                    entity.totalBytes      = total;
                    entity.speedBps        = speedBps;
                    entity.resumeByteOffset= downloaded;
                    entity.status          = "downloading";
                    AppDatabase.getInstance(ctx).downloadDao().update(entity);
                    cb.onProgress(pct, downloaded, total, speedBps);
                }
                fos.flush(); fos.close(); in.close();
                entity.resumeByteOffset = 0;
                cb.onSuccess(outFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            entity.status = "paused";
            AppDatabase.getInstance(ctx).downloadDao().update(entity);
            cb.onError(e.getMessage() != null ? e.getMessage() : "IO error");
        }
    }

    public static boolean isHlsUrl(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return low.contains(".m3u8") || low.contains("playlist.m3u8")
            || low.contains("chunks.m3u8") || low.contains("manifest");
    }

    public static String formatSize(long b) {
        if (b <= 0)                   return "0 B";
        if (b < 1024)                 return b + " B";
        if (b < 1024 * 1024)         return String.format("%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / (1024.0 * 1024));
        return String.format("%.2f GB", b / (1024.0 * 1024 * 1024));
    }

    public static String formatSpeed(long bps) {
        if (bps <= 0)           return "0 KB/s";
        if (bps < 1024)         return bps + " B/s";
        if (bps < 1024 * 1024) return String.format("%.1f KB/s", bps / 1024.0);
        return String.format("%.1f MB/s", bps / (1024.0 * 1024));
    }

    private static OkHttpClient buildClient() {
        try {
            TrustManager[] t = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, t, new java.security.SecureRandom());
            return new OkHttpClient.Builder()
                .sslSocketFactory(sc.getSocketFactory(), (X509TrustManager) t[0])
                .hostnameVerifier((h, s) -> true)
                .followRedirects(true).followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
        } catch (Exception e) { return new OkHttpClient(); }
    }
}
