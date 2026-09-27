package com.videobox.movie.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.videobox.movie.R;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;
import com.videobox.movie.data.VodItem;
import com.videobox.movie.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 全局搜索页：输入关键词搜索当前源影片 */
public class SearchActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private EditText etKeyword;
    private SwipeRefreshLayout refresh;
    private VodAdapter adapter;
    private String lastKeyword = "";
    private int currentPage = 1;
    private boolean loadingMore = false;
    private boolean noMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etKeyword = findViewById(R.id.et_keyword);
        refresh = findViewById(R.id.refresh);
        RecyclerView rv = findViewById(R.id.rv_result);
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isFooter(position) ? 2 : 1;
            }
        });
        rv.setLayoutManager(glm);
        adapter = new VodAdapter();
        adapter.setOnItemClick(item -> Ui.openDetail(this, item));
        rv.setAdapter(adapter);
        // 无限滚动：滑到底自动加载下一页
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (dy <= 0) return;
                RecyclerView.LayoutManager lm = r.getLayoutManager();
                if (!(lm instanceof GridLayoutManager)) return;
                int last = ((GridLayoutManager) lm).findLastVisibleItemPosition();
                if (last >= lm.getItemCount() - 5) loadMore();
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TextView btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> doSearch(etKeyword.getText().toString().trim()));
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(v.getText().toString().trim());
                return true;
            }
            return false;
        });
        refresh.setOnRefreshListener(() -> {
            if (lastKeyword.isEmpty()) { refresh.setRefreshing(false); return; }
            doSearch(lastKeyword);
        });
    }

    private void doSearch(final String kw) {
        if (kw.isEmpty()) {
            adapter.setData(new ArrayList<>());
            refresh.setRefreshing(false);
            return;
        }
        lastKeyword = kw;
        currentPage = 1;
        noMore = false;
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) {
            refresh.setRefreshing(false);
            Toast.makeText(this, "请先配置播放源接口", Toast.LENGTH_SHORT).show();
            return;
        }
        final String api = source.getApiBase();
        exec.execute(() -> {
            List<VodItem> list;
            try {
                list = ApiClient.fetchSearch(api, kw, 1);
            } catch (Exception e) {
                list = new ArrayList<>();
            }
            final List<VodItem> out = list;
            main.post(() -> {
                if (out.isEmpty()) Toast.makeText(this, "没有找到相关影片", Toast.LENGTH_SHORT).show();
                adapter.setData(out);
                refresh.setRefreshing(false);
                PosterFetcher.fetch(out, api, adapter::updateItem);
            });
        });
    }

    /** 搜索滑到底自动加载下一页 */
    private void loadMore() {
        if (loadingMore || noMore || lastKeyword.isEmpty()) return;
        loadingMore = true;
        adapter.setLoading(true);
        PlaySource source = Prefs.get(this).getPlaySource();
        if (source == null) { adapter.setLoading(false); loadingMore = false; return; }
        final String api = source.getApiBase();
        final int page = currentPage + 1;
        exec.execute(() -> {
            List<VodItem> list;
            try {
                list = ApiClient.fetchSearch(api, lastKeyword, page);
            } catch (Exception e) {
                list = new ArrayList<>();
            }
            final List<VodItem> out = list;
            main.post(() -> {
                loadingMore = false;
                if (!out.isEmpty()) {
                    currentPage = page;
                    adapter.addData(out);
                    PosterFetcher.fetch(out, api, adapter::updateItem);
                } else {
                    noMore = true;
                }
                adapter.setLoading(false);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdown();
    }
}
