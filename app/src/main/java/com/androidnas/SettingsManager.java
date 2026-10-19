package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static SharedPreferences sp;

    public static void init(Context context) {
        sp = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE);
    }

    // LocalSend PIN
    public static String getLsPin() { return sp.getString("ls_pin", "1234"); }
    public static void setLsPin(String pin) { sp.edit().putString("ls_pin", pin).apply(); }

    // 设备别名
    public static String getDeviceAlias() { return sp.getString("device_alias", "Android NAS"); }
    public static void setDeviceAlias(String alias) { sp.edit().putString("device_alias", alias).apply(); }

    // 指纹 (持久化)
    public static String getFingerprint() {
        String fp = sp.getString("fingerprint", "");
        if (fp.isEmpty()) {
            fp = "android_nas_" + java.util.UUID.randomUUID().toString();
            sp.edit().putString("fingerprint", fp).apply();
        }
        return fp;
    }

    // 是否开启 LocalSend
    public static boolean isLocalSendEnabled() { return sp.getBoolean("ls_enabled", true); }
    public static void setLocalSendEnabled(boolean enabled) { sp.edit().putBoolean("ls_enabled", enabled).apply(); }
}
