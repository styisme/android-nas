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
    private static class ShareInfo { String owner; String path; long expire; }
    private Map<String, ShareInfo> sharedLinks = new ConcurrentHashMap<String, ShareInfo>();
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
        
        // 🔥 核心修复：去除前导斜杠，防止 Java File 构造函数忽略父目录导致沙箱逃逸与全站 404
        String relativePath = path.replaceAll("^/+", "");
        File f = new File(rootDir, relativePath);
        String c = f.getCanonicalPath();
        String r = rootDir.getCanonicalPath();
        if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
        return f;
    }

    private String getSafeName(String name) {
        if (name == null || name.trim().isEmpty() || name.equals(".") || name.equals("..")) return null;
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
    
    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        if (!dir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            dir.delete();
        }
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleSecureShare(session, uri.substring(7));
            
            if ("/manifest.json".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/json", getClass().getClassLoader().getResourceAsStream("assets/manifest.json"), -1);
            if ("/sw.js".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/javascript", getClass().getClassLoader().getResourceAsStream("assets/sw.js"), -1);

            String clientIp = session.getHeaders().get("x-forwarded-for");
            if (clientIp == null) clientIp = session.getHeaders().get("remote-addr");
            if (clientIp == null) clientIp = "127.0.0.1";
            
            if (SettingsManager.isIpWhitelistEnabled()) {
                String list = SettingsManager.getIpWhitelist();
                boolean allowed = false;
                if (list != null) {
                    for (String rule : list.split(",")) {
                        rule = rule.trim();
                        if (rule.endsWith("*") && clientIp.startsWith(rule.substring(0, rule.length() - 1))) { allowed = true; break; }
                        if (rule.equals(clientIp)) { allowed = true; break; }
                    }
                }
                if (!allowed) return text(Response.Status.FORBIDDEN, "IP Blocked by Whitelist");
            }
            
            if (SettingsManager.isFail2BanEnabled() && SecurityFilter.isBlocked(clientIp)) {
                return text(Response.Status.TOO_MANY_REQUESTS, "Too many attempts. IP Blocked.");
            }

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    SecurityFilter.recordFail(clientIp);
                    LogManager.audit("登录失败: " + clientIp + " 尝试账号 " + AuthManager.getCurrentUser());
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return r;
                }
                SecurityFilter.recordSuccess(clientIp, AuthManager.getCurrentUser());
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getClass().getClassLoader().getResourceAsStream("assets/index.html"), -1);
            }

            if ("/api/status".equals(uri)) {
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + ",\"httpsEnabled\":" + SettingsManager.isHttpsEnabled() + ",\"isDefaultPwd\":" + DatabaseManager.isDefaultPwd + ",\"fail2ban\":" + SettingsManager.isFail2BanEnabled() + ",\"pinRequired\":" + SettingsManager.isPinRequired() + ",\"auditEnabled\":" + SettingsManager.isAuditEnabled() + ",\"ipWhitelistEnabled\":" + SettingsManager.isIpWhitelistEnabled() + ",\"ipWhitelist\":\"" + SettingsManager.getIpWhitelist() + "\"}");
            }
            if ("/api/monitor".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/json", SystemMonitor.getStatusJson(context));
            if ("/api/ota".equals(uri)) return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"version\":\"4.3.0\"}");
            
            if ("/api/settings".equals(uri)) {
                if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                if (Method.GET.equals(session.getMethod())) {
                    String json = "{\"alias\":\"" + SettingsManager.getDeviceAlias() + "\",\"webPort\":" + SettingsManager.getWebPort() + ",\"davPort\":" + SettingsManager.getWebDavPort() + ",\"ftpPort\":" + SettingsManager.getFtpPort() + ",\"webdav\":" + SettingsManager.isWebDavEnabled() + ",\"ftp\":" + SettingsManager.isFtpEnabled() + ",\"dlna\":" + SettingsManager.isDlnaEnabled() + ",\"stream\":" + SettingsManager.isStreamEnabled() + ",\"clipboard\":" + SettingsManager.isClipboardEnabled() + ",\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"globalMode\":" + SettingsManager.isGlobalMode() + ",\"webhookUrl\":\"" + SettingsManager.getWebhookUrl() + "\",\"webhookChatId\":\"" + SettingsManager.getWebhookChatId() + "\"}";
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }
            }
            
            if ("/api/security/audit".equals(uri) && role == 1) return text(Response.Status.OK, LogManager.getAuditLogs());
            if ("/api/security/sessions".equals(uri) && role == 1) return newFixedLengthResponse(Response.Status.OK, "application/json", SecurityFilter.getActiveSessionsJson());
            
            if ("/api/users".equals(uri) && Method.GET.equals(session.getMethod())) {
                if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                return newFixedLengthResponse(Response.Status.OK, "application/json", DatabaseManager.getInstance().getAllUsersJson());
            }
            if ("/api/clipboard".equals(uri) && Method.GET.equals(session.getMethod())) return text(Response.Status.OK, clipboardContent);
            
            if ("/api/photos".equals(uri)) {
                File dir = safeFile("/");
                if (dir == null) return text(Response.Status.FORBIDDEN, "No access");
                List<File> photos = new ArrayList<File>();
                scanMedia(dir, photos, 0, "photo");
                final String rootPath = dir.getAbsolutePath();
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
                if (dir == null) return text(Response.Status.FORBIDDEN, "No access");
                List<File> videos = new ArrayList<File>();
                scanMedia(dir, videos, 0, "video");
                final String rootPath = dir.getAbsolutePath();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : videos) {
                    JSONObject meta = MediaScraper.scrape(f.getName());
                    if (meta == null) {
                        meta = new JSONObject();
                        meta.put("title", f.getName()); meta.put("poster", ""); meta.put("rating", "0.0"); meta.put("overview", "暂无");
                    }
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(rootPath, ""))).append("\",")
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
                    if (f.length() > 2 * 1024 * 1024) return text(Response.Status.BAD_REQUEST, "Too Large");
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
                if (q == null || q.trim().isEmpty()) return newFixedLengthResponse(Response.Status.OK, "application/json", "[]");
                File dir = safeFile("/");
                if (dir == null) return text(Response.Status.FORBIDDEN, "No access");
                List<File> results = new ArrayList<File>();
                searchFiles(dir, q, results, 0);
                final String rootPath = dir.getAbsolutePath();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (File f : results) {
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(esc(f.getName())).append("\",\"path\":\"").append(esc(f.getAbsolutePath().replace(rootPath, ""))).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append("}");
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
                    long start = 0;
                    long end = file.length() - 1;
                    try {
                        if (parts.length > 0 && !parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                        if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                    } catch (Exception ignored) {}
                    if (start < 0) start = file.length() - 1; 
                    if (end >= file.length()) end = file.length() - 1;
                    if (start > end) return text(Response.Status.BAD_REQUEST, "Bad Range");
                    long len = end - start + 1;
                    
                    FileInputStream fis = new FileInputStream(file);
                    fis.skip(start);
                    InputStream limited = new LimitedInputStream(fis, len);
                    
                    Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime(file.getName()), limited, len);
                    res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + file.length());
                    res.addHeader("Accept-Ranges", "bytes");
                    return res;
                }
                Response resp = newChunkedResponse(Response.Status.OK, mime(file.getName()), new FileInputStream(file));
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\r", "").replace("\n", "").replace("\"", "") + "\"");
                resp.addHeader("Accept-Ranges", "bytes");
                return resp;
            }

            if (Method.POST.equals(session.getMethod())) {
                if ("/api/system/wipe".equals(uri)) {
                    if (role != 1) return text(Response.Status.FORBIDDEN, "Only admin can wipe.");
                    String pwd = session.getParms().get("password");
                    boolean isRootWipe = "true".equals(session.getParms().get("root"));
                    int verify = DatabaseManager.getInstance().verifyUser("admin", pwd);
                    if (verify != 1) return text(Response.Status.UNAUTHORIZED, "密码错误，自毁程序已中止。");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                File root = Environment.getExternalStorageDirectory();
                                deleteDir(root);
                                File thumbs = new File(root, ".thumbnails");
                                if (thumbs.exists()) deleteDir(thumbs);
                                if (isRootWipe && AdvancedKeepAliveManager.hasRoot()) {
                                    RootManager.execCommand("echo 0 > /proc/" + android.os.Process.myPid() + "/oom_score_adj");
                                    RootManager.execCommand("rm -rf /data/media/0/*");
                                    RootManager.execCommand("rm -rf /data/data/com.androidnas.all.server");
                                    RootManager.execCommand("reboot");
                                } else {
                                    Thread.sleep(1000); System.exit(0);
                                }
                            } catch (Exception ignored) {}
                        }
                    }).start();
                    return text(Response.Status.OK, "WIPING");
                }

                if ("/api/upload/chunk".equals(uri)) {
                    String fileName = getSafeName(session.getParms().get("name"));
                    String hash = session.getParms().get("hash");
                    if (fileName == null || hash == null) return text(Response.Status.BAD_REQUEST, "Bad params");
                    File targetDir = safeFile(session.getParms().get("path"));
                    if (targetDir == null) return text(Response.Status.FORBIDDEN, "Access denied");
                    if (!targetDir.exists()) targetDir.mkdirs();
                    
                    int index = Integer.parseInt(session.getParms().get("index"));
                    int total = Integer.parseInt(session.getParms().get("total"));
                    if (total <= 0 || total > 100000 || index < 0 || index >= total) return text(Response.Status.BAD_REQUEST, "Bad Range");

                    File tempFile = new File(targetDir, fileName + "." + hash + ".part");
                    FileOutputStream fos = new FileOutputStream(tempFile, true);
                    InputStream is = session.getInputStream();
                    byte[] buf = new byte[8192]; int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close();
                    
                    if (index == total - 1) {
                        File finalFile = new File(targetDir, fileName);
                        if (finalFile.exists()) finalFile.delete();
                        tempFile.renameTo(finalFile);
                        DatabaseManager.getInstance().saveHash(hash, finalFile.getAbsolutePath());
                        WebhookManager.send("文件上传完成", finalFile.getName());
                    }
                    return text(Response.Status.OK, "OK");
                }
                
                if ("/api/upload/check".equals(uri)) {
                    String hash = session.getParms().get("hash");
                    if (hash != null) {
                        String userRoot = DatabaseManager.getInstance().getUserRoot(AuthManager.getCurrentUser());
                        if (DatabaseManager.getInstance().getPathByHash(hash, userRoot) != null) return text(Response.Status.OK, "SECONDS_UPLOAD");
                    }
                    return text(Response.Status.OK, "NEED_UPLOAD");
                }
                
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                
                if ("/api/upload".equals(uri)) {
                    String targetPath = session.getParms().get("path");
                    File targetDir = safeFile(targetPath);
                    if (targetDir == null || !targetDir.exists()) targetDir = safeFile("/");
                    if (targetDir == null) return text(Response.Status.FORBIDDEN, "Access denied");
                    String tmp = null; String orig = null;
                    for (Map.Entry<String, String> e : files.entrySet()) {
                        if (e.getValue() != null && new File(e.getValue()).exists()) { tmp = e.getValue(); orig = e.getKey(); break; }
                    }
                    if (tmp == null) return text(Response.Status.BAD_REQUEST, "No file");
                    orig = getSafeName(orig != null && !orig.equals("file") ? orig : new File(tmp).getName());
                    if (orig == null) orig = "upload_" + System.currentTimeMillis();
                    File dest = new File(targetDir, orig);
                    copy(new File(tmp), dest);
                    new File(tmp).delete();
                    WebhookManager.send("文件上传完成", dest.getName());
                    return text(Response.Status.OK, "OK");
                }
                
                if ("/api/mkdir".equals(uri)) {
                    File dir = safeFile(session.getParms().get("path"));
                    String name = getSafeName(session.getParms().get("name"));
                    if (dir != null && name != null) {
                        File newDir = new File(dir, name);
                        if (newDir.exists() || newDir.mkdirs()) return text(Response.Status.OK, "OK");
                    }
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/delete".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null && !f.getAbsolutePath().equals(safeFile("/").getAbsolutePath()) && deleteRec(f)) {
                        LogManager.audit("删除文件: " + f.getAbsolutePath());
                        return text(Response.Status.OK, "OK");
                    }
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/rename".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    String newName = getSafeName(session.getParms().get("newName"));
                    if (f != null && newName != null && f.getParentFile() != null && f.renameTo(new File(f.getParentFile(), newName))) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/save".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    String content = session.getParms().get("content");
                    if (f != null && content != null && content.length() < 5 * 1024 * 1024) {
                        if (f.exists()) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US);
                            File bak = new File(f.getParentFile(), f.getName() + ".bak_" + sdf.format(new java.util.Date()));
                            f.renameTo(bak);
                        }
                        FileWriter fw = new FileWriter(f); fw.write(content); fw.close(); return text(Response.Status.OK, "OK");
                    }
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/clipboard".equals(uri)) {
                    clipboardContent = session.getParms().get("content");
                    return text(Response.Status.OK, "OK");
                }
                
                if ("/api/security/pin".equals(uri) && role == 1) {
                    if (!DatabaseManager.getInstance().verifyPin(session.getParms().get("oldPin"))) return text(Response.Status.FORBIDDEN, "Invalid PIN");
                    String np = session.getParms().get("newPin");
                    if (np == null || np.length() < 4) return text(Response.Status.BAD_REQUEST, "PIN too short");
                    DatabaseManager.getInstance().setPin(np);
                    return text(Response.Status.OK, "OK");
                }
                
                if (("/api/settings".equals(uri)) || ("/api/shell".equals(uri))) {
                    if (SettingsManager.isPinRequired() && !DatabaseManager.getInstance().verifyPin(session.getParms().get("pin"))) {
                        return text(Response.Status.FORBIDDEN, "Invalid Emergency PIN");
                    }
                }
                
                if ("/api/settings".equals(uri)) {
                    if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                    try {
                        if (session.getParms().get("alias") != null) SettingsManager.setDeviceAlias(session.getParms().get("alias"));
                        if (session.getParms().get("webPort") != null) SettingsManager.setWebPort(Integer.parseInt(session.getParms().get("webPort")));
                        if (session.getParms().get("davPort") != null) SettingsManager.setWebDavPort(Integer.parseInt(session.getParms().get("davPort")));
                        if (session.getParms().get("ftpPort") != null) SettingsManager.setFtpPort(Integer.parseInt(session.getParms().get("ftpPort")));
                        SettingsManager.setWebDavEnabled("true".equals(session.getParms().get("webdav")));
                        SettingsManager.setFtpEnabled("true".equals(session.getParms().get("ftp")));
                        SettingsManager.setDlnaEnabled("true".equals(session.getParms().get("dlna")));
                        SettingsManager.setStreamEnabled("true".equals(session.getParms().get("stream")));
                        SettingsManager.setClipboardEnabled("true".equals(session.getParms().get("clipboard")));
                        SettingsManager.setLowPowerMode("true".equals(session.getParms().get("lowPower")));
                        SettingsManager.setGlobalMode("true".equals(session.getParms().get("globalMode")));
                        SettingsManager.setFail2BanEnabled("true".equals(session.getParms().get("fail2ban")));
                        SettingsManager.setPinRequired("true".equals(session.getParms().get("pinRequired")));
                        SettingsManager.setAuditEnabled("true".equals(session.getParms().get("auditEnabled")));
                        SettingsManager.setIpWhitelistEnabled("true".equals(session.getParms().get("ipWhitelistEnabled")));
                        SettingsManager.setIpWhitelist(session.getParms().get("ipWhitelist"));
                        if (session.getParms().get("webhookUrl") != null) SettingsManager.setWebhookUrl(session.getParms().get("webhookUrl"));
                        if (session.getParms().get("webhookTemplate") != null) SettingsManager.setWebhookTemplate(session.getParms().get("webhookTemplate"));
                        if (session.getParms().get("webhookChatId") != null) SettingsManager.setWebhookChatId(session.getParms().get("webhookChatId"));
                        
                        new Thread(new Runnable() {
                            @Override public void run() {
                                try { Thread.sleep(1000); } catch (Exception ignored) {}
                                System.exit(0);
                            }
                        }).start();
                        
                        return text(Response.Status.OK, "OK");
                    } catch (Exception e) { return text(Response.Status.BAD_REQUEST, "Invalid params"); }
                }
                if ("/api/password".equals(uri)) {
                    String pwd = session.getParms().get("password");
                    if (pwd != null && pwd.length() > 0) {
                        DatabaseManager.getInstance().changePassword(AuthManager.getCurrentUser(), pwd);
                        return text(Response.Status.OK, "OK");
                    }
                    return text(Response.Status.BAD_REQUEST, "Empty password");
                }
                if ("/api/users".equals(uri)) {
                    if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
                    DatabaseManager.getInstance().addUser(session.getParms().get("u"), session.getParms().get("p"), 2, session.getParms().get("r"));
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/share".equals(uri)) {
                    String path = session.getParms().get("path");
                    if (safeFile(path) == null) return text(Response.Status.FORBIDDEN, "No access");
                    String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                    ShareInfo info = new ShareInfo();
                    info.owner = AuthManager.getCurrentUser();
                    info.path = path;
                    info.expire = System.currentTimeMillis() + 86400000L;
                    sharedLinks.put(token, info);
                    return text(Response.Status.OK, token);
                }
                if ("/api/unzip".equals(uri)) {
                    File f = safeFile(session.getParms().get("path"));
                    if (f != null && unzip(f)) return text(Response.Status.OK, "OK");
                    return text(Response.Status.INTERNAL_ERROR, "Failed");
                }
                if ("/api/webhook/test".equals(uri)) {
                    WebhookManager.sendTest();
                    return text(Response.Status.OK, "OK");
                }
                if ("/api/shell".equals(uri)) {
                    if (role != 1 || !SettingsManager.isGlobalMode() || !AdvancedKeepAliveManager.hasRoot()) return text(Response.Status.FORBIDDEN, "Forbidden");
                    String cmd = session.getParms().get("cmd");
                    if (cmd != null && (cmd.contains("rm -rf /") || cmd.contains("mkfs") || cmd.contains("dd if="))) return text(Response.Status.FORBIDDEN, "Dangerous command blocked");
                    LogManager.audit("执行 Shell: " + cmd);
                    String out = RootManager.execCommand(cmd);
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
            AuthManager.clearCurrentUser();
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response handleSecureShare(IHTTPSession session, String token) throws Exception {
        ShareInfo info = sharedLinks.get(token);
        if (info == null) return text(Response.Status.NOT_FOUND, "失效");
        if (System.currentTimeMillis() > info.expire) {
            sharedLinks.remove(token);
            return text(Response.Status.FORBIDDEN, "已过期");
        }
        
        AuthManager.setCurrentUser(info.owner);
        try {
            File f = safeFile(info.path);
            if (f != null && f.exists() && !f.isDirectory()) {
                Response r = newChunkedResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(f));
                r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName().replace("\r", "").replace("\n", "").replace("\"", "") + "\"");
                return r;
            }
        } finally {
            AuthManager.clearCurrentUser();
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
                if (!f.getCanonicalPath().startsWith(destDir.getCanonicalPath())) continue;
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
    
    private String esc(String s) { 
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    
    private static class LimitedInputStream extends FilterInputStream {
        private long limit;
        protected LimitedInputStream(InputStream in, long limit) { super(in); this.limit = limit; }
        @Override public int read() throws IOException {
            if (limit <= 0) return -1;
            int res = super.read(); if (res != -1) limit--; return res;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (limit <= 0) return -1;
            int toRead = (int) Math.min(len, limit);
            int res = super.read(b, off, toRead);
            if (res != -1) limit -= res;
            return res;
        }
    }
}
