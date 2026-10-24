package com.androidnas;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import org.json.JSONObject;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private Map<String, String> sharedLinks = new ConcurrentHashMap<String, String>();
    private static volatile String clipboardContent = "";
    private Context context;

    public NasHttpServer(int port, Context context) { 
        super(port); 
        this.context = context;
    }

    private File safeFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) path = "/";
        String user = AuthManager.getCurrentUser();
        String userRoot = DatabaseManager.getInstance().getUserRoot(user);
        
        if (user.equals("admin") && SettingsManager.isGlobalMode()) {
            return new File(path);
        }
        
        File rootDir = new File(ROOT, userRoot);
        if (!rootDir.exists()) rootDir.mkdirs();
        
        File f = new File(rootDir, path);
        String c = f.getCanonicalPath();
        String r = rootDir.getCanonicalPath();
        if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
        return f;
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleSecureShare(session, uri.substring(7));
            
            // PWA 静态资源 (不需要鉴权)
            if ("/manifest.json".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/manifest.json");
                return newFixedLengthResponse(Response.Status.OK, "application/json", is, -1);
            }
            if ("/sw.js".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/sw.js");
                return newFixedLengthResponse(Response.Status.OK, "application/javascript", is, -1);
            }

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return r;
                }
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            }

            if (Method.POST.equals(session.getMethod())) {
                if ("/api/upload/check".equals(uri)) {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    String hash = session.getParms().get("hash");
                    String existingPath = DatabaseManager.getInstance().getPathByHash(hash);
                    if (existingPath != null && new File(existingPath).exists()) {
                        return text(Response.Status.OK, "SECONDS_UPLOAD:" + existingPath);
                    }
                    return text(Response.Status.OK, "NEED_UPLOAD");
                }
                
                if ("/api/upload/chunk".equals(uri)) {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    String hash = session.getParms().get("hash");
                    String indexStr = session.getParms().get("index");
                    String totalStr = session.getParms().get("total");
                    String fileName = session.getParms().get("name");
                    String path = session.getParms().get("path");
                    
                    int index = Integer.parseInt(indexStr);
                    int total = Integer.parseInt(totalStr);
                    
                    File targetDir = safeFile(path);
                    if (targetDir == null || !targetDir.exists()) targetDir.mkdirs();
                    
                    File tempFile = new File(targetDir, fileName + ".part");
                    File finalFile = new File(targetDir, fileName);
                    
                    InputStream is = session.getInputStream();
                    FileOutputStream fos = new FileOutputStream(tempFile, true);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();

                    if (index == total - 1) {
                        synchronized (NasHttpServer.class) {
                            if (tempFile.length() > 0) {
                                if (finalFile.exists()) finalFile.delete();
                                tempFile.renameTo(finalFile);
                                DatabaseManager.getInstance().saveHash(hash, finalFile.getAbsolutePath());
                                WebhookManager.send("文件上传完成", finalFile.getName());
                            }
                        }
                    }
                    return text(Response.Status.OK, "CHUNK_OK");
                }

                if ("/api/upload".equals(uri)) {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    String targetPath = "/";
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        if ("path".equals(e.getKey()) && e.getValue() != null) targetPath = e.getValue();
                    }
                    File targetDir = safeFile(targetPath);
                    if (targetDir == null || !targetDir.exists()) targetDir = safeFile("/");
                    
                    String tmpFilePath = null;
                    String originalName = null;
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        String val = e.getValue();
                        if (val != null && new File(val).exists()) {
                            tmpFilePath = val;
                            originalName = e.getKey();
                            break;
                        }
                    }
                    if (tmpFilePath == null) return text(Response.Status.BAD_REQUEST, "No file uploaded");
                    if (originalName == null || originalName.length() == 0 || "file".equals(originalName)) {
                        originalName = new File(tmpFilePath).getName();
                    }
                    originalName = new File(originalName).getName();
                    
                    File dest = new File(targetDir, originalName);
                    copy(new File(tmpFilePath), dest);
                    new File(tmpFilePath).delete();
                    WebhookManager.send("文件上传完成", dest.getName());
                    return text(Response.Status.OK, "OK: " + originalName);
                }
                
                if ("/api/webhook/test".equals(uri)) {
                    WebhookManager.send("测试通知", "这是一条来自 Android NAS 的测试消息！");
                    return text(Response.Status.OK, "OK");
                }
            }

            if ("/api/media/wall".equals(uri)) {
                File dir = safeFile("/");
                List<File> videos = new ArrayList<File>();
                scanVideos(dir, videos, 0);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (int i = 0; i < videos.size(); i++) {
                    File f = videos.get(i);
                    JSONObject meta = MediaScraper.scrape(f.getName());
                    if (meta == null) {
                        meta = new JSONObject();
                        meta.put("title", f.getName());
                        meta.put("poster", "");
                        meta.put("rating", "0.0");
                        meta.put("overview", "暂无信息");
                    }
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",")
                      .append("\"path\":\"").append(esc(f.getAbsolutePath().replace(ROOT, ""))).append("\",")
                      .append("\"title\":\"").append(esc(meta.optString("title"))).append("\",")
                      .append("\"poster\":\"").append(esc(meta.optString("poster"))).append("\",")
                      .append("\"rating\":").append(meta.optDouble("rating", 0)).append(",")
                      .append("\"overview\":\"").append(esc(meta.optString("overview").replace("\"", "'"))).append("\"}");
                    first = false;
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            if ("/api/thumb".equals(uri)) {
                File img = safeFile(session.getParms().get("path"));
                if (img != null && img.exists() && !img.isDirectory()) {
                    File thumb = ThumbnailManager.getThumbnail(img);
                    FileInputStream fis = new FileInputStream(thumb);
                    return newChunkedResponse(Response.Status.OK, "image/jpeg", fis);
                }
                return text(Response.Status.NOT_FOUND, "Not found");
            }

            if ("/api/photos".equals(uri)) {
                File dir = safeFile("/");
                List<File> photos = new ArrayList<File>();
                scanPhotos(dir, photos, 0);
                final String root = ROOT;
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
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(root, ""))).append("\"}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

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

        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private void scanVideos(File dir, List<File> videos, int depth) {
        if (dir == null || !dir.exists() || depth > 5) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) scanVideos(f, videos, depth + 1);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) {
                    videos.add(f);
                }
            }
        }
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
        WebhookManager.send("文件上传完成", f.getName());
        return text(Response.Status.OK, "OK");
    }

    private Response handleSecureShare(IHTTPSession session, String token) throws Exception {
        String data = sharedLinks.get(token);
        if (data == null) return text(Response.Status.NOT_FOUND, "分享已失效");
        String[] parts = data.split("\\|");
        String path = parts[0];
        String pass = parts.length > 1 ? parts[1] : "";
        long expireAt = parts.length > 2 ? Long.parseLong(parts[2]) : 0;

        if (expireAt > 0 && System.currentTimeMillis() > expireAt) {
            return text(Response.Status.FORBIDDEN, "分享已过期");
        }
        if (!pass.isEmpty()) {
            String reqPass = session.getParms().get("pass");
            if (reqPass == null || !reqPass.equals(pass)) {
                return text(Response.Status.UNAUTHORIZED, "请输入正确的提取码");
            }
        }
        File f = safeFile(path);
        if (f != null && f.exists() && !f.isDirectory()) {
            FileInputStream fis = new FileInputStream(f);
            Response r = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
            r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
            return r;
        }
        return text(Response.Status.NOT_FOUND, "文件已丢失");
    }

    private String mime(String n) { 
        if (n.endsWith(".mp4")) return "video/mp4"; 
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream"; 
    }
    
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
    
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }

    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
