package com.androidnas.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.appbar.MaterialToolbar;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        SharedPreferences sp = getSharedPreferences("settings", Context.MODE_PRIVATE);
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
    }
}
