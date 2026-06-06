package com.cinema.jazz.ui.home;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.cinema.jazz.R;
import com.cinema.jazz.db.AppDatabase;
import com.cinema.jazz.db.DownloadEntity;
import java.util.List;
import java.util.concurrent.Executors;
public class TrendingFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s){
        return i.inflate(R.layout.fragment_trending, c, false);
    }
    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        RecyclerView rv = v.findViewById(R.id.rv_trending);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        Executors.newSingleThreadExecutor().execute(() -> {
            List<DownloadEntity> all = AppDatabase.getInstance(requireContext()).downloadDao().getAll();
            List<DownloadEntity> list = all.subList(0, Math.min(10, all.size()));
            if (isAdded()) requireActivity().runOnUiThread(() -> rv.setAdapter(new TrendingAdapter(list)));
        });
    }
}
