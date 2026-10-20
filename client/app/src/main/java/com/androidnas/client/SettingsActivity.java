package com.androidnas.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences sp;
    private TextInputEditText etPort, etTimeout;
    private MaterialSwitch switchAutoScan;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        sp = getSharedPreferences("settings", Context.MODE_PRIVATE);
        MaterialToolbar toolbar = findViewById(R.id.toolbar); setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        RadioGroup g = findViewById(R.id.themeGroup);
        int t = sp.getInt("theme", 0);
        if (t == 1) g.check(R.id.radioLight);
        else if (t == 2) g.check(R.id.radioDark);
        else g.check(R.id.radioSystem);
        g.setOnCheckedChangeListener((gg, id) -> {
            int m = 0;
            if (id == R.id.radioLight) { m = 1; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); }
            else if (id == R.id.radioDark) { m = 2; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); }
            else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            sp.edit().putInt("theme", m).apply();
        });
        etPort = findViewById(R.id.etScanPort); etTimeout = findViewById(R.id.etScanTimeout);
        switchAutoScan = findViewById(R.id.switchAutoScan);
        etPort.setText(String.valueOf(sp.getInt("scan_port", 8080)));
        etTimeout.setText(String.valueOf(sp.getInt("scan_timeout", 500)));
        if (switchAutoScan != null) {
            switchAutoScan.setChecked(sp.getBoolean("auto_scan", true));
            switchAutoScan.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("auto_scan", c).apply());
        }
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            if (etPort != null) {
                String s = etPort.getText() != null ? etPort.getText().toString().trim() : "";
                if (!s.isEmpty()) { int p = Integer.parseInt(s); if (p >= 1 && p <= 65535) sp.edit().putInt("scan_port", p).apply(); }
            }
            if (etTimeout != null) {
                String s = etTimeout.getText() != null ? etTimeout.getText().toString().trim() : "";
                if (!s.isEmpty()) { int t = Integer.parseInt(s); if (t > 0 && t <= 60000) sp.edit().putInt("scan_timeout", t).apply(); }
            }
        } catch (Exception ignored) {}
    }
}
