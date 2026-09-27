package com.videobox.movie.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.M3uParser;
import com.videobox.movie.player.LivePlayerActivity;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** 电视台：IPTV 直播频道列表 */
public class LiveFragment extends Fragment {

    private Prefs prefs;
    private LinearLayout list;
    private TextView tvHint;
    private TextView tvTitle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        prefs = Prefs.get(requireContext());

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg_dark));

        // 顶部栏
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(6));

        tvTitle = new TextView(requireContext());
        tvTitle.setText("电视台");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(getColor(R.color.text_primary));
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(tvTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnManage = tv("管理", 14, 0xFFECEFF4);
        btnManage.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnManage.setBackgroundResource(R.drawable.bg_card);
        btnManage.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LiveSourceActivity.class)));
        header.addView(btnManage, lpWrap());

        TextView btnRefresh = tv("刷新", 14, 0xFFECEFF4);
        btnRefresh.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnRefresh.setBackgroundResource(R.drawable.bg_card);
        btnRefresh.setOnClickListener(v -> reload(true));
        header.addView(btnRefresh, lpWrap());

        root.addView(header);

        tvHint = tv("", 12, 0xFF8A93A0);
        tvHint.setPadding(dp(16), dp(2), dp(16), dp(6));
        root.addView(tvHint, lpWrap());

        ScrollView sv = new ScrollView(requireContext());
        sv.setFillViewport(true);
        list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(12));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload(false);
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams lpWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        return lp;
    }

    private void reload(boolean force) {
        List<M3uParser.Channel> ch = new ArrayList<>(prefs.getLiveChannels());
        String url = prefs.getLiveUrl();

        if (ch.isEmpty() && url != null && !url.isEmpty()) {
            // 有远程地址但本地无缓存频道 → 拉取解析
            tvHint.setText("正在获取电视台源…");
            fetchRemote(url);
            return;
        }
        render(ch);
    }

    private void fetchRemote(String url) {
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
                        if (isAdded()) requireActivity().runOnUiThread(() ->
                                tvHint.setText("获取失败 HTTP " + res.code() + "，点右上角刷新重试"));
                        return;
                    }
                    String body = res.body() != null ? res.body().string() : "";
                    List<M3uParser.Channel> parsed = M3uParser.parse(body);
                    if (isAdded()) {
                        prefs.setLiveChannels(parsed);
                        requireActivity().runOnUiThread(() -> render(parsed));
                    }
                }
            } catch (Exception e) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                        tvHint.setText("获取失败：" + e.getMessage() + "，点右上角刷新"));
            }
        }).start();
    }

    private void render(List<M3uParser.Channel> ch) {
        list.removeAllViews();
        if (ch.isEmpty()) {
            TextView empty = tv("暂无频道，点右上角「管理」配置 m3u 或单条直播流", 14, 0xFF8A93A0);
            empty.setPadding(dp(8), dp(20), dp(8), dp(8));
            list.addView(empty, lpWrap());
            tvHint.setText("未配置电视台源");
            return;
        }
        String name = prefs.getLiveName();
        tvHint.setText((name == null || name.isEmpty() ? "" : name + " · ") + "共 " + ch.size() + " 个频道");

        for (M3uParser.Channel c : ch) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundResource(R.drawable.bg_card);

            TextView n = tv(c.name == null ? "频道" : c.name, 15, 0xFFECEFF4);
            row.addView(n);

            TextView u = tv(c.url == null ? "" : c.url, 11, 0xFF8A93A0);
            u.setPadding(0, dp(3), 0, 0);
            row.addView(u);

            row.setOnClickListener(v -> {
                Intent it = new Intent(requireContext(), LivePlayerActivity.class);
                it.putExtra("name", c.name);
                it.putExtra("url", c.url);
                startActivity(it);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            list.addView(row, lp);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getColor(int res) {
        return requireContext().getColor(res);
    }
}
