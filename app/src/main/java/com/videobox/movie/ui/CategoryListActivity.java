package com.videobox.movie.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
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
 * 主分类数据页：显示该主分类下的子分类 Tab + 数据网格。
 */
public class CategoryListActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private SwipeRefreshLayout refresh;
    private VodAdapter adapter;
    private CatTabAdapter subTabAdapter;
    private EditText etSearch;
    private String typeName;
    private int mainTypeId;
    private int currentTypeId;
    private int page = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_list);

        typeName = getIntent().getStringExtra("type_name");
        mainTypeId = getIntent().getIntExtra("type", 0);
        currentTypeId = mainTypeId;

        TextView tvTitle = findViewById(R.id.tv_title);
        tvTitle.setText(typeName == null ? "分类" : typeName);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        etSearch = findViewById(R.id.et_search);
        refresh = findViewById(R.id.refresh);
        RecyclerView rv = findViewById(R.id.rv_list);
        rv.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        adapter = new VodAdapter();
        adapter.setOnItemClick(item -> Ui.openDetail(this, item));
        rv.setAdapter(adapter);

        RecyclerView rvSub = findViewById(R.id.rv_sub_tabs);
        rvSub.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        subTabAdapter = new CatTabAdapter((cat, idx) -> {
            currentTypeId = cat.id;
            page = 1;
            load(page, false);
        });
        rvSub.setAdapter(subTabAdapter);

        refresh.setOnRefreshListener(() -> { page = 1; load(page, false); });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String wd = v.getText().toString().trim();
                if (wd.isEmpty()) { page = 1; load(page, false); }
                else search(wd);
                return true;
            }
            return false;
        });

        loadSubCats();
    }

    /** 拉取该主分类下的子分类 */
    private void loadSubCats() {
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) return;
        final String api = source.getApiBase();
        exec.execute(() -> {
            List<Category> cats;
            try {
                cats = ApiClient.fetchCategories(api);
            } catch (Exception e) {
                cats = new ArrayList<>();
            }
            List<Category> subs = new ArrayList<>();
            for (Category c : cats) {
                if (!c.isMain() && c.pid == mainTypeId) subs.add(c);
            }
            main.post(() -> {
                if (subs.isEmpty()) {
                    subTabAdapter.setData(new ArrayList<>());
                    currentTypeId = mainTypeId;
                } else {
                    subTabAdapter.setData(subs);
                    currentTypeId = subs.get(0).id;
                }
                page = 1;
                load(page, false);
            });
        });
    }

    private void load(int pg, boolean append) {
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) {
            refresh.setRefreshing(false);
            Toast.makeText(this, "请先配置播放源接口", Toast.LENGTH_SHORT).show();
            return;
        }
        final String api = source.getApiBase();
        final int tid = currentTypeId;
        exec.execute(() -> {
            try {
                List<VodItem> list = ApiClient.fetchList(api, tid, pg);
                if (list.isEmpty() && tid > 0) {
                    list = ApiClient.fetchList(api, 0, pg); // 空则回退全部
                }
                final List<VodItem> out = list;
                main.post(() -> {
                    if (append) adapter.addData(out); else adapter.setData(out);
                    refresh.setRefreshing(false);
                    PosterFetcher.fetch(out, api, adapter::updateItem);
                });
            } catch (Exception e) {
                main.post(() -> {
                    Toast.makeText(this, "加载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    refresh.setRefreshing(false);
                });
            }
        });
    }

    private void search(String wd) {
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) return;
        exec.execute(() -> {
            try {
                List<VodItem> list = ApiClient.fetchSearch(source.getApiBase(), wd);
                main.post(() -> adapter.setData(list == null ? new ArrayList<>() : list));
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "搜索失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }
}
