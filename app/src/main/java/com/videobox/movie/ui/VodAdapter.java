package com.videobox.movie.ui;

import android.annotation.SuppressLint;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 影视卡片网格适配器（首页/分类/收藏/历史共用）
 */
public class VodAdapter extends RecyclerView.Adapter<VodAdapter.VH> {

    public interface OnItemClick {
        void onClick(VodItem item);
    }

    private final List<VodItem> data = new ArrayList<>();
    private OnItemClick listener;
    private boolean showLoading = false;
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_LOADING = 1;

    public void setData(List<VodItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    public void addData(List<VodItem> list) {
        if (list != null) {
            int start = data.size();
            data.addAll(list);
            notifyItemRangeInserted(start, list.size());
        }
    }

    /** 是否显示底部"加载中"提示（滑到底自动加载下一页时） */
    public void setLoading(boolean v) {
        if (showLoading == v) return;
        showLoading = v;
        notifyDataSetChanged();
    }

    public boolean isFooter(int position) {
        return showLoading && position == data.size();
    }

    public void setOnItemClick(OnItemClick l) { this.listener = l; }

    public List<VodItem> getData() { return data; }

    /** 海报补取完成后，只刷新该卡片（避免整表闪烁） */
    public void updateItem(VodItem item) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i) == item || (item.vod_id != null && item.vod_id.equals(data.get(i).vod_id))) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return isFooter(position) ? TYPE_LOADING : TYPE_NORMAL;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v;
        if (viewType == TYPE_LOADING) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_loading, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vod, parent, false);
        }
        return new VH(v);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (isFooter(position)) {
            h.itemView.setOnClickListener(null);
            return;
        }
        VodItem item = data.get(position);
        h.title.setText(item.getTitle());
        h.subtitle.setText(item.getSubtitle());
        if (item.vod_remarks != null && !item.vod_remarks.isEmpty()) {
            h.remark.setVisibility(View.VISIBLE);
            h.remark.setText(item.vod_remarks);
        } else {
            h.remark.setVisibility(View.GONE);
        }
        Img.load(h.itemView.getContext(), item.vod_pic, h.poster);
        // 无海报时用片名文字占位
        boolean hasPic = item.vod_pic != null && !item.vod_pic.trim().isEmpty();
        h.posterText.setVisibility(hasPic ? View.GONE : View.VISIBLE);
        h.posterText.setText(item.getTitle());
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() { return data.size() + (showLoading ? 1 : 0); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView posterText, title, subtitle, remark;
        VH(@NonNull View v) {
            super(v);
            poster = v.findViewById(R.id.iv_poster);
            posterText = v.findViewById(R.id.tv_poster_text);
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
            remark = v.findViewById(R.id.tv_remark);
        }
    }
}
