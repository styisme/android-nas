package com.androidnas;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramSocket;

public class DlnaServer extends Thread {
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private volatile boolean running = true;
    private Context context;
    private MulticastSocket socket;
    private WifiManager.MulticastLock multicastLock;
    private InetAddress group;

    public DlnaServer(Context context) { this.context = context; }

    @Override public void run() {
        if (Build.VERSION.SDK_INT < 12) {
            LogManager.add("DLNA", "API<12 不支持 MulticastLock，跳过");
            return;
        }
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("DlnaLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();
            group = InetAddress.getByName(SSDP_ADDR);
            socket = new MulticastSocket(SSDP_PORT);
            socket.setReuseAddress(true);
            socket.joinGroup(group);
            byte[] buf = new byte[2048];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                if (msg.startsWith("M-SEARCH")) {
                    String ip = getLocalIpAddress();
                    String response = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\n" +
                        "LOCATION: http://" + ip + ":" + SettingsManager.getWebPort() + "/dlna/description.xml\r\n" +
                        "SERVER: Android/1.0 UPnP/1.0 AndroidNAS/3.0\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n" +
                        "USN: uuid:" + SettingsManager.getFingerprint() + "::urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
                    byte[] resBuf = response.getBytes("UTF-8");
                    DatagramPacket resPacket = new DatagramPacket(resBuf, resBuf.length, packet.getAddress(), packet.getPort());
                    DatagramSocket replySocket = new DatagramSocket();
                    replySocket.send(resPacket);
                    replySocket.close();
                }
            }
        } catch (Exception e) { if (running) e.printStackTrace(); }
        finally {
            try { if (socket != null && group != null && !socket.isClosed()) socket.leaveGroup(group); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            if (multicastLock != null && multicastLock.isHeld()) { try { multicastLock.release(); } catch (Exception ignored) {} }
        }
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Exception e) { return "127.0.0.1"; }
    }

    public void stopServer() {
        running = false;
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }
}
