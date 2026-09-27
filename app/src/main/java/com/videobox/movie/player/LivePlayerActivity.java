package com.videobox.movie.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.M3uParser;
import com.videobox.movie.ui.LiveBlockActivity;
import com.videobox.movie.ui.LiveSourceActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** IPTV 全屏直播播放器：点电视台直接全屏、手势切频道、频道面板、设置菜单、多线路自动切换 */
public class LivePlayerActivity extends Activity {

    /** 合并去重后的频道：一个台含多条线路 */
    private static class LiveChannel {
        String name;
        String group;
        final List<String> urls = new ArrayList<>();
        int curUrl = 0;
        LiveChannel(String name, String group) {
            this.name = name;
            this.group = group;
        }
    }

    private Prefs prefs;
    private PlayerView playerView;
    private ExoPlayer player;

    private FrameLayout root;
    private View clickCatcher;
    private FrameLayout loadingOverlay;
    private LinearLayout topBar;
    private TextView tvName;
    private TextView tvSpeed;
    private LinearLayout panel;
    private HorizontalScrollView groupScroll;
    private LinearLayout channelCol;

    private GestureDetector gestureDetector;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<LiveChannel> all = new ArrayList<>();
    private List<String> groups = new ArrayList<>();
    private int curGroup = 0;
    private int curIndex = 0;

    private boolean panelVisible = false;
    private boolean showSpeed = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        all = loadChannels();
        if (all.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("电视台")
                    .setMessage("尚未配置电视台源，是否去「设置 → 电视台管理」配置 m3u 或单条直播流？")
                    .setCancelable(false)
                    .setPositiveButton("去配置", (d, w) -> {
                        startActivity(new Intent(this, LiveSourceActivity.class));
                        finish();
                    })
                    .setNegativeButton("返回", (d, w) -> finish())
                    .show();
            return;
        }
        buildGroups();

