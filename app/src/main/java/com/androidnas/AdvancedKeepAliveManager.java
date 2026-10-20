package com.androidnas;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AdvancedKeepAliveManager {

    // 检测是否拥有 Root 权限
    public static boolean hasRoot() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su -c id");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) try { p.destroy(); } catch (Exception ignored) {}
        }
    }

    // 检测是否拥有 ADB 授权 (WRITE_SECURE_SETTINGS)
    public static boolean hasAdbPermission(Context context) {
        try {
            return context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    // 检测是否在电池优化白名单 (Doze 白名单)
    public static boolean isDozeWhitelisted(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    // 一键通过 Root 注入系统白名单，修改各类后台限制
    public static void applyRootWhitelist(Context context) {
        String pkg = context.getPackageName();
        try {
            // 1. 加入 Doze 设备空闲白名单
            Runtime.getRuntime().exec("su -c dumpsys deviceidle whitelist +" + pkg);
            // 2. 允许后台运行
            Runtime.getRuntime().exec("su -c cmd appops set " + pkg + " RUN_IN_BACKGROUND allow");
            Runtime.getRuntime().exec("su -c cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
            // 3. 将应用设为最高活跃度桶 (防 Doze 冻结)
            Runtime.getRuntime().exec("su -c am set-standby-bucket " + pkg + " active");
        } catch (Exception ignored) {}
    }
}
