package com.androidnas;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
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

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        lastError = "";
        try {
            server = new NasHttpServer(PORT);
            server.start();
        } catch (Exception e) {
            lastError += "网页服务启动失败: " + e.getMessage() + "\\n";
        }

        try {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            webDavServer = new WebDavServer(rootDir);
            webDavServer.startServer(WEBDAV_PORT);
        } catch (Exception e) {
            lastError += "网络邻居启动失败(端口被占): " + e.getMessage() + "\\n";
        }
        
        running = lastError.isEmpty();
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n;
            if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("网页服务与网络邻居运行中")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 11) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("网页服务与网络邻居运行中")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).getNotification();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done,
                        "Android NAS 运行中", System.currentTimeMillis());
                try {
                    java.lang.reflect.Method method = n.getClass().getMethod("setLatestEventInfo",
                            android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    method.invoke(n, this, "Android NAS", "网页服务与网络邻居运行中", pi);
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
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