        enterImmersive();
        buildUi();
        playChannel(0);
        startSpeedTicker();
    }

    /** 解析频道并按"分组+台名"去重合并线路，过滤被屏蔽分组与空地址 */
    private List<LiveChannel> loadChannels() {
        Map<String, LiveChannel> map = new LinkedHashMap<>();
        for (M3uParser.Channel c : prefs.getLiveChannels()) {
            if (c == null || c.url == null || c.url.trim().isEmpty()) continue;
            if (prefs.isLiveGroupBlocked(c.group)) continue;
            String name = (c.name == null || c.name.trim().isEmpty()) ? "频道" : c.name.trim();
            String group = (c.group == null || c.group.trim().isEmpty()) ? "全部" : c.group.trim();
            String key = group + "\u0000" + name;
            LiveChannel lc = map.get(key);
            if (lc == null) {
                lc = new LiveChannel(name, group);
                map.put(key, lc);
            }
            if (!lc.urls.contains(c.url.trim())) lc.urls.add(c.url.trim());
        }
        return new ArrayList<>(map.values());
    }

    private void buildGroups() {
        Set<String> set = new LinkedHashSet<>();
        for (LiveChannel lc : all) {
            if (lc.group.contains("更新时间")) continue; // 过滤占位垃圾分组
            set.add(lc.group);
        }
        groups = new ArrayList<>();
        groups.add("全部");
        groups.addAll(new ArrayList<>(set));
        curGroup = 0;
    }

    // ---------------- UI ----------------

    private void enterImmersive() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, matchParent());

        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(0xCC000000);
        TextView lt = new TextView(this);
        lt.setText("直播加载中…");
        lt.setTextColor(Color.WHITE);
        lt.setTextSize(16);
        loadingOverlay.addView(lt, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        root.addView(loadingOverlay, matchParent());

        clickCatcher = new View(this);
        clickCatcher.setClickable(true);
        root.addView(clickCatcher, matchParent());

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                togglePanel();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (Math.abs(velocityY) > Math.abs(velocityX)) {
                    if (velocityY < 0) switchNext();   // 上滑 → 下一个频道
                    else switchPrev();                 // 下滑 → 上一个频道
                    return true;
                }
                return false;
            }
        });
        clickCatcher.setOnTouchListener((v, e) -> gestureDetector.onTouchEvent(e));

        buildTopBar();
        buildPanel();

        setContentView(root);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(12), dp(8), dp(8));
        topBar.setBackgroundColor(0x55000000);

        ImageView back = icon(R.drawable.ic_back, 0xFFECEFF4);
        back.setOnClickListener(v -> finish());
        topBar.addView(back, lpIconSmall());

        tvName = new TextView(this);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(16);
        tvName.setPadding(dp(8), 0, 0, 0);
        topBar.addView(tvName, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tvSpeed = new TextView(this);
        tvSpeed.setText("");
        tvSpeed.setTextColor(0xFFB9C2CC);
        tvSpeed.setTextSize(12);
        topBar.addView(tvSpeed, lpIconSmall());

        ImageView btnSettings = icon(R.drawable.ic_settings, 0xFFECEFF4);
        btnSettings.setOnClickListener(v -> showSettings());
        topBar.addView(btnSettings, lpIconSmall());

        root.addView(topBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xCC11151C);
        panel.setVisibility(View.GONE);

        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setPadding(dp(12), dp(8), dp(12), dp(4));

        TextView head = new TextView(this);
        head.setText("频道");
        head.setTextColor(0xFFECEFF4);
        head.setTextSize(15);
        headRow.addView(head, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnLine = new TextView(this);
        btnLine.setText("换源");
        btnLine.setTextColor(0xFF3E9BFF);
        btnLine.setTextSize(13);
        btnLine.setPadding(dp(12), dp(6), dp(12), dp(6));
        btnLine.setBackgroundResource(R.drawable.bg_card);
        btnLine.setOnClickListener(v -> manualSwitchLine());
        headRow.addView(btnLine);

        panel.addView(headRow, wrap());
        groupScroll = new HorizontalScrollView(this);
        groupScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout groupRow = new LinearLayout(this);
        groupRow.setOrientation(LinearLayout.HORIZONTAL);
        groupRow.setPadding(dp(8), 0, dp(8), 0);
        groupScroll.addView(groupRow);
        panel.addView(groupScroll, wrap());

        channelCol = new LinearLayout(this);
        channelCol.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.addView(channelCol);
        panel.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(190), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP);
        lp.topMargin = dp(64);
        root.addView(panel, lp);
    }

    private void selectGroup(int gi) {
        curGroup = gi;
        LinearLayout row = (LinearLayout) groupScroll.getChildAt(0);
        if (row == null) return;
        row.removeAllViews();
        for (int i = 0; i < groups.size(); i++) {
            final int fi = i;
            TextView g = new TextView(this);
            g.setText(groups.get(i));
            g.setTextSize(13);
            g.setPadding(dp(12), dp(7), dp(12), dp(7));
            g.setBackgroundResource(fi == curGroup ? R.drawable.bg_chip_selected : R.drawable.bg_card);
            g.setTextColor(fi == curGroup ? Color.WHITE : 0xFFB9C2CC);
            g.setOnClickListener(v -> selectGroup(fi));
            row.addView(g);
        }
        renderChannelsForGroup();
    }

    private void renderChannelsForGroup() {
        channelCol.removeAllViews();
        String g = groups.get(curGroup);
        for (LiveChannel lc : all) {
            if (!"全部".equals(g) && !lc.group.equals(g)) continue;
            TextView row = new TextView(this);
            String extra = lc.urls.size() > 1 ? "  (" + lc.urls.size() + "源)" : "";
            row.setText(lc.name + extra);
            row.setTextSize(14);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            int idx = all.indexOf(lc);
            row.setTextColor(idx == curIndex ? 0xFF3E9BFF : 0xFFECEFF4);
            row.setOnClickListener(v -> {
                int ii = all.indexOf(lc);
                if (ii >= 0) playChannel(ii);
            });
            channelCol.addView(row, wrap());
        }
    }

    private void togglePanel() {
        panelVisible = !panelVisible;
        panel.setVisibility(panelVisible ? View.VISIBLE : View.GONE);
        if (panelVisible) renderChannelsForGroup();
    }

    private void switchNext() {
        if (all.isEmpty()) return;
        playChannel((curIndex + 1) % all.size());
    }

    private void switchPrev() {
        if (all.isEmpty()) return;
        playChannel((curIndex - 1 + all.size()) % all.size());
    }

    private void playChannel(int idx) {
        if (idx < 0 || idx >= all.size()) return;
        curIndex = idx;
        LiveChannel lc = all.get(idx);
        lc.curUrl = 0;
        updateName();
        if (panelVisible) renderChannelsForGroup();
        initPlayer(lc.urls.get(0));
    }

    private void updateName() {
        LiveChannel lc = all.get(curIndex);
        if (lc.urls.size() > 1) {
            tvName.setText(lc.name + "（线路 " + (lc.curUrl + 1) + "/" + lc.urls.size() + "）");
        } else {
            tvName.setText(lc.name);
        }
    }

    // ---------------- 设置菜单 ----------------

    private void showSettings() {
        final String[] decModes = {"智能解码", "IJK硬解", "原生解码"};
        final String[] scrModes = {"原始", "4:3", "16:9", "全屏"};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        box.addView(settingRow("解码模式", decModes[prefs.getDecodeMode()],
                () -> new AlertDialog.Builder(this)
                        .setTitle("解码模式")
                        .setSingleChoiceItems(decModes, prefs.getDecodeMode(), (d, w) -> {
                            prefs.setDecodeMode(w);
                            d.dismiss();
                            LiveChannel lc = all.get(curIndex);
                            initPlayer(lc.urls.get(lc.curUrl));
                        })
                        .setNegativeButton("取消", null)
                        .show()));
        box.addView(settingRow("屏幕比例", scrModes[prefs.getLiveScreenMode()],
                () -> new AlertDialog.Builder(this)
                        .setTitle("屏幕比例")
                        .setSingleChoiceItems(scrModes, prefs.getLiveScreenMode(), (d, w) -> {
                            prefs.setLiveScreenMode(w);
                            d.dismiss();
                            applyScreenMode();
                        })
                        .setNegativeButton("取消", null)
                        .show()));
        box.addView(settingRow("显示网速", showSpeed ? "显示" : "隐藏",
                () -> {
                    showSpeed = !showSpeed;
                    tvSpeed.setVisibility(showSpeed ? View.VISIBLE : View.GONE);
                    if (!showSpeed) tvSpeed.setText("");
                }));
        box.addView(settingRow("屏蔽分类", "忽略电信等分组",
                () -> startActivity(new Intent(this, LiveBlockActivity.class))));
        box.addView(settingRow("数据管理", "配置 m3u / 导入 / 单条流",
                () -> startActivity(new Intent(this, LiveSourceActivity.class))));

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("关闭", null)
                .show();
    }

    /** 按用户选的屏幕比例应用：0原始 1 4:3 2 16:9 3全屏 */
    private void applyScreenMode() {
        if (playerView == null) return;
        int sm = prefs.getLiveScreenMode();
        // 通过内部 AspectRatioFrameLayout 设置画面比例
        AspectRatioFrameLayout arf = playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        try {
            switch (sm) {
                case 1:
                    if (arf != null) arf.setAspectRatio(4f / 3f);
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    break;
                case 2:
                    if (arf != null) arf.setAspectRatio(16f / 9f);
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    break;
                case 3:
                    if (arf != null) arf.setAspectRatio(0);
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    break;
                default:
                    if (arf != null) arf.setAspectRatio(0);
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    break;
            }
        } catch (Exception ignored) {
            // 保底：至少切到对应拉伸方式
            if (sm == 3) playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            else playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
    }

    private LinearLayout settingRow(String title, String desc, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setClickable(true);
        row.setOnClickListener(v -> onClick.run());
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(0xFFECEFF4);
        row.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(0xFF8A93A0);
        row.addView(d);
        return row;
    }

    // ---------------- 播放器 ----------------

    private LoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2000, 20000, 800, 2000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private void initPlayer(String url) {
        if (player != null) {
            player.release();
            player = null;
        }
        boolean adBlock = prefs.isAdBlockEnabled();
        final List<String> keywords = prefs.getAdKeywords();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
        DataSource.Factory dsFactory = adBlock
                ? () -> new FilteringDataSource(http.createDataSource(), keywords)
                : http;

        androidx.media3.exoplayer.DefaultRenderersFactory rf =
                new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                        .setEnableDecoderFallback(true);
        // 解码模式：0智能=硬解+软解回退；1 IJK硬解=仅硬解(更快失败触发换线路)；2 原生解码=软解优先
        int dm = prefs.getDecodeMode();
        if (dm == 1) {
            rf.setEnableDecoderFallback(false); // 强制硬解，不回退软解
        }

        player = new ExoPlayer.Builder(this, rf)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
                .setLoadControl(buildLoadControl())
                .build();
        playerView.setPlayer(player);
        applyScreenMode();

        MediaItem item = MediaItem.fromUri(url);
        player.setMediaItem(item);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    loadingOverlay.setVisibility(View.VISIBLE);
                    scheduleTimeout();
                } else if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    loadingOverlay.setVisibility(View.GONE);
                    handler.removeCallbacks(timeoutRunnable);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                    boolean vertical = videoSize.height > videoSize.width;
                    setRequestedOrientation(vertical
                            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                loadingOverlay.setVisibility(View.GONE);
                handler.removeCallbacks(timeoutRunnable);
                tryNextLine("线路 " + (all.get(curIndex).curUrl + 1) + " 加载失败");
            }
        });
    }

    /** 缓冲超时后自动切下一线路，避免一直转圈不换源 */
    private Runnable timeoutRunnable;
    private void scheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable);
        timeoutRunnable = () -> {
            if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
                tryNextLine("线路加载超时");
            }
        };
        handler.postDelayed(timeoutRunnable, 10000);
    }

    /** 尝试当前台的下一条线路；无线路可切则提示换台 */
    private void tryNextLine(String reason) {
        LiveChannel lc = all.get(curIndex);
        if (lc.curUrl + 1 < lc.urls.size()) {
            lc.curUrl++;
            updateName();
            loadingOverlay.setVisibility(View.VISIBLE);
            Toast.makeText(this, reason + "，已自动切换线路 " + (lc.curUrl + 1)
                    + "/" + lc.urls.size(), Toast.LENGTH_SHORT).show();
            handler.post(() -> initPlayer(lc.urls.get(lc.curUrl)));
        } else {
            loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "该台所有线路均失败，上滑/下滑可换台", Toast.LENGTH_LONG).show();
        }
    }

    /** 手动换源：强制切到当前台的下一条线路（循环），不等待失败 */
    private void manualSwitchLine() {
        LiveChannel lc = all.get(curIndex);
        if (lc.urls.size() <= 1) {
            Toast.makeText(this, "该台仅一条线路", Toast.LENGTH_SHORT).show();
            return;
        }
        lc.curUrl = (lc.curUrl + 1) % lc.urls.size();
        updateName();
        loadingOverlay.setVisibility(View.VISIBLE);
        Toast.makeText(this, "手动切换到线路 " + (lc.curUrl + 1) + "/" + lc.urls.size(),
                Toast.LENGTH_SHORT).show();
        initPlayer(lc.urls.get(lc.curUrl));
    }

    private void startSpeedTicker() {
        final long[] lastRx = { -1 };
        final long[] lastTime = { 0 };
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (tvSpeed != null) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    long rx = android.net.TrafficStats.getTotalRxBytes();
                    if (lastRx[0] >= 0 && now - lastTime[0] > 0) {
                        long bps = (rx - lastRx[0]) * 1000L / (now - lastTime[0]);
                        tvSpeed.setText(formatBps(bps));
                    }
                    lastRx[0] = rx;
                    lastTime[0] = now;
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    private String formatBps(long bps) {
        float kbps = bps / 1024f;
        if (kbps >= 1024) {
            return String.format("%.1fMB/s", kbps / 1024f);
        }
        return (int) kbps + "KB/s";
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 切到后台自动暂停
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 切回来自动继续播放
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpIconSmall() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private ImageView icon(int res, int tint) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        return iv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
