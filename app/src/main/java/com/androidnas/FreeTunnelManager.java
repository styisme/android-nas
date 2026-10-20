package com.androidnas;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeTunnelManager {
    private static Session session;
    private static Channel activeChannel;
    private static String publicUrl = "";
    private static volatile boolean isConnected = false;

    public static boolean isRunning() { return isConnected; }
    public static String getPublicUrl() { return publicUrl; }

    private static class Node {
        String name, host, user, regex; int port, remotePort;
        Node(String n, String h, int p, int rp, String u, String r) {
            name = n; host = h; port = p; remotePort = rp; user = u; regex = r;
        }
    }

    private static final List<Node> nodes = new ArrayList<Node>() {{
        add(new Node("Tunnl.gg", "proxy.tunnl.gg", 22, 80, "tunnl", "https://[a-zA-Z0-9.-]+\\.tunnl\\.gg"));
        add(new Node("Skytunnel", "skytunnel.pp.ua", 22, 443, "skytunnel", "https://[a-zA-Z0-9.-]+\\.skytunnel\\.pp\\.ua"));
        add(new Node("Serveo", "serveo.net", 22, 80, "serveo", "https://[a-zA-Z0-9.-]+\\.serveo\\.net"));
        add(new Node("Localhost.run", "localhost.run", 22, 80, "nokey", "https://[a-zA-Z0-9.-]+\\.life"));
        add(new Node("Pinggy", "a.pinggy.io", 22, 80, "pinggy", "https://[a-zA-Z0-9.-]+\\.pinggy\\.link"));
        add(new Node("Tuns.sh", "tuns.sh", 22, 80, "tuns", "https://[a-zA-Z0-9.-]+\\.tuns\\.sh"));
        add(new Node("Localto.net", "localto.net", 22, 80, "localto", "https://[a-zA-Z0-9.-]+\\.localto\\.net"));
    }};

    public static String[] getNodeNames() {
        String[] names = new String[nodes.size() + 1];
        names[0] = "自动选择 (推荐)";
        for (int i = 0; i < nodes.size(); i++) names[i + 1] = nodes.get(i).name;
        return names;
    }

    public static void start(final int localPort, final int nodeIndex) {
        if (isConnected) return;
        new Thread(() -> {
            if (nodeIndex == 0) {
                for (Node node : nodes) {
                    if (isConnected) break;
                    if (tryConnect(node, localPort)) break;
                }
            } else tryConnect(nodes.get(nodeIndex - 1), localPort);
        }).start();
    }

    private static boolean tryConnect(Node node, int localPort) {
        Session tempSession = null;
        Channel channel = null;
        try {
            JSch jsch = new JSch();
            tempSession = jsch.getSession(node.user, node.host, node.port);
            tempSession.setConfig("StrictHostKeyChecking", "no");
            tempSession.setTimeout(5000);
            tempSession.connect(5000);
            tempSession.setPortForwardingR(node.remotePort, "127.0.0.1", localPort);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                channel = tempSession.openChannel("shell");
                channel.setOutputStream(out);
                try {
                    java.lang.reflect.Method m = channel.getClass().getMethod("setErrStream", java.io.OutputStream.class);
                    m.invoke(channel, out);
                } catch (Exception ignored) {}
                channel.connect(5000);
            } catch (Exception e) {
                try { if (channel != null) channel.disconnect(); } catch (Exception ignored) {}
                channel = null;
            }
            LogManager.add("127.0.0.1", "尝试穿透: " + node.name);
            Pattern pattern = Pattern.compile(node.regex);
            for (int i = 0; i < 20; i++) {
                Thread.sleep(500);
                Matcher matcher = pattern.matcher(out.toString("UTF-8"));
                if (matcher.find()) {
                    publicUrl = matcher.group();
                    session = tempSession;
                    activeChannel = channel;
                    isConnected = true;
                    LogManager.add("127.0.0.1", node.name + " 穿透成功!");
                    return true;
                }
            }
            try { if (channel != null) channel.disconnect(); } catch (Exception ignored) {}
            tempSession.disconnect();
            return false;
        } catch (Exception e) {
            LogManager.add("127.0.0.1", node.name + " 失败: " + e.getMessage());
            try { if (channel != null) channel.disconnect(); } catch (Exception ignored) {}
            try { if (tempSession != null) tempSession.disconnect(); } catch (Exception ignored) {}
            return false;
        }
    }

    public static synchronized void stop() {
        isConnected = false;
        publicUrl = "";
        try { if (activeChannel != null) activeChannel.disconnect(); } catch (Exception ignored) {}
        activeChannel = null;
        try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignored) {}
        session = null;
    }
}
