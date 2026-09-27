package com.videobox.movie.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.data.VodDetail;
import com.videobox.movie.data.VodItem;
import com.videobox.movie.net.ApiClient;
import com.videobox.movie.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用跳转辅助
 */
public class Ui {
    private static final ExecutorService playExec = Executors.newSingleThreadExecutor();
    private static final Handler playMain = new Handler(Looper.getMainLooper());

    /** 打开详情页 */
    public static void openDetail(Context c, VodItem item) {
        Intent i = new Intent(c, DetailActivity.class);
        i.putExtra("vod_id", item.vod_id);
        i.putExtra("title", item.getTitle());
        c.startActivity(i);
    }

    /** 直接播放：只传播放地址与标题，避免大 Bundle 序列化崩溃 */
    public static void play(Context c, String title, String url) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra("title", title);
        i.putExtra("url", url);
        c.startActivity(i);
    }

    /** 带剧集列表播放（支持选集/上一集下一集）：传当前集索引，其余集地址一并传入 */
    public static void playWithEpisodes(Context c, String title, String url,
                                        List<String> episodeUrls, int index) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra("title", title);
        i.putExtra("url", url);
        if (episodeUrls != null && !episodeUrls.isEmpty()) {
            i.putStringArrayListExtra("episodes", new ArrayList<>(episodeUrls));
            i.putExtra("episode_index", index);
        }
        c.startActivity(i);
    }

    /** 带全线路播放：传所有线路名 + 各线路剧集地址，播放器内可切换线路 */
    public static void playWithLines(Context c, String title, String url,
                                     List<String> lineNames, List<List<String>> lines,
                                     int lineIndex, int episodeIndex) {
        playWithLines(c, title, url, lineNames, lines, lineIndex, episodeIndex, 0);
    }

    public static void playWithLines(Context c, String title, String url,
                                     List<String> lineNames, List<List<String>> lines,
                                     int lineIndex, int episodeIndex, long startPosMs) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra("title", title);
        i.putExtra("url", url);
        ArrayList<String> names = new ArrayList<>();
        ArrayList<ArrayList<String>> urls = new ArrayList<>();
        for (String n : lineNames) names.add(n == null ? "" : n);
        for (List<String> l : lines) urls.add(new ArrayList<>(l));
        i.putStringArrayListExtra("line_names", names);
        i.putExtra("lines", urls);
        i.putExtra("line_index", lineIndex);
        i.putExtra("episode_index", episodeIndex);
        i.putExtra("start_pos", startPosMs);
        if (lineIndex >= 0 && lineIndex < urls.size()) {
            i.putStringArrayListExtra("episodes", urls.get(lineIndex));
        }
        c.startActivity(i);
    }

    /** 点击片源直接播放：后台拉详情，取第一个有地址的线路第一集播放 */
    public static void playDirect(Context c, final VodItem item) {
        final Context app = c.getApplicationContext();
        PlaySource source = Prefs.get(app).getPlaySource();
        if (source == null) {
            Toast.makeText(app, "请先在设置里配置播放源", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(app, "正在加载…", Toast.LENGTH_SHORT).show();
        playExec.execute(() -> {
            try {
                VodDetail d = ApiClient.fetchDetail(source.getApiBase(), item.vod_id);
                if (d == null) {
                    playMain.post(() -> Toast.makeText(app, "获取播放地址失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                String url = null, epName = null;
                for (VodDetail.PlayGroup g : d.groups) {
                    if (g.episodes != null) for (VodDetail.PlayEpisode e : g.episodes) {
                        if (e.url != null && !e.url.trim().isEmpty()) {
                            url = e.url; epName = e.name; break;
                        }
                    }
                    if (url != null) break;
                }
                if (url == null) {
                    playMain.post(() -> Toast.makeText(app,
                            "未获取到可播放地址，请从搜索进详情页选线路", Toast.LENGTH_SHORT).show());
                    return;
                }
                // 记录历史
                VodItem hist = new VodItem();
                hist.vod_id = d.vod_id != null ? d.vod_id : item.vod_id;
                hist.vod_name = d.vod_name != null ? d.vod_name : item.getTitle();
                hist.vod_pic = d.vod_pic;
                hist.vod_remarks = epName;
                hist.vod_year = d.vod_year;
                hist.vod_type_name = d.vod_type_name;
                Prefs.get(app).recordHistory(hist);
                final String title = d.getTitle() != null ? d.getTitle() : item.getTitle();
                final String fUrl = url;
                playMain.post(() -> Ui.play(app, title, fUrl));
            } catch (Exception e) {
                playMain.post(() -> Toast.makeText(app, "播放失败：" + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        });
    }
}
