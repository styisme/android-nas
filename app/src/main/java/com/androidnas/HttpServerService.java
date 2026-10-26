package com.androidnas;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Environment;
import java.io.File;
import javax.net.ssl.SSLServerSocketFactory;
import java.util.ArrayList;
import java.util.List;

public class HttpServerService extends Service {
    public static volatile List<String> startedServices = new ArrayList<String>();
    public static volatile String lastError = "";
    private static volatile boolean running = false;
    private static final int NOTIFICATION_ID = 1001;
    private static final long WAKELOCK_TIMEOUT = 6 * 60 * 60 * 1000L;

    public static boolean isRunning() { return running; }

    private NasHttpServer serverHttp;
    private WebDavServer webDavHttp;
    private FtpServer ftpServer;
    private DlnaServer dlnaServer;
    private LocalSendServer lsServer;
    private SilentPlayer silentPlayer;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override public void onCreate() {
        super.onCreate();
        goForeground();
        AuthManager.init(this);
        SettingsManager.init(this);
        running = false;
        startedServices.clear();
        lastError = "";

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidNAS::CpuLock");
            wakeLock.acquire(WAKELOCK_TIMEOUT);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidNAS::WifiHigh");
            wifiLock.acquire();
        } catch (Exception ignored) {}

        // 初始化底层监听器 (解决 6, 29)
        RemoteDesktopManager.init(this);
        BatteryProtector.init(this);
        SmsManager.init(this);
        if (SettingsManager.isSilentKeepAlive()) {
            silentPlayer = new SilentPlayer(); silentPlayer.start();
        }

        boolean ok = true;
        try {
            serverHttp = new NasHttpServer(SettingsManager.getWebPort(), this);
            if (SettingsManager.isHttpsEnabled()) {
                SSLServerSocketFactory ssl = CertificateUtils.getSSLFactory();
                if (ssl != null) { serverHttp.makeSecure(ssl, null); startedServices.add("Web(HTTPS)"); }
                else { lastError += "HTTPS 降级为 HTTP\n"; startedServices.add("Web(HTTP)"); }
            } else { startedServices.add("Web(HTTP)"); }
            
            serverHttp.start();
            // 解决 16: 只有 start 不抛异常，才算真正启动
        } catch (Exception e) { 
            lastError += "Web: " + e.getMessage() + "\n"; 
            ok = false; 
            startedServices.remove("Web(HTTP)"); startedServices.remove("Web(HTTPS)");
        }

        if (SettingsManager.isWebDavEnabled()) try {
            webDavHttp = new WebDavServer(Environment.getExternalStorageDirectory().getAbsolutePath());
            webDavHttp.startServer(SettingsManager.getWebDavPort()); startedServices.add("WebDAV");
        } catch (Exception e) { lastError += "WebDAV: " + e.getMessage() + "\n"; ok = false; }
        
        if (SettingsManager.isFtpEnabled()) try {
            ftpServer = new FtpServer(SettingsManager.getFtpPort(), this); ftpServer.start(); startedServices.add("FTP");
        } catch (Exception e) { lastError += "FTP: " + e.getMessage() + "\n"; ok = false; }

        // 解决 7: 真正启动 DLNA 与 LocalSend
        if (SettingsManager.isDlnaEnabled()) try {
            dlnaServer = new DlnaServer(this); dlnaServer.start(); startedServices.add("DLNA");
        } catch (Exception e) { lastError += "DLNA: " + e.getMessage() + "\n"; ok = false; }

        if (SettingsManager.isLocalSendEnabled()) try {
            lsServer = new LocalSendServer(53317); lsServer.start(); startedServices.add("LocalSend");
        } catch (Exception e) { lastError += "LocalSend: " + e.getMessage() + "\n"; ok = false; }

        running = ok;
        startCleanupTask();
    }

    private void goForeground() {
        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel("nas_service_channel", "NAS 后台服务", android.app.NotificationManager.IMPORTANCE_LOW);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
                n = new Notification.Builder(this, "nas_service_channel").setContentTitle("Android NAS").setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this).setContentTitle("Android NAS").setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(pi).setOngoing(true).build();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done, "Android NAS", System.currentTimeMillis());
                try { java.lang.reflect.Method m = n.getClass().getMethod("setLatestEventInfo", android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class); m.invoke(n, this, "Android NAS", "运行中", pi); } catch (Exception ignored) {}
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void startCleanupTask() {
        new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    try { Thread.sleep(6 * 60 * 60 * 1000L); cleanOldParts(Environment.getExternalStorageDirectory(), 0); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void cleanOldParts(File dir, int depth) {
        if (dir == null || !dir.exists() || depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) {
            if (f.isDirectory()) { if (!f.getName().startsWith(".")) cleanOldParts(f, depth + 1); } 
            else if (f.getName().endsWith(".part") && now - f.lastModified() > 24 * 60 * 60 * 1000L) f.delete();
        }
    }

    @Override public int onStartCommand(Intent i, int f, int s) { return START_STICKY; }

    @Override public void onDestroy() {
        if (serverHttp != null) serverHttp.stop();
        if (webDavHttp != null) webDavHttp.stopServer();
        if (ftpServer != null) ftpServer.stopServer();
        if (dlnaServer != null) dlnaServer.stopServer();
        if (lsServer != null) lsServer.stop();
        if (silentPlayer != null) silentPlayer.stopPlayer();
        
        RemoteDesktopManager.stopStream(); // 解决 17: 关闭 WS
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
