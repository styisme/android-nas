package com.androidnas.client;

public class NasDevice {
    public static final int TYPE_ANDROID = 1;
    public static final int TYPE_WEBDAV = 2;
    public static final int TYPE_FTP = 3;

    public String name;
    public String url;
    public int type;
    public String username;
    public String password;

    public NasDevice(String name, String url, int type, String username, String password) {
        this.name = name;
        this.url = url;
        this.type = type;
        this.username = username;
        this.password = password;
    }

    public String getBaseUrl() {
        if (url == null) return "";
        String base = url.trim();
        if (type != TYPE_FTP && !base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
