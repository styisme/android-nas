package com.androidnas;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class RemoteService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 绑定主进程，如果主进程死了，这里会收到回调
        return START_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
