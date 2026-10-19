package com.androidnas;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramSocket;

public class LocalSendDiscovery extends Thread {
    private boolean running = true;
    private Context context;
    private MulticastSocket socket;
    private WifiManager.MulticastLock multicastLock;

    public LocalSendDiscovery(Context context) { this.context = context; }

    @Override
    public void run() {
        while (running) {
            try {
                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (multicastLock == null) {
                    multicastLock = wm.createMulticastLock("LocalSendLock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                InetAddress group = InetAddress.getByName("224.0.0.167");
                socket = new MulticastSocket(53317);
                socket.setReuseAddress(true);
                socket.joinGroup(group);

                byte[] buf = new byte[2048];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    
                    if (!msg.contains(SettingsManager.getFingerprint())) {
                        String myInfo = "{\"alias\":\"" + SettingsManager.getDeviceAlias() + "\",\"version\":\"2.2\",\"deviceModel\":\"Android\",\"deviceType\":\"desktop\",\"fingerprint\":\"" + SettingsManager.getFingerprint() + "\",\"port\":53317,\"protocol\":\"http\",\"download\":false}";
                        byte[] resBuf = myInfo.getBytes("UTF-8");
                        DatagramPacket resPacket = new DatagramPacket(resBuf, resBuf.length, packet.getAddress(), packet.getPort());
                        
                        DatagramSocket replySocket = new DatagramSocket();
                        replySocket.send(resPacket);
                        replySocket.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (socket != null) socket.close();
                try { Thread.sleep(3000); } catch (Exception ignored) {} // 断网后等待3秒重连
            }
        }
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
    }

    public void stopDiscovery() {
        running = false;
        if (socket != null) socket.close();
    }
}
