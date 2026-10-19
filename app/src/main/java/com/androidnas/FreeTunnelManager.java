package com.androidnas;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeTunnelManager {
    public static final int SERVICE_LOCALHOST_RUN = 1;
    public static final int SERVICE_TUNNL_GG = 2;
    public static final int SERVICE_SKYTUNNEL = 3;
    public static final int SERVICE_PINGGY = 4;

    private static Session session;
    private static String publicUrl = "";
    private static boolean isConnected = false;

    public static boolean isRunning() { return isConnected; }
    public static String getPublicUrl() { return publicUrl; }

    public static synchronized void start(final int localPort, final int serviceType) {
        if (isConnected) return;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                String host = "";
                int port = 22;
                int remotePort = 80;
                String regex = "";

                switch (serviceType) {
                    case SERVICE_TUNNL_GG:
                        host = "proxy.tunnl.gg";
                        remotePort = 80;
                        regex = "https://[a-zA-Z0-9.-]+\\.tunnl\\.gg";
                        break;
                    case SERVICE_SKYTUNNEL:
                        host = "skytunnel.pp.ua";
                        remotePort = 443;
                        regex = "https://[a-zA-Z0-9.-]+\\.skytunnel\\.pp\\.ua";
                        break;
                    case SERVICE_PINGGY:
                        host = "a.pinggy.io";
                        remotePort = 80;
                        regex = "https://[a-zA-Z0-9.-]+\\.pinggy\\.link";
                        break;
                    case SERVICE_LOCALHOST_RUN:
                    default:
                        host = "localhost.run";
                        remotePort = 80;
                        regex = "https://[a-zA-Z0-9.-]+\\.life";
                        break;
                }

                session = jsch.getSession("nokey", host, port);
                session.setConfig("StrictHostKeyChecking", "no");
                
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                session.setOutputStream(out);
                session.setTimeout(15000);
                session.connect();
                
                // 映射端口
                session.setPortForwardingR(remotePort, "127.0.0.1", localPort);
                isConnected = true;
                LogManager.add("127.0.0.1", "免费穿透启动 (" + host + ")");

                Pattern pattern = Pattern.compile(regex);
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
