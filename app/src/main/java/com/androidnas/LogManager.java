package com.androidnas;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    private static final int MAX_LOGS = 200;
    private static final List<String> logs = new ArrayList<String>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    public static synchronized void add(String ip, String action) {
        String time = sdf.format(new Date());
        logs.add(0, "[" + time + "] " + ip + " - " + action);
        if (logs.size() > MAX_LOGS) {
            logs.remove(logs.size() - 1);
        }
    }

    public static synchronized String getAllLogs() {
        if (logs.isEmpty()) return "暂无日志";
        StringBuilder sb = new StringBuilder();
        for (String s : logs) {
            sb.append(s).append("\n\n");
        }
        return sb.toString();
    }
}
