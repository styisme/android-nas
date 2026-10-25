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
    public static void setCurrentUser(String user) { currentUser.set(user); }
    public static void clearCurrentUser() { currentUser.remove(); }


    // 仅验证密码，忽略 2FA (用于判断是否是因为缺少 2FA 导致的失败)
    public static int verifyPasswordOnly(String authHeader) {
        if (!authEnabled) { currentUser.set("admin"); return 1; }
        if (authHeader == null || !authHeader.startsWith("Basic ")) return 0;
        try {
            String base64 = authHeader.substring(6).trim();
            byte[] decodedBytes = Base64.decode(base64, Base64.NO_WRAP);
            String decoded = new String(decodedBytes, "UTF-8");
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                return DatabaseManager.getInstance().verifyUser(parts[0], parts[1]);
            }
        } catch (Throwable e) { return 0; }
        return 0;
    }

    // 双参数版本：支持 2FA 动态码校验
    public static int verifyRole(String authHeader, String totpCode) {
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
                    // 2FA 校验（仅对 admin 且开启了 2FA 的情况生效）
                    if (parts[0].equals("admin") && SettingsManager.is2FAEnabled()) {
                        if (totpCode == null || totpCode.trim().isEmpty()) return 0; // 缺失验证码
                        try {
                            int code = Integer.parseInt(totpCode.trim());
                            if (!TotpManager.verifyCode(SettingsManager.get2FASecret(), code)) return 0;
                        } catch (Exception e) { return 0; }
                    }
                    currentUser.set(parts[0]);
                    return role;
                }
            }
        } catch (Throwable e) { return 0; }
        return 0;
    }
    
    // 单参数兼容版（供 FTP 等无法传 TOTP 的内部服务使用）
    public static int verifyRole(String authHeader) { 
        return verifyRole(authHeader, null); 
    }
    
    public static boolean verify(String authHeader) { 
        return verifyRole(authHeader) > 0; 
    }
}
