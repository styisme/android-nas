package com.androidnas.tv;

import java.io.*;
import java.net.*;

public class TvSyncServer extends Thread {
    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private volatile boolean running = true;
    private MainActivity activity;
    private int port = 8090;

    public TvSyncServer(MainActivity activity) {
        this.activity = activity;
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (udpSocket != null) udpSocket.close(); } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        // 启动 UDP 广播响应 (让手机能发现电视)
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(port);
                byte[] buf = new byte[1024];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    udpSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    if (msg.equals("DISCOVER_TV")) {
                        String reply = "TV_HERE";
                        byte[] resBuf = reply.getBytes("UTF-8");
                        DatagramPacket resPacket = new DatagramPacket(resBuf, resBuf.length, packet.getAddress(), packet.getPort());
                        udpSocket.send(resPacket);
                    }
                }
            } catch (Exception ignored) {}
        }).start();

        // 启动 HTTP 接收 (接收配置和播放指令)
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (Exception ignored) {}
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                String line = reader.readLine();
                if (line == null) return;
                
                String[] parts = line.split(" ");
                if (parts.length < 2) return;
                String uri = parts[1];

                int contentLength = 0;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                String body = "";
                if (contentLength > 0) {
                    char[] buf = new char[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int r = reader.read(buf, read, contentLength - read);
                        if (r == -1) break;
                        read += r;
                    }
                    body = new String(buf);
                }

                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");

                if (uri.startsWith("/sync")) {
                    String[] params = body.split("&");
                    String ip = "", user = "", pass = "";
                    for (String p : params) {
                        if (p.startsWith("ip=")) ip = p.substring(3);
                        if (p.startsWith("user=")) user = p.substring(5);
                        if (p.startsWith("pass=")) pass = p.substring(5);
                    }
                    activity.updateNasConfig(ip, user, pass);
                } else if (uri.startsWith("/play")) {
                    String videoUrl = uri.substring(6);
                    videoUrl = URLDecoder.decode(videoUrl, "UTF-8");
                    activity.playVideoDirectly(videoUrl);
                }
            } catch (Exception ignored) {
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }
}
