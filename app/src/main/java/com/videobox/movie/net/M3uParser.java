package com.videobox.movie.net;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** IPTV m3u 播放列表解析器 */
public class M3uParser {

    public static class Channel {
        public String name;
        public String url;
        public String group;
        public Channel() {}
        public Channel(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private static final Pattern P_TVG = Pattern.compile("tvg-name=\"([^\"]*)\"");
    private static final Pattern P_GRP = Pattern.compile("group-title=\"([^\"]*)\"");

    public static List<Channel> parse(String content) {
        List<Channel> out = new ArrayList<>();
        if (content == null) return out;
        String[] lines = content.split("\\r?\\n");
        Channel cur = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF")) {
                cur = new Channel();
                cur.name = extractName(line);
                cur.group = extractGroup(line);
            } else if (line.startsWith("#")) {
                // #EXTM3U / #EXTGRP / 其它指令跳过
            } else if (cur != null) {
                if (line.startsWith("http://") || line.startsWith("https://")
                        || line.startsWith("rtmp://") || line.startsWith("rtsp://")) {
                    cur.url = line;
                    if (cur.name == null || cur.name.isEmpty()) cur.name = "频道";
                    out.add(cur);
                }
                cur = null;
            }
        }
        return out;
    }

    private static String extractName(String extinf) {
        Matcher m = P_TVG.matcher(extinf);
        if (m.find() && !m.group(1).trim().isEmpty()) return m.group(1).trim();
        int comma = extinf.lastIndexOf(',');
        if (comma >= 0) {
            String n = extinf.substring(comma + 1).trim();
            if (!n.isEmpty()) return n;
        }
        return "频道";
    }

    private static String extractGroup(String extinf) {
        Matcher m = P_GRP.matcher(extinf);
        return m.find() ? m.group(1).trim() : "";
    }
}
