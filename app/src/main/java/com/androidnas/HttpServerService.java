package com.androidnas;

import android.app.Notification;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Environment;
import java.util.ArrayList;
import java.util.List;

public class HttpServerService extends Service {
    public static volatile List<String> startedServices = new ArrayList<>();
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
    private LocalSendDiscovery lsDiscovery;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private Handler renewHandler;
    private Runnable renewRunnable;

    @Override public void onCreate() {
        super.onCreate();
        goForeground();
        AuthManager.init(this);
        SettingsManager.init(this);
        startedServices.clear();
        lastError = "";
        boolean lowPower = SettingsManager.isLowPowerMode();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidNAS::CpuLock");
            wakeLock.acquire(WAKELOCK_TIMEOUT);
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = lowPower ? wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "AndroidNAS::WifiLow")
                                : wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidNAS::WifiHigh");
            wifiLock.acquire();
            renewHandler = new Handler();
            renewRunnable = new Runnable() {
                @Override public void run() {
                    try { if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(WAKELOCK_TIMEOUT); } catch (Exception ignored) {}
                    if (renewHandler != null) renewHandler.postDelayed(this, 5 * 60 * 60 * 1000L);
                }
            };
            renewHandler.postDelayed(renewRunnable, 5 * 60 * 60 * 1000L);
        } catch (Exception e) { e.printStackTrace(); }

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        boolean ok = true;
        try { serverHttp = new NasHttpServer(SettingsManager.getWebPort()); serverHttp.start(); startedServices.add("Web"); }
        catch (Exception e) { lastError += "Web: " + e.getMessage() + "\n"; ok = false; }
        if (SettingsManager.isWebDavEnabled()) try {
            webDavHttp = new WebDavServer(root); webDavHttp.startServer(SettingsManager.getWebDavPort()); startedServices.add("WebDAV");
        } catch (Exception e) { lastError += "WebDAV: " + e.getMessage() + "\n"; ok = false; }
        if (SettingsManager.isFtpEnabled()) try {
            ftpServer = new FtpServer(SettingsManager.getFtpPort(), this); ftpServer.start(); startedServices.add("FTP");
        } catch (Exception e) { lastError += "FTP: " + e.getMessage() + "\n"; ok = false; }
        if (SettingsManager.isDlnaEnabled()) try {
            dlnaServer = new DlnaServer(this); dlnaServer.start(); startedServices.add("DLNA");
        } catch (Exception e) { lastError += "DLNA: " + e.getMessage() + "\n"; ok = false; }
        if (lowPower) { FreeTunnelManager.stop(); SshTunnelManager.stop(); }
        else if (SettingsManager.isLocalSendEnabled()) try {
            lsServer = new LocalSendServer(53317); lsServer.start();
            lsDiscovery = new LocalSendDiscovery(this); lsDiscovery.start();
            startedServices.add("LocalSend");
        } catch (Exception e) { lastError += "LocalSend: " + e.getMessage() + "\n"; ok = false; }
        running = ok || startedServices.contains("Web");
    }


    // 使用 AlarmManager 定时唤醒，防止被系统清理后彻底死亡
    private void setupAlarmKeepAlive() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(this, AlarmReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, flags);
            
            // 每 5 分钟触发一次唤醒检查
            long interval = 5 * 60 * 1000L;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + interval, pi);
            } else {
                am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + interval, interval, pi);
            }
        } catch (Exception ignored) {}
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                        "nas_service_channel", "NAS 后台服务", android.app.NotificationManager.IMPORTANCE_LOW);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
                n = new Notification.Builder(this, "nas_service_channel").setContentTitle("Android NAS")
                        .setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this).setContentTitle("Android NAS")
                        .setContentText("文件服务运行中").setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done, "Android NAS", System.currentTimeMillis());
                try {
                    java.lang.reflect.Method m = n.getClass().getMethod("setLatestEventInfo",
                        android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    m.invoke(n, this, "Android NAS", "运行中", pi);
                } catch (Exception ignored) {}
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @Override public int onStartCommand(Intent i, int f, int s) { return START_STICKY; }

    @Override public void onDestroy() {
        try { FreeTunnelManager.stop(); } catch (Exception ignored) {}
        try { SshTunnelManager.stop(); } catch (Exception ignored) {}
        if (renewHandler != null) { renewHandler.removeCallbacks(renewRunnable); renewHandler = null; }
        if (serverHttp != null) serverHttp.stop();
        if (webDavHttp != null) webDavHttp.stopServer();
        if (ftpServer != null) ftpServer.stopServer();
        if (dlnaServer != null) dlnaServer.stopServer();
        if (lsServer != null) lsServer.stop();
        if (lsDiscovery != null) lsDiscovery.stopDiscovery();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
