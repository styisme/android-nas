package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class SettingsManager {
    private static SharedPreferences sp;

    public static void init(Context context) {
        sp = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE);
    }

    public static String getDeviceAlias() { return sp.getString("device_alias", "Android NAS"); }
    public static void setDeviceAlias(String alias) { sp.edit().putString("device_alias", alias).apply(); }

    // 低性能模式
    public static boolean isLowPowerMode() { return sp.getBoolean("low_power_mode", false); }
    public static void setLowPowerMode(boolean enabled) { sp.edit().putBoolean("low_power_mode", enabled).apply(); }

    // WebDAV：低性能模式下依然允许开启
    public static boolean isWebDavEnabled() { return sp.getBoolean("webdav_enabled", true); }
    public static void setWebDavEnabled(boolean enabled) { sp.edit().putBoolean("webdav_enabled", enabled).apply(); }

    // LocalSend：低性能模式下强制关闭
    public static boolean isLocalSendEnabled() { return sp.getBoolean("localsend_enabled", true) && !isLowPowerMode(); }
    public static void setLocalSendEnabled(boolean enabled) { sp.edit().putBoolean("localsend_enabled", enabled).apply(); }

    public static int getWebPort() { return sp.getInt("web_port", 8080); }
    public static int getWebDavPort() { return sp.getInt("webdav_port", 8081); }
    
    public static String getLsPin() { return sp.getString("ls_pin", "1234"); }
    public static void setLsPin(String pin) { sp.edit().putString("ls_pin", pin).apply(); }

    public static String getFingerprint() {
        String fp = sp.getString("fingerprint", "");
        if (fp.isEmpty()) {
            fp = "android_nas_" + UUID.randomUUID().toString();
            sp.edit().putString("fingerprint", fp).apply();
        }
        return fp;
    }
}
