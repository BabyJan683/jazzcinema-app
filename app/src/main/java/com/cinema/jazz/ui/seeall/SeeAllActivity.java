package com.cinema.jazz.ui.seeall;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cinema.jazz.Constants;
import com.cinema.jazz.R;
import com.cinema.jazz.model.Movie;
import com.cinema.jazz.ui.detail.MovieDetailActivity;
import com.cinema.jazz.ui.detail.SeriesEpisodesActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeeAllActivity extends AppCompatActivity {

    private static final Map<String, List<Movie>> CACHE = new HashMap<>();

    public static void cacheMovies(String category, List<Movie> movies) {
        CACHE.put(category, new ArrayList<>(movies));
    }

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_see_all);

        String category = getIntent().getStringExtra("category");
        List<Movie> movies = CACHE.getOrDefault(category, new ArrayList<>());

        TextView txtTitle = findViewById(R.id.txt_see_all_title);
        if (txtTitle != null) txtTitle.setText(category != null ? category : "Movies");

        android.view.View btnBack = findViewById(R.id.btn_see_all_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rv_see_all);
        if (rv != null) {
            rv.setLayoutManager(new GridLayoutManager(this, 3));
            rv.setHasFixedSize(true);
            rv.setAdapter(new SeeAllAdapter(movies, m -> openDetail(m)));
        }
    }

    private void openDetail(Movie m) {
        // If it's a series (grouped), open the episodes screen
        if (m.isSeries) {
            Intent i = new Intent(this, SeriesEpisodesActivity.class);
            i.putExtra("show_name", m.title);
            startActivity(i);
            return;
        }
        Intent i = new Intent(this, MovieDetailActivity.class);
        i.putExtra(Constants.KEY_TITLE,    m.title);
        i.putExtra(Constants.KEY_DRIVE_URL,m.driveUrl);
        i.putExtra(Constants.KEY_PLAY_URL, m.playUrl);
        i.putExtra(Constants.KEY_THUMB_URL,m.thumbnailUrl);
        i.putExtra(Constants.KEY_CATEGORY, m.category);
        i.putExtra(Constants.KEY_YEAR,     m.releaseYear);
        startActivity(i);
    }
}
