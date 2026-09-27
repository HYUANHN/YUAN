package com.videobox.movie.player;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;

import com.videobox.movie.net.AdFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 广告过滤数据源：拦截 m3u8 播放列表，剔除广告分片后再交给播放器。
 * 对非 m3u8 资源（mp4 等）透传。
 */
@UnstableApi
public class FilteringDataSource implements DataSource {

    private final DataSource base;
    private final List<String> keywords;
    private byte[] content;
    private int pos;
    private boolean filtered;

    public FilteringDataSource(DataSource base, List<String> keywords) {
        this.base = base;
        this.keywords = keywords;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        base.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        long len = base.open(dataSpec);
        filtered = false;
        if (dataSpec.uri != null && dataSpec.uri.toString().toLowerCase().contains(".m3u8")) {
            // 读取整个 m3u8 并过滤
            byte[] raw = readAll();
            String text = new String(raw, StandardCharsets.UTF_8);
            String filteredText = AdFilter.filterM3u8(text, keywords);
            content = filteredText.getBytes(StandardCharsets.UTF_8);
            pos = 0;
            filtered = true;
            return content.length;
        }
        return len;
    }

    private byte[] readAll() throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = base.read(buf, 0, buf.length)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (filtered) {
            if (pos >= content.length) return C.RESULT_END_OF_INPUT;
            int toCopy = Math.min(length, content.length - pos);
            System.arraycopy(content, pos, buffer, offset, toCopy);
            pos += toCopy;
            return toCopy;
        }
        return base.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return base.getUri();
    }

    @Override
    public void close() throws IOException {
        base.close();
    }
}
