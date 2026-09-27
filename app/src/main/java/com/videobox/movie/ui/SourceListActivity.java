package com.videobox.movie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.videobox.movie.R;
import com.videobox.movie.data.PlaySource;
import com.videobox.movie.data.Prefs;

import java.util.List;

/** 源地址列表：展示所有已保存的播放源，点一下跳转源管理并预填地址 */
public class SourceListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_list);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        // 添加：弹出对话框填写名字/地址后保存（不跳转）
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());

        RecyclerView rv = findViewById(R.id.rv_sources);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
    }

    private Adapter adapter;

    /** 添加源弹窗：填名字 + 地址（api）后保存 */
    private void showAddDialog() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, 0);

        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("源名字（如：量子资源）");
        etName.setTextColor(0xFFF2F4F7);
        etName.setHintTextColor(0xFF9AA3AF);
        etName.setSingleLine(true);
        box.addView(etName);

        final android.widget.EditText etAddr = new android.widget.EditText(this);
        etAddr.setHint("接口地址（如：https://cj.lziapi.com/api.php/provide/vod/from/lzm3u8/）");
        etAddr.setTextColor(0xFFF2F4F7);
        etAddr.setHintTextColor(0xFF9AA3AF);
        android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(8);
        etAddr.setLayoutParams(lp2);
        box.addView(etAddr);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("添加播放源")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String addr = etAddr.getText().toString().trim();
                    if (name.isEmpty() || addr.isEmpty()) {
                        Toast.makeText(this, "名字和地址都不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PlaySource ps = new PlaySource();
                    ps.name = name;
                    ps.api = addr;
                    ps.url = addr;
                    Prefs.get(this).recordSource(ps);
                    adapter = new Adapter();
                    ((androidx.recyclerview.widget.RecyclerView) findViewById(R.id.rv_sources)).setAdapter(adapter);
                    Toast.makeText(this, "已添加：" + name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 编辑源弹窗：预填名字/地址，改完保存（不跳转） */
    private void showEditDialog(final PlaySource ps) {
        String curUrl = ps.api != null && !ps.api.trim().isEmpty() ? ps.api : ps.url;

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, 0);

        final android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("源名字");
        etName.setText(ps.name == null ? "" : ps.name);
        etName.setTextColor(0xFFF2F4F7);
        etName.setHintTextColor(0xFF9AA3AF);
        etName.setSingleLine(true);
        box.addView(etName);

        final android.widget.EditText etAddr = new android.widget.EditText(this);
        etAddr.setHint("接口地址");
        etAddr.setText(curUrl == null ? "" : curUrl);
        etAddr.setTextColor(0xFFF2F4F7);
        etAddr.setHintTextColor(0xFF9AA3AF);
        android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(8);
        etAddr.setLayoutParams(lp2);
        box.addView(etAddr);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("编辑播放源")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String addr = etAddr.getText().toString().trim();
                    if (name.isEmpty() || addr.isEmpty()) {
                        Toast.makeText(this, "名字和地址都不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 移除旧的，写入新的（地址变了也能正确替换）
                    Prefs.get(this).removeSource(ps);
                    PlaySource np = new PlaySource();
                    np.name = name;
                    np.api = addr;
                    np.url = addr;
                    Prefs.get(this).recordSource(np);
                    adapter = new Adapter();
                    ((androidx.recyclerview.widget.RecyclerView) findViewById(R.id.rv_sources)).setAdapter(adapter);
                    Toast.makeText(this, "已更新：" + name, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<PlaySource> data = Prefs.get(SourceListActivity.this).getAllSources();

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_source_list, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PlaySource ps = data.get(position);
            h.name.setText(ps.name);
            String url = ps.api != null && !ps.api.trim().isEmpty() ? ps.api : ps.url;
            h.desc.setText(url == null ? "" : url);
            h.desc.setVisibility(View.VISIBLE);
            // 编辑/点行：带入地址到源管理
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(SourceListActivity.this, SourceManagerActivity.class);
                i.putExtra("prefill_url", url);
                startActivity(i);
                Toast.makeText(SourceListActivity.this, "已带入地址：" + ps.name, Toast.LENGTH_SHORT).show();
            });
            h.edit.setOnClickListener(v -> showEditDialog(ps));
            // 删除（带二次确认）
            h.del.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(SourceListActivity.this)
                        .setTitle("删除播放源")
                        .setMessage("确定要删除播放源【" + ps.name + "】吗？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            Prefs.get(SourceListActivity.this).removeSource(ps);
                            Toast.makeText(SourceListActivity.this, "已删除：" + ps.name, Toast.LENGTH_SHORT).show();
                            data.remove(position);
                            notifyItemRemoved(position);
                        })
                        .show();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, desc, edit, del;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_source_name);
                desc = v.findViewById(R.id.tv_source_desc);
                edit = v.findViewById(R.id.btn_edit_source);
                del = v.findViewById(R.id.btn_del_source);
            }
        }
    }
}
