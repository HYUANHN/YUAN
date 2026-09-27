package com.videobox.movie.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.videobox.movie.R;
import com.videobox.movie.data.VodItem;

import java.util.List;

/**
 * 首页 Banner 轮播适配器
 */
public class BannerPagerAdapter extends RecyclerView.Adapter<BannerPagerAdapter.VH> {

    public interface OnBannerClick { void onClick(VodItem item); }

    private final List<VodItem> data = new java.util.ArrayList<>();
    private OnBannerClick listener;

    public BannerPagerAdapter() { }

    public void setData(List<VodItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    public void setOnBannerClick(OnBannerClick l) { this.listener = l; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_banner, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VodItem item = data.get(position);
        h.title.setText(item.getTitle());
        h.desc.setText(item.getSubtitle() + (item.vod_remarks != null && !item.vod_remarks.isEmpty()
                ? " · " + item.vod_remarks : ""));
        Img.load(h.itemView.getContext(), item.vod_pic, h.image);        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, desc;
        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.iv_banner);
            title = v.findViewById(R.id.tv_banner_title);
            desc = v.findViewById(R.id.tv_banner_desc);
        }
    }
}
