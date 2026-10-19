package com.androidnas;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SshTunnelManager {
    private static Session session;
    private static Thread keepAliveThread;

    public static boolean isRunning() {
        return session != null && session.isConnected();
    }

    public static synchronized boolean start(final String host, final int port, final String user, final String pass, final int remotePort, final int localPort) {
        if (isRunning()) return true;
        new Thread(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(user, host, port);
                session.setPassword(pass);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setTimeout(15000);
                session.connect();
                
                // 将远端的 remotePort 映射到本地的 localPort
                session.setPortForwardingR(remotePort, "127.0.0.1", localPort);
                LogManager.add("127.0.0.1", "SSH穿透启动: " + host + ":" + remotePort + " -> 127.0.0.1:" + localPort);

                keepAliveThread = new Thread(() -> {
                    while (isRunning()) {
                        try { 
                            Thread.sleep(10000); 
                            if (session != null) session.sendKeepAliveMsg();
                        } catch (Exception ignored) {}
                    }
                });
                keepAliveThread.start();
            } catch (Exception e) {
                e.printStackTrace();
                LogManager.add("127.0.0.1", "SSH穿透失败: " + e.getMessage());
                stop();
            }
        }).start();
        return true;
    }

    public static synchronized void stop() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        session = null;
        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            keepAliveThread = null;
        }
    }
}
