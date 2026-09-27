package com.videobox.movie.ui;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.videobox.movie.R;
import com.videobox.movie.data.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * 横向分类标签适配器（支持选中高亮）。主分类 / 子分类通用。
 */
public class CatTabAdapter extends RecyclerView.Adapter<CatTabAdapter.VH> {

    public interface OnSelect { void onSelect(Category c, int index); }

    private List<Category> data = new ArrayList<>();
    private final OnSelect listener;
    private int selected = 0;

    public CatTabAdapter(OnSelect listener) { this.listener = listener; }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<Category> d) {
        this.data = d == null ? new ArrayList<>() : d;
        selected = 0;
        notifyDataSetChanged();
    }

    public void setSelected(int index) {
        if (index >= 0 && index < data.size()) { selected = index; notifyDataSetChanged(); }
    }

    public Category getSelected() {
        return data.isEmpty() ? null : data.get(selected);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_chip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Category c = data.get(position);
        h.text.setText(c.name);
        h.text.setBackgroundResource(position == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        h.text.setTextColor(position == selected ? 0xFFFFFFFF : 0xFF9AA3AF);
        h.itemView.setOnClickListener(v -> {
            if (position != selected) {
                selected = position;
                notifyDataSetChanged();
            }
            if (listener != null) listener.onSelect(c, position);
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
