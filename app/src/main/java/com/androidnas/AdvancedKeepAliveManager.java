package com.androidnas;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class AdvancedKeepAliveManager {

    // 1. 检测 Root
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

    // 2. 检测 ADB
    public static boolean hasAdbPermission(Context context) {
        return context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    // 3. Doze 白名单
    public static boolean isDozeWhitelisted(Context context) {
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true;
    }

    // 4. 执行 Root 指令的封装
    private static void execRootCommand(String cmd) {
        Process p = null;
        DataOutputStream os = null;
        try {
            p = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (os != null) try { os.close(); } catch (Exception ignored) {}
            if (p != null) try { p.destroy(); } catch (Exception ignored) {}
        }
    }

    // 5. 一键执行极致保活（OOM免死 + 斩杀休眠服务 + 分桶活跃）
    public static void applyExtremeWhitelist(Context context) {
        String pkg = context.getPackageName();
        int pid = android.os.Process.myPid();

        // A. 把自己的 OOM 权重拉到最低（免死）
        execRootCommand("echo -1000 > /proc/" + pid + "/oom_score_adj");

        // B. 设置活跃度最高分桶，关闭省电模式限制
        execRootCommand("am set-standby-bucket " + pkg + " active");
        execRootCommand("cmd appops set " + pkg + " RUN_IN_BACKGROUND allow");
        execRootCommand("cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
        execRootCommand("cmd deviceidle whitelist +" + pkg);

        // C. 劫杀国产 ROM 的系统级杀后台元凶（需谨慎，确实有效）
        String[] killers = {
            "com.miui.powerkeeper",       // 小米
            "com.huawei.powergenie",      // 华为
            "com.coloros.safecenter",     // OPPO
            "com.vivo.permissionmanager", // vivo
            "com.samsung.android.lool"    // 三星
        };
        for (String k : killers) {
            // 尝试停用它们（若没安装会静默失败）
            execRootCommand("pm disable-user --user 0 " + k);
        }
    }

    // 6. 恢复系统原有服务（防用户后悔）
    public static void restoreSystemServices() {
        String[] killers = {
            "com.miui.powerkeeper",
            "com.huawei.powergenie",
            "com.coloros.safecenter",
            "com.vivo.permissionmanager",
            "com.samsung.android.lool"
        };
        for (String k : killers) {
            execRootCommand("pm enable " + k);
        }
    }

    // 7. 生成 Magisk 模块（将当前 App 变为系统级特权 App）
    public static boolean generateMagiskModule(Context context) {
        String pkg = context.getPackageName();
        String apkPath = context.getPackageCodePath();
        // Magisk 模块的存放路径
        String dir = "/data/adb/modules/android_nas_priv/";
        String cmd = 
            "mkdir -p " + dir + "system/priv-app/AndroidNAS\n" +
            "cp " + apkPath + " " + dir + "system/priv-app/AndroidNAS/AndroidNAS.apk\n" +
            "echo 'id=android_nas_priv' > " + dir + "module.prop\n" +
            "echo 'name=Android NAS Privilege' >> " + dir + "module.prop\n" +
            "echo 'version=v1.0' >> " + dir + "module.prop\n" +
            "echo 'versionCode=1' >> " + dir + "module.prop\n" +
            "echo 'author=AndroidNAS' >> " + dir + "module.prop\n" +
            "echo 'description=Make Android NAS a System Privileged App' >> " + dir + "module.prop\n" +
            "touch " + dir + "auto_mount\n" +
            "chmod -R 755 " + dir;
        execRootCommand(cmd);
        return true;
    }
}
