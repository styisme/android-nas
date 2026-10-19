package com.androidnas.client;

public class NasDevice {
    public String name;
    public String url;
    public boolean isAndroidNas;
    public String username;
    public String password;

    public NasDevice(String name, String url, boolean isAndroidNas, String username, String password) {
        this.name = name;
        this.url = url;
        this.isAndroidNas = isAndroidNas;
        this.username = username;
        this.password = password;
    }

    // 补齐由于重构丢失的 getBaseUrl 方法
    public String getBaseUrl() {
        if (url == null) return "";
        String base = url.trim();
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
