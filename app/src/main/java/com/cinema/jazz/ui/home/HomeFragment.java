package com.cinema.jazz.ui.home;

import android.content.Intent;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.bumptech.glide.Glide;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.db.MovieRepository;
import com.cinema.jazz.db.WatchHistoryManager;
import com.cinema.jazz.model.Movie;
import com.cinema.jazz.ui.detail.MovieDetailActivity;
import com.cinema.jazz.ui.detail.SeriesEpisodesActivity;
import com.cinema.jazz.ui.seeall.SeeAllActivity;
import com.cinema.jazz.util.SeriesHelper;
import java.util.*;

public class HomeFragment extends Fragment {

    private LinearLayout categoriesContainer;
    private ImageView    imgBanner, imgHeaderLogo;
    private TextView     txtBannerTitle, txtBannerCategory;
    private View         progressBar, errorLayout, searchLayout, bannerSection;
    private TextView     txtError;
    private EditText     edtSearch;
    private ImageView    btnSearchClear;
    private LinearLayout searchContainer;
    private RecyclerView rvSearch;
    private View         btnRefresh;

    private final Handler mainH   = new Handler(Looper.getMainLooper());
    private final Handler bannerH = new Handler(Looper.getMainLooper());
    private final Runnable bannerRunnable = this::rotateBanner;

    private List<Movie> bannerMovies = new ArrayList<>();
    private int         bannerIdx    = 0;
    private List<Movie> allMovies    = new ArrayList<>();
    private MovieSearchAdapter searchAdapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_home, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        categoriesContainer = v.findViewById(R.id.categories_container);
        imgBanner           = v.findViewById(R.id.img_banner);
        imgHeaderLogo       = v.findViewById(R.id.img_header_logo);
        txtBannerTitle      = v.findViewById(R.id.txt_banner_title);
        txtBannerCategory   = v.findViewById(R.id.txt_banner_category);
        progressBar         = v.findViewById(R.id.progress_loading);
        errorLayout         = v.findViewById(R.id.layout_error);
        txtError            = v.findViewById(R.id.txt_error_msg);
        searchLayout        = v.findViewById(R.id.layout_search_results);
        edtSearch           = v.findViewById(R.id.edt_search);
        btnSearchClear      = v.findViewById(R.id.btn_search_clear);
        searchContainer     = v.findViewById(R.id.search_container);
        rvSearch            = v.findViewById(R.id.rv_search_results);
        bannerSection       = v.findViewById(R.id.banner_section);
        btnRefresh          = v.findViewById(R.id.btn_refresh);

        if (imgHeaderLogo != null) {
            int r = getResources().getIdentifier("app_logo", "drawable", requireActivity().getPackageName());
            imgHeaderLogo.setImageResource(r != 0 ? r : Constants.LOGO_DRAWABLE);
        }

        if (btnRefresh != null) btnRefresh.setOnClickListener(vv -> refreshNewMovies());

        View btnRetry = v.findViewById(R.id.btn_retry);
        if (btnRetry != null) btnRetry.setOnClickListener(x -> startLoad());

        setupSearchBar();

        if (rvSearch != null) {
            rvSearch.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            searchAdapter = new MovieSearchAdapter(new ArrayList<>(), this::openDetail);
            rvSearch.setAdapter(searchAdapter);
        }

