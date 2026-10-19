package com.androidnas.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sp = getSharedPreferences("settings", Context.MODE_PRIVATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 主题设置
        RadioGroup group = findViewById(R.id.themeGroup);
        int theme = sp.getInt("theme", 0);
        if (theme == 1) group.check(R.id.radioLight);
        else if (theme == 2) group.check(R.id.radioDark);
        else group.check(R.id.radioSystem);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int mode = 0;
            if (checkedId == R.id.radioLight) {
                mode = 1;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.radioDark) {
                mode = 2;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            sp.edit().putInt("theme", mode).apply();
        });

        // 扫描超时
        com.google.android.material.textfield.TextInputEditText etPort = findViewById(R.id.etScanPort);
        etPort.setText(String.valueOf(sp.getInt("scan_port", 8080)));
        etPort.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int port = Integer.parseInt(etPort.getText().toString());
                    sp.edit().putInt("scan_port", port).apply();
                } catch (Exception ignored) {}
            }
        });
        
        TextInputEditText etTimeout = findViewById(R.id.etScanTimeout);
        etTimeout.setText(String.valueOf(sp.getInt("scan_timeout", 500)));
        etTimeout.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int timeout = Integer.parseInt(etTimeout.getText().toString());
                    sp.edit().putInt("scan_timeout", timeout).apply();
                } catch (Exception ignored) {}
            }
        });

        // 自动扫描
        MaterialSwitch switchAutoScan = findViewById(R.id.switchAutoScan);
        switchAutoScan.setChecked(sp.getBoolean("auto_scan", true));
        switchAutoScan.setOnCheckedChangeListener((v, isChecked) -> sp.edit().putBoolean("auto_scan", isChecked).apply());
    }
}
