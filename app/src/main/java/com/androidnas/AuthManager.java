package com.androidnas;

import android.content.Context;
import android.util.Base64;

public class AuthManager {
    private static boolean authEnabled = true;
    private static final ThreadLocal<String> currentUser = new ThreadLocal<String>() {
        @Override protected String initialValue() { return "admin"; }
    };

    public static void init(Context context) {
        DatabaseManager.init(context);
        authEnabled = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE).getBoolean("auth_enabled", true);
    }

    public static boolean isAuthEnabled() { return authEnabled; }
    public static void setAuthEnabled(Context context, boolean enabled) {
        authEnabled = enabled;
        context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE).edit().putBoolean("auth_enabled", enabled).apply();
    }
    
    public static String getCurrentUser() { return currentUser.get(); }
    
    // 核心修复：暴露 set 方法，解决 NasHttpServer 编译失败
    public static void setCurrentUser(String user) { currentUser.set(user); }
    public static void clearCurrentUser() { currentUser.remove(); }

    public static int verifyRole(String authHeader) {
        if (!authEnabled) { currentUser.set("admin"); return 1; }
        if (authHeader == null || !authHeader.startsWith("Basic ")) return 0;
        try {
            String base64 = authHeader.substring(6).trim();
            if (!base64.matches("^[A-Za-z0-9+/=]+$")) return 0;
            byte[] decodedBytes = Base64.decode(base64, Base64.NO_WRAP);
            if (decodedBytes == null) return 0;
            String decoded = new String(decodedBytes, "UTF-8");
            
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                int role = DatabaseManager.getInstance().verifyUser(parts[0], parts[1]);
                if (role > 0) {
                    currentUser.set(parts[0]);
                    return role;
                }
            }
        } catch (Throwable e) { return 0; }
        return 0;
    }
    
    public static boolean verify(String authHeader) { return verifyRole(authHeader) > 0; }
}
