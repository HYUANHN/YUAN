package com.videobox.movie;

import android.app.Application;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃捕获：把未捕获异常写入 /files/crash.log，方便排查真机崩溃。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrash(throwable);
            } catch (Exception ignored) { }
            if (def != null) def.uncaughtException(thread, throwable);
        });
    }

    private void writeCrash(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("=== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date()) + " ===");
            t.printStackTrace(pw);
            pw.flush();
            File f = new File(getFilesDir(), "crash.log");
            FileOutputStream fos = new FileOutputStream(f, true);
            fos.write(sw.toString().getBytes("UTF-8"));
            fos.close();
            Log.e("VideoBox", sw.toString());
        } catch (Exception ignored) { }
    }
}
