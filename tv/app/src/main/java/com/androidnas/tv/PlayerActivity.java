package com.androidnas.tv;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.androidnas.tv.R.layout.activity_player);

        VideoView videoView = findViewById(com.androidnas.tv.R.id.videoView);
        TextView tvLoading = findViewById(com.androidnas.tv.R.id.tvLoading);

        String url = getIntent().getStringExtra("url");
        String auth = getIntent().getStringExtra("auth");
        String title = getIntent().getStringExtra("title");

        if (title != null) setTitle(title);

        // 设置播放控制器 (自带进度条、快进快退)
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        // 设置请求头，防止被服务端鉴权拦截
        Map<String, String> headers = new HashMap<>();
        if (auth != null) {
            headers.put("Authorization", auth);
        }

        try {
            videoView.setVideoURI(Uri.parse(url), headers);
        } catch (Exception e) {
            // 老版本 Android 如果 setVideoURI 不支持 headers，可能会导致播放黑屏
            videoView.setVideoURI(Uri.parse(url));
        }

        videoView.setOnPreparedListener(mp -> {
            tvLoading.setVisibility(View.GONE);
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            tvLoading.setText("播放失败，请检查网络或格式不支持");
            return true;
        });
    }
}
