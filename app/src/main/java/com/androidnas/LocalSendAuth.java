package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class LocalSendAuth {
    private static String pin = "1234";
    private static String fingerprint = "";

    public static void init(Context context) {
        SharedPreferences sp = context.getSharedPreferences("localsend_auth", Context.MODE_PRIVATE);
        pin = sp.getString("pin", "1234");
        fingerprint = sp.getString("fingerprint", "");
        if (fingerprint.isEmpty()) {
            fingerprint = "android_nas_" + UUID.randomUUID().toString();
            sp.edit().putString("fingerprint", fingerprint).apply();
        }
    }

    public static String getPin() { return pin; }
    public static String getFingerprint() { return fingerprint; }

    public static void setPin(Context context, String newPin) {
        pin = newPin;
        SharedPreferences sp = context.getSharedPreferences("localsend_auth", Context.MODE_PRIVATE);
        sp.edit().putString("pin", newPin).apply();
    }
    
    public static String getDeviceInfo(int port) {
        return "{\"alias\":\"Android NAS\",\"version\":\"2.2\",\"deviceModel\":\"Android\",\"deviceType\":\"desktop\",\"fingerprint\":\"" + fingerprint + "\",\"port\":" + port + ",\"protocol\":\"http\",\"download\":false}";
    }
}
