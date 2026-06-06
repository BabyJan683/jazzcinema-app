package com.cinema.jazz.ui.home;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.cinema.jazz.R;
import com.cinema.jazz.db.DownloadEntity;
import java.util.List;

public class TrendingAdapter extends RecyclerView.Adapter<TrendingAdapter.VH> {
    private final List<DownloadEntity> list;
    public TrendingAdapter(List<DownloadEntity> l){ this.list=l; }
    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t){
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_trending, p, false));
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        DownloadEntity e = list.get(pos);
        h.rank.setText(String.valueOf(pos+1));
        h.title.setText(e.title != null ? e.title : "Unknown");
        h.cat.setText(e.category != null ? e.category : "");
    }
    @Override public int getItemCount(){ return list.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView rank, title, cat;
        VH(View v){ super(v); rank=v.findViewById(R.id.txt_rank); title=v.findViewById(R.id.txt_title); cat=v.findViewById(R.id.txt_cat); }
    }
}
