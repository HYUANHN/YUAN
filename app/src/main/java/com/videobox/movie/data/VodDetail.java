package com.videobox.movie.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 影视详情（含线路与剧集）
 */
public class VodDetail extends VodItem {
    public List<PlayGroup> groups = new ArrayList<>();

    /** 单条剧集 */
    public static class PlayEpisode implements Serializable {
        public String name;
        public String url;
        public PlayEpisode(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    /** 一个播放线路（分组） */
    public static class PlayGroup implements Serializable {
        public String name;             // 线路名，如"超清2"
        public List<PlayEpisode> episodes = new ArrayList<>();
    }
}
