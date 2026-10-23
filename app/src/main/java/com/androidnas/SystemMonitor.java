package com.androidnas;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

public class SystemMonitor {
    public static String getStatusJson(Context context) {
        try {
            Runtime rt = Runtime.getRuntime();
            long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMem = rt.maxMemory() / 1024 / 1024;

            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long availStorage = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
            long totalStorage = (long)stat.getBlockSize() * (long)stat.getBlockCount();

            int battery = -1;
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                    battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                } catch (Exception ignored) {}
            }

            double cpuUsage = getCpuUsage();

            return "{\"cpu\":" + cpuUsage + ",\"memUsed\":" + usedMem + ",\"memTotal\":" + maxMem + 
                   ",\"storageUsed\":" + ((totalStorage-availStorage)/1024/1024/1024) + ",\"storageTotal\":" + (totalStorage/1024/1024/1024) + 
                   ",\"battery\":" + battery + "}";
        } catch (Exception e) {
            return "{}";
        }
    }

    private static double getCpuUsage() {
        try {
            java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            String[] toks = load.split(" +");
            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);
            Thread.sleep(300);
            reader.seek(0);
            load = reader.readLine();
            reader.close();
            toks = load.split(" +");
            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);
            double usage = (cpu2 - cpu1) * 100.0 / ((cpu2 + idle2) - (cpu1 + idle1));
            return Math.round(usage * 10.0) / 10.0;
        } catch (Exception e) { return 0.0; }
    }
}
