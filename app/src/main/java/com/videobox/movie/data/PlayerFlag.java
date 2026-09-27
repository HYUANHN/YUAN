package com.videobox.movie.data;

/** 播放器标识（MacCMS 线路 flag，如 sdm3u8 / feifan / gsm3u8）配置 */
public class PlayerFlag {
    /** 播放器编码，如 sdm3u8 */
    public String code;
    /** 显示名称 */
    public String name;
    /** 是否启用该线路（禁用后解析时直接过滤掉） */
    public boolean enabled;
    /** 是否开启 share 网页解析（提取真实 m3u8） */
    public boolean webParse;

    public PlayerFlag() {
        this.code = "";
        this.name = "";
        this.enabled = true;
        this.webParse = true;
    }

    public PlayerFlag(String code, String name, boolean enabled, boolean webParse) {
        this.code = code == null ? "" : code;
        this.name = name == null ? this.code : name;
        this.enabled = enabled;
        this.webParse = webParse;
    }

    public static PlayerFlag of(String code, String name) {
        return new PlayerFlag(code, name, true, true);
    }
}
