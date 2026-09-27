package com.videobox.movie.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.videobox.movie.R;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.data.VodDetail;
import com.videobox.movie.data.VodItem;
import com.videobox.movie.net.ApiClient;
import com.videobox.movie.player.FilteringDataSource;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends AppCompatActivity {

    /** 全屏播放器返回时带回来的续播进度（毫秒），0=无 */
    public static volatile long RETURN_POS_MS = 0;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private TextView tvName, tvMeta, tvScore, tvContent;
    private ImageView ivPoster;
    private RecyclerView rvLines, rvEpisodes;
    private androidx.appcompat.widget.AppCompatButton btnFavorite;

    private VodDetail detail;
    private int currentGroup = 0;
    private List<Boolean> lineSelected;
    private String vodId;

    // 小窗播放器（图三：详情页顶部小窗继续播）
    private ExoPlayer miniPlayer;
    private PlayerView miniPlayerView;
    private View miniControls, btnMiniPlay, btnMiniFullscreen;
    private TextView tvMiniPos, tvMiniDur;
    private boolean miniPlaying = false;
    private boolean returnFromFullscreen = false;
    private java.util.List<String> miniUrls = new java.util.ArrayList<>();
    private int miniLine = 0, miniEp = 0;
    private long miniResumePos = 0;
    private boolean autoPlayDone = false;
    private View bigPlayBtn;
    private String miniTitle = "";
    private View btnMiniPrev, btnMiniNext, btnMiniSettings;
    private TextView btnMiniSpeed;
    private float miniSpeed = 1.0f;
    private int currentSize = 0;
    private static final float[] MINI_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private int miniSpeedIdx = 2;
    private android.media.AudioManager miniAudio;
    /** 切后台时若正在播放，记录以便切回来自动续播 */
    private boolean miniWasPlayingBeforePause = false;

    // 手势（同全屏）：轻点播放/暂停、上划亮度下划音量、左右划进度、长按倍速
    private TextView tvMiniGesture;
    private int gestureMode = 0;
    private float gestureStartX, gestureStartY;
    private boolean gestureLeftHalf;
    private long gestureStartPosMs;
    private int gestureStartBright, gestureStartVol;
    private boolean miniLongPressActive = false;
    private float miniNormalSpeed = 0f;
    private final Runnable miniLongPressRunnable = new Runnable() {
        @Override public void run() {
            if (miniPlayer != null) {
                miniLongPressActive = true;
                miniNormalSpeed = miniPlayer.getPlaybackParameters().speed;
                miniPlayer.setPlaybackSpeed(Prefs.get(DetailActivity.this).getLongPressSpeed());
            }
        }
    };
    private final Runnable hideMiniGesture = new Runnable() {
        @Override public void run() {
            if (tvMiniGesture != null) tvMiniGesture.setVisibility(View.GONE);
        }
    };
    private final Runnable miniTicker = new Runnable() {
        @Override
        public void run() {
            updateMiniTime();
            main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        vodId = getIntent().getStringExtra("vod_id");
        String title = getIntent().getStringExtra("title");

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(title == null ? "影片详情" : title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tvName = findViewById(R.id.tv_name);
        tvMeta = findViewById(R.id.tv_meta);
        tvScore = findViewById(R.id.tv_score);
        tvContent = findViewById(R.id.tv_content);
        ivPoster = findViewById(R.id.iv_poster);
        rvLines = findViewById(R.id.rv_lines);
        rvEpisodes = findViewById(R.id.rv_episodes);
        btnFavorite = findViewById(R.id.btn_favorite);

        rvLines.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvEpisodes.setLayoutManager(new GridLayoutManager(this, 5));

        miniPlayerView = findViewById(R.id.mini_player);
        miniControls = findViewById(R.id.mini_controls);
        btnMiniPlay = findViewById(R.id.btn_mini_play);
        btnMiniFullscreen = findViewById(R.id.btn_mini_fullscreen);
        tvMiniPos = findViewById(R.id.tv_mini_pos);
        tvMiniDur = findViewById(R.id.tv_mini_dur);
        tvMiniGesture = findViewById(R.id.tv_mini_gesture);
        btnMiniPrev = findViewById(R.id.btn_mini_prev);
        btnMiniNext = findViewById(R.id.btn_mini_next);
        btnMiniSpeed = findViewById(R.id.btn_mini_speed);
        btnMiniSettings = findViewById(R.id.btn_mini_settings);
        miniAudio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        btnMiniPlay.setOnClickListener(v -> toggleMiniPlay());
        btnMiniFullscreen.setOnClickListener(v -> goFullscreen());
        btnMiniPrev.setOnClickListener(v -> playMiniEpisode(miniEp - 1));
        btnMiniNext.setOnClickListener(v -> playMiniEpisode(miniEp + 1));
        btnMiniSpeed.setOnClickListener(v -> cycleMiniSpeed());
        btnMiniSettings.setOnClickListener(v -> showMiniSettings());
        btnMiniSpeed.setText("倍速 " + speedText(miniSpeed));
        miniPlayerView.setOnTouchListener((v, e) -> handleMiniGesture(v, e));

        findViewById(R.id.btn_play).setOnClickListener(v -> playFirst());
        bigPlayBtn = findViewById(R.id.btn_play);
        btnFavorite.setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.btn_history).setOnClickListener(v -> {
            List<VodItem> hist = Prefs.get(this).getHistory();
            if (hist.isEmpty()) {
                Toast.makeText(this, "暂无播放历史", Toast.LENGTH_SHORT).show();
            } else {
                Ui.openDetail(this, hist.get(0));
            }
        });

        loadDetail();
    }

    private void loadDetail() {
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) {
            Toast.makeText(this, "请先配置播放源", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        exec.execute(() -> {
            try {
                VodDetail d = ApiClient.fetchDetail(source.getApiBase(), vodId);
                main.post(() -> {
                    if (d == null) {
                        Toast.makeText(this, "获取详情失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    VodDetail d0 = d;
                    d0.groups = filterDisabledFlags(d0.groups);
                    detail = d0;
                    bind();
                    if (d0.groups.isEmpty()) {
                        Toast.makeText(this, "该片暂无可用播放线路（线路被禁用或源无数据），可检查设置-播放器标识", Toast.LENGTH_LONG).show();
                    }
                    // 点击片源直接播放：详情加载完成即自动开播第一条线路的第一集
                    if (!autoPlayDone) {
                        autoPlayDone = true;
                        tryAutoPlay();
                    }
                    // 多源聚合：把其它已保存源里同一部片的线路也合并进来
                    String title = detail.getTitle();
                    if (title != null && !title.trim().isEmpty()) aggregateOtherSources(title);
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "加载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 节点列表配置 → 子源 的缓存，避免每个详情页都重复拉取配置 */
    private static final java.util.Map<String, List<PlaySource>> NODE_CACHE = new java.util.HashMap<>();

    /**
     * 多源聚合线路：并行去其它已保存源里按标题搜同一部片，取其所有线路合并到当前详情。
     * 采用"增量合并"：每个源一返回就立刻加进来，快的先出现，慢/卡的不拖累整体。
     * 若某源是"节点列表配置地址"（如 node.mac-cms.com），先解析出其下所有子源再逐个聚合，
     * 线路名前面加"源名·"前缀以区分来源。
     */
    private void aggregateOtherSources(final String title) {
        final String curApi = Prefs.get(this).getPlaySource() != null
                ? Prefs.get(this).getPlaySource().getApiBase() : "";
        List<PlaySource> saved = Prefs.get(this).getAllSources();
        if (saved.size() <= 1) return; // 只有当前源，无需聚合
        final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        for (PlaySource ps : saved) {
            final String api = ps.api != null && !ps.api.trim().isEmpty() ? ps.api : ps.url;
            if (api == null || api.trim().isEmpty()) continue;
            if (api.equalsIgnoreCase(curApi)) continue; // 跳过当前源
            final String nm = ps.name == null ? api : ps.name;
            pool.execute(() -> expandAndCollect(api, nm, title, pool));
        }
        pool.shutdown();
    }

    /** 若为"节点列表"则展开为子源逐个聚合，否则当作直连源聚合 */
    private void expandAndCollect(String api, String nm, String title,
                                  java.util.concurrent.ExecutorService pool) {
        try {
            List<PlaySource> subs;
            synchronized (NODE_CACHE) {
                subs = NODE_CACHE.get(api);
            }
            if (subs == null) {
                subs = ApiClient.fetchNodeList(api);
                if (subs == null) subs = new ArrayList<>();
                synchronized (NODE_CACHE) { NODE_CACHE.put(api, subs); }
            }
            if (subs.size() > 1) {
                for (PlaySource sub : subs) {
                    String subApi = sub.api != null && !sub.api.trim().isEmpty() ? sub.api : sub.url;
                    if (subApi == null || subApi.trim().isEmpty()) continue;
                    final String subName = sub.name == null ? nm : sub.name;
                    pool.execute(() -> collectOne(subApi, subName, title));
                }
            } else {
                collectOne(api, nm, title);
            }
        } catch (Exception ignored) { }
    }

    /** 标题规范化：去掉空格和常见分隔符，用于匹配比较 */
    private static String normTitle(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s·\\-:：()（）\\[\\]]", "").trim().toLowerCase();
    }

    /** 在单个源里按标题搜同一部片，取线路并立刻合并 */
    private void collectOne(String api, String nm, String title) {
        try {
            java.util.List<VodItem> hits = ApiClient.fetchSearch(api, title);
            VodItem match = null;
            String nTarget = normTitle(title);
            for (VodItem it : hits) {
                String t = it.getTitle();
                if (t != null && normTitle(t).equals(nTarget)) { match = it; break; }
            }
            // 只聚合标题精确匹配的片，绝不取"搜索结果第一条"，避免把别的片子并进来导致放错内容
            if (match == null || match.vod_id == null) return;
            VodDetail d = ApiClient.fetchDetail(api, match.vod_id);
            if (d == null || d.groups == null || d.groups.isEmpty()) return;
            // 该片返回标题也应匹配，防止详情接口串数据
            if (d.getTitle() != null && !normTitle(d.getTitle()).equals(nTarget)) return;
            final java.util.List<VodDetail.PlayGroup> merged = new java.util.ArrayList<>();
            for (VodDetail.PlayGroup g : d.groups) {
                VodDetail.PlayGroup ng = new VodDetail.PlayGroup();
                ng.name = (g.name == null || g.name.isEmpty()) ? nm : nm + "·" + g.name;
                ng.episodes.addAll(g.episodes);
                merged.add(ng);
            }
            main.post(() -> appendLines(filterDisabledFlags(merged)));
        } catch (Exception ignored) { }
    }

    /** 按播放器标识启用状态过滤线路：被禁用的 flag 直接剔除 */
    private java.util.List<VodDetail.PlayGroup> filterDisabledFlags(java.util.List<VodDetail.PlayGroup> groups) {
        if (groups == null) return groups;
        java.util.List<VodDetail.PlayGroup> out = new java.util.ArrayList<>();
        Prefs prefs = Prefs.get(this);
        for (VodDetail.PlayGroup g : groups) {
            if (prefs.isPlayerFlagEnabled(flagCodeOf(g.name))) out.add(g);
        }
        return out;
    }

    /** 从线路名里提取 flag（形如 "源名·sdm3u8"，取最后一段；无分隔符则取整段） */
    private String flagCodeOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('·');
        return (i >= 0 ? name.substring(i + 1) : name).trim();
    }

    /** 把一个源的所有线路追加到详情并刷新线路栏（增量） */
    private void appendLines(java.util.List<VodDetail.PlayGroup> newGroups) {
        if (isFinishing() || detail == null || newGroups.isEmpty()) return;
        boolean anyAdded = false;
        for (VodDetail.PlayGroup ng : newGroups) {
            boolean dup = false;
            for (VodDetail.PlayGroup eg : detail.groups) {
                if (eg.name != null && eg.name.equals(ng.name)) { dup = true; break; }
            }
            if (dup) continue;
            detail.groups.add(ng);
            anyAdded = true;
        }
        if (!anyAdded) return;
        lineSelected.clear();
        for (int i = 0; i < detail.groups.size(); i++) lineSelected.add(false);
        lineSelected.set(0, true);
        rvLines.setAdapter(new LineAdapter());
        rvEpisodes.setAdapter(new EpisodeAdapter(currentGroup));
    }

    private void bind() {
        tvName.setText(detail.getTitle());
        tvMeta.setText(detail.getSubtitle());
        if (detail.vod_score != null && !detail.vod_score.isEmpty()) {
            tvScore.setText("评分：" + detail.vod_score);
        } else {
            tvScore.setText("");
        }
        tvContent.setText(detail.vod_content == null ? "" : detail.vod_content);
        Img.load(this, detail.vod_pic, ivPoster);

        // 线路
        lineSelected = new ArrayList<>();
        for (int i = 0; i < detail.groups.size(); i++) lineSelected.add(false);
        if (!detail.groups.isEmpty()) lineSelected.set(0, true);
        rvLines.setAdapter(new LineAdapter());

        // 选集
        rvEpisodes.setAdapter(new EpisodeAdapter(currentGroup));

        updateFavoriteButton();
    }

    private void playFirst() {
        if (detail == null || detail.groups.isEmpty()) return;
        VodDetail.PlayGroup group = detail.groups.get(currentGroup);
        if (group.episodes.isEmpty()) {
            Toast.makeText(this, "该线路暂无可用剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        play(currentGroup, 0);
    }

    /** 进入详情即自动开播：取当前线路第一个有地址的剧集 */
    private void tryAutoPlay() {
        if (detail == null || detail.groups.isEmpty()) return;
        VodDetail.PlayGroup g = detail.groups.get(currentGroup);
        if (g == null || g.episodes.isEmpty()) return;
        for (int i = 0; i < g.episodes.size(); i++) {
            String u = g.episodes.get(i).url;
            if (u != null && !u.trim().isEmpty()) { play(currentGroup, i); return; }
        }
    }

    private void play(int group, int ep) {
        if (detail == null || detail.groups.isEmpty()) return;
        VodDetail.PlayGroup g = detail.groups.get(group);
        if (ep >= g.episodes.size()) return;
        // 记录历史
        Prefs prefs = Prefs.get(this);
        VodItem hist = new VodItem();
        hist.vod_id = detail.vod_id;
        hist.vod_name = detail.vod_name;
        hist.vod_pic = detail.vod_pic;
        hist.vod_remarks = g.episodes.get(ep).name;
        hist.vod_year = detail.vod_year;
        hist.vod_type_name = detail.vod_type_name;
        prefs.recordHistory(hist);
        String url = g.episodes.get(ep).url;
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "该集播放地址为空，请换线路", Toast.LENGTH_SHORT).show();
            return;
        }
        // 记录当前线路全部剧集，支持选集/上一集下一集/线路切换
        miniUrls = new java.util.ArrayList<>();
        for (VodDetail.PlayEpisode e : g.episodes) miniUrls.add(e.url);
        miniLine = group;
        miniEp = ep;
        miniTitle = detail.getTitle() != null ? detail.getTitle() : "";
        returnFromFullscreen = false;
        // 图三：在详情页顶部小窗里播放
        playInMini(url);
    }

    /** 小窗播放器：复用与播放器一致的广告过滤数据源 */
    private void playInMini(String url) {
        ensureMiniPlayer();
        ivPoster.setVisibility(View.GONE);
        if (bigPlayBtn != null) bigPlayBtn.setVisibility(View.GONE);
        miniPlayerView.setVisibility(View.VISIBLE);
        miniControls.setVisibility(View.VISIBLE);
        // HTML 播放页（如 ffzy /share/）先异步解析出真实 m3u8，避免直接播 HTML 报错
        if (ApiClient.isSharePageUrl(url)) {
            showMiniGesture("解析播放地址…");
            exec.execute(() -> {
                final String resolved = ApiClient.resolvePlayUrl(url);
                main.post(() -> {
                    if (isFinishing()) return;
                    hideMiniGesture();
                    startMiniPlayback(resolved);
                });
            });
        } else {
            startMiniPlayback(url);
        }
    }

    private void ensureMiniPlayer() {
        if (miniPlayer != null) return;
        boolean adBlock = Prefs.get(this).isAdBlockEnabled();
        final List<String> keywords = Prefs.get(this).getAdKeywords();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
        DataSource.Factory dsFactory = adBlock
                ? () -> new FilteringDataSource(http.createDataSource(), keywords)
                : http;
        DefaultRenderersFactory rf = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
        miniPlayer = new ExoPlayer.Builder(this, rf)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1500, 12000, 500, 1500)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build())
                .build();
        miniPlayerView.setPlayer(miniPlayer);
        miniPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        miniPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long dur = miniPlayer.getDuration();
                    if (dur > 0) tvMiniDur.setText(" / " + format(dur));
                    if (miniResumePos > 0) {
                        miniPlayer.seekTo(miniResumePos);
                        miniResumePos = 0;
                    }
                }
            }
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateMiniIcon();
                if (isPlaying) startMiniTicker(); else main.removeCallbacks(miniTicker);
            }
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(DetailActivity.this,
                        "播放出错，可能该线路失效，请换线路尝试", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startMiniPlayback(String url) {
        if (miniPlayer == null) return;
        miniPlayer.setMediaItem(MediaItem.fromUri(url));
        miniPlayer.setPlayWhenReady(true);
        miniPlayer.prepare();
        updateMiniIcon();
    }

    private void toggleMiniPlay() {
        if (miniPlayer == null) return;
        if (miniPlayer.isPlaying()) miniPlayer.pause(); else miniPlayer.play();
        updateMiniIcon();
    }

    private boolean handleMiniGesture(View v, android.view.MotionEvent e) {
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                gestureMode = 0;
                gestureStartX = e.getX();
                gestureStartY = e.getY();
                gestureLeftHalf = e.getX() < v.getWidth() / 2f;
                if (miniPlayer != null) gestureStartPosMs = miniPlayer.getCurrentPosition();
                gestureStartBright = Prefs.get(this).getBrightness();
                gestureStartVol = miniDeviceVolumePct();
                main.removeCallbacks(miniLongPressRunnable);
                main.postDelayed(miniLongPressRunnable, 400);
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - gestureStartX;
                float dy = e.getY() - gestureStartY;
                if (gestureMode == 0 && (Math.abs(dx) > 24 || Math.abs(dy) > 24)) {
                    main.removeCallbacks(miniLongPressRunnable);
                    gestureMode = (Math.abs(dy) >= Math.abs(dx)) ? 1 : 2;
                }
                if (gestureMode == 1) {
                    float delta = -dy / v.getHeight();
                    if (gestureLeftHalf) {
                        int nb = clamp(gestureStartBright + (int) (delta * 100), 0, 100);
                        applyBrightness(nb);
                        showMiniGesture("亮度 " + nb + "%");
                    } else {
                        int nv = clamp(gestureStartVol + (int) (delta * 100), 0, 100);
                        miniSetDeviceVolume(nv);
                        showMiniGesture("音量 " + nv + "%");
                    }
                } else if (gestureMode == 2 && miniPlayer != null) {
                    long dur = miniPlayer.getDuration();
                    if (dur > 0) {
                        float ratio = dx / v.getWidth();
                        long target = clamp(gestureStartPosMs + (long) (ratio * dur), 0, dur);
                        miniPlayer.seekTo(target);
                        tvMiniPos.setText(format(target));
                        showMiniGesture(format(target) + " / " + format(dur));
                    }
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
                main.removeCallbacks(miniLongPressRunnable);
                if (gestureMode == 0) {
                    if (miniLongPressActive) {
                        miniPlayer.setPlaybackSpeed(miniNormalSpeed > 0 ? miniNormalSpeed : miniSpeed);
                        miniLongPressActive = false;
                        miniNormalSpeed = 0;
                        hideMiniGesture();
                    } else {
                        toggleMiniPlay(); // 小窗轻点：播放/暂停
                    }
                } else {
                    hideMiniGesture();
                }
                gestureMode = 0;
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(miniLongPressRunnable);
                if (miniLongPressActive && miniPlayer != null) {
                    miniPlayer.setPlaybackSpeed(miniNormalSpeed > 0 ? miniNormalSpeed : miniSpeed);
                    miniLongPressActive = false;
                    miniNormalSpeed = 0;
                }
                hideMiniGesture();
                gestureMode = 0;
                return true;
            default:
                return false;
        }
    }

    private void showMiniGesture(String s) {
        tvMiniGesture.setText(s);
        tvMiniGesture.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideMiniGesture);
        main.postDelayed(hideMiniGesture, 700);
    }

    private void hideMiniGesture() {
        if (tvMiniGesture != null) tvMiniGesture.setVisibility(View.GONE);
    }

    private int miniDeviceVolumePct() {
        if (miniAudio == null) return 0;
        int max = miniAudio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int cur = miniAudio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        return max > 0 ? cur * 100 / max : 0;
    }

    private void miniSetDeviceVolume(int pct) {
        if (miniAudio == null) return;
        int max = miniAudio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        miniAudio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (int) (pct / 100f * max), 0);
    }

    private static int clamp(int v, int min, int max) { return v < min ? min : (v > max ? max : v); }
    private static long clamp(long v, long min, long max) { return v < min ? min : (v > max ? max : v); }

    private void updateMiniIcon() {
        boolean playing = miniPlayer != null && miniPlayer.isPlaying();
        if (btnMiniPlay instanceof ImageView) {
            ((ImageView) btnMiniPlay).setImageResource(playing
                    ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void startMiniTicker() {
        main.removeCallbacks(miniTicker);
        main.postDelayed(miniTicker, 500);
    }

    private void updateMiniTime() {
        if (miniPlayer == null) return;
        long pos = miniPlayer.getCurrentPosition();
        tvMiniPos.setText(format(pos));
    }

    private static String speedText(float s) {
        if (s == (long) s) return String.valueOf((long) s);
        return String.valueOf(s);
    }

    private void playMiniEpisode(int ep) {
        if (miniUrls.isEmpty()) { Toast.makeText(this, "暂无剧集", Toast.LENGTH_SHORT).show(); return; }
        if (ep < 0) { Toast.makeText(this, "已经是第一集", Toast.LENGTH_SHORT).show(); return; }
        if (ep >= miniUrls.size()) { Toast.makeText(this, "已经是最后一集", Toast.LENGTH_SHORT).show(); return; }
        String url = miniUrls.get(ep);
        if (url == null || url.trim().isEmpty()) { Toast.makeText(this, "该集地址为空", Toast.LENGTH_SHORT).show(); return; }
        miniEp = ep;
        playInMini(url);
    }

    private void switchMiniLine(int line) {
        if (detail == null || line < 0 || line >= detail.groups.size()) return;
        if (line == miniLine) return;
        VodDetail.PlayGroup g = detail.groups.get(line);
        if (g.episodes.isEmpty()) { Toast.makeText(this, "该线路暂无剧集", Toast.LENGTH_SHORT).show(); return; }
        miniLine = line;
        miniUrls = new java.util.ArrayList<>();
        for (VodDetail.PlayEpisode e : g.episodes) miniUrls.add(e.url);
        miniEp = 0;
        playInMini(miniUrls.get(0));
    }

    private void cycleMiniSpeed() {
        miniSpeedIdx = (miniSpeedIdx + 1) % MINI_SPEEDS.length;
        miniSpeed = MINI_SPEEDS[miniSpeedIdx];
        if (miniPlayer != null) miniPlayer.setPlaybackSpeed(miniSpeed);
        btnMiniSpeed.setText("倍速 " + speedText(miniSpeed));
    }

    /** 小窗设置面板：音量/亮度/倍速/画面尺寸/跳过片头片尾 */
    private void showMiniSettings() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(8));

        root.addView(label("音量"));
        android.widget.SeekBar sbVol = new android.widget.SeekBar(this);
        int max = miniAudio != null ? miniAudio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) : 10;
        int cur = miniAudio != null ? miniAudio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) : 5;
        sbVol.setMax(max); sbVol.setProgress(cur);
        sbVol.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                if (miniAudio != null) miniAudio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, p, 0);
            }
        });
        root.addView(sbVol);

        root.addView(label("亮度"));
        android.widget.SeekBar sbBri = new android.widget.SeekBar(this);
        sbBri.setMax(100); sbBri.setProgress(Prefs.get(this).getBrightness());
        sbBri.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                applyBrightness(p);
            }
        });
        root.addView(sbBri);

        root.addView(label("倍速"));
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < MINI_SPEEDS.length; i++) {
            final int idx = i;
            final float sp = MINI_SPEEDS[i];
            TextView c = chip(speedText(sp), sp == miniSpeed);
            c.setOnClickListener(v -> { miniSpeed = sp; miniSpeedIdx = idx;
                if (miniPlayer != null) miniPlayer.setPlaybackSpeed(sp);
                btnMiniSpeed.setText("倍速 " + speedText(sp)); });
            speedRow.addView(c);
        }
        root.addView(speedRow);

        root.addView(label("画面尺寸"));
        final String[] sizes = {"默认", "拉伸", "填充", "缩放"};
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < sizes.length; i++) {
            final int idx = i;
            TextView c = chip(sizes[i], idx == currentSize);
            c.setOnClickListener(v -> { currentSize = idx; applyMiniResize(idx); });
            sizeRow.addView(c);
        }
        root.addView(sizeRow);

        root.addView(label("跳过片头片尾（秒）"));
        LinearLayout skipRow = new LinearLayout(this);
        skipRow.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.EditText etIntro = new android.widget.EditText(this);
        etIntro.setText(String.valueOf(Prefs.get(this).getSkipIntroSec())); etIntro.setHint("片头");
        etIntro.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etIntro.setTextColor(0xFFFFFFFF);
        final android.widget.EditText etOutro = new android.widget.EditText(this);
        etOutro.setText(String.valueOf(Prefs.get(this).getSkipOutroSec())); etOutro.setHint("片尾");
        etOutro.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etOutro.setTextColor(0xFFFFFFFF);
        skipRow.addView(etIntro, new LinearLayout.LayoutParams(0, dp(46), 1));
        skipRow.addView(etOutro, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(skipRow);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("播放设置")
                .setView(root)
                .setPositiveButton("完成", (d, w) -> {
                    try { Prefs.get(this).setSkipIntroSec(Integer.parseInt(etIntro.getText().toString())); }
                    catch (Exception ignored) { }
                    try { Prefs.get(this).setSkipOutroSec(Integer.parseInt(etOutro.getText().toString())); }
                    catch (Exception ignored) { }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyMiniResize(int idx) {
        if (miniPlayerView == null) return;
        switch (idx) {
            case 1: miniPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL); break;  // 拉伸
            case 2: miniPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;  // 填充
            case 3: miniPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;  // 缩放
            default: miniPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;  // 默认
        }
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView chip(String s, boolean selected) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setTextColor(selected ? Color.WHITE : 0xFF9AA3AF);
        t.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void applyBrightness(int b) {
        Prefs.get(this).setBrightness(b);
        float f = Math.max(0.01f, b / 100f);
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = f;
        getWindow().setAttributes(lp);
    }

    private static abstract class SimpleSeek implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(android.widget.SeekBar s) { }
        @Override public void onStopTrackingTouch(android.widget.SeekBar s) { }
    }

    private static String format(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, m = s / 60, h = m / 60;
        if (h > 0) return String.format(java.util.Locale.CHINA, "%d:%02d:%02d", h, m % 60, s % 60);
        return String.format(java.util.Locale.CHINA, "%02d:%02d", m, s);
    }

    /** 小窗 → 全屏：记录当前进度，进入全屏播放器 */
    private void goFullscreen() {
        if (miniPlayer == null) return;
        long pos = miniPlayer.getCurrentPosition();
        String url = miniPlayer.getCurrentMediaItem() != null
                ? miniPlayer.getCurrentMediaItem().localConfiguration.uri.toString() : null;
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "暂无播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        miniPlayer.pause();        // 停小窗（保留冻结帧，交由全屏接管）
        miniResumePos = pos;
        returnFromFullscreen = true;
        // 传全线路给播放器（支持播放器内选集/线路切换）
        java.util.List<String> lnames = new java.util.ArrayList<>();
        java.util.List<java.util.List<String>> allLines = new java.util.ArrayList<>();
        for (VodDetail.PlayGroup gg : detail.groups) {
            lnames.add(gg.name == null ? "" : gg.name);
            java.util.List<String> gu = new java.util.ArrayList<>();
            for (VodDetail.PlayEpisode e : gg.episodes) gu.add(e.url);
            allLines.add(gu);
        }
        Ui.playWithLines(this, miniTitle, url, lnames, allLines, miniLine, miniEp, pos);
        // 淡入过渡，让全屏切换更丝滑
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 切后台：暂停小窗播放，切回来自动续播
        if (miniPlayer != null && miniPlayer.isPlaying()) {
            miniWasPlayingBeforePause = true;
            miniPlayer.pause();
            updateMiniIcon();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从全屏返回：恢复小窗按进度续播
        if (returnFromFullscreen && miniUrls != null && !miniUrls.isEmpty()) {
            returnFromFullscreen = false;
            if (RETURN_POS_MS > 0) { miniResumePos = RETURN_POS_MS; RETURN_POS_MS = 0; }
            String url = miniUrls.get(Math.min(miniEp, miniUrls.size() - 1));
            if (url != null && !url.trim().isEmpty()) playInMini(url);
            return;
        }
        // 切后台回来：若之前正在播放，自动继续
        if (miniWasPlayingBeforePause && miniPlayer != null) {
            miniWasPlayingBeforePause = false;
            miniPlayer.play();
            updateMiniIcon();
        }
    }

    private void releaseMini() {
        main.removeCallbacks(miniTicker);
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        if (miniPlayerView != null) miniPlayerView.setPlayer(null);
    }

    private void toggleFavorite() {
        Prefs prefs = Prefs.get(this);
        List<VodItem> fav = prefs.getFavorites();
        boolean exists = false;
        for (VodItem it : fav) {
            if (detail.vod_id != null && detail.vod_id.equals(it.vod_id)) { exists = true; break; }
        }
        if (exists) {
            List<VodItem> newList = new ArrayList<>();
            for (VodItem it : fav) if (!detail.vod_id.equals(it.vod_id)) newList.add(it);
            prefs.saveFavorites(newList);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            VodItem item = new VodItem();
            item.vod_id = detail.vod_id;
            item.vod_name = detail.vod_name;
            item.vod_pic = detail.vod_pic;
            item.vod_year = detail.vod_year;
            item.vod_type_name = detail.vod_type_name;
            item.vod_remarks = detail.vod_remarks;
            fav.add(item);
            prefs.saveFavorites(fav);
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        boolean fav = false;
        for (VodItem it : Prefs.get(this).getFavorites()) {
            if (detail != null && detail.vod_id != null && detail.vod_id.equals(it.vod_id)) { fav = true; break; }
        }
        btnFavorite.setText(fav ? "取消收藏" : "收藏影片");
    }

    private class LineAdapter extends RecyclerView.Adapter<LineAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_line, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            VodDetail.PlayGroup g = detail.groups.get(position);
            h.tv.setText(g.name);
            h.tv.setBackgroundResource(lineSelected.get(position)
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            h.tv.setTextColor(lineSelected.get(position) ? Color.WHITE : 0xFF9AA3AF);
            h.itemView.setOnClickListener(v -> {
                if (position != currentGroup) {
                    currentGroup = position;
                    for (int i = 0; i < lineSelected.size(); i++) lineSelected.set(i, i == position);
                    notifyDataSetChanged();
                    rvEpisodes.setAdapter(new EpisodeAdapter(position));
                    // 切换到该线路并播放其第一集
                    play(position, 0);
                }
            });
        }

        @Override
        public int getItemCount() { return detail.groups.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull View v) { super(v); tv = v.findViewById(R.id.tv_line); }
        }
    }

    private class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.VH> {
        private final int groupIndex;

        EpisodeAdapter(int groupIndex) { this.groupIndex = groupIndex; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            if (detail.groups.isEmpty() || groupIndex >= detail.groups.size()) return;
            VodDetail.PlayGroup g = detail.groups.get(groupIndex);
            if (position >= g.episodes.size()) return;
            VodDetail.PlayEpisode ep = g.episodes.get(position);
            h.tv.setText(ep.name == null || ep.name.isEmpty() ? ("第" + (position + 1) + "集") : ep.name);
            h.itemView.setOnClickListener(v -> play(groupIndex, position));
        }

        @Override
        public int getItemCount() {
            if (detail == null || detail.groups.isEmpty() || groupIndex >= detail.groups.size()) return 0;
            VodDetail.PlayGroup g = detail.groups.get(groupIndex);
            return g == null || g.episodes == null ? 0 : g.episodes.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull View v) { super(v); tv = v.findViewById(R.id.tv_episode); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMini();
        exec.shutdown();
    }
}
