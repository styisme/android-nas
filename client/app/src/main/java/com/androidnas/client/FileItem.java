package com.androidnas.client;

public class FileItem {
    public String name;
    public String path;
    public boolean isDir;
    public long size;

    public FileItem(String name, String path, boolean isDir, long size) {
        this.name = name;
        this.path = path;
        this.isDir = isDir;
        this.size = size;
    }
}
