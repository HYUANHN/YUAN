package com.videobox.movie;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.net.ApiClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开屏页：品牌加载 UI（图一/图二样式）。
 * 后台预热当前播放源的分类资源，加载完成后自动进入首页。
 */
public class SplashActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 版本号
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.tv_splash_version)).setText("V" + v);
        } catch (Exception ignored) { }

        final TextView status = findViewById(R.id.tv_splash_status);
        final PlaySource source = Prefs.get(this).getPlaySource();
        final long t0 = System.currentTimeMillis();

        exec.execute(() -> {
            boolean ok;
            try {
                if (source != null && source.getApiBase() != null) {
                    // 预热分类资源，保证进首页有数据、不用手动刷新
                    ApiClient.fetchCategories(source.getApiBase());
                    ok = true;
                } else {
                    ok = true; // 未配置源也放行，首页会提示去配置
                }
            } catch (Throwable t) {
                ok = false;
            }
            final boolean loaded = ok;
            main.post(() -> {
                // 至少展示 1.2s，避免一闪而过
                long wait = 1200 - (System.currentTimeMillis() - t0);
                if (wait < 0) wait = 0;
                main.postDelayed(() -> {
                    if (loaded) status.setText("加载成功");
                    main.postDelayed(() -> {
                        Intent i = new Intent(SplashActivity.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    }, loaded ? 650 : 450);
                }, wait);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        exec.shutdown();
    }
}
