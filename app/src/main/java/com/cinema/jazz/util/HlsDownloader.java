package com.cinema.jazz.util;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;

/**
 * HLS (m3u8) segment downloader with RESUME support.
 *
 * How it works:
 * 1. Fetch the m3u8 URL.
 * 2. If it is a master playlist, pick the highest-quality variant.
 * 3. Parse the media playlist to get ordered .ts segment URLs.
 * 4. Skip already-downloaded segments (startSegment > 0 for resume).
 * 5. Append new segments to the existing output file.
 * 6. Report real progress (bytes downloaded vs total estimate) and live speed.
 */
public class HlsDownloader {

    private static final String TAG = "HlsDownloader";

    public interface ProgressCallback {
        void onProgress(int pct, long downloaded, long totalEstimate, long speedBps);
        void onSuccess(String path);
        void onError(String msg);
    }

    /** Start a fresh download (startSegment = 0). */
    public static void download(File outFile, String m3u8Url, ProgressCallback cb) {
        download(outFile, m3u8Url, 0, 0L, cb);
    }

    /**
     * Resume-aware download.
     *
     * @param startSegment  Index of the first segment to download (0 = full download).
     * @param alreadyBytes  Bytes already written to outFile from a previous run.
     */
    public static void download(File outFile, String m3u8Url,
                                int startSegment, long alreadyBytes,
                                ProgressCallback cb) {
        OkHttpClient client = buildClient();
        try {
            String content = fetchText(client, m3u8Url);
            if (content == null) { cb.onError("Could not fetch playlist"); return; }

            String mediaUrl = resolveMediaPlaylist(client, m3u8Url, content);
            if (mediaUrl == null) { cb.onError("No media segments found in playlist"); return; }

            String mediaContent = mediaUrl.equals(m3u8Url) ? content : fetchText(client, mediaUrl);
            if (mediaContent == null) { cb.onError("Could not fetch media playlist"); return; }

            List<String> segUrls = parseSegments(mediaUrl, mediaContent);
            if (segUrls.isEmpty()) { cb.onError("Playlist has 0 segments"); return; }

            int total = segUrls.size();
            // Clamp startSegment in case playlist changed
            int from  = Math.max(0, Math.min(startSegment, total - 1));

            long downloadedBytes = alreadyBytes;
            long lastTime  = System.currentTimeMillis();
            long lastBytes = alreadyBytes;
            long speedBps  = 0;
            long totalEstimate = alreadyBytes > 0
                    ? (alreadyBytes / Math.max(from, 1)) * total
                    : (long) total * 2 * 1024 * 1024;

            // Append mode when resuming (from > 0 and file already exists)
            boolean append = (from > 0 && outFile.exists() && outFile.length() > 0);

            try (FileOutputStream fos = new FileOutputStream(outFile, append)) {
                for (int i = from; i < total; i++) {
                    byte[] seg = fetchBytes(client, segUrls.get(i));
                    if (seg == null) {
                        Log.w(TAG, "Segment " + i + " failed, skipping");
                        continue;
                    }
                    fos.write(seg);
                    downloadedBytes += seg.length;
                    totalEstimate = (downloadedBytes / (i - from + 1)) * (total - from) + alreadyBytes;

                    long now     = System.currentTimeMillis();
                    long elapsed = now - lastTime;
                    if (elapsed >= 500) {
                        speedBps  = (long) ((downloadedBytes - lastBytes) * 1000.0 / elapsed);
                        lastTime  = now;
                        lastBytes = downloadedBytes;
                    }

                    int pct = (int) ((i + 1) * 100 / total);
                    cb.onProgress(pct, downloadedBytes, totalEstimate, speedBps);
                }
                fos.flush();
            }
            cb.onSuccess(outFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "HLS download failed: " + e.getMessage());
            cb.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static String resolveMediaPlaylist(OkHttpClient client, String baseUrl, String content) {
        if (!content.contains("#EXT-X-STREAM-INF")) return baseUrl;
        String[] lines = content.split("\\n");
        long bestBandwidth = -1;
        String bestUri = null;
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                long bw  = parseBandwidth(line);
                String uri = lines[i + 1].trim();
                if (!uri.startsWith("#") && !uri.isEmpty() && bw > bestBandwidth) {
                    bestBandwidth = bw;
                    bestUri = resolveUrl(baseUrl, uri);
                }
            }
        }
        return bestUri != null ? bestUri : baseUrl;
    }

    private static List<String> parseSegments(String mediaPlaylistUrl, String content) {
        List<String> urls = new ArrayList<>();
        for (String line : content.split("\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            urls.add(resolveUrl(mediaPlaylistUrl, line));
        }
        return urls;
    }

    private static long parseBandwidth(String infLine) {
        try {
            int idx = infLine.toUpperCase().indexOf("BANDWIDTH=");
            if (idx < 0) return 0;
            String sub = infLine.substring(idx + 10);
            int end = sub.indexOf(','); if (end < 0) end = sub.length();
            return Long.parseLong(sub.substring(0, end).trim());
        } catch (Exception e) { return 0; }
    }

    private static String resolveUrl(String base, String relative) {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
        try { return new URI(base).resolve(relative).toString(); }
        catch (Exception e) {
            int s = base.lastIndexOf('/');
            return s >= 0 ? base.substring(0, s + 1) + relative : relative;
        }
    }

    private static String fetchText(OkHttpClient client, String url) {
        try {
            Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36").build();
            try (Response r = client.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return null;
                return r.body().string();
            }
        } catch (Exception e) { Log.e(TAG, "fetchText: " + e.getMessage()); return null; }
    }

    private static byte[] fetchBytes(OkHttpClient client, String url) {
        try {
            Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36").build();
            try (Response r = client.newCall(req).execute()) {
                if (!r.isSuccessful() || r.body() == null) return null;
                return r.body().bytes();
            }
        } catch (Exception e) { return null; }
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
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        } catch (Exception e) { return new OkHttpClient(); }
    }
}
