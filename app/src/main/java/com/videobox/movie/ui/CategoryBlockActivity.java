package com.videobox.movie.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.videobox.movie.R;
import com.videobox.movie.data.Category;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 分类屏蔽：自定义屏蔽首页主分类/子分类 */
public class CategoryBlockActivity extends AppCompatActivity {

    private LinearLayout root;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private List<Category> cats = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_block);
        root = findViewById(R.id.cat_block_root);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            Prefs.get(this).setBlockedCats(new ArrayList<>());
            rebuild();
            Toast.makeText(this, "已清空屏蔽", Toast.LENGTH_SHORT).show();
        });

        load();
    }

    private void load() {
        root.removeAllViews();
        root.addView(loadingRow("正在加载分类…"));
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null || source.getApiBase().isEmpty()) {
            root.removeAllViews();
            root.addView(loadingRow("未配置播放源，请先在播放源管理添加"));
            return;
        }
        final String api = source.getApiBase();
        exec.execute(() -> {
            List<Category> list;
            try {
                list = ApiClient.fetchCategories(api);
            } catch (Exception e) {
                list = new ArrayList<>();
            }
            final List<Category> result = list;
            runOnUiThread(() -> {
                cats = result;
                rebuild();
            });
        });
    }

    private void rebuild() {
        root.removeAllViews();
        if (cats.isEmpty()) {
            root.addView(loadingRow("未获取到分类，或播放源不支持分类"));
            return;
        }
        Prefs prefs = Prefs.get(this);
        for (Category c : cats) {
            if (!c.isMain()) continue;
            boolean mainBlocked = prefs.isCatBlocked(c.name);
            root.addView(catRow(c.name, mainBlocked, on -> {
                toggle(prefs, c.name, on);
                if (on) {
                    // 屏蔽主分类：其子分类一并隐藏，从 UI 移除子分类行
                    rebuild();
                }
            }, true));
            if (mainBlocked) continue; // 主分类已屏蔽，不再展示其子分类
            for (Category s : cats) {
                if (s.isMain() || s.pid != c.id) continue;
                root.addView(catRow("   " + s.name, prefs.isCatBlocked(s.name), on ->
                        toggle(prefs, s.name, on), false));
            }
        }
    }

    private void toggle(Prefs prefs, String name, boolean blocked) {
        List<String> list = new ArrayList<>(prefs.getBlockedCats());
        if (blocked) {
            if (!list.contains(name)) list.add(name);
        } else {
            list.remove(name);
        }
        prefs.setBlockedCats(list);
    }

    private View catRow(String name, boolean checked,
                        java.util.function.Consumer<Boolean> onChanged, boolean main) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundColor(0xFF1B2026);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextColor(main ? 0xFFF2F4F7 : 0xFF9AA3AF);
        tv.setTextSize(main ? 16 : 14);
        tv.setPadding(dp(8), 0, 0, 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        SwitchMaterial sw = new SwitchMaterial(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((v, isChecked) -> onChanged.accept(isChecked));

        row.addView(tv);
        row.addView(sw);
        return row;
    }

    private View loadingRow(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF9AA3AF);
        t.setTextSize(14);
        t.setPadding(dp(8), dp(20), 0, dp(20));
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
