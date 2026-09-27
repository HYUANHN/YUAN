package com.videobox.movie.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.videobox.movie.R;
import com.videobox.movie.data.Category;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.data.VodItem;
import com.videobox.movie.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 分类首页：主分类 + 子分类 + 数据网格（分类从播放源动态读取）。
 */
public class HomeFragment extends Fragment {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private SwipeRefreshLayout refresh;
    private RecyclerView rvGrid, rvMainTabs, rvSubTabs;
    private TextView tvTitle;
    private TextView tvSourceHint;
    private TextView btnSettings;

    private VodAdapter vodAdapter;
    private CatTabAdapter mainTabAdapter, subTabAdapter;

    private PlaySource source;
    private String lastApiBase = "";
    private String lastBlockedKey = "";

    private final List<Category> allCats = new ArrayList<>();
    private Category currentMain;   // 当前主分类
    private Category currentSub;    // 当前子分类（可能为空 → 用主分类加载）
    private boolean loadingCats = false;
    private int currentPage = 1;          // 当前分类已加载到第几页
    private boolean loadingMore = false;  // 防止滚动时重复加载
    private boolean noMore = false;       // 该分类已到底

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        tvTitle = root.findViewById(R.id.tv_home_title);
        tvSourceHint = root.findViewById(R.id.tv_source_hint);
        btnSettings = root.findViewById(R.id.btn_settings);
        refresh = root.findViewById(R.id.refresh);
        rvGrid = root.findViewById(R.id.rv_grid);
        rvMainTabs = root.findViewById(R.id.rv_main_tabs);
        rvSubTabs = root.findViewById(R.id.rv_sub_tabs);

        GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return vodAdapter.isFooter(position) ? 2 : 1;
            }
        });
        rvGrid.setLayoutManager(glm);
        vodAdapter = new VodAdapter();
        vodAdapter.setOnItemClick(item -> Ui.openDetail(requireContext(), item));
        rvGrid.setAdapter(vodAdapter);
        // 无限滚动：滑到底部自动加载下一页
        rvGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof GridLayoutManager)) return;
                int last = ((GridLayoutManager) lm).findLastVisibleItemPosition();
                if (last >= lm.getItemCount() - 5) loadMore();
            }
        });

        rvMainTabs.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        mainTabAdapter = new CatTabAdapter((cat, idx) -> {
            currentMain = cat;
            currentSub = null;
            loadSubCats();
        });
        rvMainTabs.setAdapter(mainTabAdapter);

        rvSubTabs.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        subTabAdapter = new CatTabAdapter((cat, idx) -> {
            currentSub = cat;
            loadData(cat.id);
        });
        rvSubTabs.setAdapter(subTabAdapter);

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SourcePickerActivity.class)));
        ImageView btnSearch = root.findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SearchActivity.class)));

        refresh.setOnRefreshListener(() -> {
            if (source == null) { refresh.setRefreshing(false); return; }
            int id = currentSub != null ? currentSub.id : (currentMain != null ? currentMain.id : 0);
            loadData(id);
        });
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        reloadOnShow(); // 冷启动/回到前台都自动刷新
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadOnShow();
    }

    /** 底部导航用 hide/show 切换，切回首页时不触发 onResume，需在此自动刷新 */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) reloadOnShow();
    }

    /** 进入/切回首页时自动刷新（无需手动下拉） */
    private void reloadOnShow() {
        source = Prefs.get(requireContext()).getPlaySource();
        // 标题固定为应用名，当前源名显示在提示行
        tvTitle.setText("影视YUAN");
        if (source == null) {
            tvSourceHint.setText("分类展示有限请搜索");
            vodAdapter.setData(new ArrayList<>());
            mainTabAdapter.setData(new ArrayList<>());
            subTabAdapter.setData(new ArrayList<>());
            Toast.makeText(requireContext(), "请先到【设置】配置播放源接口", Toast.LENGTH_LONG).show();
            return;
        }
        String sname = source.name == null || source.name.isEmpty() ? "当前源" : source.name;
        tvSourceHint.setText(sname + " · 分类展示有限请搜索");
        String api = source.getApiBase();
        // 屏蔽分类集合变化 → 强制重载分类树，实现屏蔽即时生效
        String blockedKey = blockedKey(Prefs.get(requireContext()).getBlockedCats());
        boolean blockedChanged = !blockedKey.equals(lastBlockedKey);
        lastBlockedKey = blockedKey;
        if (!api.equals(lastApiBase) || blockedChanged) {
            lastApiBase = api;
            allCats.clear();
            currentMain = null;
            currentSub = null;
            loadCategories();
        } else if (allCats.isEmpty()) {
            loadCategories();
        } else {
            loadCurrentData();
        }
    }

    private static String blockedKey(java.util.List<String> blocked) {
        if (blocked == null || blocked.isEmpty()) return "";
        java.util.Collections.sort(blocked);
        return String.join("|", blocked);
    }

    /** 拉取当前源的分类树，填充主分类 Tab */
    private void loadCategories() {
        if (loadingCats) return;
        loadingCats = true;
        final String api = source.getApiBase();
        // UI 线程取屏蔽列表，避免后台线程访问 Fragment
        final java.util.Set<String> blocked = new java.util.HashSet<>(
                Prefs.get(requireContext()).getBlockedCats());
        exec.execute(() -> {
            List<Category> cats;
            try {
                cats = ApiClient.fetchCategories(api);
            } catch (Exception e) {
                cats = new ArrayList<>();
            }
            // 过滤被屏蔽的分类
            cats.removeIf(c -> c.name != null && blocked.contains(c.name));
            List<Category> mainCats = new ArrayList<>();
            List<Category> subs = new ArrayList<>();
            for (Category c : cats) {
                if (c.isMain()) mainCats.add(c); else subs.add(c);
            }
            List<Category> finalCats = cats;
            main.post(() -> {
                loadingCats = false;
                if (!isAdded() || getActivity() == null) return;
                allCats.clear();
                allCats.addAll(finalCats);
                if (mainCats.isEmpty()) {
                    // 分类不可用时回退加载"全部"，避免首页空白
                    mainTabAdapter.setData(new ArrayList<>());
                    subTabAdapter.setData(new ArrayList<>());
                    currentMain = null;
                    currentSub = null;
                    loadData(0);
                    return;
                }
                mainTabAdapter.setData(mainCats);
                currentMain = mainCats.get(0);
                loadSubCats();
            });
        });
    }

    /** 加载当前主分类下的子分类 Tab */
    private void loadSubCats() {
        if (currentMain == null) return;
        List<Category> subs = new ArrayList<>();
        for (Category c : allCats) {
            if (!c.isMain() && c.pid == currentMain.id) subs.add(c);
        }
        if (subs.isEmpty()) {
            subTabAdapter.setData(new ArrayList<>());
            currentSub = null;
            loadData(currentMain.id);
        } else {
            subTabAdapter.setData(subs);
            currentSub = subs.get(0);
            loadData(currentSub.id);
        }
    }

    /** 加载某分类 id 的数据（typeId<=0 表示全部） */
    private void loadCurrentData() {
        int id = currentSub != null ? currentSub.id : (currentMain != null ? currentMain.id : 0);
        loadData(id);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadData(final int typeId) {
        if (source == null) { if (refresh != null) refresh.setRefreshing(false); return; }
        currentPage = 1;
        noMore = false;
        fetchPage(typeId, 1, false);
    }

    /** 滑到底自动加载下一页 */
    private void loadMore() {
        if (loadingMore || noMore || source == null || refresh.isRefreshing()) return;
        int id = currentSub != null ? currentSub.id : (currentMain != null ? currentMain.id : 0);
        vodAdapter.setLoading(true); // 底部显示"加载中…"
        fetchPage(id, currentPage + 1, true);
    }

    /** 拉取一页；append=true 时追加到当前列表，否则重置为新列表 */
    private void fetchPage(final int typeId, final int page, final boolean append) {
        loadingMore = true;
        final String api = source.getApiBase();
        exec.execute(() -> {
            try {
                List<VodItem> result = ApiClient.fetchList(api, typeId, page);
                if (result.isEmpty() && page == 1 && typeId > 0) {
                    result = ApiClient.fetchList(api, 0, 1); // 首页空则回退全部
                }
                final List<VodItem> out = result;
                main.post(() -> {
                    loadingMore = false;
                    if (getActivity() == null) return;
                    if (append) {
                        if (!out.isEmpty()) {
                            currentPage = page;
                            vodAdapter.addData(out);
                        } else {
                            noMore = true; // 没有更多了
                        }
                        vodAdapter.setLoading(false); // 隐藏底部"加载中…"
                    } else {
                        currentPage = 1;
                        vodAdapter.setData(out);
                    }
                    refresh.setRefreshing(false);
                    if (!out.isEmpty()) PosterFetcher.fetch(out, api, vodAdapter::updateItem);
                });
            } catch (Exception e) {
                main.post(() -> {
                    loadingMore = false;
                    if (append) vodAdapter.setLoading(false);
                    if (getActivity() != null) {
                        Toast.makeText(requireContext(), "加载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        refresh.setRefreshing(false);
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }
}
