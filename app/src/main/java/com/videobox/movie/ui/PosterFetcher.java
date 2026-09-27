package com.videobox.movie.ui;

import android.os.Handler;
import android.os.Looper;

import com.videobox.movie.data.VodDetail;
import com.videobox.movie.data.VodItem;
import com.videobox.movie.net.ApiClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 海报补取器：列表源未返回海报字段（如 mac-cms 的暴风/量子/非凡）时，
 * 后台从详情接口补取 vod_pic，补到后回调主线程更新对应卡片。
 */
public class PosterFetcher {

    public interface OnItemUpdated { void onItemUpdated(VodItem item); }

    /** 片ID → 海报地址 的内存缓存：切分类时避免反复拉详情补海报 */
    private static final java.util.Map<String, String> POSTER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void fetch(List<VodItem> items, String apiBase, OnItemUpdated cb) {
        if (items == null || items.isEmpty() || apiBase == null) return;
        final Handler main = new Handler(Looper.getMainLooper());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        for (VodItem item : items) {
            if (item.vod_pic != null && !item.vod_pic.trim().isEmpty()) continue;
            if (item.vod_id == null) continue;
            // 缓存 key 带上源地址，避免不同源相同 id 串海报
            String cacheKey = apiBase + "#" + item.vod_id;
            String cached = POSTER_CACHE.get(cacheKey);
            if (cached != null && !cached.trim().isEmpty()) {
                item.vod_pic = cached;
                continue;
            }
            final VodItem it = item;
            final String key = cacheKey;
            pool.execute(() -> {
                try {
                    VodDetail d = ApiClient.fetchDetail(apiBase, it.vod_id);
                    if (d != null && d.vod_pic != null && !d.vod_pic.trim().isEmpty()) {
                        it.vod_pic = d.vod_pic;
                        POSTER_CACHE.put(key, d.vod_pic);
                        main.post(() -> { if (cb != null) cb.onItemUpdated(it); });
                    }
                } catch (Exception ignored) { }
            });
        }
        pool.shutdown();
    }
}
