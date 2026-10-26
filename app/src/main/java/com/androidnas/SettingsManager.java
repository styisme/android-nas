package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class SettingsManager {
    private static SharedPreferences sp;
    public static void init(Context context) { sp = context.getSharedPreferences("nas_settings", Context.MODE_PRIVATE); }

    public static String getWebhookTemplate() { return sp.getString("webhook_template", ""); }
    public static void setWebhookTemplate(String template) { sp.edit().putString("webhook_template", template).apply(); }
    public static String getDeviceAlias() { return sp.getString("device_alias", "Android NAS"); }
    public static void setDeviceAlias(String alias) { sp.edit().putString("device_alias", alias).apply(); }
    public static boolean isGlobalMode() { return sp.getBoolean("global_mode", false); }
    public static void setGlobalMode(boolean enabled) { sp.edit().putBoolean("global_mode", enabled).apply(); }
    public static boolean isLowPowerMode() { return sp.getBoolean("low_power_mode", false); }
    public static void setLowPowerMode(boolean enabled) { sp.edit().putBoolean("low_power_mode", enabled).apply(); }
    public static boolean isWebDavEnabled() { return sp.getBoolean("webdav_enabled", true); }
    public static void setWebDavEnabled(boolean enabled) { sp.edit().putBoolean("webdav_enabled", enabled).apply(); }
    public static boolean isFtpEnabled() { return sp.getBoolean("ftp_enabled", true); }
    public static void setFtpEnabled(boolean enabled) { sp.edit().putBoolean("ftp_enabled", enabled).apply(); }
    public static boolean isDlnaEnabled() { return sp.getBoolean("dlna_enabled", true); }
    public static void setDlnaEnabled(boolean enabled) { sp.edit().putBoolean("dlna_enabled", enabled).apply(); }
    public static boolean isLocalSendEnabled() { return sp.getBoolean("localsend_enabled", true); }
    public static void setLocalSendEnabled(boolean enabled) { sp.edit().putBoolean("localsend_enabled", enabled).apply(); }
    public static boolean isStreamEnabled() { return sp.getBoolean("stream_enabled", true); }
    public static void setStreamEnabled(boolean enabled) { sp.edit().putBoolean("stream_enabled", enabled).apply(); }
    public static boolean isClipboardEnabled() { return sp.getBoolean("clipboard_enabled", true); }
    public static void setClipboardEnabled(boolean enabled) { sp.edit().putBoolean("clipboard_enabled", enabled).apply(); }
    public static int getWebPort() { return sp.getInt("web_port", 8080); }
    public static void setWebPort(int port) { sp.edit().putInt("web_port", port).apply(); }
    public static int getWebDavPort() { return sp.getInt("webdav_port", 8081); }
    public static void setWebDavPort(int port) { sp.edit().putInt("webdav_port", port).apply(); }
    public static int getFtpPort() { return sp.getInt("ftp_port", 2121); }
    public static void setFtpPort(int port) { sp.edit().putInt("ftp_port", port).apply(); }
    public static String getLsPin() { return sp.getString("ls_pin", "1234"); }
    public static void setLsPin(String pin) { sp.edit().putString("ls_pin", pin).apply(); }
    public static String getWebhookUrl() { return sp.getString("webhook_url", ""); }
    public static void setWebhookUrl(String url) { sp.edit().putString("webhook_url", url).apply(); }
    public static String getWebhookChatId() { return sp.getString("webhook_chat_id", ""); }
    public static void setWebhookChatId(String id) { sp.edit().putString("webhook_chat_id", id).apply(); }
    public static boolean isSilentKeepAlive() { return sp.getBoolean("silent_keepalive", true); }
    public static void setSilentKeepAlive(boolean enabled) { sp.edit().putBoolean("silent_keepalive", enabled).apply(); }
    public static boolean isFail2BanEnabled() { return sp.getBoolean("fail2ban_enabled", true); }
    public static void setFail2BanEnabled(boolean enabled) { sp.edit().putBoolean("fail2ban_enabled", enabled).apply(); }
    public static boolean isPinRequired() { return sp.getBoolean("pin_required", true); }
    public static void setPinRequired(boolean required) { sp.edit().putBoolean("pin_required", required).apply(); }
    public static boolean isHttpsEnabled() { return sp.getBoolean("https_enabled", false); }
    public static void setHttpsEnabled(boolean enabled) { sp.edit().putBoolean("https_enabled", enabled).apply(); }
    public static boolean isIpWhitelistEnabled() { return sp.getBoolean("ip_whitelist_enabled", false); }
    public static void setIpWhitelistEnabled(boolean enabled) { sp.edit().putBoolean("ip_whitelist_enabled", enabled).apply(); }
    public static String getIpWhitelist() { return sp.getString("ip_whitelist", "192.168.1.*,127.0.0.1"); }
    public static void setIpWhitelist(String list) { sp.edit().putString("ip_whitelist", list).apply(); }
    public static boolean isAuditEnabled() { return sp.getBoolean("audit_enabled", true); }
    public static void setAuditEnabled(boolean enabled) { sp.edit().putBoolean("audit_enabled", enabled).apply(); }
    public static String getFingerprint() { String fp = sp.getString("fingerprint", ""); if (fp.isEmpty()) { fp = UUID.randomUUID().toString(); sp.edit().putString("fingerprint", fp).apply(); } return fp; }

    // 🔥 2FA 与电池保护开关（默认完全关闭）
    public static boolean is2FAEnabled() { return sp.getBoolean("2fa_enabled", false); }
    public static void set2FAEnabled(boolean enabled) { sp.edit().putBoolean("2fa_enabled", enabled).apply(); }
    public static String get2FASecret() { return sp.getString("2fa_secret", ""); }
    public static void set2FASecret(String secret) { sp.edit().putString("2fa_secret", secret).apply(); }
    public static boolean isSmartChargeEnabled() { return sp.getBoolean("smart_charge", false); }
    public static void setSmartChargeEnabled(boolean enabled) { sp.edit().putBoolean("smart_charge", enabled).apply(); }
    
    // 🔥 高级安全防线开关
    public static String getSecretPath() { return sp.getString("secret_path", ""); }
    public static void setSecretPath(String path) { sp.edit().putString("secret_path", path).apply(); }
    public static boolean isLanOnlyStrict() { return sp.getBoolean("lan_only_strict", true); }
    public static void setLanOnlyStrict(boolean strict) { sp.edit().putBoolean("lan_only_strict", strict).apply(); }
    public static boolean isCaptchaEnabled() { return sp.getBoolean("captcha_enabled", true); }
    public static void setCaptchaEnabled(boolean enabled) { sp.edit().putBoolean("captcha_enabled", enabled).apply(); }
    public static boolean isCcLimitEnabled() { return sp.getBoolean("cc_limit_enabled", true); }
    public static void setCcLimitEnabled(boolean enabled) { sp.edit().putBoolean("cc_limit_enabled", enabled).apply(); }
    public static int getSessionTimeout() { return sp.getInt("session_timeout", 30); }
    public static void setSessionTimeout(int mins) { sp.edit().putInt("session_timeout", mins).apply(); }
    public static boolean isHideHeaderEnabled() { return sp.getBoolean("hide_header", true); }
    public static void setHideHeaderEnabled(boolean enabled) { sp.edit().putBoolean("hide_header", enabled).apply(); }
}
