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
public class MovieRowAdapter extends RecyclerView.Adapter<MovieRowAdapter.VH>{
    private final List<Movie> list;
    private final Consumer<Movie> onClick;
    public MovieRowAdapter(List<Movie>list,Consumer<Movie>onClick){this.list=list;this.onClick=onClick;}
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p,int t){return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_movie_card,p,false));}
    @Override public void onBindViewHolder(@NonNull VH h,int pos){Movie m=list.get(pos);h.title.setText(m.title);Glide.with(h.thumb).load(m.thumbnailUrl).placeholder(R.drawable.ic_movie_placeholder).centerCrop().into(h.thumb);h.itemView.setOnClickListener(v->onClick.accept(m));}
    @Override public int getItemCount(){return list.size();}
    static class VH extends RecyclerView.ViewHolder{ImageView thumb;TextView title;VH(View v){super(v);thumb=v.findViewById(R.id.img_card_thumb);title=v.findViewById(R.id.txt_card_title);}}
}