        startLoad();
    }

    // ── Search bar setup ──────────────────────────────────────────────────────

    private void setupSearchBar() {
        if (edtSearch == null) return;

        // Focus → red border; blur → normal border
        edtSearch.setOnFocusChangeListener((view, hasFocus) -> {
            if (searchContainer != null) {
                searchContainer.setBackground(ContextCompat.getDrawable(requireContext(),
                    hasFocus ? R.drawable.bg_search_bar_focused : R.drawable.bg_search_bar));
            }
            if (!hasFocus && edtSearch.getText().toString().isEmpty()) {
                if (searchLayout != null) searchLayout.setVisibility(View.GONE);
                if (bannerSection != null) bannerSection.setVisibility(View.VISIBLE);
                if (categoriesContainer != null) categoriesContainer.setVisibility(View.VISIBLE);
            }
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                boolean hasText = s.length() > 0;
                // Show/hide X button
                if (btnSearchClear != null)
                    btnSearchClear.setVisibility(hasText ? View.VISIBLE : View.GONE);
                // Hide/show content behind overlay
                if (bannerSection != null)
                    bannerSection.setVisibility(hasText ? View.GONE : View.VISIBLE);
                if (categoriesContainer != null)
                    categoriesContainer.setVisibility(hasText ? View.GONE : View.VISIBLE);
                filterSearch(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        // Clear X button — clears text and resets UI
        if (btnSearchClear != null) {
            btnSearchClear.setOnClickListener(vv -> {
                edtSearch.setText("");
                edtSearch.clearFocus();
                btnSearchClear.setVisibility(View.GONE);
                if (searchLayout != null) searchLayout.setVisibility(View.GONE);
                if (bannerSection != null) bannerSection.setVisibility(View.VISIBLE);
                if (categoriesContainer != null) categoriesContainer.setVisibility(View.VISIBLE);
                if (searchContainer != null)
                    searchContainer.setBackground(ContextCompat.getDrawable(requireContext(),
                        R.drawable.bg_search_bar));
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
            });
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void startLoad() {
        setLoading(true);
        hideError();
        MovieRepository.fetchWithCache(requireContext(),
            (data, err) -> mainH.post(() -> {
                if (!isAdded()) return;
                setLoading(false);
                if (data != null && !data.isEmpty()) buildUI(data);
            }),
            (data, err) -> mainH.post(() -> {
                if (!isAdded()) return;
                setLoading(false);
                if (data != null && !data.isEmpty()) {
                    buildUI(data);
                    if (err != null) showOfflineBanner(err);
                } else if (err != null
                        && (categoriesContainer == null || categoriesContainer.getChildCount() == 0)) {
                    showError(err);
                }
            }));
    }

    private void refreshNewMovies() {
        if (btnRefresh != null) btnRefresh.setEnabled(false);
        Toast.makeText(requireContext(), "Checking for new movies...", Toast.LENGTH_SHORT).show();
        MovieRepository.fetchNewOnly(requireContext(), (data, err) -> mainH.post(() -> {
            if (!isAdded()) return;
            if (btnRefresh != null) btnRefresh.setEnabled(true);
            if (data == null || data.isEmpty()) {
                Toast.makeText(requireContext(), "No new movies found", Toast.LENGTH_SHORT).show();
                return;
            }
            int count = 0;
            for (List<Movie> l : data.values()) count += l.size();
            Toast.makeText(requireContext(), count + " new movie(s) loaded!", Toast.LENGTH_LONG).show();
            buildUI(data);
        }));
    }

    // ── UI building ───────────────────────────────────────────────────────────

    private void buildUI(Map<String, List<Movie>> data) {
        if (!isAdded() || getContext() == null || categoriesContainer == null) return;
        hideError();
        categoriesContainer.removeAllViews();
        allMovies.clear();
        for (List<Movie> list : data.values()) allMovies.addAll(list);

        // Banner setup
        bannerMovies.clear();
        for (List<Movie> list : data.values()) {
            if (!list.isEmpty()) bannerMovies.add(list.get(0));
            if (bannerMovies.size() >= 10) break;
        }
        bannerIdx = 0;
        if (!bannerMovies.isEmpty()) {
            setupBanner(bannerMovies.get(0));
            bannerH.removeCallbacks(bannerRunnable);
            bannerH.postDelayed(bannerRunnable, 5000);
        }

        // ── Continue Watching row (from local Room DB) ────────────────────────
        WatchHistoryManager.getRecent(requireContext(), history -> {
            if (!isAdded() || categoriesContainer == null) return;
            if (!history.isEmpty()) {
                addCategoryRow("⏯ Continue Watching", history);
            }
            // Then add all the main categories
            for (Map.Entry<String, List<Movie>> e : data.entrySet())
                if (!e.getValue().isEmpty()) addCategoryRow(e.getKey(), e.getValue());
        });
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    private void rotateBanner() {
        if (!isAdded() || bannerMovies.isEmpty()) return;
        bannerIdx = (bannerIdx + 1) % bannerMovies.size();
        Movie m = bannerMovies.get(bannerIdx);
        if (imgBanner != null) {
            imgBanner.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                if (!isAdded()) return;
                setupBanner(m);
                imgBanner.animate().alpha(1f).setDuration(400).start();
            }).start();
        } else {
            setupBanner(m);
        }
        bannerH.postDelayed(bannerRunnable, 5000);
    }

    private void setupBanner(Movie m) {
        if (!isAdded() || getContext() == null || imgBanner == null) return;
        Glide.with(this).load(m.thumbnailUrl).centerCrop().into(imgBanner);
        if (txtBannerTitle    != null) txtBannerTitle.setText(m.title);
        if (txtBannerCategory != null)
            txtBannerCategory.setText(m.category + (m.releaseYear > 0 ? "  -  " + m.releaseYear : ""));
        View root = getView();
        if (root == null) return;
        View bp = root.findViewById(R.id.btn_banner_play);
        View bi = root.findViewById(R.id.btn_banner_info);
        if (bp != null) bp.setOnClickListener(vv -> openDetail(m));
        if (bi != null) bi.setOnClickListener(vv -> openDetail(m));
    }

    // ── Category rows ─────────────────────────────────────────────────────────

    private void addCategoryRow(String label, List<Movie> rawItems) {
        if (!isAdded() || getContext() == null || categoriesContainer == null) return;
        boolean hasEpisodes = false;
        for (Movie m : rawItems) { if (SeriesHelper.isEpisode(m.title)) { hasEpisodes = true; break; } }
        boolean isSeason = label.toLowerCase().contains("season") || hasEpisodes;
        List<Movie> displayItems;
        if (isSeason) {
            Map<String, List<Movie>> grouped = SeriesHelper.groupBySeries(rawItems);
            SeriesEpisodesActivity.SERIES_CACHE.putAll(grouped);
            displayItems = SeriesHelper.representativeList(grouped);
        } else {
            displayItems = rawItems;
        }
        if (displayItems.isEmpty()) return;

        View row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_category_row, categoriesContainer, false);
        ((TextView) row.findViewById(R.id.txt_category_label)).setText(label);

        final List<Movie> finalDisplay = displayItems;
        TextView seeMore = row.findViewById(R.id.btn_see_more);
        if (seeMore != null) seeMore.setOnClickListener(vv -> {
            Intent i = new Intent(requireContext(), SeeAllActivity.class);
            i.putExtra("category", label);
            SeeAllActivity.cacheMovies(label, finalDisplay);
            startActivity(i);
        });

        RecyclerView rv = row.findViewById(R.id.rv_row);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rv.setHasFixedSize(true);
        rv.setAdapter(new MovieRowAdapter(displayItems, this::openDetail));
        categoriesContainer.addView(row);
    }

    // ── Search filter ─────────────────────────────────────────────────────────

    private void filterSearch(String query) {
        if (!isAdded() || searchLayout == null) return;
        if (query.trim().isEmpty()) {
            searchLayout.setVisibility(View.GONE);
            return;
        }
        searchLayout.setVisibility(View.VISIBLE);
        String q = query.toLowerCase().trim();
        List<Movie> filtered = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Movie m : allMovies) {
            if (seen.contains(m.id)) continue;
            if (m.title.toLowerCase().contains(q) || m.category.toLowerCase().contains(q)) {
                filtered.add(m);
                seen.add(m.id);
            }
        }
        if (searchAdapter != null) searchAdapter.update(filtered);
        if (rvSearch != null) rvSearch.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Detail navigation ─────────────────────────────────────────────────────

    private void openDetail(Movie m) {
        if (!isAdded() || getContext() == null) return;
        if (m.isSeries) {
            Intent i = new Intent(requireContext(), SeriesEpisodesActivity.class);
            i.putExtra("show_name", m.title);
            startActivity(i);
            return;
        }
        // Save to watch history
        WatchHistoryManager.record(requireContext(),
            m.id, m.title, m.thumbnailUrl, m.driveUrl, m.playUrl, m.category, m.releaseYear);

        Intent i = new Intent(requireContext(), MovieDetailActivity.class);
        i.putExtra(Constants.KEY_TITLE,    m.title);
        i.putExtra(Constants.KEY_DRIVE_URL,m.driveUrl);
        i.putExtra(Constants.KEY_PLAY_URL, m.playUrl);
        i.putExtra(Constants.KEY_THUMB_URL,m.thumbnailUrl);
        i.putExtra(Constants.KEY_CATEGORY, m.category);
        i.putExtra(Constants.KEY_YEAR,     m.releaseYear);
        startActivity(i);
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void showOfflineBanner(String msg) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean on) {
        if (!isAdded() || progressBar == null) return;
        boolean has = categoriesContainer != null && categoriesContainer.getChildCount() > 0;
        progressBar.setVisibility((on && !has) ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        if (!isAdded() || errorLayout == null) return;
        errorLayout.setVisibility(View.VISIBLE);
        if (txtError != null) txtError.setText(msg);
    }

    private void hideError() {
        if (!isAdded() || errorLayout == null) return;
        errorLayout.setVisibility(View.GONE);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        mainH.removeCallbacksAndMessages(null);
        bannerH.removeCallbacksAndMessages(null);
        categoriesContainer = null; imgBanner = null; imgHeaderLogo = null;
        txtBannerTitle = null; txtBannerCategory = null; progressBar = null;
        errorLayout = null; txtError = null; edtSearch = null; btnSearchClear = null;
        searchContainer = null; rvSearch = null; searchLayout = null;
        bannerSection = null; btnRefresh = null;
    }
}
