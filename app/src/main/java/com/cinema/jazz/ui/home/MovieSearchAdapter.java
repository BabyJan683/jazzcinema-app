package com.cinema.jazz.ui.home;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cinema.jazz.R;
import com.cinema.jazz.model.Movie;
import java.util.List;
import java.util.function.Consumer;

/**
 * Search results adapter — 2-column grid, each cell shows:
 *   ┌──────────┐
 *   │  poster  │  ← centerCrop image (160dp tall)
 *   ├──────────┤
 *   │  title   │  ← bold white, 2 lines max
 *   │ category │  ← red small text
 *   └──────────┘
 */
public class MovieSearchAdapter extends RecyclerView.Adapter<MovieSearchAdapter.VH> {
    private List<Movie> list;
    private final Consumer<Movie> onClick;

    public MovieSearchAdapter(List<Movie> list, Consumer<Movie> onClick) {
        this.list = list;
        this.onClick = onClick;
    }

    public void update(List<Movie> l) {
        this.list = l;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_search_result, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Movie m = list.get(pos);
        h.title.setText(m.title != null ? m.title : "");

        if (h.category != null) {
            String cat = m.category != null ? m.category : "";
            String yr  = m.releaseYear > 0 ? "  " + m.releaseYear : "";
            h.category.setText(cat + yr);
        }

        if (m.thumbnailUrl != null && !m.thumbnailUrl.isEmpty()) {
            Glide.with(h.thumb)
                .load(m.thumbnailUrl)
                .placeholder(R.drawable.ic_movie_placeholder)
                .error(R.drawable.ic_movie_placeholder)
                .centerCrop()
                .into(h.thumb);
        } else {
            h.thumb.setImageResource(R.drawable.ic_movie_placeholder);
        }

        h.itemView.setOnClickListener(v -> onClick.accept(m));
    }

    @Override public int getItemCount() { return list == null ? 0 : list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView  title, category;
        VH(View v) {
            super(v);
            thumb    = v.findViewById(R.id.img_thumb);
            title    = v.findViewById(R.id.txt_title);
            category = v.findViewById(R.id.txt_category);
        }
    }
}
