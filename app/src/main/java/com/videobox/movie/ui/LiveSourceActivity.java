package com.videobox.movie.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.M3uParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** 电视台源管理：m3u 地址 / 导入本地 m3u / 单条直播流 / 清空 */
public class LiveSourceActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private Prefs prefs;
    private TextView tvStatus;
    private EditText etUrl;
    private EditText etName;
    private EditText etStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        sv.addView(box);
        setContentView(sv);

        TextView title = tv("电视台源管理", 18, 0xFFECEFF4);
        box.addView(title);

        tvStatus = tv("当前未配置", 13, 0xFF8A93A0);
        tvStatus.setPadding(0, dp(8), 0, dp(12));
        box.addView(tvStatus);

        // ---- m3u 地址 ----
        TextView l1 = tv("m3u 播放列表地址（远程）", 14, 0xFFECEFF4);
        box.addView(l1);
        etUrl = new EditText(this);
        etUrl.setHint("例如 https://example.com/live.m3u");
        etUrl.setTextColor(0xFFECEFF4);
        etUrl.setHintTextColor(0xFF5A6470);
        etUrl.setTextSize(14);
        etUrl.setSingleLine(true);
        box.addView(etUrl, matchWrap());

        Button btnFetch = btn("获取并解析", 0xFFE5484D);
        btnFetch.setOnClickListener(v -> fetchRemote());
        box.addView(btnFetch, btnLp());

        // ---- 导入本地 ----
        Button btnLocal = btn("导入本地 m3u 文件", 0xFF3E63DD);
        btnLocal.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_FILE);
        });
        box.addView(btnLocal, btnLp());

        // ---- 单条直播流 ----
        TextView l2 = tv("单条直播流地址（选填）", 14, 0xFFECEFF4);
        l2.setPadding(0, dp(14), 0, 0);
        box.addView(l2);
        etName = new EditText(this);
        etName.setHint("频道名称，如 CCTV1");
        etName.setTextColor(0xFFECEFF4);
        etName.setHintTextColor(0xFF5A6470);
        etName.setTextSize(14);
        etName.setSingleLine(true);
        box.addView(etName, matchWrap());
        etStream = new EditText(this);
        etStream.setHint("直播流地址，如 http://.../index.m3u8");
        etStream.setTextColor(0xFFECEFF4);
        etStream.setHintTextColor(0xFF5A6470);
        etStream.setTextSize(14);
        etStream.setSingleLine(true);
        box.addView(etStream, matchWrap());
        Button btnAddOne = btn("添加单条频道", 0xFF3E9BFF);
        btnAddOne.setOnClickListener(v -> addSingle());
        box.addView(btnAddOne, btnLp());

        // ---- 清空 ----
        Button btnClear = btn("清空电视台源", 0xFFE5484D);
        btnClear.setOnClickListener(v -> {
            prefs.clearLive();
            refreshStatus();
            Toast.makeText(this, "已清空电视台源", Toast.LENGTH_SHORT).show();
        });
        box.addView(btnClear, btnLp());

        refreshStatus();
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private Button btn(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.bg_chip_selected);
        b.setTextSize(14);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(6));
        return lp;
    }

    private void refreshStatus() {
        List<M3uParser.Channel> ch = prefs.getLiveChannels();
        String name = prefs.getLiveName();
        String url = prefs.getLiveUrl();
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isEmpty()) sb.append("名称：").append(name).append("\n");
        if (url != null && !url.isEmpty()) sb.append("m3u地址：").append(url).append("\n");
        if (ch.isEmpty()) sb.append("当前未配置（频道数：0）");
        else sb.append("频道数：").append(ch.size());
        tvStatus.setText(sb.toString().trim());
    }

    private void fetchRemote() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请先输入 m3u 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在获取解析…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                Request req = new Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0").build();
                try (Response res = client.newCall(req).execute()) {
                    if (!res.isSuccessful()) {
                        runOnUiThread(() -> Toast.makeText(this,
                                "获取失败 HTTP " + res.code(), Toast.LENGTH_LONG).show());
                        return;
                    }
                    String body = res.body() != null ? res.body().string() : "";
                    List<M3uParser.Channel> ch = M3uParser.parse(body);
                    runOnUiThread(() -> {
                        if (ch.isEmpty()) {
                            Toast.makeText(this, "解析到 0 个频道，请检查地址", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.setLiveName(url);
                        prefs.setLiveUrl(url);
                        prefs.setLiveChannels(ch);
                        refreshStatus();
                        Toast.makeText(this, "已导入 " + ch.size() + " 个频道", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "获取失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void addSingle() {
        String name = etName.getText().toString().trim();
        String url = etStream.getText().toString().trim();
        if (name.isEmpty() || url.isEmpty()) {
            Toast.makeText(this, "频道名称和地址都要填", Toast.LENGTH_SHORT).show();
            return;
        }
        List<M3uParser.Channel> ch = prefs.getLiveChannels();
        ch.add(new M3uParser.Channel(name, url));
        prefs.setLiveChannels(ch);
        if (prefs.getLiveName() == null) prefs.setLiveName(name);
        refreshStatus();
        Toast.makeText(this, "已添加频道：" + name, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            String name = queryName(uri);
            new Thread(() -> {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    byte[] bytes = readAll(is);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    List<M3uParser.Channel> ch = M3uParser.parse(content);
                    runOnUiThread(() -> {
                        if (ch.isEmpty()) {
                            Toast.makeText(this, "文件解析到 0 个频道", Toast.LENGTH_LONG).show();
                            return;
                        }
                        prefs.setLiveName(name != null ? name : "本地m3u");
                        prefs.setLiveUrl(null);
                        prefs.setLiveChannels(ch);
                        refreshStatus();
                        Toast.makeText(this, "已导入 " + ch.size() + " 个频道", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private String queryName(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) return c.getString(idx);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return "本地m3u";
    }

    private byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
