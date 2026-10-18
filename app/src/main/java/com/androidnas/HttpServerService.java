package com.androidnas;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class HttpServerService extends Service {

    public static final int PORT = 8080;
    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    private NasHttpServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        try {
            server = new NasHttpServer(PORT);
            server.start();
            running = true;
        } catch (Exception e) {
            e.printStackTrace();
            running = false;
        }
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0,
                    new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n;
            if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("文件服务运行中 (端口 " + PORT + ")")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .build();
            } else if (Build.VERSION.SDK_INT >= 11) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("文件服务运行中 (端口 " + PORT + ")")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .getNotification();
            } else {
                n = new Notification(
                        android.R.drawable.stat_sys_download_done,
                        "Android NAS 运行中",
                        System.currentTimeMillis());
                n.setLatestEventInfo(this, "Android NAS",
                        "文件服务运行中 (端口 " + PORT + ")", pi);
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
        }
        running = false;
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
