package com.videobox.movie.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.M3uParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 电视台分组屏蔽：默认忽略"电信"，其余可勾选 */
public class LiveBlockActivity extends Activity {

    private Prefs prefs;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(14), dp(16), dp(14));
        sv.addView(list);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("屏蔽分类");
        title.setTextSize(18);
        title.setTextColor(0xFFECEFF4);
        list.addView(title);

        TextView hint = new TextView(this);
        hint.setText("勾选后，对应分组在电视台里不显示（「电信」始终屏蔽）");
        hint.setTextSize(12);
        hint.setTextColor(0xFF8A93A0);
        hint.setPadding(0, dp(4), 0, dp(10));
        list.addView(hint);

        render();
    }

    private void render() {
        list.removeViews(2, Math.max(0, list.getChildCount() - 2));
        List<String> allGroups = collectGroups();
        List<String> blocked = new ArrayList<>(prefs.getBlockedLiveGroups());

        for (String g : allGroups) {
            if (g.contains("电信")) continue; // 电信恒屏蔽，不展示
            final String grp = g;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(6), dp(4), dp(6));

            CheckBox cb = new CheckBox(this);
            boolean isBlocked = false;
            for (String b : blocked) if (b.equalsIgnoreCase(grp)) isBlocked = true;
            cb.setChecked(isBlocked);
            cb.setText(grp);
            cb.setTextColor(0xFFECEFF4);
            cb.setOnCheckedChangeListener((btn, checked) -> {
                List<String> bl = new ArrayList<>(prefs.getBlockedLiveGroups());
                if (checked) {
                    boolean has = false;
                    for (String b : bl) if (b.equalsIgnoreCase(grp)) has = true;
                    if (!has) bl.add(grp);
                } else {
                    bl.removeIf(b -> b.equalsIgnoreCase(grp));
                }
                prefs.setBlockedLiveGroups(bl);
                Toast.makeText(this, (checked ? "已屏蔽：" : "已取消屏蔽：") + grp,
                        Toast.LENGTH_SHORT).show();
            });
            row.addView(cb, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            list.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private List<String> collectGroups() {
        Set<String> set = new LinkedHashSet<>();
        for (M3uParser.Channel c : prefs.getLiveChannels()) {
            if (c == null || c.group == null || c.group.trim().isEmpty()) continue;
            set.add(c.group.trim());
        }
        return new ArrayList<>(set);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
