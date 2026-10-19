package com.androidnas.watch;

import java.io.Serializable;

public class WatchDevice implements Serializable {
    public static final int TYPE_ANDROID = 1;
    public static final int TYPE_WEBDAV = 2;
    public static final int TYPE_FTP = 3;

    public String name;
    public String ip;
    public String port;
    public String user;
    public String pass;
    public int type;

    public WatchDevice(String name, String ip, String port, String user, String pass, int type) {
        this.name = name;
        this.ip = ip;
        this.port = port;
        this.user = user;
        this.pass = pass;
        this.type = type;
    }
}
