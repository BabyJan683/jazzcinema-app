package com.cinema.jazz.ui.admin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class AdminPanelActivity extends AppCompatActivity {

    private static final String ADMIN_URL =
        "https://jazz-cinema--ritagleason2893.replit.app/admin-panel/";

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0A0A);

        // ── Top bar ──
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF1A1A2E);
        topBar.setPadding(16, 48, 16, 16);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(android.R.drawable.ic_menu_revert);
        btnBack.setBackgroundColor(0x00000000);
        btnBack.setColorFilter(0xFFFFFFFF);
        btnBack.setPadding(8, 8, 8, 8);
        btnBack.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("  Admin Panel");
        title.setTextColor(0xFFE50914);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        topBar.addView(btnBack);
        topBar.addView(title);

        // ── Progress bar ──
        ProgressBar progress = new ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);

        // ── WebView ──
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                progress.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req,
                                        WebResourceError err) {
                if (req.isForMainFrame()) {
                    v.loadData(
                        "<html><body style='background:#0a0a0a;color:#fff;"
                        + "font-family:sans-serif;text-align:center;padding:80px 24px'>"
                        + "<h2 style='color:#E50914'>⚠ Cannot Connect</h2>"
                        + "<p>Check your internet and try again.</p>"
                        + "</body></html>",
                        "text/html", "utf-8");
                    progress.setVisibility(View.GONE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                if (p >= 100) progress.setVisibility(View.GONE);
            }
        });

        // ── Layout ──
        int barH = (int)(64 * getResources().getDisplayMetrics().density);
        int prgH = (int)(4  * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams lBar = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, barH);
        root.addView(topBar, lBar);

        FrameLayout.LayoutParams lPrg = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, prgH);
        lPrg.topMargin = barH;
        root.addView(progress, lPrg);

        FrameLayout.LayoutParams lWv = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lWv.topMargin = barH;
        root.addView(webView, lWv);

        setContentView(root);
        webView.loadUrl(ADMIN_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
