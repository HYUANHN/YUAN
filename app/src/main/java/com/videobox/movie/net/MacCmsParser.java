package com.videobox.movie.net;

import android.text.TextUtils;

import com.videobox.movie.data.VodDetail;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放串解析，兼容两类主流格式：
 * <p>
 * 1) mac-cms(苹果CMS) 格式：
 *    vod_play_from = "线路A$$$线路B"（线路之间用 $$$ 分隔）
 *    vod_play_url  = "剧名1$$$url1#剧名2$$$url2$$$剧名1$$$url1#..."
 *      - 剧集之间用 # 分隔；剧名与地址用 $$$ 分隔；线路之间用 $$$ 分隔
 * <p>
 * 2) 聚合源(采集/短剧类) 格式：
 *    vod_play_url  = "剧名1$url1#剧名2$url2"
 *      - 剧集之间用 # 分隔；剧名与地址用单个 $ 分隔（单线路）
 */
public class MacCmsParser {

    public static List<VodDetail.PlayGroup> parsePlay(String playFrom, String playUrl) {
        List<VodDetail.PlayGroup> groups = new ArrayList<>();
        if (TextUtils.isEmpty(playUrl)) return groups;

        if (playUrl.contains("$$$")) {
            parseMacCms(playFrom, playUrl, groups);
        } else if (playUrl.contains("#") || playUrl.contains("$")) {
            parseSingleDollar(playFrom, playUrl, groups);
        } else {
            // 单个地址
            VodDetail.PlayGroup g = new VodDetail.PlayGroup();
            g.name = firstGroupName(playFrom, 0);
            g.episodes.add(new VodDetail.PlayEpisode("", playUrl.trim()));
            groups.add(g);
        }
        return groups;
    }

    /** mac-cms 格式：$$$ 分隔 */
    private static void parseMacCms(String playFrom, String playUrl, List<VodDetail.PlayGroup> groups) {
        String[] groupNames = TextUtils.isEmpty(playFrom) ? new String[0] : playFrom.split("\\$\\$\\$");
        List<VodDetail.PlayEpisode> cur = new ArrayList<>();
        String pendingName = null;
        String[] tokens = playUrl.split("\\$\\$\\$");
        for (String token : tokens) {
            if (token == null) continue;
            token = token.trim();
            if (token.isEmpty()) continue;
            String[] subs = token.split("#");
            boolean startsWithName = subs.length > 0 && !subs[0].trim().isEmpty() && !isUrl(subs[0].trim());
            if (startsWithName && !cur.isEmpty()) {
                flushGroup(groups, groupNames, cur);
                cur = new ArrayList<>();
            }
            for (String s : subs) {
                if (s == null) continue;
                s = s.trim();
                if (s.isEmpty()) continue;
                if (isUrl(s)) {
                    if (pendingName != null) {
                        cur.add(new VodDetail.PlayEpisode(pendingName, s));
                        pendingName = null;
                    } else {
                        cur.add(new VodDetail.PlayEpisode("", s));
                    }
                } else {
                    pendingName = s;
                }
            }
        }
        if (!cur.isEmpty()) flushGroup(groups, groupNames, cur);
    }

    /** 聚合源格式：单个 $ 分隔剧名与地址，# 分隔剧集（单线路） */
    private static void parseSingleDollar(String playFrom, String playUrl, List<VodDetail.PlayGroup> groups) {
        VodDetail.PlayGroup g = new VodDetail.PlayGroup();
        g.name = firstGroupName(playFrom, 0);
        String[] episodes = playUrl.split("#");
        for (String ep : episodes) {
            if (ep == null) continue;
            ep = ep.trim();
            if (ep.isEmpty()) continue;
            int idx = ep.lastIndexOf('$');
            if (idx >= 0) {
                String name = ep.substring(0, idx).trim();
                String url = ep.substring(idx + 1).trim();
                g.episodes.add(new VodDetail.PlayEpisode(name, url));
            } else {
                g.episodes.add(new VodDetail.PlayEpisode("", ep));
            }
        }
        groups.add(g);
    }

    private static String firstGroupName(String playFrom, int index) {
        if (TextUtils.isEmpty(playFrom)) return "线路1";
        String[] names = playFrom.split("\\$\\$\\$");
        return index < names.length ? names[index] : ("线路" + (index + 1));
    }

    private static void flushGroup(List<VodDetail.PlayGroup> groups, String[] names,
                                   List<VodDetail.PlayEpisode> cur) {
        if (cur.isEmpty()) return;
        VodDetail.PlayGroup g = new VodDetail.PlayGroup();
        g.name = names.length > groups.size() && names[groups.size()] != null
                ? names[groups.size()]
                : ("线路" + (groups.size() + 1));
        g.episodes.addAll(cur);
        groups.add(g);
    }

    public static boolean isUrl(String s) {
        if (TextUtils.isEmpty(s)) return false;
        String lower = s.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("rtmp://") || lower.startsWith("rtsp://")
                || s.contains("://")
                || lower.endsWith(".m3u8") || lower.endsWith(".mp4")
                || lower.endsWith(".flv") || lower.endsWith(".ts");
    }
}
