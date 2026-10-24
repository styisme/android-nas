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

    private String getSafeName(String name) {
        if (name == null) return "unknown";
        File f = new File(name);
        return f.getName(); // 仅保留文件名，防目录穿越
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleSecureShare(session, uri.substring(7));
            
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

            if ("/api/status".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + "}");
            }
            if ("/api/monitor".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", SystemMonitor.getStatusJson(context));
            }
            if ("/api/settings".equals(uri)) {
                if (Method.GET.equals(session.getMethod())) {
                    String json = "{\"alias\":\"" + SettingsManager.getDeviceAlias() + "\",\"webPort\":" + SettingsManager.getWebPort() + ",\"davPort\":" + SettingsManager.getWebDavPort() + ",\"ftpPort\":" + SettingsManager.getFtpPort() + ",\"webdav\":" + SettingsManager.isWebDavEnabled() + ",\"ftp\":" + SettingsManager.isFtpEnabled() + ",\"dlna\":" + SettingsManager.isDlnaEnabled() + ",\"stream\":" + SettingsManager.isStreamEnabled() + ",\"clipboard\":" + SettingsManager.isClipboardEnabled() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + ",\"webhookUrl\":\"" + SettingsManager.getWebhookUrl() + "\",\"webhookChatId\":\"" + SettingsManager.getWebhookChatId() + "\"}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }
            }
            if ("/api/clipboard".equals(uri)) {
                if (Method.GET.equals(session.getMethod())) return text(Response.Status.OK, clipboardContent);
                if (Method.POST.equals(session.getMethod())) {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    clipboardContent = session.getParms().get("content");
                    return text(Response.Status.OK, "OK");
                }
            }

            if (Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                
                if ("/api/upload/check".equals(uri)) {
                    String hash = session.getParms().get("hash");
                    String existingPath = DatabaseManager.getInstance().getPathByHash(hash);
                    if (existingPath != null && new File(existingPath).exists()) {
                        return text(Response.Status.OK, "SECONDS_UPLOAD");
                    }
                    return text(Response.Status.OK, "NEED_UPLOAD");
                }
                
                if ("/api/upload/chunk".equals(uri)) {
                    String hash = session.getParms().get("hash");
                    String indexStr = session.getParms().get("index");
                    String totalStr = session.getParms().get("total");
                    String fileName = getSafeName(session.getParms().get("name"));
                    String path = session.getParms().get("path");
                    
                    int index = Integer.parseInt(indexStr);
                    int total = Integer.parseInt(totalStr);
                    
                    File targetDir = safeFile(path);
                    if (targetDir != null && !targetDir.exists()) targetDir.mkdirs();
                    
                    File tempFile = new File(targetDir, fileName + ".part");
                    File finalFile = new File(targetDir, fileName);
                    
                    InputStream is = session.getInputStream();
                    FileOutputStream fos = new FileOutputStream(tempFile, true);
                    byte[] buf = new byte[8192]; int len;
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
                    String targetPath = "/";
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        if ("path".equals(e.getKey()) && e.getValue() != null) targetPath = e.getValue();
                    }
                    File targetDir = safeFile(targetPath);
                    if (targetDir == null || !targetDir.exists()) targetDir = safeFile("/");
                    
                    String tmpFilePath = null; String originalName = null;
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        String val = e.getValue();
                        if (val != null && new File(val).exists()) {
                            tmpFilePath = val; originalName = e.getKey(); break;
                        }
                    }
                    if (tmpFilePath == null) return text(Response.Status.BAD_REQUEST, "No file");
                    originalName = getSafeName(originalName != null && !originalName.equals("file") ? originalName : new File(tmpFilePath).getName());
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

                if ("/api/mkdir".equals(uri)) {
                    File dir = safeFile(session.getParms().get("path"));
                    String name = getSafeName(session.getParms().get("name"));
                    if (dir != null && new File(dir, name).mkdirs()) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                
                if ("/api/delete".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null && deleteRec(f)) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                
                if ("/api/rename".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    String newName = getSafeName(session.getParms().get("newName"));
                    if (f != null && f.renameTo(new File(f.getParent(), newName))) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                
                if ("/api/save".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null) {
                        FileWriter fw = new FileWriter(f);
                        fw.write(session.getParms().get("content")); fw.close();
                        return text(Response.Status.OK, "OK");
                    }
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }

                if ("/api/share".equals(uri)) {
                    String path = session.getParms().get("path");
                    String token = UUID.randomUUID().toString().substring(0, 8);
                    sharedLinks.put(token, path + "||" + (System.currentTimeMillis() + 86400000L));
                    return text(Response.Status.OK, token);
                }
            }

            if ("/api/search".equals(uri)) {
                String q = session.getParms().get("q");
                File dir = safeFile("/");
                List<File> results = new ArrayList<File>();
                searchFiles(dir, q, results, 0);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : results) {
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(safeFile("/").getAbsolutePath(), ""))).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append("}");
                    first = false;
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                File dir = safeFile(path == null || path.isEmpty() ? "/" : path);
                if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }

            if ("/api/download".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                
                String range = session.getHeaders().get("range");
                if (range != null && range.startsWith("bytes=")) {
                    String[] parts = range.substring(6).split("-");
                    long start = Long.parseLong(parts[0]);
                    long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : file.length() - 1;
                    long len = end - start + 1;
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    raf.seek(start);
                    Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime(file.getName()), new FileInputStream(raf.getFD()), len);
                    res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + file.length());
                    res.addHeader("Accept-Ranges", "bytes");
                    return res;
                }
                
                FileInputStream fis = new FileInputStream(file);
                Response resp = newChunkedResponse(Response.Status.OK, mime(file.getName()), fis);
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                resp.addHeader("Accept-Ranges", "bytes");
                return resp;
            }

            if (Method.PUT.equals(session.getMethod())) {
                File f = safeFile(uri);
                if (f == null) return text(Response.Status.FORBIDDEN, "Path invalid");
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                long contentLength = 0;
                try { contentLength = Long.parseLong(session.getHeaders().get("content-length")); } catch (Exception ignored) {}
                InputStream is = session.getInputStream();
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[8192]; int len; long total = 0;
                while (total < contentLength && (len = is.read(buf, 0, (int)Math.min(buf.length, contentLength - total))) > 0) {
                    fos.write(buf, 0, len); total += len;
                }
                fos.close();
                WebhookManager.send("文件上传完成", f.getName());
                return text(Response.Status.OK, "OK");
            }
        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private boolean deleteRec(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) if (!deleteRec(c)) return false;
        }
        return f.delete();
    }

    private void searchFiles(File dir, String q, List<File> res, int depth) {
        if (dir == null || !dir.exists() || depth > 4 || res.size() > 200) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().contains(q.toLowerCase())) res.add(f);
            if (f.isDirectory() && !f.getName().startsWith(".")) searchFiles(f, q, res, depth + 1);
        }
    }

    private Response handleSecureShare(IHTTPSession session, String token) throws Exception {
        String data = sharedLinks.get(token);
        if (data == null) return text(Response.Status.NOT_FOUND, "分享已失效");
        String[] parts = data.split("\\|\\|");
        File f = safeFile(parts[0]);
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
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + esc(path) + "\",\"files\":[");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
    
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192]; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }

    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
