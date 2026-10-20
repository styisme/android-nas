package com.androidnas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 当闹钟、亮屏、解锁等事件触发时，如果服务被系统杀了，自动拉起服务
        if (!HttpServerService.isRunning()) {
            Intent serviceIntent = new Intent(context, HttpServerService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
