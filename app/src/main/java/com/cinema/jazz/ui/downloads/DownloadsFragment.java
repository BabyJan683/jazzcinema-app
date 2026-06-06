package com.cinema.jazz.ui.downloads;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import com.cinema.jazz.ui.player.PlayerActivity;
import com.cinema.jazz.util.DownloadEngine;
import com.cinema.jazz.util.JazzDriveResolver;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executors;

public class DownloadsFragment extends Fragment {
    private RecyclerView rv;
    private View emptyState;
    private final Handler mainH = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::loadData;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        return i.inflate(R.layout.fragment_downloads, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        rv = v.findViewById(R.id.rv_downloads);
        emptyState = v.findViewById(R.id.txt_empty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadData();
    }

    @Override public void onResume() { super.onResume(); loadData(); schedulePoll(); }
    @Override public void onPause()  { super.onPause();  mainH.removeCallbacks(pollRunnable); }

    private void schedulePoll() {
        mainH.removeCallbacks(pollRunnable);
        mainH.postDelayed(pollRunnable, 2000);
    }

    private void loadData() {
        if (!isAdded()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<DownloadEntity> list = AppDatabase.getInstance(requireContext()).downloadDao().getAll();
            mainH.post(() -> {
                if (!isAdded()) return;
                if (list.isEmpty()) {
                    rv.setVisibility(View.GONE);
                    if (emptyState != null) emptyState.setVisibility(View.VISIBLE);
                } else {
                    if (emptyState != null) emptyState.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                    rv.setAdapter(new DownloadsAdapter(list,
                        this::onPlay, this::onDelete, this::onResume));
                }
                boolean active = list.stream().anyMatch(d -> "downloading".equals(d.status));
                if (active && isAdded()) schedulePoll();
            });
        });
    }

    /**
     * Play action:
     *  - completed  → play from local file.
     *  - saved/watched → resolve Jazz Drive URL first, then stream.
     *  - downloading/paused → show toast.
     */
    private void onPlay(DownloadEntity item) {
        if ("completed".equals(item.status) && item.filePath != null && !item.filePath.isEmpty()) {
            launchPlayer(item.title, "file://" + item.filePath);

        } else if ("saved".equals(item.status) || "watched".equals(item.status)) {
            String url = item.driveUrl;
            if (url == null || url.isEmpty()) {
                Toast.makeText(requireContext(), "No URL available for this item.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (url.contains("jazzdrive.com.pk/share/f/")) {
                Toast.makeText(requireContext(), "Loading stream…", Toast.LENGTH_SHORT).show();
                Executors.newSingleThreadExecutor().execute(() ->
                    JazzDriveResolver.resolve(url, new JazzDriveResolver.ResolveCallback() {
                        public void onResolved(String streamUrl) {
                            mainH.post(() -> launchPlayer(item.title, streamUrl));
                        }
                        public void onError(String msg) {
                            mainH.post(() -> {
                                if (url.startsWith("http")) launchPlayer(item.title, url);
                                else Toast.makeText(requireContext(),
                                    "Cannot play: " + msg, Toast.LENGTH_LONG).show();
                            });
                        }
                    }));
            } else {
                launchPlayer(item.title, url);
            }

        } else {
            String status = item.status != null ? item.status : "unknown";
            Toast.makeText(requireContext(),
                "downloading".equals(status) ? "Still downloading… please wait."
                                             : "Tap Resume to continue download.",
                Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Resume a paused or failed download.
     * Re-resolves the URL via JazzDriveResolver if needed, then calls DownloadEngine.resume().
     */
    private void onResume(DownloadEntity item) {
        if (item.driveUrl == null || item.driveUrl.isEmpty()) {
            Toast.makeText(requireContext(), "No URL to resume from.", Toast.LENGTH_SHORT).show();
            return;
        }

        item.status = "downloading";
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).downloadDao().update(item);
            mainH.post(() -> {
                Toast.makeText(requireContext(), "Resuming download…", Toast.LENGTH_SHORT).show();
                loadData();
            });
        });

        String url = item.driveUrl;
        if (url.contains("jazzdrive.com.pk/share/f/")) {
            // Need to re-resolve the share link to get a fresh CDN URL
            Executors.newSingleThreadExecutor().execute(() ->
                JazzDriveResolver.resolve(url, new JazzDriveResolver.ResolveCallback() {
                    public void onResolved(String cdnUrl) {
                        runResumeTransfer(item, cdnUrl);
                    }
                    public void onError(String msg) {
                        mainH.post(() -> Toast.makeText(requireContext(),
                            "Cannot resume: " + msg, Toast.LENGTH_LONG).show());
                        item.status = "failed";
                        Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(requireContext()).downloadDao().update(item));
                    }
                }));
        } else {
            // Direct URL — resume immediately
            Executors.newSingleThreadExecutor().execute(() -> runResumeTransfer(item, url));
        }
    }

    private void runResumeTransfer(DownloadEntity item, String cdnUrl) {
        if (!isAdded()) return;
        DownloadEngine.resume(requireContext(), item, cdnUrl, new DownloadEngine.ProgressCallback() {
            public void onProgress(int p, long dl, long t, long spd) {
                if (isAdded()) mainH.post(DownloadsFragment.this::loadData);
            }
            public void onSuccess(String path) {
                item.filePath = path; item.status = "completed"; item.progress = 100;
                AppDatabase.getInstance(requireContext()).downloadDao().update(item);
                mainH.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                            "Download complete: " + item.title, Toast.LENGTH_SHORT).show();
                        loadData();
                    }
                });
            }
            public void onError(String msg) {
                mainH.post(() -> {
                    if (isAdded())
                        Toast.makeText(requireContext(),
                            "Resume failed: " + msg, Toast.LENGTH_LONG).show();
                    loadData();
                });
            }
        });
    }

    private void launchPlayer(String title, String url) {
        if (!isAdded()) return;
        Intent i = new Intent(requireContext(), PlayerActivity.class);
        i.putExtra(Constants.KEY_TITLE, title);
        i.putExtra(Constants.KEY_DRIVE_URL, url);
        startActivity(i);
    }

    private void onDelete(DownloadEntity item) {
        new AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setTitle("Delete")
            .setMessage("Remove \"" + item.title + "\" from list?")
            .setPositiveButton("Delete", (d, w) ->
                Executors.newSingleThreadExecutor().execute(() -> {
                    if (item.filePath != null && !item.filePath.isEmpty())
                        new File(item.filePath).delete();
                    AppDatabase.getInstance(requireContext()).downloadDao().deleteById(item.id);
                    mainH.post(this::loadData);
                }))
            .setNegativeButton("Cancel", null)
            .show();
    }
}
