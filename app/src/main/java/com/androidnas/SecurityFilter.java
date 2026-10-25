package com.androidnas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class SecurityFilter {
    private static class FailInfo { int count; long lastAttempt; }
    private static class SessionInfo { String user; long lastActive; }
    private static class RateInfo { long windowStart; int count; }
    
    private static final Map<String, FailInfo> failMap = new ConcurrentHashMap<String, FailInfo>();
    private static final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<String, SessionInfo>();
    private static final Map<String, RateInfo> rateLimitMap = new ConcurrentHashMap<String, RateInfo>();
    private static final int MAX_FAILS = 5;
    private static final long BAN_TIME = 15 * 60 * 1000L;

    public static boolean isBlocked(String ip) {
        FailInfo info = failMap.get(ip);
        if (info != null && info.count >= MAX_FAILS) {
            if (System.currentTimeMillis() - info.lastAttempt > BAN_TIME) {
                failMap.remove(ip);
            } else {
                return true;
            }
        }
        
        // 如果关闭了 CC 限流，直接跳过检测
        if (!SettingsManager.isCcLimitEnabled()) return false;

        long now = System.currentTimeMillis();
        RateInfo rInfo = rateLimitMap.get(ip);
        if (rInfo == null || now - rInfo.windowStart > 1000) {
            rInfo = new RateInfo();
            rInfo.windowStart = now;
            rInfo.count = 1;
            rateLimitMap.put(ip, rInfo);
        } else {
            rInfo.count++;
            if (rInfo.count > 30) {
                return true;
            }
        }
        return false;
    }

    public static void recordFail(String ip) {
        FailInfo info = failMap.get(ip);
        if (info == null) {
            info = new FailInfo();
            failMap.put(ip, info);
        }
        info.count++;
        info.lastAttempt = System.currentTimeMillis();
    }

    public static void recordSuccess(String ip, String user) {
        failMap.remove(ip);
        SessionInfo info = activeSessions.get(ip);
        if (info == null) {
            info = new SessionInfo();
            activeSessions.put(ip, info);
        }
        info.user = user;
        info.lastActive = System.currentTimeMillis();
    }

    public static String getActiveSessionsJson() {
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, SessionInfo> entry : activeSessions.entrySet()) {
            if (now - entry.getValue().lastActive > 24 * 60 * 60 * 1000L) {
                activeSessions.remove(entry.getKey()); continue;
            }
            try {
                JSONObject obj = new JSONObject();
                obj.put("ip", entry.getKey());
                obj.put("user", entry.getValue().user);
                obj.put("lastActive", sdf.format(new Date(entry.getValue().lastActive)));
                arr.put(obj);
            } catch (Exception ignored) {}
        }
        return arr.toString();
    }
}
