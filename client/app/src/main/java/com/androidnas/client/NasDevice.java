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
}
