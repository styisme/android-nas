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
        return new File(name).getName().replace("\\", "_"); 
    }

    private boolean deleteRec(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) if (!deleteRec(c)) return false;
        }
        return f.delete();
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
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + "}");
            }
            if ("/api/monitor".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", SystemMonitor.getStatusJson(context));
            }
            if ("/api/ota".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"version\":\"4.2.0\"}");
            }
            
            // 核心修复：增加 Admin 角色校验 (role == 1)
            if ("/api/settings".equals(uri)) {
                if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                if (Method.GET.equals(session.getMethod())) {
                    String json = "{\"alias\":\"" + SettingsManager.getDeviceAlias() + "\",\"webPort\":" + SettingsManager.getWebPort() + ",\"davPort\":" + SettingsManager.getWebDavPort() + ",\"ftpPort\":" + SettingsManager.getFtpPort() + ",\"webdav\":" + SettingsManager.isWebDavEnabled() + ",\"ftp\":" + SettingsManager.isFtpEnabled() + ",\"dlna\":" + SettingsManager.isDlnaEnabled() + ",\"stream\":" + SettingsManager.isStreamEnabled() + ",\"clipboard\":" + SettingsManager.isClipboardEnabled() + ",\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + ",\"webhookUrl\":\"" + SettingsManager.getWebhookUrl() + "\",\"webhookChatId\":\"" + SettingsManager.getWebhookChatId() + "\"}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }
            }
            
            if ("/api/users".equals(uri) && Method.GET.equals(session.getMethod())) {
                if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                return newFixedLengthResponse(Response.Status.OK, "application/json", DatabaseManager.getInstance().getAllUsersJson());
            }

            if ("/api/clipboard".equals(uri)) {
                if (Method.GET.equals(session.getMethod())) return text(Response.Status.OK, clipboardContent);
            }
            if ("/api/photos".equals(uri)) {
                File dir = safeFile("/");
                List<File> photos = new ArrayList<File>();
                scanMedia(dir, photos, 0, "photo");
                final String rootPath = safeFile("/").getAbsolutePath();
                Collections.sort(photos, new Comparator<File>() {
                    @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
                });
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < photos.size(); i++) {
                    if (i > 0) sb.append(",");
                    File f = photos.get(i);
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(rootPath, ""))).append("\"}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }
            if ("/api/media/wall".equals(uri)) {
                File dir = safeFile("/");
                List<File> videos = new ArrayList<File>();
                scanMedia(dir, videos, 0, "video");
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : videos) {
                    JSONObject meta = MediaScraper.scrape(f.getName());
                    if (meta == null) {
                        meta = new JSONObject();
                        meta.put("title", f.getName()); meta.put("poster", ""); meta.put("rating", "0.0"); meta.put("overview", "暂无");
                    }
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(safeFile("/").getAbsolutePath(), ""))).append("\",")
                      .append("\"title\":\"").append(esc(meta.optString("title"))).append("\",\"poster\":\"").append(esc(meta.optString("poster"))).append("\",\"rating\":").append(meta.optDouble("rating", 0)).append(",\"overview\":\"").append(esc(meta.optString("overview").replace("\"", "'"))).append("\"}");
                    first = false;
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }
            if ("/api/thumb".equals(uri)) {
                File img = safeFile(session.getParms().get("path"));
                if (img != null && img.exists() && !img.isDirectory()) {
                    File thumb = ThumbnailManager.getThumbnail(img);
                    if (thumb != null && thumb.exists()) return newChunkedResponse(Response.Status.OK, "image/jpeg", new FileInputStream(thumb));
                }
                return text(Response.Status.NOT_FOUND, "Not found");
            }
            if ("/api/preview".equals(uri)) {
                File f = safeFile(session.getParms().get("path"));
                if (f != null && f.exists() && !f.isDirectory()) {
                    if (f.length() > 2 * 1024 * 1024) return text(Response.Status.REQUEST_ENTITY_TOO_LARGE, "Too Large");
                    return newChunkedResponse(Response.Status.OK, "text/plain; charset=utf-8", new FileInputStream(f));
                }
                return text(Response.Status.NOT_FOUND, "Not found");
            }
            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                File dir = safeFile(path == null || path.isEmpty() ? "/" : path);
                if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }
            if ("/api/search".equals(uri)) {
                String q = session.getParms().get("q");
                File dir = safeFile("/");
                if (dir == null) return text(Response.Status.FORBIDDEN, "No access");
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
            if ("/api/download".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                
                String range = session.getHeaders().get("range");
                if (range != null && range.startsWith("bytes=")) {
                    String[] parts = range.substring(6).split("-");
                    long start = Long.parseLong(parts[0]);
                    long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : file.length() - 1;
                    if (start < 0) start = 0;
                    if (end >= file.length()) end = file.length() - 1;
                    if (start > end) return text(Response.Status.BAD_REQUEST, "Bad Range");
                    long len = end - start + 1;
                    
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    raf.seek(start);
                    Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime(file.getName()), new FileInputStream(raf.getFD()), len);
                    res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + file.length());
                    res.addHeader("Accept-Ranges", "bytes");
                    return res;
                }
                Response resp = newChunkedResponse(Response.Status.OK, mime(file.getName()), new FileInputStream(file));
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\r", "").replace("\n", "").replace("\"", "") + "\"");
                resp.addHeader("Accept-Ranges", "bytes");
                return resp;
            }

            // ---------------- POST & PUT ----------------
            if (Method.POST.equals(session.getMethod())) {
                if ("/api/upload/chunk".equals(uri)) {
                    // 修复：直接从 Query 拿参数，不再 parseBody 吞流
                    String fileName = getSafeName(session.getParms().get("name"));
                    File targetDir = safeFile(session.getParms().get("path"));
                    if (targetDir == null) return text(Response.Status.FORBIDDEN, "Access denied");
                    if (!targetDir.exists()) targetDir.mkdirs();
                    
                    File tempFile = new File(targetDir, fileName + ".part");
                    FileOutputStream fos = new FileOutputStream(tempFile, true);
                    InputStream is = session.getInputStream();
                    byte[] buf = new byte[8192]; int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();
                    
                    int index = Integer.parseInt(session.getParms().get("index"));
                    int total = Integer.parseInt(session.getParms().get("total"));
                    if (index == total - 1) {
                        File finalFile = new File(targetDir, fileName);
                        if (finalFile.exists()) finalFile.delete();
                        tempFile.renameTo(finalFile);
                        DatabaseManager.getInstance().saveHash(session.getParms().get("hash"), finalFile.getAbsolutePath());
                        WebhookManager.send("文件上传完成", finalFile.getName());
                    }
                    return text(Response.Status.OK, "OK");
                }
                
                if ("/api/upload/check".equals(uri)) {
                    // 修复：前端改用 Query 传 hash，不解析 Body
                    String hash = session.getParms().get("hash");
                    if (hash != null && DatabaseManager.getInstance().getPathByHash(hash) != null) return text(Response.Status.OK, "SECONDS_UPLOAD");
                    return text(Response.Status.OK, "NEED_UPLOAD");
                }
                
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                
                if ("/api/upload".equals(uri)) {
                    String targetPath = session.getParms().get("path");
                    File targetDir = safeFile(targetPath);
                    if (targetDir == null || !targetDir.exists()) targetDir = safeFile("/");
                    String tmp = null; String orig = null;
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        if (e.getValue() != null && new File(e.getValue()).exists()) { tmp = e.getValue(); orig = e.getKey(); break; }
                    }
                    if (tmp == null) return text(Response.Status.BAD_REQUEST, "No file");
                    orig = getSafeName(orig != null && !orig.equals("file") ? orig : new File(tmp).getName());
                    File dest = new File(targetDir, orig);
                    copy(new File(tmp), dest);
                    new File(tmp).delete();
                    WebhookManager.send("文件上传完成", dest.getName());
                    return text(Response.Status.OK, "OK");
                }
                
                if ("/api/mkdir".equals(uri)) {
                    File dir = safeFile(session.getParms().get("path"));
                    String name = getSafeName(session.getParms().get("name"));
                    if (dir != null && name.length() > 0 && new File(dir, name).mkdirs()) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/delete".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    // 防止删除用户根目录
                    if (f != null && !f.getAbsolutePath().equals(safeFile("/").getAbsolutePath()) && deleteRec(f)) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/rename".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    String newName = getSafeName(session.getParms().get("newName"));
                    if (f != null && newName.length() > 0 && f.getParentFile() != null && f.renameTo(new File(f.getParentFile(), newName))) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/save".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null) { FileWriter fw = new FileWriter(f); fw.write(session.getParms().get("content")); fw.close(); return text(Response.Status.OK, "OK"); }
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/clipboard".equals(uri)) {
                    clipboardContent = session.getParms().get("content");
                    return text(Response.Status.OK, "OK");
                }
                
                if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                
                if ("/api/settings".equals(uri)) {
                    SettingsManager.setWebhookUrl(session.getParms().get("webhookUrl"));
                    SettingsManager.setWebhookChatId(session.getParms().get("webhookChatId"));
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/password".equals(uri)) {
                    DatabaseManager.getInstance().changePassword(AuthManager.getCurrentUser(), session.getParms().get("password"));
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/users".equals(uri)) {
                    DatabaseManager.getInstance().addUser(session.getParms().get("u"), session.getParms().get("p"), 2, session.getParms().get("r"));
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/share".equals(uri)) {
                    String path = session.getParms().get("path");
                    String token = UUID.randomUUID().toString().substring(0, 8);
                    sharedLinks.put(token, path + "||" + (System.currentTimeMillis() + 86400000L));
                    return text(Response.Status.OK, token);
                }
                if ("/api/unzip".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null && unzip(f)) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/webhook/test".equals(uri)) {
                    WebhookManager.send("测试通知", "这是一条测试消息！");
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/shell".equals(uri)) {
                    String out = RootManager.execCommand(session.getParms().get("cmd"));
                    return text(Response.Status.OK, out);
                }
            }
            
            if (Method.PUT.equals(session.getMethod())) {
                File f = safeFile(uri);
                if (f == null) return text(Response.Status.FORBIDDEN, "Path invalid");
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                long len = 0;
                try { len = Long.parseLong(session.getHeaders().get("content-length")); } catch (Exception ignored) {}
                InputStream is = session.getInputStream();
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[8192]; int read; long total = 0;
                while (total < len && (read = is.read(buf, 0, (int)Math.min(buf.length, len - total))) > 0) {
                    fos.write(buf, 0, read); total += read;
                }
                fos.close();
                WebhookManager.send("文件上传完成", f.getName());
                return text(Response.Status.OK, "OK");
            }

        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        } finally {
            AuthManager.clearCurrentUser(); // 核心修复：防越权残留
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response handleSecureShare(IHTTPSession session, String token) throws Exception {
        String data = sharedLinks.get(token);
        if (data == null) return text(Response.Status.NOT_FOUND, "失效");
        String[] parts = data.split("\\|\\|");
        if (parts.length > 1 && System.currentTimeMillis() > Long.parseLong(parts[1])) return text(Response.Status.FORBIDDEN, "已过期");
        File f = safeFile(parts[0]);
        if (f != null && f.exists() && !f.isDirectory()) {
            Response r = newChunkedResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(f));
            r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName().replace("\r", "").replace("\n", "").replace("\"", "") + "\"");
            return r;
        }
        return text(Response.Status.NOT_FOUND, "丢失");
    }

    private void scanMedia(File dir, List<File> list, int depth, String type) {
        if (dir == null || !dir.exists() || depth > 4) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { if (!f.getName().startsWith(".")) scanMedia(f, list, depth + 1, type); }
            else {
                String n = f.getName().toLowerCase();
                if (type.equals("photo") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) list.add(f);
                if (type.equals("video") && (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi"))) list.add(f);
            }
        }
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

    private boolean unzip(File zipFile) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            File destDir = zipFile.getParentFile();
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File f = new File(destDir, entry.getName());
                if (!f.getCanonicalPath().startsWith(destDir.getCanonicalPath())) continue; // 防穿越
                if (entry.isDirectory()) f.mkdirs();
                else {
                    f.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(f);
                    int len; while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();
                }
            }
            zis.close();
            return true;
        } catch (Exception e) { return false; }
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
        InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }
    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
