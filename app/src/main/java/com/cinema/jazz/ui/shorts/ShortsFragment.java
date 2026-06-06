package com.cinema.jazz.ui.shorts;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.cinema.jazz.MainActivity;
import com.cinema.jazz.R;
import com.cinema.jazz.ui.home.HomeFragment;
import java.util.List;

public class ShortsFragment extends Fragment {
    private static final int PRE_BUFFER_COUNT = 5;
    private static final int MAX_RETRY        = 3;

    private ViewPager2 viewPager;
    private ShortsAdapter adapter;
    private View layoutLoading, layoutError, layoutLoadingMore;
    private TextView txtError;
    private boolean loadingMore = false, allLoaded = false;
    private int retryCount = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        return inf.inflate(R.layout.fragment_shorts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        super.onViewCreated(v, saved);
        viewPager        = v.findViewById(R.id.vp_shorts);
        layoutLoading    = v.findViewById(R.id.layout_loading);
        layoutError      = v.findViewById(R.id.layout_error);
        layoutLoadingMore= v.findViewById(R.id.layout_loading_more);
        txtError         = v.findViewById(R.id.txt_error);
        adapter = new ShortsAdapter(requireContext());
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(PRE_BUFFER_COUNT);
        setBottomNavVisible(false);

        View btnBack = v.findViewById(R.id.btn_shorts_back);
        if (btnBack != null) btnBack.setOnClickListener(vv -> goToHome());

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                adapter.pauseAll();
                adapter.playAt(position);
                for (int i = 1; i <= PRE_BUFFER_COUNT; i++) adapter.preBufferAt(position + i);
                if (position < adapter.getItemCount())
                    ShortsRepository.markSeen(requireContext(), adapter.getItem(position).id);
                // Load more when 3 from end — with loading indicator instead of black screen
                if (!loadingMore && !allLoaded && position >= adapter.getItemCount() - 3)
                    loadMore();
            }
        });

        v.findViewById(R.id.btn_retry).setOnClickListener(vv -> {
            layoutError.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            retryCount = 0;
            loadInitial();
        });

        loadInitial();
    }

    private void loadInitial() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        ShortsRepository.fetchNext(requireContext(), new ShortsRepository.Callback() {
            @Override public void onResult(List<ShortVideo> videos) {
                if (!isAdded()) return;
                layoutLoading.setVisibility(View.GONE);
                retryCount = 0;
                if (videos.isEmpty()) {
                    showError("No Shorts available yet.");
                    return;
                }
                adapter.appendVideos(videos);
                adapter.playAt(0);
                for (int i = 1; i <= PRE_BUFFER_COUNT; i++) adapter.preBufferAt(i);
                if (adapter.getItemCount() > 0)
                    ShortsRepository.markSeen(requireContext(), adapter.getItem(0).id);
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                layoutLoading.setVisibility(View.GONE);
                if (retryCount < MAX_RETRY) {
                    retryCount++;
                    // Auto-retry after 2 seconds instead of showing black error screen
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdded()) loadInitial();
                    }, 2000);
                } else {
                    showError("Could not load Shorts.\n" + message);
                }
            }
        });
    }

    private void loadMore() {
        loadingMore = true;
        if (layoutLoadingMore != null)
            layoutLoadingMore.setVisibility(View.VISIBLE);

        ShortsRepository.fetchNext(requireContext(), new ShortsRepository.Callback() {
            @Override public void onResult(List<ShortVideo> videos) {
                loadingMore = false;
                if (!isAdded()) return;
                if (layoutLoadingMore != null)
                    layoutLoadingMore.setVisibility(View.GONE);
                if (videos.isEmpty()) {
                    allLoaded = true;
                    return;
                }
                adapter.appendVideos(videos);
            }
            @Override public void onError(String message) {
                loadingMore = false;
                if (!isAdded()) return;
                if (layoutLoadingMore != null)
                    layoutLoadingMore.setVisibility(View.GONE);
                // Retry loadMore after 3 seconds without showing error to user
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && !allLoaded) loadMore();
                }, 3000);
            }
        });
    }

    private void showError(String msg) {
        if (layoutError != null) layoutError.setVisibility(View.VISIBLE);
        if (txtError != null) txtError.setText(msg);
    }

    private void goToHome() {
        if (!isAdded() || getActivity() == null) return;
        if (adapter != null) adapter.pauseAll();
        setBottomNavVisible(true);
        getActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, new HomeFragment())
            .commit();
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).selectHomeNav();
    }

    @Override public void onResume() {
        super.onResume();
        setBottomNavVisible(false);
        if (adapter != null && adapter.getItemCount() > 0)
            adapter.playAt(viewPager.getCurrentItem());
    }

    @Override public void onPause() {
        super.onPause();
        if (adapter != null) adapter.pauseAll();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) adapter.releaseAll();
        setBottomNavVisible(true);
    }

    private void setBottomNavVisible(boolean visible) {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).setBottomNavVisible(visible);
    }
}
