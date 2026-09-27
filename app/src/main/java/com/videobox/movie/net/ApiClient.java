package com.videobox.movie.net;

import android.text.TextUtils;
import android.util.Xml;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.videobox.movie.data.Category;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.VodDetail;
import com.videobox.movie.data.VodItem;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 网络请求客户端（OkHttp）。
 * 兼容两类播放源：
 *  - mac-cms(苹果CMS)：接口地址含 provide/vod，用 ac=list / ac=detail
 *  - 聚合源(采集/短剧类)：接口地址不含 provide/vod，用 ac=videolist / ac=detail
 */
public class ApiClient {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    private static final Gson gson = new Gson();

    public static String get(String url) throws Exception {
        Request req = new Request.Builder()
                .url(normalizeUrl(url))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build();
        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
            return res.body() != null ? res.body().string() : "";
        }
    }

    /** 修正常见 URL 拼写错误：去掉协议前的多余字符（如 hhttps:// → https://）、修正 https// / http// 等 */
    public static String normalizeUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        String lower = u.toLowerCase();
        int i1 = lower.indexOf("http://");
        int i2 = lower.indexOf("https://");
        int idx;
        if (i1 >= 0 && (i2 < 0 || i1 < i2)) idx = i1;
        else if (i2 >= 0) idx = i2;
        else idx = -1;
        if (idx > 0) u = u.substring(idx);
        lower = u.toLowerCase();
        if (lower.startsWith("https//")) u = "https://" + u.substring("https//".length());
        else if (lower.startsWith("http//")) u = "http://" + u.substring("http//".length());
        return u;
    }

    // ---------------- 地址构建 ----------------

    private static boolean isMacCms(String apiBase) {
        return apiBase != null && apiBase.contains("provide/vod");
    }

    /** 统一要求 JSON 输出：mac-cms 的 at/xml 路径/参数 → at/json（App 只按 JSON 解析） */
    private static String normalizeBase(String apiBase) {
        if (apiBase == null) return "";
        String b = normalizeUrl(apiBase);
        b = b.replaceAll("(?i)at/xml/?", "at/json/");
        b = b.replaceAll("(?i)at=xml(&|$)", "at=json$1");
        return b;
    }

    /** mac-cms 接口：在 provide/vod 路径上直接拼查询参数 */
    private static String macCmsUrl(String apiBase, String query) {
        String b = normalizeBase(apiBase);
        if (b.isEmpty()) return "";
        if (!b.contains("?") && !b.endsWith("/")) b = b + "/";
        return b + (b.contains("?") ? "&" : "?") + query;
    }

    /** 聚合源：站点根路径（保证一个尾斜杠） */
    private static String aggBase(String apiBase) {
        String b = normalizeBase(apiBase);
        if (b.isEmpty()) return "";
        if (!b.endsWith("/")) b = b + "/";
        return b;
    }

    /** 列表接口地址；typeId>0 时按分类过滤（mac-cms 用 t=，聚合源用 type=） */
    public static String buildListUrl(String apiBase, int typeId, int page) {
        if (isMacCms(apiBase)) {
            String q = "ac=list&pg=" + page;
            if (typeId > 0) q += "&t=" + typeId;
            return macCmsUrl(apiBase, q);
        } else {
            String q = "ac=videolist&pg=" + page;
            if (typeId > 0) q += "&type=" + typeId;
            return aggBase(apiBase) + "?" + q;
        }
    }

    /** 详情接口地址（聚合源剥离 id 前缀） */
    public static String buildDetailUrl(String apiBase, String vodId) {
        if (isMacCms(apiBase)) {
            return macCmsUrl(apiBase, "ac=detail&ids=" + vodId);
        } else {
            String id = vodId;
            if (id != null && id.contains("_")) id = id.substring(id.lastIndexOf('_') + 1);
            return aggBase(apiBase) + "?ac=detail&ids=" + id;
        }
    }

    /** 搜索接口地址（带分页 pg） */
    public static String buildSearchUrl(String apiBase, String wd, int page) {
        String q = "ac=detail&wd=" + encode(wd) + "&pg=" + page;
        if (isMacCms(apiBase)) return macCmsUrl(apiBase, q);
        return aggBase(apiBase) + "?" + q;
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ---------------- 播放源节点列表 ----------------

    public static List<PlaySource> fetchNodeList(String configUrl) throws Exception {
        String body = get(configUrl);
        List<PlaySource> result = new ArrayList<>();
        JsonElement el = null;
        try { el = gson.fromJson(body, JsonElement.class); } catch (Exception ignored) { /* XML/非JSON：交给下方直连兜底 */ }
        if (el != null && el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                PlaySource ps = parseSource(e);
                if (ps != null && !TextUtils.isEmpty(ps.name)) result.add(ps);
            }
        } else if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray arr = null;
            if (obj.has("sources") && obj.get("sources").isJsonArray()) arr = obj.getAsJsonArray("sources");
            else if (obj.has("list") && obj.get("list").isJsonArray()) arr = obj.getAsJsonArray("list");
            else if (obj.has("data") && obj.get("data").isJsonArray()) arr = obj.getAsJsonArray("data");
            else if (obj.has("sites") && obj.get("sites").isJsonArray()) arr = obj.getAsJsonArray("sites");
            else if (obj.has("nodes") && obj.get("nodes").isJsonArray()) arr = obj.getAsJsonArray("nodes");
            if (arr != null) {
                for (JsonElement e : arr) {
                    PlaySource ps = parseSource(e);
                    if (ps != null && !TextUtils.isEmpty(ps.name)) result.add(ps);
                }
            } else {
                PlaySource ps = parseSource(obj);
                if (ps != null && !TextUtils.isEmpty(ps.name)) result.add(ps);
            }
        }
        // 解析不出节点列表、但该地址本身就是可用的直连采集站接口时，把它作为一个源返回
        if (result.isEmpty() && isLikelyDirectApi(configUrl, el)) {
            String trim = normalizeBase(configUrl == null ? "" : configUrl.trim());
            PlaySource ps = new PlaySource();
            ps.name = deriveSourceName(trim);
            ps.api = trim;
            ps.url = trim;
            ps.description = "直连采集站接口";
            result.add(ps);
        }
        return result;
    }

    /** 判断该地址是否为"直连采集站接口"（非节点列表配置） */
    private static boolean isLikelyDirectApi(String url, JsonElement el) {
        String u = url == null ? "" : url.toLowerCase();
        if (u.contains("provide/vod") || u.contains("api.php") || u.contains("/api/vod")
                || u.contains("vod.php") || u.contains("/api/")) return true;
        if (el != null && el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("class")) return true;                       // mac-cms 返回特征
            if (o.has("list") && o.get("list").isJsonArray()) {
                JsonArray arr = o.getAsJsonArray("list");
                if (arr.size() > 0 && arr.get(0).isJsonObject()
                        && arr.get(0).getAsJsonObject().has("vod_id")) return true;
            }
        }
        return false;
    }

    /** 从地址推导源名字（默认用主机名） */
    private static String deriveSourceName(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) return host;
        } catch (Exception ignored) { }
        return url;
    }

    private static PlaySource parseSource(JsonElement e) {
        try {
            if (!e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            PlaySource ps = new PlaySource();
            ps.name = getStr(o, "name", "site_name", "title");
            ps.url = getStr(o, "url", "site_url", "domain", "host");
            ps.api = getStr(o, "api", "api_url", "apiUrl", "vodUrl", "vod_url", "interface");
            ps.description = getStr(o, "description", "desc", "remark", "note");
            if (TextUtils.isEmpty(ps.api) && !TextUtils.isEmpty(ps.url)) {
                ps.api = ps.url;
            }
            if (TextUtils.isEmpty(ps.api)) return null;
            return ps;
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------- 影视数据 ----------------

    /** 首页 / 分类列表 */
    public static List<VodItem> fetchList(String apiBase, int typeId, int page) throws Exception {
        String url = buildListUrl(apiBase, typeId, page);
        String body = get(url);
        if (isXmlBody(body)) return parseXmlList(body);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        List<VodItem> list = new ArrayList<>();
        if (obj == null || !obj.has("list")) return list;
        JsonArray arr = obj.getAsJsonArray("list");
        for (JsonElement e : arr) {
            try {
                VodItem v = parseVod(e.getAsJsonObject());
                if (!isBlockedCommentary(v.getTitle())) list.add(v);
            } catch (Exception ignored) { }
        }
        return list;
    }

    /** 搜索 */
    public static List<VodItem> fetchSearch(String apiBase, String wd) throws Exception {
        return fetchSearch(apiBase, wd, 1);
    }

    /** 搜索（支持分页 pg） */
    public static List<VodItem> fetchSearch(String apiBase, String wd, int page) throws Exception {
        String url = buildSearchUrl(apiBase, wd, page);
        String body = get(url);
        if (isXmlBody(body)) return parseXmlList(body);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        List<VodItem> list = new ArrayList<>();
        if (obj == null || !obj.has("list")) return list;
        JsonArray arr = obj.getAsJsonArray("list");
        for (JsonElement e : arr) {
            try {
                VodItem v = parseVod(e.getAsJsonObject());
                if (!isBlockedCommentary(v.getTitle())) list.add(v);
            } catch (Exception ignored) { }
        }
        return list;
    }

    /** 屏蔽"解说"类内容（片名含"解说"的影视解说/盘点视频不展示） */
    private static boolean isBlockedCommentary(String title) {
        if (title == null) return false;
        return title.contains("解说");
    }

    /** 详情 */
    public static VodDetail fetchDetail(String apiBase, String vodId) throws Exception {
        String url = buildDetailUrl(apiBase, vodId);
        String body = get(url);
        if (isXmlBody(body)) return parseXmlDetail(body);
        JsonObject obj = gson.fromJson(body, JsonObject.class);
        if (obj == null || !obj.has("list")) return null;
        JsonArray arr = obj.getAsJsonArray("list");
        if (arr.size() == 0) return null;
        return parseDetail(arr.get(0).getAsJsonObject());
    }

    // ---------------- 播放源分类 ----------------

    /**
     * 从播放源动态读取分类树（type_id / type_pid / type_name）。
     * mac-cms 源走 ?ac=type；聚合源走站点根 class；失败则交叉兜底。
     */
    public static List<Category> fetchCategories(String apiBase) throws Exception {
        List<Category> cats;
        if (isMacCms(apiBase)) {
            cats = parseCats(get(macCmsUrl(apiBase, "ac=type")));
            if (cats.isEmpty()) cats = parseCats(get(aggBase(apiBase)));
        } else {
            cats = parseCats(get(aggBase(apiBase)));
            if (cats.isEmpty()) cats = parseCats(get(macCmsUrl(apiBase, "ac=type")));
        }
        return normalizeCategories(cats);
    }

    /**
     * 分类归一化：若源未返回 type_pid（无法区分主/子分类），用名称启发式补齐。
     * 已有 pid 信息的源原样返回。
     */
    static List<Category> normalizeCategories(List<Category> in) {
        if (in == null || in.isEmpty()) return in;
        boolean hasPid = false;
        for (Category c : in) if (c.pid > 0) { hasPid = true; break; }
        if (hasPid) return in;

        int idMovie = -1, idTV = -1, idVar = -1, idAnime = -1, idSports = -1;
        for (Category c : in) {
            if (isMainName(c.name)) {
                c.pid = 0;
                String n = c.name;
                if (n.equals("电影") || n.equals("电影片")) idMovie = c.id;
                else if (n.equals("电视剧") || n.equals("连续剧")) idTV = c.id;
                else if (n.equals("综艺") || n.equals("综艺片")) idVar = c.id;
                else if (n.equals("动漫") || n.equals("动漫片")) idAnime = c.id;
                else if (n.equals("体育") || n.equals("体育赛事")) idSports = c.id;
            }
        }
        int fallback = 0;
        for (Category c : in) if (c.pid == 0) { fallback = c.id; break; }
        if (fallback <= 0 && !in.isEmpty()) fallback = in.get(0).id;
        if (idMovie == -1) idMovie = fallback;
        if (idTV == -1) idTV = fallback;
        if (idVar == -1) idVar = fallback;
        if (idAnime == -1) idAnime = fallback;
        if (idSports == -1) idSports = fallback;

        for (Category c : in) {
            if (c.pid == 0) continue;
            String n = c.name == null ? "" : c.name;
            int pid;
            if (n.contains("综艺")) pid = idVar;
            else if (n.contains("动漫") || n.contains("动画")
                    || n.contains("国漫") || n.contains("日漫")) pid = idAnime;
            else if (n.contains("体育")) pid = idSports;
            else if (n.endsWith("片") || n.contains("电影")) pid = idMovie;
            else if (n.contains("剧")) pid = idTV;
            else pid = idMovie;
            c.pid = pid;
        }
        return in;
    }

    /** 精确匹配的标准主分类名 */
    private static boolean isMainName(String name) {
        if (name == null) return false;
        return name.equals("电影") || name.equals("电影片")
                || name.equals("电视剧") || name.equals("连续剧")
                || name.equals("综艺") || name.equals("综艺片")
                || name.equals("动漫") || name.equals("动漫片")
                || name.equals("体育") || name.equals("体育赛事")
                || name.equals("纪录片") || name.equals("电影解说")
                || name.equals("预告片") || name.equals("专题");
    }

    private static List<Category> parseCats(String body) {
        if (isXmlBody(body)) return parseXmlCats(body);
        List<Category> out = new ArrayList<>();
        try {
            JsonObject o = gson.fromJson(body, JsonObject.class);
            if (o == null) return out;
            JsonArray arr = null;
            if (o.has("class") && o.get("class").isJsonArray()) {
                arr = o.getAsJsonArray("class");
            } else if (o.has("list") && o.get("list").isJsonArray()) {
                JsonArray l = o.getAsJsonArray("list");
                if (l.size() > 0 && l.get(0).isJsonObject() && l.get(0).getAsJsonObject().has("type_name")) {
                    arr = l;
                }
            }
            if (arr == null) return out;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject c = e.getAsJsonObject();
                Category cat = new Category();
                cat.id = getInt(c, "type_id");
                cat.pid = getInt(c, "type_pid");
                cat.name = getStr(c, "type_name");
                if (cat.id > 0 && !cat.name.isEmpty()) out.add(cat);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static int getInt(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return -1;
        JsonElement v = o.get(key);
        try {
            if (v.isJsonPrimitive()) {
                String s = v.getAsString();
                if (s == null || s.isEmpty()) return -1;
                return (int) Double.parseDouble(s.trim());
            }
        } catch (Exception ignored) { }
        return -1;
    }

    private static VodItem parseVod(JsonObject o) {
        VodItem item = new VodItem();
        item.vod_id = getStr(o, "vod_id", "id");
        item.vod_name = getStr(o, "vod_name", "name", "title");
        item.vod_pic = getStr(o, "vod_pic", "pic", "vod_img");
        item.vod_remarks = getStr(o, "vod_remarks", "remarks", "remark");
        item.vod_year = getStr(o, "vod_year", "year");
        item.vod_area = getStr(o, "vod_area", "area");
        item.vod_type_name = getStr(o, "type_name", "vod_type_name", "class");
        item.vod_score = getStr(o, "vod_score", "score", "vod_douban_score", "douban_score");
        item.vod_content = getStr(o, "vod_content", "content", "des");
        item.vod_play_url = getStr(o, "vod_play_url", "play_url");
        item.vod_play_from = getStr(o, "vod_play_from", "play_from", "source");
        return item;
    }

    private static VodDetail parseDetail(JsonObject o) {
        VodDetail d = new VodDetail();
        d.vod_id = getStr(o, "vod_id", "id");
        d.vod_name = getStr(o, "vod_name", "name", "title");
        d.vod_pic = getStr(o, "vod_pic", "pic", "vod_img");
        d.vod_remarks = getStr(o, "vod_remarks", "remarks", "remark");
        d.vod_year = getStr(o, "vod_year", "year");
        d.vod_area = getStr(o, "vod_area", "area");
        d.vod_type_name = getStr(o, "type_name", "vod_type_name", "class");
        d.vod_score = getStr(o, "vod_score", "score", "vod_douban_score", "douban_score");
        d.vod_content = getStr(o, "vod_content", "content", "des");
        String playUrl = getStr(o, "vod_play_url", "play_url");
        String playFrom = getStr(o, "vod_play_from", "play_from", "source");
        d.groups = MacCmsParser.parsePlay(playFrom, playUrl);
        return d;
    }

    private static String getStr(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                JsonElement v = o.get(k);
                String s = v.getAsString();
                if (s != null && !s.isEmpty() && !"0".equals(s)) return s;
            }
        }
        return "";
    }

    // ---------------- XML 采集源（mac-cms at/xml，如 /provide/vod/at/xml/） ----------------

    /** 判断 URL 是否为"HTML 播放页"（mac-cms /share/ 或 /play/ 这类，需解析出真实 m3u8） */
    public static boolean isSharePageUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("/share/") || u.contains("/play/")
                || u.contains("/player/") || u.contains("/vodplay/");
    }

    /**
     * 解析 HTML 播放页里的真实视频地址。
     * 这类页（如 ffzy 的 /share/xxx）用 JS 给 const url = "相对/绝对m3u8"，ExoPlayer 无法直接播 HTML，
     * 这里抓页面把 m3u8 提取出来并补全成绝对地址；解析失败则原样返回。
     * 结果做短期内存缓存（m3u8 带时效签名，缓存 5 分钟），减少重复选集时的页面抓取与转圈卡顿。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, CacheHit> PLAY_URL_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    public static String resolvePlayUrl(String url) {
        if (url == null || !isSharePageUrl(url)) return url;
        CacheHit hit = PLAY_URL_CACHE.get(url);
        long now = System.currentTimeMillis();
        if (hit != null && now - hit.time < CACHE_TTL_MS) return hit.url;
        try {
            String body = get(url);
            String path = extractVideoUrl(body);
            String resolved = url;
            if (path != null && !path.trim().isEmpty()) {
                path = path.trim();
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    resolved = path;
                } else {
                    try {
                        java.net.URI u = java.net.URI.create(url);
                        String scheme = u.getScheme();
                        String host = u.getHost();
                        if (host != null && !host.isEmpty()) {
                            String base = scheme + "://" + host;
                            resolved = path.startsWith("/") ? base + path : base + "/" + path;
                        } else {
                            resolved = path;
                        }
                    } catch (Exception ignored) {
                        resolved = path;
                    }
                }
            }
            PLAY_URL_CACHE.put(url, new CacheHit(resolved, now));
            return resolved;
        } catch (Exception e) {
            return url;
        }
    }

    private static final class CacheHit {
        final String url;
        final long time;
        CacheHit(String u, long t) { url = u; time = t; }
    }

    /** 从 HTML 播放页脚本里提取视频地址（const url="..." / "url":"..." / 变量 url） */
    private static String extractVideoUrl(String body) {
        if (body == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?i)(?:const|var|let)\\s+url\\s*[:=]\\s*[\"']([^\"']+)[\"']").matcher(body);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile(
                "(?i)[\"']url[\"']\\s*[:=]\\s*[\"']([^\"']+)[\"']").matcher(body);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile(
                "(?i)data-src\\s*=\\s*[\"']([^\"']+)[\"']").matcher(body);
        if (m.find()) return m.group(1);
        return null;
    }

    /** 判断响应是否为 XML（mac-cms 的 at/xml 输出） */
    private static boolean isXmlBody(String body) {
        String t = body == null ? "" : body.trim();
        return t.startsWith("<?xml") || t.startsWith("<rss") || t.startsWith("<list");
    }

    /** 解析 mac-cms XML 列表/搜索：<rss><list><video><id><name><type><pic><note>... */
    private static List<VodItem> parseXmlList(String body) {
        List<VodItem> out = new ArrayList<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(body));
            VodItem cur = null;
            String tag = null;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("video".equals(tag)) cur = new VodItem();
                } else if (ev == XmlPullParser.TEXT) {
                    String txt = p.getText() == null ? "" : p.getText().trim();
                    if (cur == null || tag == null || txt.isEmpty()) continue;
                    switch (tag) {
                        case "id": cur.vod_id = txt; break;
                        case "name": cur.vod_name = txt; break;
                        case "type": cur.vod_type_name = txt; break;
                        case "pic": cur.vod_pic = txt; break;
                        case "note": cur.vod_remarks = txt; break;
                        case "year": cur.vod_year = txt; break;
                        case "area": cur.vod_area = txt; break;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("video".equals(p.getName())) {
                        if (cur != null && cur.vod_id != null && !cur.vod_id.isEmpty()
                                && !isBlockedCommentary(cur.vod_name)) out.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** 解析 mac-cms XML 详情：<rss><list><video>...<dl><dd flag=线路名>集名$地址#...</dl> */
    private static VodDetail parseXmlDetail(String body) {
        VodDetail d = new VodDetail();
        VodDetail.PlayGroup curGroup = null;
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(body));
            String tag = null;
            boolean inDl = false;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("dl".equals(tag)) {
                        inDl = true;
                    } else if ("dd".equals(tag)) {
                        curGroup = new VodDetail.PlayGroup();
                        curGroup.name = p.getAttributeValue(null, "flag");
                        if (curGroup.name == null) curGroup.name = "";
                        d.groups.add(curGroup);
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    String txt = p.getText() == null ? "" : p.getText().trim();
                    if (txt.isEmpty()) continue;
                    if (inDl && curGroup != null) {
                        // 该 dd 的剧集：集名$地址，用 # 分隔
                        for (String seg : txt.split("#")) {
                            seg = seg.trim();
                            if (seg.isEmpty()) continue;
                            int di = seg.indexOf('$');
                            if (di < 0) continue;
                            String nm = seg.substring(0, di);
                            String url = seg.substring(di + 1);
                            curGroup.episodes.add(new VodDetail.PlayEpisode(nm, url));
                        }
                    } else if (tag != null) {
                        switch (tag) {
                            case "id": d.vod_id = txt; break;
                            case "name": d.vod_name = txt; break;
                            case "type": d.vod_type_name = txt; break;
                            case "pic": d.vod_pic = txt; break;
                            case "note": d.vod_remarks = txt; break;
                            case "year": d.vod_year = txt; break;
                            case "area": d.vod_area = txt; break;
                            case "content": case "des": if (d.vod_content == null || d.vod_content.isEmpty()) d.vod_content = txt; break;
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String n = p.getName();
                    if ("dd".equals(n)) curGroup = null;
                    else if ("dl".equals(n)) inDl = false;
                    tag = null;
                }
            }
        } catch (Exception ignored) { }
        return d;
    }

    /** 解析 mac-cms XML 分类：<rss><class><ty id="" pid=""><name>..</name></ty> */
    private static List<Category> parseXmlCats(String body) {
        List<Category> out = new ArrayList<>();
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(body));
            String tag = null;
            Category cur = null;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("ty".equals(tag)) {
                        cur = new Category();
                        String id = p.getAttributeValue(null, "id");
                        String pid = p.getAttributeValue(null, "pid");
                        try { if (id != null && !id.trim().isEmpty()) cur.id = Integer.parseInt(id.trim()); } catch (Exception ignored) { }
                        try { if (pid != null && !pid.trim().isEmpty()) cur.pid = Integer.parseInt(pid.trim()); } catch (Exception ignored) { }
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    String txt = p.getText() == null ? "" : p.getText().trim();
                    if (cur == null || txt.isEmpty()) continue;
                    if ("name".equals(tag)) cur.name = txt;
                } else if (ev == XmlPullParser.END_TAG) {
                    if ("ty".equals(p.getName())) {
                        if (cur != null && cur.id > 0 && cur.name != null && !cur.name.isEmpty()) out.add(cur);
                        cur = null;
                    }
                    tag = null;
                }
            }
        } catch (Exception ignored) { }
        return out;
    }
}
