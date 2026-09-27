package com.videobox.movie.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.videobox.movie.R;
import com.videobox.movie.data.Prefs;

import java.util.ArrayList;
import java.util.List;

public class AdBlockListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adblock);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        EditText et = findViewById(R.id.et_keywords);
        List<String> list = Prefs.get(this).getAdKeywords();
        et.setText(TextUtils.join("\n", list));

        Button btn = findViewById(R.id.btn_save);
        btn.setOnClickListener(v -> {
            String text = et.getText().toString();
            List<String> newList = new ArrayList<>();
            for (String line : text.split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) newList.add(s);
            }
            Prefs.get(this).saveAdKeywords(newList);
            Toast.makeText(this, "黑名单已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
