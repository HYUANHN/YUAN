package com.videobox.movie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.videobox.movie.R;
import com.videobox.movie.data.Category;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分类 tab：从播放源动态读取主分类入口，点击进入该主分类的数据页。
 */
public class CategoryFragment extends Fragment {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final List<Category> mainCats = new ArrayList<>();
    private String lastApiBase = "";
    private RecyclerView rv;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_category, container, false);
        rv = root.findViewById(R.id.rv_category);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        PlaySource source = Prefs.get(requireContext()).getPlaySource();
        if (source == null) {
            setGrid();
            Toast.makeText(requireContext(), "请先配置播放源接口", Toast.LENGTH_LONG).show();
            return;
        }
        String api = source.getApiBase();
        if (!api.equals(lastApiBase) || mainCats.isEmpty()) {
            lastApiBase = api;
            loadCats(api);
        }
    }

    private void loadCats(final String api) {
        exec.execute(() -> {
            List<Category> cats;
            try {
                cats = ApiClient.fetchCategories(api);
            } catch (Exception e) {
                cats = new ArrayList<>();
            }
            List<Category> mains = new ArrayList<>();
            for (Category c : cats) if (c.isMain()) mains.add(c);
            main.post(() -> {
                mainCats.clear();
                mainCats.addAll(mains);
                setGrid();
            });
        });
    }

    private void setGrid() {
        if (rv == null) return;
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                Category cat = mainCats.get(position);
                TextView tv = h.itemView.findViewById(R.id.tv_category_name);
                tv.setText(cat.name);
                h.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(requireContext(), CategoryListActivity.class);
                    i.putExtra("type_name", cat.name);
                    i.putExtra("type", cat.id);
                    startActivity(i);
                });
            }

            @Override
            public int getItemCount() { return mainCats.size(); }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }
}
