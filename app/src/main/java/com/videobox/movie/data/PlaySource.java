package com.videobox.movie.data;

import android.text.TextUtils;

/**
 * 播放源节点配置（mac-cms 采集站节点）
 */
public class PlaySource {
    public String name;      // 播放源名称，如"量子资源"
    public String url;       // 站点地址
    public String api;       // 接口地址，如 https://host/api.php/provide/vod/
    public String description; // 资源说明，如"资源较全"

    public String getApiBase() {
        if (TextUtils.isEmpty(api)) return url;
        return api;
    }
}
