package com.videobox.movie.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.videobox.movie.net.M3uParser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地偏好存储：播放源、收藏、播放历史、广告过滤配置
 */
public class Prefs {
    private static final String PREF_NAME = "videobox_prefs";
    private static final String KEY_PLAY_SOURCE = "play_source_json";
    private static final String KEY_FAVORITES = "favorites_json";
    private static final String KEY_HISTORY = "history_json";
    private static final String KEY_ADBLOCK = "adblock_enabled";
    private static final String KEY_ADLIST = "ad_blacklist";

    private static Prefs instance;
    private final SharedPreferences sp;
    private final Gson gson = new Gson();

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    // ---- 当前播放源 ----
    public PlaySource getPlaySource() {
        String json = sp.getString(KEY_PLAY_SOURCE, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, PlaySource.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void setPlaySource(PlaySource ps) {
        sp.edit().putString(KEY_PLAY_SOURCE, gson.toJson(ps)).apply();
        recordSource(ps);
    }

    // ---- 已保存的源列表（用于"源地址列表"与导入输入框）----
    private static final String KEY_ALL_SOURCES = "all_sources_json";

    public List<PlaySource> getAllSources() {
        String json = sp.getString(KEY_ALL_SOURCES, null);
        if (json == null) return new ArrayList<>();
        try {
            List<PlaySource> list = gson.fromJson(json, new TypeToken<List<PlaySource>>() {}.getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveAllSources(List<PlaySource> list) {
        sp.edit().putString(KEY_ALL_SOURCES, gson.toJson(list)).apply();
    }

    /** 记录一个源到已保存列表（去重，新的放最前） */
    public void recordSource(PlaySource ps) {
        if (ps == null) return;
        List<PlaySource> list = getAllSources();
        List<PlaySource> out = new ArrayList<>();
        String key = ps.api != null ? ps.api : ps.url;
        for (PlaySource it : list) {
            String itk = it.api != null ? it.api : it.url;
            if (itk != null && itk.equals(key)) continue;
            out.add(it);
        }
        out.add(0, ps);
        saveAllSources(out);
    }

    /** 删除一个已保存的源（按 api/url 匹配） */
    public void removeSource(PlaySource ps) {
        if (ps == null) return;
        String key = ps.api != null ? ps.api : ps.url;
        if (key == null) return;
        List<PlaySource> list = getAllSources();
        List<PlaySource> out = new ArrayList<>();
        for (PlaySource it : list) {
            String itk = it.api != null ? it.api : it.url;
            if (itk != null && itk.equals(key)) continue;
            out.add(it);
        }
        saveAllSources(out);
    }

    // ---- 收藏 ----
    public List<VodItem> getFavorites() {
        String json = sp.getString(KEY_FAVORITES, null);
        if (json == null) return new ArrayList<>();
        try {
            return gson.fromJson(json, new TypeToken<List<VodItem>>() {}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveFavorites(List<VodItem> list) {
        sp.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply();
    }

    // ---- 播放历史 ----
    public List<VodItem> getHistory() {
        String json = sp.getString(KEY_HISTORY, null);
        if (json == null) return new ArrayList<>();
        try {
            return gson.fromJson(json, new TypeToken<List<VodItem>>() {}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveHistory(List<VodItem> list) {
        sp.edit().putString(KEY_HISTORY, gson.toJson(list)).apply();
    }

    public void clearHistory() {
        sp.edit().remove(KEY_HISTORY).apply();
    }

    public void recordHistory(VodItem item) {
        List<VodItem> list = getHistory();
        List<VodItem> filtered = new ArrayList<>();
        for (VodItem it : list) {
            if (it.vod_id == null || it.vod_id.equals(item.vod_id)) continue;
            filtered.add(it);
        }
        filtered.add(0, item);
        if (filtered.size() > 100) {
            filtered = filtered.subList(0, 100);
        }
        saveHistory(filtered);
    }

    // ---- 广告过滤 ----
    public boolean isAdBlockEnabled() {
        return sp.getBoolean(KEY_ADBLOCK, true);
    }

    public void setAdBlockEnabled(boolean on) {
        sp.edit().putBoolean(KEY_ADBLOCK, on).apply();
    }

    public List<String> getAdKeywords() {
        List<String> def = new ArrayList<>();
        def.add("googletag");
        def.add("adservice");
        def.add("adserv");
        def.add("adcdn");
        def.add("adtrack");
        def.add("googlesyndication");
        def.add("doubleclick");
        def.add("pos.baidu.com");
        def.add("pos.baidu");
        def.add("ads");
        def.add("/advert");
        def.add("advert");
        def.add("pagead");
        def.add("ad_");
        def.add("umeng");
        def.add("tongji");
        def.add("bet");
        def.add("casino");
        def.add("gambling");
        def.add("pgsoft");
        def.add("bodog");
        def.add("bet365");
        def.add("duchang");
        def.add("huangjin");
        def.add("yule");
        def.add("娱乐城");
        def.add("赌博");
        def.add("新葡京");
        String json = sp.getString(KEY_ADLIST, null);
        if (json == null) return def;
        try {
            List<String> list = gson.fromJson(json, new TypeToken<List<String>>() {}.getType());
            if (list == null || list.isEmpty()) return def;
            return list;
        } catch (Exception e) {
            return def;
        }
    }

    public void saveAdKeywords(List<String> list) {
        sp.edit().putString(KEY_ADLIST, gson.toJson(list)).apply();
    }

    // ---- 分类屏蔽 ----
    private static final String KEY_BLOCKED_CATS = "blocked_cats";

    /** 被屏蔽的分类名（主分类与子分类统一按 type_name 屏蔽） */
    public List<String> getBlockedCats() {
        String json = sp.getString(KEY_BLOCKED_CATS, null);
        if (json == null) return new ArrayList<>();
        try {
            return gson.fromJson(json, new TypeToken<List<String>>() {}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setBlockedCats(List<String> list) {
        sp.edit().putString(KEY_BLOCKED_CATS, gson.toJson(list == null ? new ArrayList<>() : list)).apply();
    }

    public boolean isCatBlocked(String name) {
        if (name == null) return false;
        return getBlockedCats().contains(name);
    }

    // ---- 播放器设置 ----
    private static final String KEY_SPEED = "playback_speed";
    private static final String KEY_LONGPRESS_SPEED = "longpress_speed";
    private static final String KEY_SKIP_INTRO = "skip_intro_sec";
    private static final String KEY_SKIP_OUTRO = "skip_outro_sec";
    private static final String KEY_VOLUME = "player_volume";
    private static final String KEY_BRIGHTNESS = "player_brightness";
    private static final String KEY_HW = "hardware_decode";

    public float getPlaybackSpeed() { return sp.getFloat(KEY_SPEED, 1.0f); }
    public void setPlaybackSpeed(float s) { sp.edit().putFloat(KEY_SPEED, s).apply(); }

    public float getLongPressSpeed() { return sp.getFloat(KEY_LONGPRESS_SPEED, 1.5f); }
    public void setLongPressSpeed(float s) { sp.edit().putFloat(KEY_LONGPRESS_SPEED, s).apply(); }

    public int getSkipIntroSec() { return sp.getInt(KEY_SKIP_INTRO, 0); }
    public void setSkipIntroSec(int s) { sp.edit().putInt(KEY_SKIP_INTRO, s).apply(); }

    public int getSkipOutroSec() { return sp.getInt(KEY_SKIP_OUTRO, 0); }
    public void setSkipOutroSec(int s) { sp.edit().putInt(KEY_SKIP_OUTRO, s).apply(); }

    public float getVolume() { return sp.getFloat(KEY_VOLUME, 1.0f); }
    public void setVolume(float v) { sp.edit().putFloat(KEY_VOLUME, v).apply(); }

    public int getBrightness() { return sp.getInt(KEY_BRIGHTNESS, 60); }
    public void setBrightness(int b) { sp.edit().putInt(KEY_BRIGHTNESS, b).apply(); }

    public boolean isHardwareDecode() { return sp.getBoolean(KEY_HW, true); }
    public void setHardwareDecode(boolean on) { sp.edit().putBoolean(KEY_HW, on).apply(); }

    // ---- 免责声明（首次启动） ----
    private static final String KEY_DISCLAIMER_AGREED = "disclaimer_agreed";

    public boolean isDisclaimerAgreed() { return sp.getBoolean(KEY_DISCLAIMER_AGREED, false); }
    public void setDisclaimerAgreed(boolean on) { sp.edit().putBoolean(KEY_DISCLAIMER_AGREED, on).apply(); }

    // ---- 播放器标识（MacCMS 线路 flag）管理 ----
    private static final String KEY_PLAYER_FLAGS = "player_flags";
    private static final String KEY_FLAGS_PRESET = "player_flags_preset_v4";

    private List<PlayerFlag> defaultFlags() {
        List<PlayerFlag> list = new ArrayList<>();
        // 常用（启用 + 网页解析）—— 其中 lzm3u8 是量子源使用的真实线路 flag，必须启用
        list.add(PlayerFlag.of("sdm3u8", "闪电 sdm3u8"));
        list.add(PlayerFlag.of("feifan", "非凡 feifan"));
        list.add(PlayerFlag.of("gsm3u8", "光速 gsm3u8"));
        list.add(PlayerFlag.of("ffm3u8", "非凡资源M3U8"));
        list.add(PlayerFlag.of("modum3u8", "魔都云"));
        list.add(PlayerFlag.of("videojs", "videojs-H5播放器"));
        list.add(PlayerFlag.of("lzm3u8", "量子 lzm3u8"));
        // 默认禁用（非真实线路 flag 的播放器类型，或用户确认不需要）
        list.add(new PlayerFlag("dplayer", "DPlayer-H5播放器", false, true));
        list.add(new PlayerFlag("liangzi", "量子播放器", false, true));
        return list;
    }

    /** 读取播放器标识配置；未配置过时返回预置默认，老版本配置会自动补齐新预置项（一次性） */
    public List<PlayerFlag> getPlayerFlags() {
        String json = sp.getString(KEY_PLAYER_FLAGS, null);
        List<PlayerFlag> list = null;
        if (json != null) {
            try {
                list = gson.fromJson(json, new TypeToken<List<PlayerFlag>>() {}.getType());
            } catch (Exception ignored) { }
        }
        boolean presetApplied = sp.getBoolean(KEY_FLAGS_PRESET, false);
        if (list == null || list.isEmpty()) {
            list = defaultFlags();
            sp.edit().putBoolean(KEY_FLAGS_PRESET, true).apply();
            return list;
        }
        // 一次性补齐新预置项 + 修正"真实线路 flag 必须启用"（修复 lzm3u8 被误禁用导致量子源崩溃）
        if (!presetApplied) {
            boolean changed = false;
            String[] mustEnable = {"sdm3u8", "feifan", "gsm3u8", "ffm3u8",
                    "modum3u8", "videojs", "lzm3u8"};
            for (PlayerFlag f : list) {
                if (f.code == null) continue;
                for (String m : mustEnable) {
                    if (f.code.equalsIgnoreCase(m) && !f.enabled) { f.enabled = true; changed = true; break; }
                }
            }
            for (PlayerFlag def : defaultFlags()) {
                boolean has = false;
                for (PlayerFlag f : list) {
                    if (f.code != null && f.code.equalsIgnoreCase(def.code)) { has = true; break; }
                }
                if (!has) { list.add(def); changed = true; }
            }
            if (changed) sp.edit().putString(KEY_PLAYER_FLAGS, gson.toJson(list)).apply();
            sp.edit().putBoolean(KEY_FLAGS_PRESET, true).apply();
        }
        return list;
    }

    public void setPlayerFlags(List<PlayerFlag> list) {
        sp.edit().putString(KEY_PLAYER_FLAGS,
                gson.toJson(list == null ? defaultFlags() : list)).apply();
    }

    /** 该 flag 是否启用（未在配置列表里则默认启用，不误伤用户自建线路名） */
    public boolean isPlayerFlagEnabled(String code) {
        if (code == null || code.trim().isEmpty()) return true;
        for (PlayerFlag f : getPlayerFlags()) {
            if (f.code != null && f.code.equalsIgnoreCase(code.trim())) return f.enabled;
        }
        return true;
    }

    /** 该 flag 是否开启 share 网页解析 */
    public boolean isPlayerFlagWebParse(String code) {
        if (code == null) return true;
        for (PlayerFlag f : getPlayerFlags()) {
            if (f.code != null && f.code.equalsIgnoreCase(code.trim())) return f.webParse;
        }
        return true;
    }

    // ---- 电视台（IPTV 直播）----
    private static final String KEY_LIVE_URL = "live_m3u_url";
    private static final String KEY_LIVE_NAME = "live_name";
    private static final String KEY_LIVE_CHANNELS = "live_channels_json";

    public String getLiveUrl() { return sp.getString(KEY_LIVE_URL, null); }
    public void setLiveUrl(String u) { sp.edit().putString(KEY_LIVE_URL, u).apply(); }

    public String getLiveName() { return sp.getString(KEY_LIVE_NAME, null); }
    public void setLiveName(String n) { sp.edit().putString(KEY_LIVE_NAME, n).apply(); }

    public List<M3uParser.Channel> getLiveChannels() {
        String json = sp.getString(KEY_LIVE_CHANNELS, null);
        if (json == null) return new ArrayList<>();
        try {
            List<M3uParser.Channel> l = gson.fromJson(json,
                    new TypeToken<List<M3uParser.Channel>>() {}.getType());
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setLiveChannels(List<M3uParser.Channel> list) {
        sp.edit().putString(KEY_LIVE_CHANNELS,
                gson.toJson(list == null ? new ArrayList<>() : list)).apply();
    }

    public void clearLive() {
        setLiveUrl(null);
        setLiveName(null);
        setLiveChannels(new ArrayList<>());
    }

    // ---- 电视台分组屏蔽（默认忽略"电信"）----
    private static final String KEY_BLOCKED_LIVE_GROUPS = "blocked_live_groups_json";

    public List<String> getBlockedLiveGroups() {
        String json = sp.getString(KEY_BLOCKED_LIVE_GROUPS, null);
        if (json == null) return new ArrayList<>();
        try {
            List<String> l = gson.fromJson(json, new TypeToken<List<String>>() {}.getType());
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setBlockedLiveGroups(List<String> list) {
        sp.edit().putString(KEY_BLOCKED_LIVE_GROUPS,
                gson.toJson(list == null ? new ArrayList<>() : list)).apply();
    }

    /** 该分组是否应被屏蔽：电信始终屏蔽，其余看用户配置 */
    public boolean isLiveGroupBlocked(String group) {
        if (group != null && group.contains("电信")) return true;
        if (group == null || group.isEmpty()) return false;
        for (String b : getBlockedLiveGroups()) {
            if (b.equalsIgnoreCase(group)) return true;
        }
        return false;
    }

    // ---- 解码模式：0 智能 1 IJK硬解 2 原生解码 ----
    private static final String KEY_DECODE_MODE = "decode_mode";
    public int getDecodeMode() { return sp.getInt(KEY_DECODE_MODE, 0); }
    public void setDecodeMode(int m) { sp.edit().putInt(KEY_DECODE_MODE, m).apply(); }

    // ---- 直播屏幕比例：0 原始 1 4:3 2 16:9 3 全屏 ----
    private static final String KEY_LIVE_SCREEN = "live_screen_mode";
    public int getLiveScreenMode() { return sp.getInt(KEY_LIVE_SCREEN, 0); }
    public void setLiveScreenMode(int m) { sp.edit().putInt(KEY_LIVE_SCREEN, m).apply(); }
}
