package com.androidnas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryProtector {
    private static float lastTemp = 0;
    private static boolean isCoolingDown = false;
    private static BroadcastReceiver receiver = null;

    public static void init(final Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                lastTemp = temp / 10.0f;
                
                if (lastTemp >= 45.0f && !isCoolingDown) {
                    isCoolingDown = true;
                    WebhookManager.send("⚠️ 电池高温警告", "当前温度 " + lastTemp + "℃，已触发熔断降温模式！");
                    if (SettingsManager.isSmartChargeEnabled() && AdvancedKeepAliveManager.hasRoot()) {
                        RootManager.execCommand("echo 0 > /sys/class/power_supply/battery/charging_enabled");
                    }
                } else if (lastTemp < 42.0f && isCoolingDown) {
                    isCoolingDown = false;
                    WebhookManager.send("✅ 电池温度恢复", "当前温度 " + lastTemp + "℃，已解除降温模式。");
                    if (SettingsManager.isSmartChargeEnabled() && AdvancedKeepAliveManager.hasRoot()) {
                        RootManager.execCommand("echo 1 > /sys/class/power_supply/battery/charging_enabled");
                    }
                }
            }
        };
        context.registerReceiver(receiver, filter);
    }
    public static float getTemp() { return lastTemp; }
}
