package com.videobox.movie.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;

public class FavoriteFragment extends Fragment {

    private VodAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_favorite, container, false);
        empty = root.findViewById(R.id.tv_empty);
        adapter = new VodAdapter();
        adapter.setOnItemClick(item -> Ui.openDetail(requireContext(), item));
        androidx.recyclerview.widget.RecyclerView rv = root.findViewById(R.id.rv_favorite);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        rv.setAdapter(adapter);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        var list = Prefs.get(requireContext()).getFavorites();
        adapter.setData(list);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
