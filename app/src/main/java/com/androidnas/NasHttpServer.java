package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private Map<String, String> sharedLinks = new ConcurrentHashMap<>();
    private static volatile String clipboardContent = "";

    public NasHttpServer(int port) { super(port); }

    private File safeFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) path = "/";
        String user = AuthManager.getCurrentUser();
        String userRoot = DatabaseManager.getInstance().getUserRoot(user);
        
        // 如果是 admin 且开了全盘，则用 /
        if (user.equals("admin") && SettingsManager.isGlobalMode()) {
            return new File(path);
        }
        
        // 普通用户映射到自己的根目录
        File rootDir = new File(ROOT, userRoot);
        if (!rootDir.exists()) rootDir.mkdirs();
        
        File f = new File(rootDir, path);
        String c = f.getCanonicalPath();
        String r = rootDir.getCanonicalPath();
        if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
        return f;
    }

    private boolean isSafeName(String n) {
        if (n == null || n.isEmpty()) return false;
        if (n.contains("/") || n.contains("\\")) return false;
        if (n.equals(".") || n.equals("..")) return false;
        return true;
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleShare(uri.substring(7));
            if (uri.startsWith("/dlna/")) return handleDlna(uri);

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return r;
                }
            }

            if ("/api/status".equals(uri))
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + "}");

            if ("/api/clipboard".equals(uri)) {
                if (Method.POST.equals(session.getMethod())) {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    clipboardContent = session.getParms().get("content");
                    return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.OK, clipboardContent);
            }

            // 1. 缩略图接口
            if ("/api/thumb".equals(uri)) {
                File img = safeFile(session.getParms().get("path"));
                if (img != null && img.exists() && !img.isDirectory()) {
                    File thumb = ThumbnailManager.getThumbnail(img);
                    FileInputStream fis = new FileInputStream(thumb);
                    return newChunkedResponse(Response.Status.OK, "image/jpeg", fis);
                }
                return text(Response.Status.NOT_FOUND, "Not found");
            }

            // 2. 相册时间轴接口
            if ("/api/photos".equals(uri)) {
                File dir = safeFile("/");
                List<File> photos = new ArrayList<>();
                scanPhotos(dir, photos, 0);
                Collections.sort(photos, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return Long.compare(b.lastModified(), a.lastModified());
                    }
                });
                
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < photos.size(); i++) {
                    if (i > 0) sb.append(",");
                    File f = photos.get(i);
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(ROOT, ""))).append("\"}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            // 3. 秒传与断点续传接口
            if ("/api/upload/check".equals(uri)) {
                String hash = session.getParms().get("hash");
                String path = DatabaseManager.getInstance().getPathByHash(hash);
                if (path != null) return text(Response.Status.OK, "SECONDS_UPLOAD");
                return text(Response.Status.OK, "NEED_UPLOAD");
            }
            if ("/api/upload/merge".equals(uri)) {
                // 简易合并逻辑
                String hash = session.getParms().get("hash");
                String path = session.getParms().get("path");
                File target = safeFile(path);
                if (target != null) {
                    DatabaseManager.getInstance().saveHash(hash, path);
                    return text(Response.Status.OK, "OK");
                }
            }

            // 4. 离线下载占位接口
            if ("/api/download/add".equals(uri)) {
                return text(Response.Status.OK, "Task Added (Mock)");
            }

            // 5. 系统 OTA 更新检查
            if ("/api/ota".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"version\":\"4.1.0\",\"url\":\"https://github.com\"}");
            }

            // 其它原有逻辑
            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                if (path == null || path.isEmpty()) path = "/";
                File dir = safeFile(path);
                if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }

            if ("/api/download".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                FileInputStream fis = new FileInputStream(file);
                Response resp = newChunkedResponse(Response.Status.OK, mime(file.getName()), fis);
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                return resp;
            }

            if (Method.PUT.equals(session.getMethod())) {
                return handlePut(session, role);
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            }

        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private void scanPhotos(File dir, List<File> photos, int depth) {
        if (dir == null || !dir.exists() || depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) scanPhotos(f, photos, depth + 1);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                    photos.add(f);
                }
            }
        }
    }

    private Response handlePut(IHTTPSession session, int role) throws Exception {
        String uri = session.getUri();
        File f = safeFile(uri);
        if (f == null) return text(Response.Status.FORBIDDEN, "Path invalid");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        long contentLength = 0;
        try { contentLength = Long.parseLong(session.getHeaders().get("content-length")); } catch (Exception ignored) {}
        
        InputStream is = session.getInputStream();
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buf = new byte[8192];
        int len; long total = 0;
        while (total < contentLength && (len = is.read(buf, 0, (int)Math.min(buf.length, contentLength - total))) > 0) {
            fos.write(buf, 0, len); total += len;
        }
        fos.close();
        return text(Response.Status.OK, "OK");
    }

    private Response handleShare(String token) { return text(Response.Status.NOT_FOUND, "Share expired"); }
    private Response handleDlna(String uri) { return text(Response.Status.NOT_FOUND, "Not found"); }
    private String mime(String n) { return n.endsWith(".mp4") ? "video/mp4" : "application/octet-stream"; }
    private Response text(Response.Status s, String m) { return newFixedLengthResponse(s, "text/plain; charset=utf-8", m); }

    private String buildJson(File dir, String path) {
        File[] files = dir.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + esc(path) + "\",\"files\":[");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"dir\":").append(f.isDirectory())
              .append(",\"size\":").append(f.length()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
