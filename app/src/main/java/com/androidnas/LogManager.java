package com.androidnas;

import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    private static final int MAX_LOGS = 200;
    private static final List<String> logs = Collections.synchronizedList(new ArrayList<String>());
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    public static void add(String ip, String action) {
        String time = sdf.format(new Date());
        synchronized (logs) {
            logs.add(0, "[" + time + "] " + ip + " - " + action);
            if (logs.size() > MAX_LOGS) logs.remove(logs.size() - 1);
        }
    }

    public static String getAllLogs() {
        synchronized (logs) {
            if (logs.isEmpty()) return "暂无日志";
            StringBuilder sb = new StringBuilder();
            for (String s : logs) sb.append(s).append("\n\n");
            return sb.toString();
        }
    }

    // 🔥 新增：持久化审计写入
    public static void audit(String msg) {
        if (!SettingsManager.isAuditEnabled()) return;
        final String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        final String line = "[" + time + "] " + msg + "\n";
        
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File dir = new File(Environment.getExternalStorageDirectory(), "AndroidNAS");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, "audit.log");
                    if (file.exists() && file.length() > 2 * 1024 * 1024) {
                        file.delete(); // 日志超过 2MB 自动轮转清理
                    }
                    FileWriter fw = new FileWriter(file, true);
                    fw.write(line);
                    fw.close();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public static String getAuditLogs() {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), "AndroidNAS/audit.log");
            if (!file.exists()) return "暂无审计日志";
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            long len = raf.length();
            long start = Math.max(0, len - 50000); // 读取最后约 50KB
            raf.seek(start);
            byte[] bytes = new byte[(int) (len - start)];
            raf.readFully(bytes);
            raf.close();
            return new String(bytes, "UTF-8");
        } catch (Exception e) { return "读取失败: " + e.getMessage(); }
    }
}
