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
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("LocalSendLock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            InetAddress group = InetAddress.getByName("224.0.0.167");
            socket = new MulticastSocket(53317);
            socket.joinGroup(group);

            byte[] buf = new byte[2048];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                
                // 防死循环：检查包内是否包含本机的指纹
                if (!msg.contains(LocalSendAuth.getFingerprint())) {
                    String myInfo = LocalSendAuth.getDeviceInfo(53317);
                    byte[] resBuf = myInfo.getBytes("UTF-8");
                    DatagramPacket resPacket = new DatagramPacket(resBuf, resBuf.length, packet.getAddress(), packet.getPort());
                    
                    // 开启独立 Socket 单播回复，避免阻塞
                    DatagramSocket replySocket = new DatagramSocket();
                    replySocket.send(resPacket);
                    replySocket.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        }
    }

    public void stopDiscovery() {
        running = false;
        if (socket != null) socket.close();
    }
}
