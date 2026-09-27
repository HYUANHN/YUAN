package com.videobox.movie.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.videobox.movie.R;

import java.util.List;

/**
 * 首页分类标签（电影/电视剧/综艺/动漫）横向适配器
 */
public class CategoryTabAdapter extends RecyclerView.Adapter<CategoryTabAdapter.VH> {

    public interface OnSelect { void onSelect(String name, int index); }

    private final List<String> data;
    private final OnSelect listener;
    private int selected = 0;

    public CategoryTabAdapter(List<String> data, OnSelect listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        String name = data.get(position);
        h.text.setText(name);
        h.text.setBackgroundResource(position == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        h.text.setTextColor(position == selected ? 0xFFFFFFFF : 0xFF9AA3AF);
        h.itemView.setOnClickListener(v -> {
            selected = position;
            notifyDataSetChanged();
            if (listener != null) listener.onSelect(name, position);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView text;
        VH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.tv_category_chip);
        }
    }
}
