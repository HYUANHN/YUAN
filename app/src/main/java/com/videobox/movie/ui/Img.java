package com.videobox.movie.ui;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.videobox.movie.R;

/**
 * 图片加载工具：地址为空时直接用占位图，避免空 URL 触发异常。
 */
public class Img {
    public static void load(Context c, String url, ImageView iv) {
        if (url == null || url.trim().isEmpty()) {
            iv.setImageResource(R.drawable.bg_placeholder);
            return;
        }
        Glide.with(c)
                .load(url)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
                .placeholder(R.drawable.bg_placeholder)
                .error(R.drawable.bg_placeholder)
                .into(iv);
    }
}
