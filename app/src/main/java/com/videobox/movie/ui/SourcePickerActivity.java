package com.videobox.movie.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.videobox.movie.R;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;

import java.util.List;

/** 换源页：只列出已保存的播放源，点一下直接切换当前源 */
public class SourcePickerActivity extends Activity {

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

        TextView title = new TextView(this);
        title.setText("换源");
        title.setTextSize(18);
        title.setTextColor(0xFFECEFF4);
        title.setPadding(0, 0, 0, dp(10));
        list.addView(title);

        render();
        setContentView(sv);
    }

    private void render() {
        list.removeViews(1, Math.max(0, list.getChildCount() - 1));
        List<PlaySource> saved = prefs.getAllSources();
        PlaySource cur = prefs.getPlaySource();
        if (saved.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("暂无已保存的源，请到【设置 → 播放源管理】添加");
            t.setTextSize(13);
            t.setTextColor(0xFF8A93A0);
            t.setPadding(0, dp(10), 0, 0);
            list.addView(t);
            return;
        }
        for (PlaySource ps : saved) {
            boolean isCur = cur != null && cur.name != null && cur.name.equals(ps.name);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundResource(R.drawable.bg_card);

            TextView name = new TextView(this);
            name.setText((ps.name == null || ps.name.isEmpty() ? "未命名源" : ps.name)
                    + (isCur ? "（当前）" : ""));
            name.setTextSize(15);
            name.setTextColor(isCur ? 0xFF3E9BFF : 0xFFECEFF4);
            row.addView(name);

            TextView sub = new TextView(this);
            sub.setText(ps.getApiBase() != null ? ps.getApiBase() : "");
            sub.setTextSize(12);
            sub.setTextColor(0xFF8A93A0);
            sub.setPadding(0, dp(4), 0, 0);
            row.addView(sub);

            row.setOnClickListener(v -> {
                prefs.setPlaySource(ps);
                Toast.makeText(this, "已切换：" + ps.name, Toast.LENGTH_SHORT).show();
                finish();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(10));
            list.addView(row, lp);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
