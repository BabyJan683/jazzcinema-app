package com.cinema.jazz.util;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import com.cinema.jazz.control.RemoteConfigManager;

/**
 * v8.7 — Notice URL now comes from GitHub config.json via RemoteConfigManager.
 * Change notice_url in your config.json → all users see a different page instantly.
 */
public class NoticeDialog {

    public static void show(Context ctx) {
        // URL from GitHub remote config — changeable without APK update
        String noticeUrl = RemoteConfigManager.noticeUrl();

        Dialog dialog = new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCancelable(false);

        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF0A0A0A);

        WebView webView = new WebView(ctx);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);

        // Loading overlay
        LinearLayout loadingOverlay = new LinearLayout(ctx);
        loadingOverlay.setOrientation(LinearLayout.VERTICAL);
        loadingOverlay.setGravity(Gravity.CENTER);
        loadingOverlay.setBackgroundColor(0xFF0A0A0A);

        ProgressBar spinner = new ProgressBar(ctx);
        spinner.setIndeterminate(true);
        loadingOverlay.addView(spinner, new LinearLayout.LayoutParams(80, 80));

        TextView loadingText = new TextView(ctx);
        loadingText.setText("Wait — Checking for Updates...");
        loadingText.setTextColor(0xFFCCCCCC);
        loadingText.setTextSize(15);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, 24, 0, 0);
        loadingOverlay.addView(loadingText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Buttons
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setBackgroundColor(0xFF111111);
        btnRow.setPadding(24, 16, 24, 16);

        Button btnClose = new Button(ctx);
        btnClose.setText("Close");
        btnClose.setTextColor(0xFFAAAAAA);
        btnClose.setBackgroundColor(0xFF222222);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp.setMargins(8, 0, 8, 0);
        btnRow.addView(btnClose, bp);

        Button btnOk = new Button(ctx);
        btnOk.setText("OK");
        btnOk.setTextColor(0xFFFFFFFF);
        btnOk.setBackgroundColor(0xFF00B4D8);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bp2.setMargins(8, 0, 8, 0);
        btnRow.addView(btnOk, bp2);

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(btnRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(outer, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dialog.setContentView(root);

        final String[] updateLink = {null};
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                loadingOverlay.setVisibility(View.GONE);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("http")) { updateLink[0] = url; }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                if (p == 100) loadingOverlay.setVisibility(View.GONE);
            }
        });

        webView.loadUrl(noticeUrl);

        btnClose.setOnClickListener(v -> { webView.destroy(); dialog.dismiss(); });
        btnOk.setOnClickListener(v -> {
            if (updateLink[0] != null && !updateLink[0].equals(noticeUrl)) {
                try { ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updateLink[0]))); }
                catch (Exception ignored) {}
            }
            webView.destroy(); dialog.dismiss();
        });

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
