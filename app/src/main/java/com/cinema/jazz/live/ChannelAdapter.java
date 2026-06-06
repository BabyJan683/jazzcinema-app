package com.cinema.jazz.live;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.cinema.jazz.R;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {
    public interface OnChannelClick { void onClick(Channel c); }

    private final List<Channel> list;
    private final OnChannelClick listener;
    private int selectedPos = -1;

    public ChannelAdapter(List<Channel> list, OnChannelClick l) {
        this.list = list; this.listener = l;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_channel, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Channel c = list.get(pos);
        h.name.setText(c.name);
        h.genre.setText(c.genre);
        h.card.setSelected(pos == selectedPos);

        // Try local offline drawable first (ch_<channel_id> e.g. ch_ary_digital)
        // Falls back to URL if not bundled in APK
        int localRes = getLocalLogo(h.logo.getContext(), c.id);
        if (localRes != 0) {
            h.logo.setImageResource(localRes);
        } else {
            Glide.with(h.logo.getContext())
                .load(c.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_movie_placeholder)
                .error(R.drawable.ic_movie_placeholder)
                .into(h.logo);
        }

        h.card.setOnClickListener(v -> {
            int old = selectedPos; selectedPos = pos;
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(pos);
            listener.onClick(c);
        });
    }

    @Override public int getItemCount() { return list.size(); }

    /**
     * Maps channel id → local drawable resource.
     * Convention: channel id "ary-digital" → drawable name "ch_ary_digital"
     */
    private static int getLocalLogo(Context ctx, String channelId) {
        if (channelId == null || channelId.isEmpty()) return 0;
        String name = "ch_" + channelId.replace("-", "_");
        return ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView logo; TextView name, genre; View card;
        VH(View v) {
            super(v); card = v;
            logo  = v.findViewById(R.id.ch_logo);
            name  = v.findViewById(R.id.ch_name);
            genre = v.findViewById(R.id.ch_genre);
        }
    }
}
