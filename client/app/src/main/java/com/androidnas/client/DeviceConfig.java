package com.androidnas.client;

public class DeviceConfig {
    public String name;
    public String ip;
    public int port;

    public DeviceConfig(String name, String ip, int port) {
        this.name = name;
        this.ip = ip;
        this.port = port;
    }
    public String getBaseUrl() { return "http://" + ip + ":" + port; }
}
