package com.cinema.jazz.ui.detail;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.cinema.jazz.R;
import com.cinema.jazz.model.Movie;
import com.cinema.jazz.ui.player.PlayerActivity;
import com.cinema.jazz.util.JazzDriveResolver;
import com.cinema.jazz.util.SeriesHelper;
import com.cinema.jazz.util.TmdbHelper;
import com.cinema.jazz.Constants;
import java.util.*;
import java.util.concurrent.Executors;

public class SeriesEpisodesActivity extends AppCompatActivity {

    public static final Map<String, List<Movie>> SERIES_CACHE = new HashMap<>();

    private final Handler mainH = new Handler(Looper.getMainLooper());
    private String showName;
    private List<Movie> episodes = new ArrayList<>();

    private LinearLayout castContainer, episodeListContainer;
    private TextView txtOverview;
    private ProgressBar loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_episodes);

        showName = getIntent().getStringExtra("show_name");
        List<Movie> cached = SERIES_CACHE.get(showName);
        if (cached != null) episodes = new ArrayList<>(cached);

        ImageView imgHero    = findViewById(R.id.img_series_hero);
        TextView  txtTitle   = findViewById(R.id.txt_series_title);
        castContainer        = findViewById(R.id.series_cast_container);
        txtOverview          = findViewById(R.id.txt_series_overview);
        episodeListContainer = findViewById(R.id.episode_list_container);
        loadingBar           = findViewById(R.id.series_loading_bar);
        View btnBack         = findViewById(R.id.btn_series_back);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (!episodes.isEmpty() && imgHero != null) {
            Glide.with(this).load(episodes.get(0).thumbnailUrl)
                 .placeholder(R.drawable.ic_movie_placeholder)
                 .centerCrop().into(imgHero);
        }
        if (txtTitle != null)
            txtTitle.setText(showName != null ? showName.toUpperCase() : "");

        buildEpisodeRows();

        if (showName != null) {
            TmdbHelper.fetch(this, showName, info -> mainH.post(() -> {
                if (isFinishing() || info == null) return;
                if (txtOverview != null && !info.overview.isEmpty()) {
                    txtOverview.setText(info.overview);
                    txtOverview.setVisibility(View.VISIBLE);
                }
                if (castContainer != null && !info.cast.isEmpty()) {
                    castContainer.setVisibility(View.VISIBLE);
                    castContainer.removeAllViews();
                    for (TmdbHelper.Cast c : info.cast) addCastCard(c);
                }
            }));
        }
    }

    @Override
    public void onBackPressed() { super.onBackPressed(); finish(); }

    private void buildEpisodeRows() {
        if (episodeListContainer == null) return;
        episodeListContainer.removeAllViews();

        Map<Integer, List<Movie>> bySeason = new LinkedHashMap<>();
        for (Movie ep : episodes) {
            int s = SeriesHelper.seasonNumber(ep.title);
            if (!bySeason.containsKey(s)) bySeason.put(s, new ArrayList<>());
            bySeason.get(s).add(ep);
        }

        for (Map.Entry<Integer, List<Movie>> entry : bySeason.entrySet()) {
            int seasonNum      = entry.getKey();
            List<Movie> epList = entry.getValue();

            View header = LayoutInflater.from(this)
                .inflate(R.layout.item_season_header, episodeListContainer, false);
            TextView hTitle = header.findViewById(R.id.txt_season_header);
            if (hTitle != null) hTitle.setText("Season " + String.format("%02d", seasonNum));
            episodeListContainer.addView(header);

            for (Movie ep : epList) {
                View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_episode_row, episodeListContainer, false);

                ImageView thumb   = row.findViewById(R.id.img_ep_thumb);
                TextView  epNum   = row.findViewById(R.id.txt_ep_number);
                TextView  epTitle = row.findViewById(R.id.txt_ep_title);
                View      dlBtn   = row.findViewById(R.id.btn_ep_play);

                if (thumb != null && ep.thumbnailUrl != null && !ep.thumbnailUrl.isEmpty()) {
                    Glide.with(this).load(ep.thumbnailUrl)
                         .placeholder(R.drawable.ic_movie_placeholder)
                         .centerCrop().into(thumb);
                }

                int num  = SeriesHelper.epNumber(ep.title);
                int seas = SeriesHelper.seasonNumber(ep.title);
                String name = showName != null ? showName : ep.title;
                String epTag = String.format("S%02d E%d", seas, num > 0 ? num : 1);
                if (epTitle != null) epTitle.setText(name + "  " + epTag);
                if (epNum   != null) epNum.setText(num > 0 ? "Episode " + num : ep.title);

                row.setOnClickListener(v -> playEpisode(ep));
                if (dlBtn != null) dlBtn.setOnClickListener(v -> playEpisode(ep));

                episodeListContainer.addView(row);
            }
        }
    }

    private void playEpisode(Movie ep) {
        if (loadingBar != null) loadingBar.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            String direct = pickDirect(ep);
            if (direct != null) {
                mainH.post(() -> { hideLoading(); launchPlayer(ep.title, direct); });
                return;
            }
            String share = isJazzShare(ep.driveUrl) ? ep.driveUrl
                         : isJazzShare(ep.playUrl)  ? ep.playUrl : null;
            if (share == null) {
                mainH.post(() -> { hideLoading(); toast("No stream URL."); });
                return;
            }
            JazzDriveResolver.resolve(share, new JazzDriveResolver.ResolveCallback() {
                public void onResolved(String url) {
                    mainH.post(() -> { hideLoading(); launchPlayer(ep.title, url); });
                }
                public void onError(String msg) {
                    mainH.post(() -> { hideLoading(); toast("Error: " + msg); });
                }
            });
        });
    }

    private void launchPlayer(String title, String url) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(Constants.KEY_TITLE, title);
        i.putExtra(Constants.KEY_DRIVE_URL, url);
        startActivity(i);
    }

    private void hideLoading() {
        if (loadingBar != null) loadingBar.setVisibility(View.GONE);
    }

    private void addCastCard(TmdbHelper.Cast c) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_cast, castContainer, false);
        ImageView photo = card.findViewById(R.id.img_cast_photo);
        TextView  name  = card.findViewById(R.id.txt_cast_name);
        TextView  role  = card.findViewById(R.id.txt_cast_role);
        if (c.photoUrl != null) Glide.with(this).load(c.photoUrl).circleCrop().into(photo);
        if (name != null) name.setText(c.name);
        if (role != null) role.setText(c.character);
        castContainer.addView(card);
    }

    private boolean isJazzShare(String u) {
        return u != null && u.contains("jazzdrive.com.pk/share/f/");
    }

    private String pickDirect(Movie ep) {
        if (ep.playUrl  != null && ep.playUrl.startsWith("http")  && !isJazzShare(ep.playUrl))  return ep.playUrl;
        if (ep.driveUrl != null && ep.driveUrl.startsWith("http") && !isJazzShare(ep.driveUrl)) return ep.driveUrl;
        return null;
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
}
