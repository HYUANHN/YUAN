package com.videobox.movie.player;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.ApiClient;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SIZES = {"默认", "拉伸", "填充", "缩放", "16:9", "4:3", "全屏", "小屏"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTimeBar timeBar;
    private TextView tvPosition, tvDuration, tvPlayerTitle, btnSpeed;
    private LinearLayout topBar, bottomBar, speedGroup, sizeGroup;
    private android.widget.ScrollView settingsPanel;
    private ImageView btnPlayPause, ivCenterPlay, btnFullscreen;
    private View btnCenterPlay;
    private EditText etSkipIntro, etSkipOutro, etLongPressSpeed;
    private SeekBar sbVolume, sbBrightness;
    private TextView tvVolumeValue, tvBrightnessValue, btnLongPress;
    private Switch swHardware;
    private View btnSelectEpisode, btnPrevEp, btnNextEp;
    private View gestureOverlay;
    private View loadingOverlay;
    private ImageView ivGestureIcon;
    private TextView tvGestureText;
    private View episodePanel;
    private TextView btnCloseEpisode;
    private RecyclerView rvEpisodeGrid;
    private EpisodeGridAdapter episodeGridAdapter;
    private android.media.AudioManager audio;

    private float currentSpeed = 1.0f;
    private int currentSize = 0;
    private boolean longPressOn = true;
    private float longPressSpeed = 1.5f;
    private boolean longPressActive = false;
    private long skipIntroMs = 0;
    private long skipOutroMs = 0;
    private boolean barsVisible = true;
    private boolean isFullscreen = false;
    /** 每个新剧集首次加载时自动全屏一次；之后手动缩小则不再自动弹回全屏 */
    private boolean autoFullscreenDone = false;
    private float normalSpeed = 1.0f;   // 长按加速前速度
    private long startPosMs = 0;        // 全屏续播位置
    private boolean startPosApplied = false; // seek 只执行一次，避免 READY 反复 seek 造成循环暂停
    private String currentUrl = "";
    private java.util.List<String> episodeUrls = new java.util.ArrayList<>();
    private int episodeIndex = 0;
    // 多线路：每条线路名 + 各自剧集地址列表
    private java.util.List<String> lineNames = new java.util.ArrayList<>();
    private java.util.List<java.util.List<String>> allLineUrls = new java.util.ArrayList<>();
    private int currentLine = 0;
    private TextView btnLine;
    private View linePanel;
    private TextView btnCloseLine;
    private RecyclerView rvLineGrid;
    private LineGridAdapter lineGridAdapter;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                longPressActive = true;
                longPressDir = gestureLeftHalf ? -1 : 1; // 左半=快退，右半=快进
                if (longPressDir < 0) {
                    // 左：快退（seek 循环，保持播放不暂停）
                    showGesture(R.drawable.ic_prev, "快退中 " + speedText(longPressSpeed) + "x");
                    handler.removeCallbacks(longPressTickRunnable);
                    handler.post(longPressTickRunnable);
                } else {
                    // 右：快进（真实倍速播放，视频继续播放）
                    normalSpeed = currentSpeed;
                    player.setPlaybackSpeed(longPressSpeed);
                    showGesture(R.drawable.ic_next, "快进中 " + speedText(longPressSpeed) + "x");
                }
            }
        }
    };

    /** 长按左半时按 longPressSpeed 倍数循环快退（每 200ms 回退 longPressSpeed 秒），保持播放 */
    private final Runnable longPressTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!longPressActive || player == null) return;
            if (longPressDir >= 0) return; // 仅快退使用
            long dur = player.getDuration();
            if (dur > 0) {
                long step = (long) (longPressSpeed * 1000 * 0.2);
                long target = clamp(player.getCurrentPosition() - step, 0, dur);
                player.seekTo(target);
                // seek 后可能进入缓冲暂停态，立即恢复播放，避免"暂停"卡住
                if (!player.isPlaying()) player.play();
                tvPosition.setText(format(target));
                showGesture(R.drawable.ic_prev, "快退 " + format(target) + " / " + format(dur));
            }
            handler.postDelayed(this, 200);
        }
    };

    /** 单击（无双击时）呼出/隐藏控制条 */
    private final Runnable singleTapRunnable = new Runnable() {
        @Override
        public void run() {
            lastTapTime = 0;
            if (episodePanel.getVisibility() == View.VISIBLE) {
                hideEpisodePanel(); // 选集面板打开时轻点视频=关闭面板
            } else {
                toggleBars(); // 轻点：呼出/隐藏控制条
            }
        }
    };
    private long lastTapTime = 0;
    private int longPressDir = 1;

    // 手势：0=无 1=上下(亮度/音量) 2=左右(快进/快退)
    private int gestureMode = 0;
    private float gestureStartX = 0, gestureStartY = 0;
    private long gestureStartPosMs = 0;
    private float gestureStartBright = 0, gestureStartVol = 0;
    private boolean gestureLeftHalf = false;
    private final Runnable hideOverlayRunnable = () -> {
        if (gestureOverlay != null) gestureOverlay.setVisibility(View.GONE);
    };

    /** 显示手势反馈浮层 */
    private void showGesture(int iconRes, String text) {
        if (gestureOverlay == null) return;
        ivGestureIcon.setImageResource(iconRes);
        tvGestureText.setText(text);
        gestureOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlayRunnable);
    }

    /** 显示后延迟隐藏（结束调节后调用） */
    private void hideGestureDelayed() {
        handler.removeCallbacks(hideOverlayRunnable);
        handler.postDelayed(hideOverlayRunnable, 900);
    }

    private int deviceVolumePct() {
        if (audio == null) return 0;
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        return max > 0 ? cur * 100 / max : 0;
    }

    /** 按系统媒体音量百分比设音量（真实可听变化） */
    private void setDeviceVolume(int pct) {
        if (audio == null) return;
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int target = (int) (pct / 100f * max);
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updatePosition();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setupPlayer();
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    private void setupPlayer() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        loadingOverlay = findViewById(R.id.loading_overlay);
        timeBar = findViewById(R.id.time_bar);
        tvPosition = findViewById(R.id.tv_position);
        tvDuration = findViewById(R.id.tv_duration);
        tvPlayerTitle = findViewById(R.id.tv_player_title);
        btnSpeed = findViewById(R.id.btn_speed);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        settingsPanel = findViewById(R.id.settings_panel);
        speedGroup = findViewById(R.id.speed_group);
        sizeGroup = findViewById(R.id.size_group);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnCenterPlay = findViewById(R.id.btn_center_play);
        ivCenterPlay = findViewById(R.id.iv_center_play);
        btnFullscreen = findViewById(R.id.btn_fullscreen);
        etSkipIntro = findViewById(R.id.et_skip_intro);
        etSkipOutro = findViewById(R.id.et_skip_outro);
        etLongPressSpeed = findViewById(R.id.et_long_press_speed);
        longPressSpeed = Prefs.get(this).getLongPressSpeed();
        etLongPressSpeed.setText(String.valueOf(longPressSpeed));
        sbVolume = findViewById(R.id.sb_volume);
        sbBrightness = findViewById(R.id.sb_brightness);
        tvVolumeValue = findViewById(R.id.tv_volume_value);
        tvBrightnessValue = findViewById(R.id.tv_brightness_value);
        btnLongPress = findViewById(R.id.btn_long_press);
        swHardware = findViewById(R.id.sw_hardware);
        btnSelectEpisode = findViewById(R.id.btn_select_episode);
        btnPrevEp = findViewById(R.id.btn_prev_ep);
        btnNextEp = findViewById(R.id.btn_next_ep);
        btnLine = findViewById(R.id.btn_line);
        linePanel = findViewById(R.id.line_panel);
        btnCloseLine = findViewById(R.id.btn_close_line);
        rvLineGrid = findViewById(R.id.rv_line_grid);
        rvLineGrid.setLayoutManager(new GridLayoutManager(this, 1));
        lineGridAdapter = new LineGridAdapter();
        rvLineGrid.setAdapter(lineGridAdapter);
        gestureOverlay = findViewById(R.id.gesture_overlay);
        ivGestureIcon = findViewById(R.id.iv_gesture_icon);
        tvGestureText = findViewById(R.id.tv_gesture_text);
        episodePanel = findViewById(R.id.episode_panel);
        btnCloseEpisode = findViewById(R.id.btn_close_episode);
        rvEpisodeGrid = findViewById(R.id.rv_episode_grid);
        rvEpisodeGrid.setLayoutManager(new GridLayoutManager(this, 3));
        episodeGridAdapter = new EpisodeGridAdapter();
        rvEpisodeGrid.setAdapter(episodeGridAdapter);
        audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        // 声音以系统媒体音量为主（手势直接可听变化），播放器音量拉满
        int dv = deviceVolumePct();
        sbVolume.setProgress(dv);
        tvVolumeValue.setText(String.valueOf(dv));

        // 剧集列表（来自详情页，用于选集/上一集下一集）
        java.util.List<String> eps = getIntent().getStringArrayListExtra("episodes");
        if (eps != null) episodeUrls = new java.util.ArrayList<>(eps);
        episodeIndex = getIntent().getIntExtra("episode_index", 0);
        boolean hasEpisodes = !episodeUrls.isEmpty();
        // 多线路数据（线路名 + 各线路剧集）
        java.util.ArrayList<String> lnames = getIntent().getStringArrayListExtra("line_names");
        if (lnames != null) lineNames = new java.util.ArrayList<>(lnames);
        java.util.ArrayList<java.util.ArrayList<String>> lurls =
                (java.util.ArrayList<java.util.ArrayList<String>>) getIntent().getSerializableExtra("lines");
        if (lurls != null) {
            allLineUrls.clear();
            for (java.util.ArrayList<String> a : lurls) allLineUrls.add(new java.util.ArrayList<>(a));
        }
        currentLine = getIntent().getIntExtra("line_index", 0);
        if (lineNames.isEmpty() && allLineUrls.isEmpty() && !episodeUrls.isEmpty()) {
            lineNames.add("当前线路");
            allLineUrls.add(new java.util.ArrayList<>(episodeUrls));
        }
        btnSelectEpisode.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        btnPrevEp.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        btnNextEp.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        btnLine.setVisibility(View.VISIBLE);

        tvPlayerTitle.setText(getIntent().getStringExtra("title"));

        startPosMs = getIntent().getLongExtra("start_pos", 0); // 全屏续播位置

        String url = getIntent().getStringExtra("url");
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "播放地址为空，请回详情页重选", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 从预读设置初始化
        currentSpeed = Prefs.get(this).getPlaybackSpeed();
        skipIntroMs = Prefs.get(this).getSkipIntroSec() * 1000;
        skipOutroMs = Prefs.get(this).getSkipOutroSec() * 1000;
        etSkipIntro.setText(String.valueOf(Prefs.get(this).getSkipIntroSec()));
        etSkipOutro.setText(String.valueOf(Prefs.get(this).getSkipOutroSec()));
        // 音量以当前系统媒体音量为准（小窗/手势改的都是系统音量），不再用 Prefs 覆盖避免重置为100
        sbBrightness.setProgress(Prefs.get(this).getBrightness());
        tvBrightnessValue.setText(String.valueOf(sbBrightness.getProgress()));
        swHardware.setChecked(Prefs.get(this).isHardwareDecode());
        applyBrightness();
        applyVolume();

        buildSpeedChips();
        buildSizeChips();
        bindControls(url);
        currentUrl = url;
        initPlayer(url);
    }

    /** 播放初始化出错时，把具体异常弹出来并写日志，而不是直接闪退 */
    private void showFatal(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.CHINA).format(new java.util.Date()) + " ===");
            t.printStackTrace(pw);
            pw.flush();
            java.io.File f = new java.io.File(getFilesDir(), "crash.log");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f, true);
            fos.write(sw.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Exception ignored) { }

        final String msg = (t.getClass().getSimpleName()) + ": " + t.getMessage()
                + "\n\n" + (t.getStackTrace() != null && t.getStackTrace().length > 0
                    ? t.getStackTrace()[0].toString() : "");
        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("播放初始化出错")
                    .setMessage(msg + "\n\n已记录到 crash.log，请反馈这段文字给开发者")
                    .setPositiveButton("知道了", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        });
    }

    private void bindControls(String url) {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_settings).setOnClickListener(v -> toggleSettingsPanel());
        findViewById(R.id.btn_close_settings).setOnClickListener(v -> toggleSettingsPanel());
        findViewById(R.id.btn_apply_settings).setOnClickListener(v -> applySettings());
        btnPlayPause.setOnClickListener(v -> togglePlay());
        btnCenterPlay.setOnClickListener(v -> togglePlay());
        btnSpeed.setOnClickListener(v -> toggleSettingsPanel());
        btnSpeed.setText("倍速 " + speedText(currentSpeed));
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());
        btnSelectEpisode.setOnClickListener(v -> showEpisodePanel());
        btnPrevEp.setOnClickListener(v -> playEpisode(episodeIndex - 1));
        btnNextEp.setOnClickListener(v -> playEpisode(episodeIndex + 1));
        btnCloseEpisode.setOnClickListener(v -> hideEpisodePanel());
        btnLine.setOnClickListener(v -> showLinePanel());
        btnCloseLine.setOnClickListener(v -> hideLinePanel());

        playerView.setOnTouchListener((v, e) -> handleGesture(v, e));

        // 声音
        sbVolume.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvVolumeValue.setText(String.valueOf(p));
                applyVolume();
            }
        });
        // 亮度
        sbBrightness.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                tvBrightnessValue.setText(String.valueOf(p));
                applyBrightness();
            }
        });
        // 长按加速开关
        btnLongPress.setOnClickListener(v -> {
            longPressOn = !longPressOn;
            btnLongPress.setBackgroundResource(longPressOn ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            btnLongPress.setTextColor(longPressOn ? Color.WHITE : 0xFF9AA3AF);
            btnLongPress.setText(longPressOn ? "开" : "关");
        });
        btnLongPress.setText(longPressOn ? "开" : "关");
        btnLongPress.setTextColor(0xFF9AA3AF);

        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(@NonNull TimeBar t, long positionMs) {
                handler.removeCallbacks(ticker);
            }

            @Override
            public void onScrubMove(@NonNull TimeBar t, long positionMs) {
                tvPosition.setText(format(positionMs));
            }

            @Override
            public void onScrubStop(@NonNull TimeBar t, long positionMs, boolean canceled) {
                if (player != null) player.seekTo(positionMs);
                startTicker();
            }
        });
    }

    /** 屏幕手势：长按=倍速；上下=左半亮度/右半音量；左右=按总时长快进快退；轻点=呼出/隐藏控制条 */
    private boolean handleGesture(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureMode = 0;
                gestureStartX = e.getX();
                gestureStartY = e.getY();
                gestureLeftHalf = e.getX() < v.getWidth() / 2f;
                if (player != null) gestureStartPosMs = player.getCurrentPosition();
                gestureStartBright = sbBrightness.getProgress();
                gestureStartVol = deviceVolumePct();
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(longPressTickRunnable);
                handler.removeCallbacks(singleTapRunnable);
                if (longPressOn) handler.postDelayed(longPressRunnable, 400);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - gestureStartX;
                float dy = e.getY() - gestureStartY;
                if (gestureMode == 0 && (Math.abs(dx) > 24 || Math.abs(dy) > 24)) {
                    handler.removeCallbacks(longPressRunnable); // 滑动了，取消长按
                    handler.removeCallbacks(longPressTickRunnable);
                    gestureMode = (Math.abs(dy) >= Math.abs(dx)) ? 1 : 2;
                }
                if (gestureMode == 1) {
                    float delta = -dy / v.getHeight();
                    if (gestureLeftHalf) {
                        int nb = (int) (gestureStartBright + delta * 100);
                        nb = clamp(nb, 0, 100);
                        sbBrightness.setProgress(nb);
                        tvBrightnessValue.setText(String.valueOf(nb));
                        applyBrightness();
                        showGesture(R.drawable.ic_brightness, "亮度 " + nb + "%");
                    } else {
                        int nv = (int) (gestureStartVol + delta * 100);
                        nv = clamp(nv, 0, 100);
                        setDeviceVolume(nv);
                        sbVolume.setProgress(nv);
                        tvVolumeValue.setText(String.valueOf(nv));
                        showGesture(R.drawable.ic_volume, "音量 " + nv + "%");
                    }
                } else if (gestureMode == 2 && player != null) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        float ratio = dx / v.getWidth();
                        long target = gestureStartPosMs + (long) (ratio * dur);
                        target = clamp(target, 0, dur);
                        player.seekTo(target);
                        tvPosition.setText(format(target));
                        showGesture(R.drawable.ic_next, format(target) + " / " + format(dur));
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(longPressTickRunnable);
                if (gestureMode == 0) {
                    if (longPressActive) {
                        // 长按结束：快进恢复原速 / 快退停止循环
                        if (longPressDir >= 0 && player != null) {
                            player.setPlaybackSpeed(normalSpeed > 0 ? normalSpeed : currentSpeed);
                        }
                        longPressActive = false;
                        normalSpeed = 0;
                        handler.removeCallbacks(longPressTickRunnable);
                        hideGestureDelayed();
                    } else {
                        // 双击检测：300ms 内第二次点击 = 暂停/播放
                        long now = System.currentTimeMillis();
                        if (now - lastTapTime < 300) {
                            handler.removeCallbacks(singleTapRunnable);
                            lastTapTime = 0;
                            togglePlay();
                        } else {
                            lastTapTime = now;
                            handler.removeCallbacks(singleTapRunnable);
                            handler.postDelayed(singleTapRunnable, 300);
                        }
                    }
                } else {
                    hideGestureDelayed();
                }
                gestureMode = 0;
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(longPressTickRunnable);
                handler.removeCallbacks(singleTapRunnable);
                if (longPressActive) {
                    if (longPressDir >= 0 && player != null) {
                        player.setPlaybackSpeed(normalSpeed > 0 ? normalSpeed : currentSpeed);
                    }
                    longPressActive = false;
                    normalSpeed = 0;
                }
                lastTapTime = 0;
                hideGestureDelayed();
                gestureMode = 0;
                return true;
            default:
                return false;
        }
    }

    private static int clamp(int v, int min, int max) { return v < min ? min : (v > max ? max : v); }
    private static long clamp(long v, long min, long max) { return v < min ? min : (v > max ? max : v); }

    // ---------- 选集 / 上一集下一集 ----------
    private void playEpisode(int index) {
        if (episodeUrls.isEmpty()) return;
        if (index < 0) { Toast.makeText(this, "已经是第一集", Toast.LENGTH_SHORT).show(); return; }
        if (index >= episodeUrls.size()) { Toast.makeText(this, "已经是最后一集", Toast.LENGTH_SHORT).show(); return; }
        String url = episodeUrls.get(index);
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "该集播放地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        episodeIndex = index;
        currentUrl = url;
        initPlayer(url);
        Toast.makeText(this, "第" + (index + 1) + "集", Toast.LENGTH_SHORT).show();
    }

    private void showEpisodePanel() {
        if (episodeUrls.isEmpty()) return;
        // 互斥：打开选集时先收起线路，避免两个面板叠加
        hideLinePanel();
        episodeGridAdapter.notifyDataSetChanged();
        episodePanel.setVisibility(View.VISIBLE);
    }

    private void hideEpisodePanel() {
        episodePanel.setVisibility(View.GONE);
    }

    private void showLinePanel() {
        if (allLineUrls.isEmpty()) return;
        // 互斥：打开线路时先收起选集，避免两个面板叠加
        hideEpisodePanel();
        lineGridAdapter.notifyDataSetChanged();
        linePanel.setVisibility(View.VISIBLE);
    }

    private void hideLinePanel() {
        linePanel.setVisibility(View.GONE);
    }

    /** 切换到指定线路：更新选集列表并播放该线路第一集 */
    private void switchLine(int i) {
        if (i < 0 || i >= allLineUrls.size()) return;
        if (i == currentLine) { hideLinePanel(); return; }
        java.util.List<String> urls = allLineUrls.get(i);
        if (urls.isEmpty()) {
            Toast.makeText(this, "该线路暂无剧集", Toast.LENGTH_SHORT).show();
            return;
        }
        currentLine = i;
        episodeUrls = new java.util.ArrayList<>(urls);
        episodeIndex = 0;
        if (episodeGridAdapter != null) episodeGridAdapter.notifyDataSetChanged();
        hideLinePanel();
        String url = episodeUrls.get(0);
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "该线路第一集地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUrl = url;
        Toast.makeText(this, "线路：" + lineName(i) + "，第1集", Toast.LENGTH_SHORT).show();
        initPlayer(url);
    }

    private String lineName(int i) {
        return (i >= 0 && i < lineNames.size() && lineNames.get(i) != null)
                ? lineNames.get(i) : ("线路" + (i + 1));
    }

    /** 右侧播放线路网格适配器：线路名，当前线路高亮 */
    private class LineGridAdapter extends RecyclerView.Adapter<LineGridAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(PlayerActivity.this);
            tv.setTextSize(14);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(dp(4), dp(10), dp(4), dp(10));
            tv.setBackgroundResource(R.drawable.bg_chip);
            tv.setTextColor(0xFFB8C1CC);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            tv.setLayoutParams(lp);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            boolean selected = position == currentLine;
            h.tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            h.tv.setTextColor(selected ? Color.WHITE : 0xFFB8C1CC);
            h.tv.setText(lineName(position));
            h.tv.setOnClickListener(v -> switchLine(position));
        }

        @Override
        public int getItemCount() { return allLineUrls.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull TextView v) { super(v); tv = v; }
        }
    }

    /** 右侧选集网格适配器：第X集按钮，当前集高亮 */
    private class EpisodeGridAdapter extends RecyclerView.Adapter<EpisodeGridAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(PlayerActivity.this);
            tv.setTextSize(14);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(dp(4), dp(10), dp(4), dp(10));
            tv.setBackgroundResource(R.drawable.bg_chip);
            tv.setTextColor(0xFFB8C1CC);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            tv.setLayoutParams(lp);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            boolean selected = position == episodeIndex;
            h.tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            h.tv.setTextColor(selected ? Color.WHITE : 0xFFB8C1CC);
            h.tv.setText("第" + (position + 1) + "集");
            h.tv.setOnClickListener(v -> {
                playEpisode(position);
                hideEpisodePanel();
            });
        }

        @Override
        public int getItemCount() { return episodeUrls.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull TextView v) { super(v); tv = v; }
        }
    }

    private void initPlayer(String url) {
        releasePlayer();
        autoFullscreenDone = false; // 新剧集，重新允许自动全屏一次
        startPosApplied = false;    // 新剧集允许再次 seek 续播
        // HTML 播放页（如 ffzy /share/）先异步解析出真实 m3u8，避免直接播 HTML 报错
        if (ApiClient.isSharePageUrl(url)) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            new Thread(() -> {
                final String resolved = ApiClient.resolvePlayUrl(url);
                runOnUiThread(() -> initPlayerResolved(resolved));
            }).start();
            return;
        }
        initPlayerResolved(url);
    }

    /** HLS 缓冲策略：快速起播 + 合理缓存，减少分享页解析流的转圈卡顿 */
    private LoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        1500,   // 起播前至少缓冲 1.5s
                        12000,  // 最大缓冲 12s，避免长时间无进度
                        500,    // 起播最小缓冲
                        1500)   // 卡顿重缓冲阈值
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    private void initPlayerResolved(String url) {
        boolean adBlock = Prefs.get(this).isAdBlockEnabled();
        final List<String> keywords = Prefs.get(this).getAdKeywords();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
        DataSource.Factory dsFactory = adBlock
                ? () -> new FilteringDataSource(http.createDataSource(), keywords)
                : http;

        DefaultRenderersFactory rf = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
        // 关闭硬件加速 = 兼容模式：允许解码回退，画面/声音不同步时可关闭硬件试
        if (!Prefs.get(this).isHardwareDecode()) {
            rf.setEnableDecoderFallback(true);
        }

        player = new ExoPlayer.Builder(this, rf)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dsFactory))
                .setLoadControl(buildLoadControl())
                .build();
        playerView.setPlayer(player);
        player.setPlaybackSpeed(currentSpeed);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        MediaItem item = MediaItem.fromUri(url);
        player.setMediaItem(item);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                }
                if (state == Player.STATE_READY) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        timeBar.setDuration(dur);
                        tvDuration.setText(" / " + format(dur));
                    }
                    if (!startPosApplied) {
                        startPosApplied = true;
                        if (skipIntroMs > 0) player.seekTo(skipIntroMs);
                        else if (startPosMs > 0) player.seekTo(startPosMs);
                    }
                    // 仅每个新剧集首次加载时自动全屏；手动缩小后不再弹回全屏
                    if (!isFullscreen && !autoFullscreenDone) {
                        enterFullscreen();
                        autoFullscreenDone = true;
                    }
                }
                if (state == Player.STATE_ENDED) {
                    tvPosition.setText(format(player.getDuration()));
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayIcon();
                if (isPlaying) startTicker(); else handler.removeCallbacks(ticker);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                // 全屏时按视频方向自动横/竖屏；画面尺寸尊重用户选择（默认=默认/FIT）
                if (isFullscreen && videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                    boolean vertical = videoSize.height > videoSize.width;
                    setRequestedOrientation(vertical
                            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                    applyResizeMode(currentSize);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(PlayerActivity.this,
                        "播放出错，可能该线路失效，请换线路尝试", Toast.LENGTH_LONG).show();
            }
        });

        applyResizeMode(currentSize);
        startTicker();
        updatePlayIcon();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // ---------- 音量 / 亮度 ----------
    private void applyVolume() {
        int pct = sbVolume.getProgress();
        Prefs.get(this).setVolume(pct / 100f);
        setDeviceVolume(pct);
        if (player != null) player.setVolume(1.0f); // 声音以系统媒体音量为准
    }

    private void applyBrightness() {
        int b = sbBrightness.getProgress();
        Prefs.get(this).setBrightness(b);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = b / 100f;
        getWindow().setAttributes(lp);
    }

    // ---------- 画面尺寸 / 全屏小屏 ----------
    private void applyResizeMode(int idx) {
        if (playerView == null) return;
        switch (idx) {
            case 0: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;   // 默认
            case 1: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL); break;  // 拉伸
            case 2: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;  // 填充
            case 3: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;  // 缩放
            case 4: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;   // 16:9
            case 5: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;   // 4:3
            case 6: // 全屏
            case 7: // 小屏
            default: playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;
        }
    }

    private void toggleFullscreen() {
        // 全屏时点"缩小"：记录进度并返回详情页（详情页小窗从该进度续播）
        if (isFullscreen) {
            if (player != null) {
                com.videobox.movie.ui.DetailActivity.RETURN_POS_MS = player.getCurrentPosition();
            }
            finish();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        isFullscreen = true;
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        // 全屏：根据视频方向自动横屏/竖屏，并铺满整个屏幕
        boolean vertical = false;
        if (player != null) {
            VideoSize vs = player.getVideoSize();
            vertical = vs != null && vs.height > vs.width;
        }
        setRequestedOrientation(vertical
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 画面尺寸尊重用户选择（默认=默认/FIT），不再强制"全屏"ZOOM 裁切
        applyResizeMode(currentSize);
    }

    private void enterSmallScreen() {
        isFullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        applyResizeMode(currentSize); // 恢复用户选的画面尺寸
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 500);
    }

    private void updatePosition() {
        if (player == null) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        tvPosition.setText(format(pos));
        if (dur > 0) timeBar.setPosition(pos);
        if (skipOutroMs > 0 && dur > 0 && pos >= dur - skipOutroMs) {
            player.pause();
        }
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
        updatePlayIcon();
    }

    private void updatePlayIcon() {
        boolean playing = player != null && player.isPlaying();
        ivCenterPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_white);
        btnCenterPlay.setVisibility(playing ? View.GONE : View.VISIBLE);
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        topBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(barsVisible ? View.VISIBLE : View.GONE);
    }

    private void toggleSettingsPanel() {
        boolean show = settingsPanel.getVisibility() == View.GONE;
        settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void applySettings() {
        try { skipIntroMs = Long.parseLong(etSkipIntro.getText().toString().trim()) * 1000; }
        catch (Exception e) { skipIntroMs = 0; }
        try { skipOutroMs = Long.parseLong(etSkipOutro.getText().toString().trim()) * 1000; }
        catch (Exception e) { skipOutroMs = 0; }
        try {
            longPressSpeed = Float.parseFloat(etLongPressSpeed.getText().toString().trim());
            if (longPressSpeed <= 0) longPressSpeed = 1.5f;
        } catch (Exception e) { longPressSpeed = 1.5f; }
        etLongPressSpeed.setText(String.valueOf(longPressSpeed));
        Prefs.get(this).setLongPressSpeed(longPressSpeed);
        Prefs.get(this).setSkipIntroSec((int) (skipIntroMs / 1000));
        Prefs.get(this).setSkipOutroSec((int) (skipOutroMs / 1000));
        boolean hwChanged = swHardware.isChecked() != Prefs.get(this).isHardwareDecode();
        Prefs.get(this).setPlaybackSpeed(currentSpeed);
        Prefs.get(this).setHardwareDecode(swHardware.isChecked());
        if (player != null && skipIntroMs > 0) player.seekTo(skipIntroMs);
        settingsPanel.setVisibility(View.GONE);
        // 硬件加速开关变化时重建播放器（保留进度）
        if (hwChanged && !currentUrl.isEmpty()) {
            long pos = player != null ? player.getCurrentPosition() : 0;
            initPlayer(currentUrl);
            if (player != null && pos > 0) player.seekTo(pos);
        }
        Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show();
    }

    // ---------- 倍速 ----------
    private void buildSpeedChips() {
        speedGroup.removeAllViews();
        for (float sp : SPEEDS) {
            TextView tv = chip(speedText(sp));
            if (Math.abs(sp - currentSpeed) < 0.01f) {
                tv.setBackgroundResource(R.drawable.bg_chip_selected);
                tv.setTextColor(Color.WHITE);
            }
            tv.setOnClickListener(v -> {
                currentSpeed = sp;
                if (player != null) player.setPlaybackSpeed(sp);
                btnSpeed.setText("倍速 " + speedText(sp));
                Prefs.get(this).setPlaybackSpeed(sp);
                buildSpeedChips();
            });
            speedGroup.addView(tv);
        }
    }

    // ---------- 画面尺寸 ----------
    private void buildSizeChips() {
        sizeGroup.removeAllViews();
        for (int i = 0; i < SIZES.length; i++) {
            TextView tv = chip(SIZES[i]);
            if (i == currentSize) {
                tv.setBackgroundResource(R.drawable.bg_chip_selected);
                tv.setTextColor(Color.WHITE);
            }
            final int idx = i;
            tv.setOnClickListener(v -> {
                if (idx == 6) { enterFullscreen(); return; }
                if (idx == 7) { enterSmallScreen(); return; }
                currentSize = idx;
                applyResizeMode(idx);
                buildSizeChips();
            });
            sizeGroup.addView(tv);
        }
    }

    private TextView chip(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setTextSize(13);
        tv.setBackgroundResource(R.drawable.bg_chip);
        tv.setTextColor(Color.parseColor("#9AA3AF"));
        tv.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String speedText(float sp) {
        return (sp == (int) sp) ? String.valueOf((int) sp) + ".0X" : String.valueOf(sp) + "X";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String format(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            if (player.getPlaybackState() == Player.STATE_READY) player.play();
            startTicker();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(longPressRunnable);
        releasePlayer();
        super.onDestroy();
    }

    private static abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) { }
        @Override public void onStopTrackingTouch(SeekBar s) { }
    }
}
