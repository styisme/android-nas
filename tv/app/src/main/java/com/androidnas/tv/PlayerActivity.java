package com.androidnas.tv;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(com.androidnas.tv.R.layout.activity_player);
        VideoView vv = findViewById(com.androidnas.tv.R.id.videoView);
        TextView tvLoading = findViewById(com.androidnas.tv.R.id.tvLoading);
        String url = getIntent().getStringExtra("url");
        String auth = getIntent().getStringExtra("auth");
        String title = getIntent().getStringExtra("title");
        if (title != null) setTitle(title);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(vv); vv.setMediaController(mc);
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                Map<String, String> h = new HashMap<>();
                if (auth != null) h.put("Authorization", auth);
                vv.setVideoURI(android.net.Uri.parse(url), h);
            } else vv.setVideoURI(android.net.Uri.parse(url));
        } catch (Throwable t) {
            try { vv.setVideoURI(android.net.Uri.parse(url)); }
            catch (Throwable t2) { tvLoading.setText("播放失败: " + t2.getMessage()); return; }
        }
        vv.setOnPreparedListener(mp -> { tvLoading.setVisibility(View.GONE); vv.start(); });
        vv.setOnErrorListener((mp, what, extra) -> { tvLoading.setText("播放失败"); return true; });
    }
}
