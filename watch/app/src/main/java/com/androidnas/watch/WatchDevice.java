package com.androidnas.watch;

public class WatchDevice {
    public String name;
    public String ip;
    public String port;
    public String user;
    public String pass;

    public WatchDevice(String name, String ip, String port, String user, String pass) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.user = user;
        this.pass = pass;
    }
}
