package com.videobox.movie.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.videobox.movie.R;
import com.videobox.movie.data.PlayerFlag;
import com.videobox.movie.data.Prefs;

import java.util.List;

/** 播放器标识（MacCMS 线路 flag）管理：启用/禁用 + 是否开启网页解析 */
public class PlayerFlagActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list;
    private java.util.List<PlayerFlag> currentFlags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        setTitle("播放器标识管理");

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(12), dp(16), dp(12));
        sv.addView(list);

        TextView tip = new TextView(this);
        tip.setText("控制 MacCMS 采集站返回的播放线路 flag 是否启用与解析方式。\n"
                + "预置：sdm3u8(闪电)、feifan(非凡)、gsm3u8(光速)。被禁用的线路会自动隐藏，不影响播放。");
        tip.setTextSize(13);
        tip.setTextColor(0xFF8A93A0);
        tip.setPadding(0, 0, 0, dp(8));
        list.addView(tip);

        render();
        setContentView(sv);

        findViewById(android.R.id.content).setClickable(false);
    }

    private void render() {
        currentFlags = prefs.getPlayerFlags();
        list.removeViews(1, Math.max(0, list.getChildCount() - 1));
        for (PlayerFlag f : currentFlags) list.addView(buildFlagRow(f));
        TextView add = new TextView(this);
        add.setText("＋ 添加播放器标识");
        add.setTextSize(14);
        add.setTextColor(0xFF3E9BFF);
        add.setPadding(0, dp(14), 0, dp(6));
        add.setOnClickListener(v -> showEdit(null));
        list.addView(add);
    }

    private View buildFlagRow(final PlayerFlag f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView title = new TextView(this);
        title.setText((f.name == null || f.name.isEmpty()) ? f.code : f.name);
        title.setTextSize(16);
        title.setTextColor(0xFFECEFF4);
        row.addView(title);

        TextView sub = new TextView(this);
        sub.setText("编码：" + f.code + (f.enabled ? "" : "（已禁用）"));
        sub.setTextSize(12);
        sub.setTextColor(0xFF8A93A0);
        row.addView(sub);

        // 启用开关
        row.addView(makeSwitch("启用该线路", f.enabled, (btn, on) -> {
            f.enabled = on;
            save();
        }));
        // 网页解析开关（share 页面提取 m3u8）
        row.addView(makeSwitch("开启网页解析", f.webParse, (btn, on) -> {
            f.webParse = on;
            save();
        }));

        row.setBackgroundResource(R.drawable.bg_card);
        row.setOnClickListener(v -> showEdit(f));
        return row;
    }

    private View makeSwitch(String label, boolean checked, CompoundButton.OnCheckedChangeListener cb) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(0, dp(6), 0, dp(2));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(0xFFB8C1CC);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ll.addView(tv, tlp);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(cb);
        ll.addView(sw);
        return ll;
    }

    private void showEdit(final PlayerFlag f) {
        final LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), 0, dp(8), 0);
        final EditText etCode = new EditText(this);
        etCode.setText(f == null ? "" : f.code);
        etCode.setHint("如 sdm3u8");
        form.addView(label("播放器编码（如 sdm3u8）"));
        form.addView(etCode);
        final EditText etName = new EditText(this);
        etName.setText(f == null ? "" : f.name);
        etName.setHint("显示名称");
        form.addView(label("显示名称"));
        form.addView(etName);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(f == null ? "添加播放器标识" : "编辑播放器标识")
                .setView(form)
                .setPositiveButton("保存", (d, w) -> {
                    String code = etCode.getText().toString().trim();
                    if (code.isEmpty()) {
                        Toast.makeText(this, "编码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (f == null) {
                        PlayerFlag nf = PlayerFlag.of(code,
                                etName.getText().toString().trim().isEmpty() ? code : etName.getText().toString().trim());
                        currentFlags.add(nf);
                    } else {
                        f.code = code;
                        f.name = etName.getText().toString().trim().isEmpty() ? code : etName.getText().toString().trim();
                    }
                    prefs.setPlayerFlags(currentFlags);
                    render();
                })
                .setNegativeButton("删除", (d, w) -> {
                    if (f == null) return;
                    // 删除前二次确认，防止误删
                    new AlertDialog.Builder(this)
                            .setTitle("删除确认")
                            .setMessage("确定要删除播放器标识“" + f.name + "（" + f.code + "）”吗？删除后无法恢复。")
                            .setPositiveButton("确认删除", (d2, w2) -> {
                                currentFlags.remove(f);
                                prefs.setPlayerFlags(currentFlags);
                                render();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNeutralButton("取消", null)
                .show();

        // 弹窗显示后唤起输入法，确保能正常输入文字
        dialog.setOnShowListener(ds -> {
            etCode.requestFocus();
            android.content.Context ctx = this;
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etCode, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private TextView label(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(12);
        tv.setTextColor(0xFF8A93A0);
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    private void save() {
        // 已就地修改对象引用，直接回写
        prefs.setPlayerFlags(prefs.getPlayerFlags());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
