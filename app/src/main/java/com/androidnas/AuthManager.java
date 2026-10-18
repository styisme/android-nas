package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class AuthManager {
    private static String username = "admin";
    private static String password = "admin";
    private static boolean authEnabled = true;

    public static void init(Context context) {
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        username = sp.getString("username", "admin");
        password = sp.getString("password", "admin");
        authEnabled = sp.getBoolean("auth_enabled", true);
    }

    public static void setAuth(Context context, String user, String pass) {
        username = user;
        password = pass;
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        sp.edit().putString("username", user).putString("password", pass).apply();
    }

    public static boolean isAuthEnabled() {
        return authEnabled;
    }

    public static void setAuthEnabled(Context context, boolean enabled) {
        authEnabled = enabled;
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        sp.edit().putBoolean("auth_enabled", enabled).apply();
    }

    public static String getAuthInfo() {
        return username + " / " + password;
    }

    public static boolean verify(String authHeader) {
        if (!authEnabled) return true; // 如果关闭了验证，直接放行
        if (authHeader == null) return false;
        if (!authHeader.startsWith("Basic ")) return false;
        try {
            String expected = Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP);
            return authHeader.substring(6).equals(expected);
        } catch (Exception e) {
            return false;
        }
    }
}
