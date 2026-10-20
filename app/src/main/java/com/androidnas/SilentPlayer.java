package com.androidnas;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class SilentPlayer extends Thread {
    private volatile boolean running = true;
    private AudioTrack track;

    @Override
    public void run() {
        try {
            // 极低配置：8000Hz 采样率，16位单声道，静音
            int minBuf = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf, AudioTrack.MODE_STREAM);
            track.play();

            byte[] silence = new byte[minBuf];
            // 循环写入 0（静音数据），让系统误以为在播放音乐
            while (running) {
                track.write(silence, 0, silence.length);
                try { Thread.sleep(1000); } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            LogManager.add("KEEPALIVE", "SilentPlayer 失败: " + t.getMessage());
        }
    }

    public void stopPlayer() {
        running = false;
        try {
            if (track != null) {
                track.stop();
                track.release();
                track = null;
            }
        } catch (Exception ignored) {}
    }
}
