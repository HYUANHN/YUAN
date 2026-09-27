package com.videobox.movie;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.player.LivePlayerActivity;
import com.videobox.movie.ui.FavoriteFragment;
import com.videobox.movie.ui.HomeFragment;
import com.videobox.movie.ui.SettingsFragment;

public class MainActivity extends AppCompatActivity {

    private HomeFragment homeFragment;
    private FavoriteFragment favoriteFragment;
    private SettingsFragment settingsFragment;
    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        homeFragment = new HomeFragment();
        favoriteFragment = new FavoriteFragment();
        settingsFragment = new SettingsFragment();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, settingsFragment, "settings")
                .hide(settingsFragment)
                .add(R.id.container, favoriteFragment, "favorite")
                .hide(favoriteFragment)
                .add(R.id.container, homeFragment, "home")
                .commit();
        current = homeFragment;

        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) showFragment(homeFragment);
            else if (id == R.id.nav_favorite) showFragment(favoriteFragment);
            else if (id == R.id.nav_live) {
                // 电视台：直接全屏直播播放
                startActivity(new Intent(this, LivePlayerActivity.class));
                nav.setSelectedItemId(R.id.nav_home); // 返回时停在首页
                return true;
            }
            else if (id == R.id.nav_settings) showFragment(settingsFragment);
            return true;
        });
        nav.setSelectedItemId(R.id.nav_home);

        showDisclaimerIfFirstLaunch();
    }

    /** 首次启动弹出免责声明；同意后不再弹 */
    private void showDisclaimerIfFirstLaunch() {
        final Prefs prefs = Prefs.get(this);
        if (prefs.isDisclaimerAgreed()) return;
        new AlertDialog.Builder(this)
                .setTitle("免责声明")
                .setMessage(getString(R.string.disclaimer_full))
                .setCancelable(false)
                .setPositiveButton("同意并继续", (d, w) -> prefs.setDisclaimerAgreed(true))
                .setNegativeButton("不同意并退出", (d, w) -> finish())
                .show();
    }

    /**
     * 一级一级返回：
     *  - 非首页 tab 按返回 → 先回到首页
     *  - 首页按返回 → 退到后台（不强制关闭 App）
     */
    @Override
    public void onBackPressed() {
        if (current != null && current != homeFragment) {
            showFragment(homeFragment);
            if (nav != null) nav.setSelectedItemId(R.id.nav_home);
            return;
        }
        moveTaskToBack(true);
    }

    private Fragment current;

    private void showFragment(@NonNull Fragment f) {
        if (current == f) return;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (current != null) tx.hide(current);
        tx.show(f);
        tx.commit();
        current = f;
    }
}
