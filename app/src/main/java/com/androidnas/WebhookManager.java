package com.androidnas;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class WebhookManager {
    
    public static void sendTest() {
        final String url = SettingsManager.getWebhookUrl();
        if (url == null || url.trim().isEmpty()) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String payload = SettingsManager.getWebhookTemplate();
                    if (payload == null || payload.isEmpty()) {
                        payload = "{\"msgtype\":\"text\",\"text\":{\"content\":\"【NAS通知】测试消息\"}}";
                    }
                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    OutputStream os = conn.getOutputStream();
                    os.write(payload.getBytes("UTF-8"));
                    os.close();
                    conn.getResponseCode();
                } catch (Exception ignored) {}
            }
        }).start();
    }
    
    public static void send(final String event, final String detail) {
        final String url = SettingsManager.getWebhookUrl();
        if (url == null || url.trim().isEmpty()) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String payload = "{\"event\":\"" + event + "\",\"detail\":\"" + detail + "\",\"device\":\"" + SettingsManager.getDeviceAlias() + "\"}";
                    // 针对 Telegram 和企业微信做简单适配
                    if (url.contains("telegram.org")) {
                        payload = "chat_id=" + SettingsManager.getWebhookChatId() + "&text=" + URLEncoder.encode("【NAS通知】\n事件: " + event + "\n详情: " + detail, "UTF-8");
                    } else if (url.contains("qyapi.weixin.qq.com")) {
                        payload = "{\"msgtype\":\"text\",\"text\":{\"content\":\"【NAS通知】\n事件: " + event + "\n详情: " + detail + "\"}}";
                    }

                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setRequestProperty("Content-Type", url.contains("telegram") ? "application/x-www-form-urlencoded" : "application/json");
                    
                    OutputStream os = conn.getOutputStream();
                    os.write(payload.getBytes("UTF-8"));
                    os.close();
                    conn.getResponseCode();
                } catch (Exception e) {
                    LogManager.add("Webhook", "推送失败: " + e.getMessage());
                }
            }
        }).start();
    }
}
