package com.videobox.movie.net;

import android.text.TextUtils;

import java.util.List;

/**
 * 广告过滤：对 HLS(m3u8) 播放列表进行内容净化。
 * 原理：拉取 m3u8，剔除 URI 命中广告关键字黑名单的分片(ts)行，
 * 并去掉对应的 EXTINF 标记，再重组播放列表交给播放器。
 * 仅对"以额外分片形式注入"的广告有效（这是绝大多数采集站内嵌广告的形式）。
 */
public class AdFilter {

    /**
     * 过滤 m3u8 内容。
     * @param content 原始 m3u8 文本
     * @param keywords 广告关键字黑名单
     * @return 过滤后的 m3u8 文本
     */
    public static String filterM3u8(String content, List<String> keywords) {
        if (TextUtils.isEmpty(content)) return content;
        String[] lines = content.split("\n");
        java.util.List<String> out = new java.util.ArrayList<>(lines.length);
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            // 判断是否是分片 URI（http 开头的 ts/视频地址）
            boolean isSegment = MacCmsParser.isUrl(line) && !line.startsWith("#");
            if (isSegment && matches(line, keywords)) {
                // 广告分片：若上一行是 EXTINF 也一并移除
                if (!out.isEmpty() && out.get(out.size() - 1).startsWith("#EXTINF")) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(line);
        }
        return String.join("\n", out) + "\n";
    }

    private static boolean matches(String url, List<String> keywords) {
        if (keywords == null) return false;
        String lower = url.toLowerCase();
        for (String kw : keywords) {
            if (TextUtils.isEmpty(kw)) continue;
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
