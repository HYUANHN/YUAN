package com.videobox.movie.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.data.VodItem;

import java.util.List;

public class SettingsFragment extends Fragment {

    private LinearLayout root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        root = v.findViewById(R.id.settings_root);
        buildUi();
        return v;
    }

    private void buildUi() {
        root.removeAllViews();
        Context c = requireContext();

        addClickRow(c, "播放源管理", "修改播放源接口、节点选择切换", () ->
                startActivity(new Intent(c, SourceManagerActivity.class)));

        addClickRow(c, "源地址列表", "查看所有已保存的播放源地址", () ->
                startActivity(new Intent(c, SourceListActivity.class)));

        addClickRow(c, "播放历史", "查看与清除播放历史", () -> showHistory(c));

        addClickRow(c, "分类屏蔽", "自定义屏蔽首页主分类/子分类", () ->
                startActivity(new Intent(c, CategoryBlockActivity.class)));

        addClickRow(c, "播放器标识", "管理线路 flag（sdm3u8/feifan/gsm3u8）启用与解析", () ->
                startActivity(new Intent(c, PlayerFlagActivity.class)));

        addClickRow(c, "电视台管理", "配置 IPTV 直播源（m3u/导入/单条流）", () ->
                startActivity(new Intent(c, LiveSourceActivity.class)));

        Prefs prefs = Prefs.get(c);
        addSwitchRow(c, "广告过滤", "拦截播放源内嵌广告分片", prefs.isAdBlockEnabled(), on -> {
            prefs.setAdBlockEnabled(on);
        });

        addClickRow(c, "广告过滤黑名单", "自定义需要拦截的广告域名/关键字", () ->
                startActivity(new Intent(c, AdBlockListActivity.class)));

        addSwitchRow(c, "诊断日志", "仅在配合排查问题时开启", false, on -> { });

        // 版本 + 免责声明（点击可查看完整免责声明）
        View footer = LayoutInflater.from(c).inflate(R.layout.item_setting, root, false);
        TextView t = footer.findViewById(R.id.tv_setting_title);
        TextView d = footer.findViewById(R.id.tv_setting_desc);
        t.setText(R.string.version);
        d.setText(R.string.disclaimer);
        d.setVisibility(View.VISIBLE);
        footer.findViewById(R.id.iv_setting_arrow).setVisibility(View.VISIBLE);
        footer.findViewById(R.id.sw_setting).setVisibility(View.GONE);
        footer.findViewById(R.id.setting_row).setOnClickListener(v ->
                new AlertDialog.Builder(c)
                        .setTitle("免责声明")
                        .setMessage(getString(R.string.disclaimer_full))
                        .setPositiveButton("我知道了", null)
                        .show());
        root.addView(footer);
    }

    private void addClickRow(Context c, String title, String desc, Runnable onClick) {
        View row = LayoutInflater.from(c).inflate(R.layout.item_setting, root, false);
        ((TextView) row.findViewById(R.id.tv_setting_title)).setText(title);
        TextView d = row.findViewById(R.id.tv_setting_desc);
        d.setText(desc);
        d.setVisibility(View.VISIBLE);
        row.findViewById(R.id.iv_setting_arrow).setVisibility(View.VISIBLE);
        row.findViewById(R.id.sw_setting).setVisibility(View.GONE);
        row.findViewById(R.id.setting_row).setOnClickListener(v -> onClick.run());
        root.addView(row);
    }

    private void addSwitchRow(Context c, String title, String desc, boolean checked,
                              java.util.function.Consumer<Boolean> onChanged) {
        View row = LayoutInflater.from(c).inflate(R.layout.item_setting, root, false);
        ((TextView) row.findViewById(R.id.tv_setting_title)).setText(title);
        TextView d = row.findViewById(R.id.tv_setting_desc);
        d.setText(desc);
        d.setVisibility(View.VISIBLE);
        row.findViewById(R.id.iv_setting_arrow).setVisibility(View.GONE);
        com.google.android.material.switchmaterial.SwitchMaterial sw =
                row.findViewById(R.id.sw_setting);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((v, isChecked) -> onChanged.accept(isChecked));
        root.addView(row);
    }

    private void showHistory(Context c) {
        List<VodItem> history = Prefs.get(c).getHistory();
        if (history.isEmpty()) {
            new AlertDialog.Builder(c)
                    .setTitle("播放历史")
                    .setMessage("暂无播放历史")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        String[] names = new String[history.size()];
        for (int i = 0; i < history.size(); i++) names[i] = history.get(i).getTitle();
        new AlertDialog.Builder(c)
                .setTitle("播放历史")
                .setItems(names, (d, which) -> Ui.openDetail(c, history.get(which)))
                .setNeutralButton("清空历史", (d, which) -> {
                    Prefs.get(c).clearHistory();
                    new AlertDialog.Builder(c).setMessage("已清空").setPositiveButton("确定", null).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
