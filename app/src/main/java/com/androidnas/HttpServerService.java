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
import java.util.ArrayList;
import java.util.List;

public class HttpServerService extends Service {
    public static volatile List<String> startedServices = new ArrayList<>();
    public static volatile String lastError = "";
    private static volatile boolean running = false;
    private static final int NOTIFICATION_ID = 1001;

    public static boolean isRunning() { return running; }

    private NasHttpServer serverHttp;
    private WebDavServer webDavHttp;
    private LocalSendServer lsServer;
    private LocalSendDiscovery lsDiscovery;
    
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        AuthManager.init(this);
        SettingsManager.init(this);
        
        startedServices.clear();
        lastError = "";

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidNAS::CpuLock");
            wakeLock.acquire();
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidNAS::WifiLock");
            wifiLock.acquire();
        } catch (Exception e) { e.printStackTrace(); }

        String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        boolean isLowPower = SettingsManager.isLowPowerMode();

        // 1. Web 服务 (核心)
        try {
            serverHttp = new NasHttpServer(SettingsManager.getWebPort());
            serverHttp.start();
            startedServices.add("Web");
        } catch (Exception e) { lastError += "Web启动失败: " + e.getMessage() + "\n"; }
        
        // 2. WebDAV (低性能模式下依然保留)
        if (SettingsManager.isWebDavEnabled()) {
            try {
                webDavHttp = new WebDavServer(rootDir);
                webDavHttp.startServer(SettingsManager.getWebDavPort());
                startedServices.add("WebDAV");
            } catch (Exception e) { lastError += "WebDAV启动失败: " + e.getMessage() + "\n"; }
        }

        // 3. LocalSend (低性能模式下强制关闭)
        if (!isLowPower && SettingsManager.isLocalSendEnabled()) {
            try {
                lsServer = new LocalSendServer(53317);
                lsServer.start();
                lsDiscovery = new LocalSendDiscovery(this);
                lsDiscovery.start();
                startedServices.add("LocalSend");
            } catch (Exception e) { lastError += "LocalSend启动失败: " + e.getMessage() + "\n"; }
        }
        
        running = true;
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel("nas_service_channel", "NAS 后台服务", android.app.NotificationManager.IMPORTANCE_LOW);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(channel);
                n = new Notification.Builder(this, "nas_service_channel").setContentTitle("Android NAS").setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this).setContentTitle("Android NAS").setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setOngoing(true).build();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done, "Android NAS 运行中", System.currentTimeMillis());
                try {
                    java.lang.reflect.Method method = n.getClass().getMethod("setLatestEventInfo", android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    method.invoke(n, this, "Android NAS", "文件服务运行中", pi);
                } catch (Exception e) { e.printStackTrace(); }
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        if (serverHttp != null) serverHttp.stop();
        if (webDavHttp != null) webDavHttp.stopServer();
        if (lsServer != null) lsServer.stop();
        if (lsDiscovery != null) lsDiscovery.stopDiscovery();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
