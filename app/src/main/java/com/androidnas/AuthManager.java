package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class AuthManager {
    private static String adminUser = "admin";
    private static String adminPass = "admin";
    private static boolean authEnabled = true;

    public static void init(Context context) {
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        adminUser = sp.getString("username", "admin");
        adminPass = sp.getString("password", "admin");
        authEnabled = sp.getBoolean("auth_enabled", true);
    }

    public static void setAuth(Context context, String user, String pass) {
        adminUser = user;
        adminPass = pass;
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        sp.edit().putString("username", user).putString("password", pass).apply();
    }

    public static String getAdminUser() { return adminUser; }
    public static String getAdminPass() { return adminPass; }

    public static boolean isAuthEnabled() { return authEnabled; }
    public static void setAuthEnabled(Context context, boolean enabled) {
        authEnabled = enabled;
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        sp.edit().putBoolean("auth_enabled", enabled).apply();
    }
    
    public static String getAuthInfo() { return adminUser + " / " + adminPass; }

    public static int verifyRole(String authHeader) {
        if (!authEnabled) return 1;
        if (authHeader == null || !authHeader.startsWith("Basic ")) return 0;
        try {
            String base64 = authHeader.substring(6).trim();
            // 严格校验 Base64 格式，防止畸形数据耗尽 CPU
            if (!base64.matches("^[A-Za-z0-9+/=]+$")) return 0;
            byte[] decodedBytes = Base64.decode(base64, Base64.NO_WRAP);
            if (decodedBytes == null) return 0;
            String decoded = new String(decodedBytes, "UTF-8");
            if (decoded.equals(adminUser + ":" + adminPass)) return 1;
            if (decoded.equals("guest:guest")) return 2;
        } catch (Throwable e) {
            return 0;
        }
        return 0;
    }
    
    public static boolean verify(String authHeader) {
        return verifyRole(authHeader) > 0;
    }
}
