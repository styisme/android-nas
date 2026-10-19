package com.androidnas.client;

import java.io.*;
import java.net.*;
import java.util.*;

public class SyncHelper {
    // 发现电视
    public static String discoverTv() {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(2000);
            
            byte[] sendData = "DISCOVER_TV".getBytes("UTF-8");
            // 向局域网广播地址发送
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 8090);
            socket.send(sendPacket);
            
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);
            
            String reply = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
            socket.close();
            if ("TV_HERE".equals(reply)) {
                return receivePacket.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // 下发配置
    public static boolean sendConfig(String tvIp, String nasIp, String user, String pass) {
        try {
            URL url = new URL("http://" + tvIp + ":8090/sync");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            
            String body = "ip=" + URLEncoder.encode(nasIp, "UTF-8") + 
                          "&user=" + URLEncoder.encode(user, "UTF-8") + 
                          "&pass=" + URLEncoder.encode(pass, "UTF-8");
                          
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
            
            return conn.getResponseCode() == 200;
        } catch (Exception e) { return false; }
    }

    // 投屏播放
    public static boolean playOnTv(String tvIp, String videoUrl) {
        try {
            URL url = new URL("http://" + tvIp + ":8090/play?" + URLEncoder.encode(videoUrl, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            return conn.getResponseCode() == 200;
        } catch (Exception e) { return false; }
    }
}
