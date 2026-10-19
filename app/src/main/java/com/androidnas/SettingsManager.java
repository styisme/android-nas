package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.util.UUID;

public class SettingsManager {
    private static SharedPreferences sp;

    public static void init(Context context) {
        sp = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE);
    }

    // 设备别名
    public static String getDeviceAlias() { return sp.getString("device_alias", "Android NAS"); }
    public static void setDeviceAlias(String alias) { sp.edit().putString("device_alias", alias).apply(); }

    // 功能开关
    public static boolean isWebDavEnabled() { return sp.getBoolean("webdav_enabled", true); }
    public static void setWebDavEnabled(boolean enabled) { sp.edit().putBoolean("webdav_enabled", enabled).apply(); }

    public static boolean isLocalSendEnabled() { return sp.getBoolean("localsend_enabled", true); }
    public static void setLocalSendEnabled(boolean enabled) { sp.edit().putBoolean("localsend_enabled", enabled).apply(); }

    // 端口配置
    public static int getWebPort() { return sp.getInt("web_port", 8080); }
    public static int getWebDavPort() { return sp.getInt("webdav_port", 8081); }
    
    // LocalSend PIN (加入高强度默认值)
    public static String getLsPin() { return sp.getString("ls_pin", "1234"); }
    public static void setLsPin(String pin) { sp.edit().putString("ls_pin", pin).apply(); }

    // 指纹
    public static String getFingerprint() {
        String fp = sp.getString("fingerprint", "");
        if (fp.isEmpty()) {
            fp = "android_nas_" + UUID.randomUUID().toString();
            sp.edit().putString("fingerprint", fp).apply();
        }
        return fp;
    }
}
