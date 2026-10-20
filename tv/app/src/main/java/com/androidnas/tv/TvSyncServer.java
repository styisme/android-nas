package com.androidnas.tv;

import java.io.*;
import java.net.*;

public class TvSyncServer extends Thread {
    private ServerSocket ss; private DatagramSocket udp;
    private volatile boolean running = true;
    private MainActivity activity;
    private int port = 8090;

    public TvSyncServer(MainActivity a) { this.activity = a; }

    public void stopServer() {
        running = false;
        try { if (ss != null) ss.close(); } catch (Exception ignored) {}
        try { if (udp != null) udp.close(); } catch (Exception ignored) {}
    }

    @Override public void run() {
        new Thread(() -> {
            try {
                udp = new DatagramSocket(port);
                byte[] buf = new byte[1024];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udp.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), "UTF-8");
                    if (msg.equals("DISCOVER_TV")) {
                        byte[] r = "TV_HERE".getBytes("UTF-8");
                        udp.send(new DatagramPacket(r, r.length, p.getAddress(), p.getPort()));
                    }
                }
            } catch (Exception ignored) {}
        }).start();
        try {
            ss = new ServerSocket(port);
            while (running) {
                Socket s = ss.accept();
                new Thread(new Handler(s)).start();
            }
        } catch (Exception ignored) {}
    }

    private class Handler implements Runnable {
        private Socket s;
        Handler(Socket x) { s = x; }
        @Override public void run() {
            try {
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                String line = r.readLine();
                if (line == null) return;
                String[] parts = line.split(" ");
                if (parts.length < 2) return;
                String uri = parts[1];
                int contentLen = 0;
                while ((line = r.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        try { contentLen = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) {}
                    }
                }
                StringBuilder body = new StringBuilder();
                if (contentLen > 0) {
                    char[] buf = new char[contentLen];
                    int read = 0;
                    while (read < contentLen) {
                        int n = r.read(buf, read, contentLen - read);
                        if (n == -1) break;
                        read += n;
                    }
                    body.append(buf, 0, read);
                }
                PrintWriter w = new PrintWriter(s.getOutputStream(), true);
                w.println("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                if (uri.startsWith("/sync")) {
                    String[] ps = body.toString().split("&");
                    String ip = "", u = "", pw = "";
                    for (String x : ps) {
                        if (x.startsWith("ip=")) ip = URLDecoder.decode(x.substring(3), "UTF-8");
                        if (x.startsWith("user=")) u = URLDecoder.decode(x.substring(5), "UTF-8");
                        if (x.startsWith("pass=")) pw = URLDecoder.decode(x.substring(5), "UTF-8");
                    }
                    activity.updateNasConfig(ip, u, pw);
                } else if (uri.startsWith("/play")) {
                    String enc = uri.substring(6);
                    if (enc.startsWith("?")) enc = enc.substring(1);
                    String url = URLDecoder.decode(enc, "UTF-8");
                    activity.playVideoDirectly(url);
                }
            } catch (Exception ignored) {
            } finally { try { s.close(); } catch (Exception ignored) {} }
        }
    }
}
