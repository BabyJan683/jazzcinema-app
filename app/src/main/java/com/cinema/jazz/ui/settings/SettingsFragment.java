package com.cinema.jazz.ui.settings;

import android.content.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.auth.AuthManager;
import com.cinema.jazz.auth.LoginActivity;
import com.cinema.jazz.control.RemoteConfigManager;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DatabaseManager;
import com.cinema.jazz.model.UserProfile;
import com.cinema.jazz.util.DataUsageManager;
import com.cinema.jazz.util.WhatsAppHelper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    private SharedPreferences prefs;
    private int tapCount = 0;

    private ProgressBar pbDataUsage;
    private TextView    txtDataUsed, txtDataTotal, txtDataPercent, txtDataPackageName;
    private View        layoutDataPackage, layoutNoPackage;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshDataUsage;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_settings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        prefs = requireContext().getSharedPreferences("jazz_settings", 0);

        // ── Profile card ─────────────────────────────────────────────────────
        UserProfile u = AuthManager.getCurrentUser(requireContext());
        if (u != null) {
            set(v, R.id.txt_email,  u.email);
            set(v, R.id.txt_status, statusLabel(u));
            set(v, R.id.txt_plan,   u.plan != null ? u.plan : "Free");
            set(v, R.id.txt_expiry, u.expiryDate != null && !u.expiryDate.isEmpty()
                ? "Expires: " + u.expiryDate : "");
            set(v, R.id.txt_daily, u.isPending()
                ? u.dailyWatchCount + " / 10 today" : "Unlimited");
        }

        // ── Data usage card ───────────────────────────────────────────────────
        pbDataUsage        = v.findViewById(R.id.pb_data_usage);
        txtDataUsed        = v.findViewById(R.id.txt_data_used);
        txtDataTotal       = v.findViewById(R.id.txt_data_total);
        txtDataPercent     = v.findViewById(R.id.txt_data_percent);
        txtDataPackageName = v.findViewById(R.id.txt_data_package_name);
        layoutDataPackage  = v.findViewById(R.id.layout_data_package);
        layoutNoPackage    = v.findViewById(R.id.layout_no_package);
        refreshDataUsage();

        View btnSubscribe = v.findViewById(R.id.btn_subscribe_package);
        if (btnSubscribe != null)
            btnSubscribe.setOnClickListener(x -> WhatsAppHelper.showSubscribeDialog(requireContext()));

        // ── Theme ─────────────────────────────────────────────────────────────
        View themeRow = v.findViewById(R.id.row_theme);
        TextView txtTheme = v.findViewById(R.id.txt_theme);
        String curTheme = prefs.getString(Constants.PREF_THEME, Constants.THEME_DEFAULT);
        if (txtTheme != null) txtTheme.setText(themeLabel(curTheme));
        if (themeRow != null) themeRow.setOnClickListener(x -> {
            String[] opts = {"Default (Red / Dark)", "Light Blue + Dark Orange"};
            new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
                .setTitle("App Theme")
                .setItems(opts, (d, w) -> {
                    String chosen = (w == 1) ? Constants.THEME_BLUE_ORANGE : Constants.THEME_DEFAULT;
                    prefs.edit().putString(Constants.PREF_THEME, chosen).apply();
                    if (txtTheme != null) txtTheme.setText(themeLabel(chosen));
                    requireActivity().recreate();
                }).show();
        });

        // ── Logout ────────────────────────────────────────────────────────────
        View logout = v.findViewById(R.id.btn_logout);
        if (logout != null) logout.setOnClickListener(x ->
            new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
                .setTitle("Logout").setMessage("Sign out of Jazz Cinema?")
                .setPositiveButton("Logout", (d, w) -> {
                    AuthManager.logout(requireContext());
                    startActivity(new Intent(requireContext(), LoginActivity.class));
                    requireActivity().finish();
                }).setNegativeButton("Cancel", null).show());

        // ── Player / quality toggles ──────────────────────────────────────────
        setupToggle(v, R.id.toggle_autoplay,     "autoplay",     true);
        setupToggle(v, R.id.toggle_pip,           "pip",          true);
        setupToggle(v, R.id.toggle_gestures,      "gestures",     true);
        setupToggle(v, R.id.toggle_subtitles,     "subtitles",    false);
        setupToggle(v, R.id.toggle_background,    "background",   false);
        setupToggle(v, R.id.toggle_datasaver,     "datasaver",    false);
        setupToggle(v, R.id.toggle_hardaccel,     "hardaccel",    true);
        setupToggle(v, R.id.toggle_doubleskip,    "doubleskip",   true);
        setupToggle(v, R.id.toggle_longpress2x,   "longpress2x",  true);
        setupToggle(v, R.id.toggle_notifications, "notifications",true);
        setupToggle(v, R.id.toggle_autodownload,  "autodownload", false);
        setupToggle(v, R.id.toggle_wifi_only,     "wifi_only",    false);
        setupToggle(v, R.id.toggle_resume,        "resume",       true);
        setupToggle(v, R.id.toggle_incognito,     "incognito",    false);
        setupToggle(v, R.id.toggle_parental,      "parental",     false);

        // ── Selection rows ────────────────────────────────────────────────────
        sel(v, R.id.row_default_player, R.id.txt_default_player, "default_player", "Built-in",
            "Default Player",     new String[]{"Built-in","MX Player","VLC"});
        sel(v, R.id.row_stream_quality, R.id.txt_stream_quality, "stream_quality", "Auto",
            "Stream Quality",     new String[]{"Auto","1080p","720p","480p","360p"});
        sel(v, R.id.row_dl_quality,     R.id.txt_dl_quality,     "dl_quality",     "720p",
            "Download Quality",   new String[]{"1080p","720p","480p","360p"});
        sel(v, R.id.row_speed_default,  R.id.txt_speed_default,  "speed_default",  "1.0×",
            "Default Speed",      new String[]{"0.5×","0.75×","1.0×","1.25×","1.5×","2.0×"});
        sel(v, R.id.row_subtitle_lang,  R.id.txt_subtitle_lang,  "sub_lang",       "English",
            "Subtitle Language",  new String[]{"English","Urdu","Arabic","Hindi","Off"});

        // ── WhatsApp support ──────────────────────────────────────────────────
        View wa = v.findViewById(R.id.row_whatsapp);
        if (wa != null) wa.setOnClickListener(x -> WhatsAppHelper.showSubscribeDialog(requireContext()));

        // ────────────────────────────────────────────────────────────────────
        // ── DATABASE STATUS CARD ─────────────────────────────────────────────
        // Shows active DB (Primary / Backup 1 / Offline) and last sync time
        // ────────────────────────────────────────────────────────────────────
        View dbStatusCard = v.findViewById(R.id.card_db_status);
        if (dbStatusCard != null) {
            dbStatusCard.setVisibility(View.VISIBLE);
            refreshDbStatus(v);
        }

        // ── DB Status Refresh button ──────────────────────────────────────────
        View btnDbRefresh = v.findViewById(R.id.btn_db_status_refresh);
        if (btnDbRefresh != null) {
            btnDbRefresh.setOnClickListener(x -> {
                Toast.makeText(requireContext(), "Checking database…", Toast.LENGTH_SHORT).show();
                Executors.newSingleThreadExecutor().execute(() -> {
                    try { DatabaseManager.getConnection(requireContext()).close(); }
                    catch (Exception ignored) {}
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        refreshDbStatus(v);
                        Toast.makeText(requireContext(),
                            "Movies: " + DatabaseManager.getMovieDbLabel(requireContext())
                            + " | Shorts: " + DatabaseManager.getTikTokDbLabel(requireContext()),
                            Toast.LENGTH_LONG).show();
                    });
                });
            });
        }

        // ─────────────────────────────────────────────────────────────────────
        // ── OFFLINE / CACHE SETTINGS ─────────────────────────────────────────
        // ─────────────────────────────────────────────────────────────────────
        View offlineCard = v.findViewById(R.id.card_offline_cache);
        if (offlineCard != null) offlineCard.setVisibility(View.VISIBLE);

        // Cache size display
        refreshCacheInfo(v);

        // Clear all cache button
        View btnClearCache = v.findViewById(R.id.btn_clear_cache);
        if (btnClearCache != null) {
            btnClearCache.setOnClickListener(x ->
                new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
                    .setTitle("Clear Offline Cache")
                    .setMessage("This will delete all cached movies and shorts. "
                        + "You will need internet to reload them. Continue?")
                    .setPositiveButton("Clear", (d, w) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            AppDatabase db = AppDatabase.getInstance(requireContext());
                            db.cachedMovieDao().clearAll();
                            db.cachedShortDao().clearAll();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (!isAdded()) return;
                                refreshCacheInfo(v);
                                Toast.makeText(requireContext(),
                                    "Cache cleared", Toast.LENGTH_SHORT).show();
                            });
                        });
                    })
                    .setNegativeButton("Cancel", null).show());
        }

        // Clear movies cache only
        View btnClearMovies = v.findViewById(R.id.btn_clear_movie_cache);
        if (btnClearMovies != null) {
            btnClearMovies.setOnClickListener(x -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getInstance(requireContext()).cachedMovieDao().clearAll();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        refreshCacheInfo(v);
                        Toast.makeText(requireContext(),
                            "Movie cache cleared", Toast.LENGTH_SHORT).show();
                    });
                });
            });
        }

        // Clear shorts cache only
        View btnClearShorts = v.findViewById(R.id.btn_clear_short_cache);
        if (btnClearShorts != null) {
            btnClearShorts.setOnClickListener(x -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getInstance(requireContext()).cachedShortDao().clearAll();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        refreshCacheInfo(v);
                        Toast.makeText(requireContext(),
                            "Shorts cache cleared", Toast.LENGTH_SHORT).show();
                    });
                });
            });
        }

        // ── Force Config Refresh button ───────────────────────────────────────
        View btnConfigRefresh = v.findViewById(R.id.btn_config_refresh);
        if (btnConfigRefresh != null) {
            btnConfigRefresh.setOnClickListener(x -> {
                Toast.makeText(requireContext(),
                    "Refreshing config from GitHub…", Toast.LENGTH_SHORT).show();
                RemoteConfigManager.forceRefresh(requireContext());
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isAdded()) return;
                    refreshDbStatus(v);
                    Toast.makeText(requireContext(),
                        "Config refreshed!", Toast.LENGTH_SHORT).show();
                }, 3000);
            });
        }

        // ── Version + hidden admin tap ────────────────────────────────────────
        TextView ver = v.findViewById(R.id.txt_app_version);
        if (ver != null) {
            ver.setText("Jazz Cinema  v" + Constants.APP_VERSION);
            ver.setOnClickListener(x -> {
                tapCount++;
                if (tapCount == 7) { tapCount = 0; showAdmin(); }
                else if (tapCount > 7) tapCount = 0;
            });
        }

        View dbRow = v.findViewById(R.id.txt_db_info);
        if (dbRow != null) dbRow.setVisibility(View.GONE);
    }

    // ── DB status card refresh ────────────────────────────────────────────────

    private void refreshDbStatus(View root) {
        if (!isAdded() || getContext() == null) return;

        TextView txtDbActive = root.findViewById(R.id.txt_db_active);
        TextView txtDbSync   = root.findViewById(R.id.txt_db_last_sync);

        if (txtDbActive != null) {
            String movieLabel  = DatabaseManager.getMovieDbLabel(requireContext());
            String tiktokLabel = DatabaseManager.getTikTokDbLabel(requireContext());
            String movieIcon   = movieLabel.startsWith("Primary") ? "🟢"
                               : movieLabel.startsWith("Backup")  ? "🟡" : "🔴";
            String tiktokIcon  = tiktokLabel.startsWith("TikTok Primary") ? "🟢"
                               : tiktokLabel.startsWith("TikTok Backup")  ? "🟡" : "🔴";
            txtDbActive.setText(
                movieIcon + " Movies: " + movieLabel + "\n" +
                tiktokIcon + " Shorts: " + tiktokLabel);
        }

        if (txtDbSync != null) {
            long lastFetch = RemoteConfigManager.lastFetchTime(requireContext());
            if (lastFetch == 0) {
                txtDbSync.setText("Config: Never synced");
            } else {
                String time = new SimpleDateFormat("HH:mm  dd MMM", Locale.getDefault())
                    .format(new Date(lastFetch));
                txtDbSync.setText("Config synced: " + time);
            }
        }
    }

    // ── Cache info refresh ────────────────────────────────────────────────────

    private void refreshCacheInfo(View root) {
        if (!isAdded() || getContext() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            int movieCount = db.cachedMovieDao().getAll().size();
            int shortCount = db.cachedShortDao().count();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                TextView txtMovieCache = root.findViewById(R.id.txt_movie_cache_count);
                TextView txtShortCache = root.findViewById(R.id.txt_short_cache_count);
                if (txtMovieCache != null)
                    txtMovieCache.setText(movieCount + " movies cached offline");
                if (txtShortCache != null)
                    txtShortCache.setText(shortCount + " shorts cached offline");
            });
        });
    }

    // ── Data usage refresh ────────────────────────────────────────────────────

    private void refreshDataUsage() {
        if (!isAdded() || getContext() == null) return;
        long pkgGb    = DataUsageManager.getPackageGB(requireContext());
        long usedByte = DataUsageManager.getUsedBytes(requireContext());
        long pkgBytes = DataUsageManager.getPackageBytes(requireContext());

        if (pkgGb > 0) {
            if (layoutDataPackage != null) layoutDataPackage.setVisibility(View.VISIBLE);
            if (layoutNoPackage   != null) layoutNoPackage.setVisibility(View.GONE);
            String usedStr  = DataUsageManager.formatBytes(usedByte);
            String totalStr = pkgGb + " GB";
            int    pct      = pkgBytes > 0 ? (int)(usedByte * 100 / pkgBytes) : 0;
            if (txtDataPackageName != null) txtDataPackageName.setText(pkgGb + " GB Package");
            if (txtDataUsed        != null) txtDataUsed.setText(usedStr + " used");
            if (txtDataTotal       != null) txtDataTotal.setText("of " + totalStr);
            if (txtDataPercent     != null) txtDataPercent.setText(pct + "%");
            if (pbDataUsage        != null) {
                pbDataUsage.setMax(100);
                pbDataUsage.setProgress(Math.min(pct, 100));
            }
        } else {
            if (layoutDataPackage != null) layoutDataPackage.setVisibility(View.GONE);
            if (layoutNoPackage   != null) layoutNoPackage.setVisibility(View.VISIBLE);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override public void onResume() {
        super.onResume();
        refreshHandler.post(refreshRunnable);
        scheduleRefresh();
    }

    @Override public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void scheduleRefresh() {
        refreshHandler.postDelayed(() -> {
            refreshDataUsage();
            if (isAdded()) scheduleRefresh();
        }, 5000);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String themeLabel(String key) {
        return Constants.THEME_BLUE_ORANGE.equals(key) ? "Blue + Orange" : "Default";
    }

    private void showAdmin() {
        EditText pin = new EditText(requireContext());
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setHint("Enter PIN"); pin.setTextColor(0xFFFFFFFF); pin.setHintTextColor(0x88FFFFFF);
        new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setTitle("Admin Access").setView(pin)
            .setPositiveButton("Unlock", (d, w) -> {
                if ("2580".equals(pin.getText().toString()))
                    startActivity(new Intent(requireContext(),
                        com.cinema.jazz.ui.admin.AdminPanelActivity.class));
                else Toast.makeText(requireContext(), "Wrong PIN", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Cancel", null).show();
    }

    private void setupToggle(View root, int id, String key, boolean def) {
        View t = root.findViewById(id);
        if (t == null) return;
        boolean on = prefs.getBoolean(key, def);
        if (t instanceof com.google.android.material.switchmaterial.SwitchMaterial) {
            com.google.android.material.switchmaterial.SwitchMaterial sw =
                (com.google.android.material.switchmaterial.SwitchMaterial) t;
            sw.setChecked(on);
            sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        } else {
            t.setSelected(on);
            t.setOnClickListener(vv -> {
                boolean cur = vv.isSelected(); vv.setSelected(!cur);
                prefs.edit().putBoolean(key, !cur).apply();
            });
        }
    }

    private void sel(View root, int rowId, int txtId, String key, String def,
                     String title, String[] opts) {
        TextView txt = root.findViewById(txtId);
        if (txt != null) txt.setText(prefs.getString(key, def));
        View row = root.findViewById(rowId);
        if (row == null) return;
        row.setOnClickListener(x ->
            new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
                .setTitle(title)
                .setItems(opts, (d, w) -> {
                    prefs.edit().putString(key, opts[w]).apply();
                    if (txt != null) txt.setText(opts[w]);
                }).show());
    }

    private void set(View root, int id, String text) {
        TextView tv = root.findViewById(id);
        if (tv != null && text != null) tv.setText(text);
    }

    private String statusLabel(UserProfile u) {
        if (u.isActive())    return "● Active";
        if (u.isBanned())    return "✕ Banned";
        if (u.isSuspended()) return "⊘ Suspended";
        if (u.isExpired())   return "⌛ Expired";
        return "◉ Pending";
    }
}
