package com.videobox.movie.data;

import java.io.Serializable;

/**
 * 影视条目（列表卡片用）
 */
public class VodItem implements Serializable {
    public String vod_id;
    public String vod_name;
    public String vod_pic;
    public String vod_remarks;   // 如"更至第42集"
    public String vod_year;
    public String vod_area;
    public String vod_type_name;
    public String vod_score;     // 豆瓣评分
    public String vod_play_url;  // 播放地址串
    public String vod_play_from; // 线路
    public String vod_content;

    // 便捷字段
    public String getTitle() { return vod_name == null ? "" : vod_name; }
    public String getRemark() { return vod_remarks == null ? "" : vod_remarks; }

    /** 列表副标题：年份/类型/地区 */
    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        if (vod_year != null && !vod_year.isEmpty()) sb.append(vod_year).append(" ");
        if (vod_type_name != null && !vod_type_name.isEmpty()) sb.append(vod_type_name).append(" ");
        if (vod_area != null && !vod_area.isEmpty()) sb.append(vod_area);
        return sb.toString().trim();
    }
}
