package com.androidnas;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Environment;

public class HttpServerService extends Service {

    public static final int PORT = 8080;
    public static final int WEBDAV_PORT = 8081;
    private static final int NOTIFICATION_ID = 1001;
    private static volatile boolean running = false;
    public static volatile String lastError = "";

    public static boolean isRunning() {
        return running;
    }

    private NasHttpServer server;
    private WebDavServer webDavServer;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        
        // 1. 申请 CPU 唤醒锁（防止息屏后 CPU 休眠切断网络）
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidNAS::CpuLock");
            wakeLock.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. 申请 Wi-Fi 高性能锁（防止息屏后 Wi-Fi 芯片进入省电模式）
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidNAS::WifiLock");
            wifiLock.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        lastError = "";
        try {
            server = new NasHttpServer(PORT);
            server.start();
        } catch (Exception e) {
            lastError += "网页服务启动失败: " + e.getMessage() + "\n";
        }

        try {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            webDavServer = new WebDavServer(rootDir);
            webDavServer.startServer(WEBDAV_PORT);
        } catch (Exception e) {
            lastError += "网络邻居启动失败(端口被占): " + e.getMessage() + "\n";
        }
        
        running = lastError.isEmpty();
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                // Android 8.0+ 必须指定 NotificationChannel
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        "nas_service_channel", "NAS 后台服务", android.app.NotificationManager.IMPORTANCE_LOW);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(channel);
                
                n = new Notification.Builder(this, "nas_service_channel")
                        .setContentTitle("Android NAS")
                        .setContentText("文件服务运行中，请勿关闭")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("文件服务运行中，请勿关闭")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 11) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("文件服务运行中，请勿关闭")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).getNotification();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done,
                        "Android NAS 运行中", System.currentTimeMillis());
                try {
                    java.lang.reflect.Method method = n.getClass().getMethod("setLatestEventInfo",
                            android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    method.invoke(n, this, "Android NAS", "文件服务运行中，请勿关闭", pi);
                } catch (Exception e) { e.printStackTrace(); }
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        if (webDavServer != null) webDavServer.stopServer();
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
