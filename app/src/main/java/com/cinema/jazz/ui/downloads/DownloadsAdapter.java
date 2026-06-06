package com.cinema.jazz.ui.downloads;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cinema.jazz.R;
import com.cinema.jazz.db.DownloadEntity;
import com.cinema.jazz.util.DownloadEngine;
import java.util.List;
import java.util.function.Consumer;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.VH> {
    private final List<DownloadEntity> list;
    private final Consumer<DownloadEntity> onPlay, onDelete, onResume;

    public DownloadsAdapter(List<DownloadEntity> list,
                            Consumer<DownloadEntity> onPlay,
                            Consumer<DownloadEntity> onDelete,
                            Consumer<DownloadEntity> onResume) {
        this.list = list; this.onPlay = onPlay; this.onDelete = onDelete; this.onResume = onResume;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_download, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DownloadEntity d = list.get(pos);
        h.title.setText(d.title);
        h.category.setText(d.category != null ? d.category : "");

        if (d.thumbUrl != null && !d.thumbUrl.isEmpty())
            Glide.with(h.thumb).load(d.thumbUrl)
                .placeholder(R.drawable.ic_movie_placeholder).centerCrop().into(h.thumb);

        switch (d.status != null ? d.status : "") {

            case "downloading":
                h.progress.setVisibility(View.VISIBLE);
                h.progress.setProgress((int) d.progress);
                String downloaded = DownloadEngine.formatSize(d.downloadedBytes);
                String total      = d.totalBytes > 0 ? DownloadEngine.formatSize(d.totalBytes) : "?";
                String speed      = d.speedBps > 0 ? DownloadEngine.formatSpeed(d.speedBps) : "Connecting...";
                h.sizeInfo.setText(downloaded + " / " + total + "  (" + (int) d.progress + "%)");
                h.speedInfo.setText("Speed: " + speed);
                h.speedInfo.setVisibility(View.VISIBLE);
                h.btnPlay.setEnabled(false); h.btnPlay.setAlpha(0.4f); h.btnPlay.setText("Downloading...");
                h.btnResume.setVisibility(View.GONE);
                break;

            case "paused":
                h.progress.setVisibility(View.VISIBLE);
                h.progress.setProgress((int) d.progress);
                String dlSz = DownloadEngine.formatSize(d.downloadedBytes);
                String ttSz = d.totalBytes > 0 ? DownloadEngine.formatSize(d.totalBytes) : "?";
                h.sizeInfo.setText("Paused · " + dlSz + " / " + ttSz + "  (" + (int) d.progress + "%)");
                h.speedInfo.setVisibility(View.GONE);
                h.btnPlay.setEnabled(false); h.btnPlay.setAlpha(0.4f); h.btnPlay.setText("Paused");
                h.btnResume.setVisibility(View.VISIBLE);
                h.btnResume.setOnClickListener(v -> onResume.accept(d));
                break;

            case "completed":
                h.progress.setVisibility(View.GONE);
                String sz = d.totalBytes > 0 ? DownloadEngine.formatSize(d.totalBytes) : "";
                h.sizeInfo.setText("Saved" + (sz.isEmpty() ? "" : " · " + sz));
                h.speedInfo.setVisibility(View.GONE);
                h.btnPlay.setEnabled(true); h.btnPlay.setAlpha(1f); h.btnPlay.setText("Play Offline");
                h.btnResume.setVisibility(View.GONE);
                break;

            case "failed":
                h.progress.setVisibility(View.GONE);
                h.sizeInfo.setText("Download failed — tap Resume to retry");
                h.speedInfo.setVisibility(View.GONE);
                h.btnPlay.setEnabled(false); h.btnPlay.setAlpha(0.4f); h.btnPlay.setText("Failed");
                h.btnResume.setVisibility(View.VISIBLE);
                h.btnResume.setOnClickListener(v -> onResume.accept(d));
                break;

            default:
                h.progress.setVisibility(View.GONE);
                h.sizeInfo.setText("Saved to Watch List");
                h.speedInfo.setVisibility(View.GONE);
                h.btnPlay.setEnabled(true); h.btnPlay.setAlpha(1f); h.btnPlay.setText("Stream");
                h.btnResume.setVisibility(View.GONE);
                break;
        }

        h.btnPlay.setOnClickListener(v -> onPlay.accept(d));
        h.btnDelete.setOnClickListener(v -> onDelete.accept(d));
    }

    @Override public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView  thumb;
        TextView   title, category, sizeInfo, speedInfo;
        Button     btnPlay, btnResume;
        ImageButton btnDelete;
        ProgressBar progress;

        VH(View v) {
            super(v);
            thumb    = v.findViewById(R.id.img_thumb);
            title    = v.findViewById(R.id.txt_title);
            category = v.findViewById(R.id.txt_category);
            sizeInfo = v.findViewById(R.id.txt_size);
            speedInfo= v.findViewById(R.id.txt_speed);
            btnPlay  = v.findViewById(R.id.btn_play);
            btnDelete= v.findViewById(R.id.btn_delete);
            btnResume= v.findViewById(R.id.btn_resume);
            progress = v.findViewById(R.id.progress_download);
        }
    }
}
