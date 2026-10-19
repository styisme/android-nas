package com.androidnas;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeTunnelManager {
    private static Session session;
    private static String publicUrl = "";
    private static boolean isConnected = false;

    public static boolean isRunning() { return isConnected; }
    public static String getPublicUrl() { return publicUrl; }

    public static synchronized void start(final int localPort) {
        if (isConnected) return;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                // localhost.run 提供免注册的临时隧道
                session = jsch.getSession("nokey", "localhost.run", 22);
                session.setConfig("StrictHostKeyChecking", "no");
                
                // 捕获服务器的终端输出，用于提取分配的网址
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                session.setOutputStream(out);

                session.setTimeout(15000);
                session.connect();
                
                // 将 localhost.run 的 80 端口映射到手机本地的 8080 端口
                session.setPortForwardingR(80, "127.0.0.1", localPort);
                isConnected = true;
                LogManager.add("127.0.0.1", "免费穿透已启动");

                // 轮询读取输出，抓取类似 https://xxxx.lhr.life 的公网链接
                Pattern pattern = Pattern.compile("https://[a-zA-Z0-9.-]+\\.life");
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(500);
                    String text = out.toString("UTF-8");
                    Matcher matcher = pattern.matcher(text);
                    if (matcher.find()) {
                        publicUrl = matcher.group();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                isConnected = false;
                LogManager.add("127.0.0.1", "免费穿透失败: " + e.getMessage());
            }
        }).start();
    }

    public static synchronized void stop() {
        isConnected = false;
        publicUrl = "";
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        session = null;
    }
}
