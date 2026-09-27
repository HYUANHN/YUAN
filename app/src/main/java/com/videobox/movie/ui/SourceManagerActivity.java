package com.videobox.movie.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.videobox.movie.R;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.ApiClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SourceManagerActivity extends AppCompatActivity {

    private static final int REQ_IMPORT_JSON = 101;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private EditText etUrl;
    private Button btnFetch;
    private TextView tvStatus;
    private RecyclerView rv;
    private LinearLayout savedRow;
    private SourceAdapter adapter;
    private List<PlaySource> sources = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_manager);

        etUrl = findViewById(R.id.et_config_url);
        btnFetch = findViewById(R.id.btn_fetch);
        tvStatus = findViewById(R.id.tv_status);
        rv = findViewById(R.id.rv_sources);
        savedRow = findViewById(R.id.saved_sources_row);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SourceAdapter();
        rv.setAdapter(adapter);

        // 预填上次输入的配置地址（或从源列表点进来的地址）
        String prefill = getIntent().getStringExtra("prefill_url");
        if (prefill != null && !prefill.isEmpty()) {
            etUrl.setText(prefill);
        } else {
            // 不预置任何默认接口地址，由用户自行填写
            String lastUrl = getSharedPreferences("videobox_prefs", MODE_PRIVATE)
                    .getString("last_config_url", "");
            etUrl.setText(lastUrl);
        }

        buildSavedChips();

        findViewById(R.id.btn_import_json).setOnClickListener(v -> pickJsonFile());

        btnFetch.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入配置节点地址", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences("videobox_prefs", MODE_PRIVATE).edit()
                    .putString("last_config_url", url).apply();
            btnFetch.setEnabled(false);
            tvStatus.setText("正在获取配置节点列表…");
            exec.execute(() -> {
                try {
                    List<PlaySource> list = ApiClient.fetchNodeList(url);
                    main.post(() -> {
                        btnFetch.setEnabled(true);
                        sources = list;
                        adapter.setData(list);
                        tvStatus.setText(list.isEmpty()
                                ? "未获取到节点，请检查地址格式"
                                : "获取到 " + list.size() + " 个源：点\"添加\"手动存入列表，点整行设为当前源");
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        btnFetch.setEnabled(true);
                        tvStatus.setText("获取失败：" + e.getMessage());
                    });
                }
            });
        });

        // 展示当前已保存的播放源
        PlaySource cur = Prefs.get(this).getPlaySource();
        if (cur != null) {
            tvStatus.setText("当前播放源：" + cur.name + "（点已保存的源名直接切换，点✕删除）");
        }
    }

    // ---------- 已保存的源：点名字直接切换当前源，点✕删除 ----------
    private void buildSavedChips() {
        savedRow.removeAllViews();
        List<PlaySource> saved = Prefs.get(this).getAllSources();
        if (saved.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("（暂无已保存的源，获取节点后点\"添加\"手动保存）");
            t.setTextColor(0xFF9AA3AF);
            t.setTextSize(13);
            savedRow.addView(t);
            return;
        }
        for (PlaySource ps : saved) {
            String url = ps.api != null && !ps.api.trim().isEmpty() ? ps.api : ps.url;
            // 外层水平容器：[名字chip][✕]
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setGravity(android.view.Gravity.CENTER_VERTICAL);
            wrap.setBackgroundResource(R.drawable.bg_chip);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wlp.setMargins(dp(4), 0, dp(4), 0);
            wrap.setLayoutParams(wlp);

            TextView chip = new TextView(this);
            chip.setText(ps.name);
            chip.setPadding(dp(12), dp(8), dp(2), dp(8));
            chip.setTextSize(13);
            chip.setTextColor(0xFFF2F4F7);
            chip.setOnClickListener(v -> {
                // 已保存的源：点一下直接切换为当前播放源，无需再走"获取配置"
                Prefs.get(this).setPlaySource(ps);
                etUrl.setText(url);
                tvStatus.setText("已切换当前播放源：" + ps.name);
                Toast.makeText(this, "已切换：" + ps.name, Toast.LENGTH_SHORT).show();
            });
            wrap.addView(chip);

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextSize(13);
            del.setTextColor(0xFFE57373);
            del.setPadding(dp(8), dp(8), dp(10), dp(8));
            del.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("删除播放源")
                        .setMessage("确定要删除播放源【" + ps.name + "】吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            Prefs.get(this).removeSource(ps);
                            buildSavedChips();
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "已删除：" + ps.name, Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
            wrap.addView(del);
            savedRow.addView(wrap);
        }
    }

    // ---------- 本地 JSON 导入 ----------
    private void pickJsonFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT_JSON);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_JSON || resultCode != Activity.RESULT_OK || data == null) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        exec.execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        getContentResolver().openInputStream(uri), StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                final List<PlaySource> parsed = parseSources(sb.toString());
                main.post(() -> {
                    if (parsed.isEmpty()) {
                        Toast.makeText(this, "未识别到播放源，请检查JSON格式", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Prefs prefs = Prefs.get(this);
                    for (PlaySource ps : parsed) prefs.recordSource(ps);
                    // 第一个源填入输入框
                    PlaySource first = parsed.get(0);
                    String url = first.api != null && !first.api.trim().isEmpty() ? first.api : first.url;
                    etUrl.setText(url);
                    buildSavedChips();
                    tvStatus.setText("已导入 " + parsed.size() + " 个源，第一个已填入输入框：" + first.name);
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "读取JSON失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 识别常见采集站 JSON：sources[] / list[] / 数组 / 单个 {name,url,api} */
    static List<PlaySource> parseSources(String json) {
        List<PlaySource> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray arr = null;
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("sources") && o.get("sources").isJsonArray()) arr = o.getAsJsonArray("sources");
                else if (o.has("list") && o.get("list").isJsonArray()) arr = o.getAsJsonArray("list");
                else if (o.has("data") && o.get("data").isJsonArray()) arr = o.getAsJsonArray("data");
                else {
                    // 单个源对象
                    PlaySource ps = fromObj(o);
                    if (ps != null) out.add(ps);
                    return out;
                }
            } else if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            }
            if (arr == null) return out;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                PlaySource ps = fromObj(e.getAsJsonObject());
                if (ps != null) out.add(ps);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static PlaySource fromObj(JsonObject o) {
        String name = str(o, "name", "site_name", "title", "node_name");
        String url = str(o, "url", "site_url", "base", "api_url");
        String api = str(o, "api", "interface", "vod_api");
        if (TextUtils.isEmpty(name)) name = "导入源";
        if (TextUtils.isEmpty(url) && TextUtils.isEmpty(api)) return null;
        PlaySource ps = new PlaySource();
        ps.name = name;
        ps.url = url;
        ps.api = api;
        ps.description = str(o, "description", "desc", "remark");
        return ps;
    }

    private static String str(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                String v = o.get(k).getAsString();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }

    private class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.VH> {
        private List<PlaySource> data = new ArrayList<>();
        private final String currentName =
                Prefs.get(SourceManagerActivity.this).getPlaySource() != null
                        ? Prefs.get(SourceManagerActivity.this).getPlaySource().name : null;

        void setData(List<PlaySource> list) {
            data = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PlaySource ps = data.get(position);
            h.name.setText(ps.name);
            if (ps.description != null && !ps.description.isEmpty()) {
                h.desc.setVisibility(View.VISIBLE);
                h.desc.setText(ps.description);
            } else {
                h.desc.setVisibility(View.GONE);
            }
            boolean selected = ps.name != null && ps.name.equals(currentName);
            h.check.setVisibility(selected ? View.VISIBLE : View.GONE);
            // 是否已在保存列表
            final String key = ps.api != null ? ps.api : ps.url;
            boolean saved = isSaved(key);
            h.addBtn.setText(saved ? "已添加" : "添加");
            h.addBtn.setEnabled(!saved);
            h.addBtn.setOnClickListener(v -> {
                if (isSaved(key)) return;
                Prefs.get(SourceManagerActivity.this).recordSource(ps);
                Toast.makeText(SourceManagerActivity.this, "已手动添加：" + ps.name, Toast.LENGTH_SHORT).show();
                buildSavedChips();
                h.addBtn.setText("已添加");
                h.addBtn.setEnabled(false);
            });
            // 点整行 = 设为当前播放源并返回
            h.itemView.setOnClickListener(v -> {
                Prefs.get(SourceManagerActivity.this).setPlaySource(ps);
                Toast.makeText(SourceManagerActivity.this, "已设为当前播放源：" + ps.name, Toast.LENGTH_SHORT).show();
                notifyDataSetChanged();
                finish();
            });
        }

        private boolean isSaved(String key) {
            if (key == null) return false;
            for (PlaySource it : Prefs.get(SourceManagerActivity.this).getAllSources()) {
                String itk = it.api != null ? it.api : it.url;
                if (itk != null && itk.equals(key)) return true;
            }
            return false;
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, desc, addBtn;
            ImageView check;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_source_name);
                desc = v.findViewById(R.id.tv_source_desc);
                check = v.findViewById(R.id.iv_check);
                addBtn = v.findViewById(R.id.btn_add_source);
            }
        }
    }
}
