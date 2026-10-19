package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private Map<String, String> sharedLinks = new HashMap<String, String>();
    private static String clipboardContent = "";

    public NasHttpServer(int port) { super(port); }

    private File safeFile(String path) throws IOException {
        if (path == null) path = "/";
        File f = new File(ROOT, path);
        if (!f.getCanonicalPath().startsWith(new File(ROOT).getCanonicalPath())) return null;
        return f;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().name();
        
        try {
            if ("/dlna/description.xml".equals(uri)) {
                String ip = getLocalIpAddress();
                String xml = "<?xml version=\"1.0\"?>\n" +
                        "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n" +
                        "<specVersion><major>1</major><minor>0</minor></specVersion>\n" +
                        "<device>\n" +
                        "<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>\n" +
                        "<friendlyName>" + SettingsManager.getDeviceAlias() + "</friendlyName>\n" +
                        "<manufacturer>AndroidNAS</manufacturer>\n" +
                        "<modelName>Android NAS</modelName>\n" +
                        "<UDN>uuid:" + SettingsManager.getFingerprint() + "</UDN>\n" +
                        "<serviceList>\n" +
                        "<service>\n" +
                        "<serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>\n" +
                        "<serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId>\n" +
                        "<controlURL>/dlna/control</controlURL>\n" +
                        "<eventSubURL>/dlna/event</eventSubURL>\n" +
                        "<SCPDURL>/dlna/scpd.xml</SCPDURL>\n" +
                        "</service>\n" +
                        "</serviceList>\n" +
                        "</device>\n" +
                        "</root>";
                return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml);
            }

            if (uri.startsWith("/dlna/control") && method.equals("POST")) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                String responseXml = "";
                
                if (body != null && body.contains("Browse")) {
                    String ip = getLocalIpAddress();
                    responseXml = "<?xml version=\"1.0\"?>\n" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                            "<s:Body>\n" +
                            "<u:BrowseResponse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\">\n" +
                            "<Result>&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot;&gt;\n";
                    
                    // 简单扫描根目录下的视频文件作为示例
                    File[] filesList = new File(ROOT).listFiles();
                    int count = 0;
                    if (filesList != null) {
                        for (File f : filesList) {
                            if (f.isFile() && (f.getName().endsWith(".mp4") || f.getName().endsWith(".mkv"))) {
                                String objId = String.valueOf(f.hashCode());
                                responseXml += "&lt;item id=&quot;" + objId + "&quot; parentID=&quot;0&quot; restricted=&quot;1&quot;&gt;" +
                                        "&lt;dc:title&gt;" + f.getName() + "&lt;/dc:title&gt;" +
                                        "&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;" +
                                        "&lt;res protocolInfo=&quot;http-get:*:video/mp4:*&quot;&gt;http://" + ip + ":" + SettingsManager.getWebPort() + "/api/download?path=" + f.getName() + "&lt;/res&gt;" +
                                        "&lt;/item&gt;\n";
                                count++;
                                if (count > 50) break; // 限制数量
                            }
                        }
                    }
                    responseXml += "&lt;/DIDL-Lite&gt;</Result>\n" +
                            "<NumberReturned>" + count + "</NumberReturned>\n" +
                            "<TotalMatches>" + count + "</TotalMatches>\n" +
                            "<UpdateID>1</UpdateID>\n" +
                            "</u:BrowseResponse>\n" +
                            "</s:Body>\n" +
                            "</s:Envelope>";
                }
                return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", responseXml);
            }

            if ("/dlna/scpd.xml".equals(uri)) {
                 String xml = "<?xml version=\"1.0\"?>\n" +
                        "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\n" +
                        "<specVersion><major>1</major><minor>0</minor></specVersion>\n" +
                        "<actionList><action><name>Browse</name><argumentList></argumentList></action></actionList>\n" +
                        "<serviceStateTable></serviceStateTable>\n" +
                        "</scpd>";
                return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml);
            }
            
            // 原有业务逻辑
            if (uri.startsWith("/share/")) {
                String token = uri.substring(7);
                String sharedPath = sharedLinks.get(token);
                if (sharedPath != null) {
                    File file = safeFile(sharedPath);
                    if (file != null && file.exists() && !file.isDirectory()) {
                        FileInputStream fis = new FileInputStream(file);
                        Response resp = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                        resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                        return resp;
                    }
                }
                return text(Response.Status.NOT_FOUND, "分享已失效");
            }

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401 Unauthorized");
                    res.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return res;
                }
            }

            if (role == 2 && !uri.startsWith("/Public") && !"/".equals(uri) && !"/index.html".equals(uri) && !"/api/list".equals(uri)) {
                return text(Response.Status.FORBIDDEN, "访客仅能访问 /Public 目录");
            }

            if ("/api/clipboard".equals(uri)) {
                if (!SettingsManager.isClipboardEnabled()) return text(Response.Status.FORBIDDEN, "Clipboard disabled");
                if (Method.POST.equals(session.getMethod())) {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    clipboardContent = files.get("content");
                    return text(Response.Status.OK, "OK");
                } else {
                    return text(Response.Status.OK, clipboardContent != null ? clipboardContent : "");
                }
            }

            if ("/api/unzip".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                File zipFile = safeFile(files.get("path"));
                if (zipFile != null && zipFile.exists() && role == 1) {
                    File destDir = new File(zipFile.getParent(), zipFile.getName().replace(".zip", ""));
                    destDir.mkdirs();
                    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            File newFile = new File(destDir, entry.getName());
                            if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) continue;
                            if (entry.isDirectory()) newFile.mkdirs();
                            else {
                                newFile.getParentFile().mkdirs();
                                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                                    byte[] buffer = new byte[8192]; int len;
                                    while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                }
                            }
                        }
                    }
                    return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.FORBIDDEN, "Fail");
            }

            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                File dir = safeFile(path);
                if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }

            if ("/api/search".equals(uri)) {
                String q = session.getParms().get("q");
                List<File> results = new ArrayList<>();
                searchRecursive(new File(ROOT), q, results);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) sb.append(",");
                    File f = results.get(i);
                    String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
                    boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("java") || ext.equals("html");
                    boolean isImg = ext.equals("jpg") || ext.equals("png");
                    boolean isMedia = (isImg || ext.equals("mp4") || ext.equals("mp3")) && SettingsManager.isStreamEnabled();
                    boolean isZip = ext.equals("zip");
                    sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"path\":\"").append(escape(f.getAbsolutePath().replace(ROOT, ""))).append("\",\"dir\":").append(f.isDirectory())
                      .append(",\"size\":").append(f.length()).append(",\"isTxt\":").append(isTxt)
                      .append(",\"isMedia\":").append(isMedia).append(",\"isZip\":").append(isZip).append("}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            if ("/api/download".equals(uri)) {
                String path = session.getParms().get("path");
                File file = safeFile(path);
                if (file == null || !file.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                
                long fileLen = file.length();
                String range = session.getHeaders().get("range");
                if (SettingsManager.isStreamEnabled() && range != null && range.startsWith("bytes=")) {
                    String[] ranges = range.substring(6).split("-");
                    long start = Long.parseLong(ranges[0]);
                    long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLen - 1;
                    if (end >= fileLen) end = fileLen - 1;
                    final long contentLen = end - start + 1;
                    
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    raf.seek(start);
                    final FileInputStream fis = new FileInputStream(raf.getFD());
                    InputStream is = new InputStream() {
                        @Override public int read() throws IOException { return fis.read(); }
                        @Override public int read(byte[] b, int off, int len) throws IOException {
                            if (len > contentLen) len = (int) contentLen;
                            return fis.read(b, off, len);
                        }
                    };
                    Response resp = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, getMimeType(file.getName()), is, contentLen);
                    resp.addHeader("Accept-Ranges", "bytes");
                    resp.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLen);
                    return resp;
                } else {
                    FileInputStream fis = new FileInputStream(file);
                    Response resp = newChunkedResponse(Response.Status.OK, getMimeType(file.getName()), fis);
                    resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                    return resp;
                }
            }

            if ("/api/preview".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                if (file.length() > 2 * 1024 * 1024) return text(Response.Status.BAD_REQUEST, "文件过大，不支持在线编辑");
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();
                return text(Response.Status.OK, sb.toString());
            }

            if ("/api/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String targetPath = files.get("path");
                File targetDir = safeFile(targetPath != null ? targetPath : "/");
                if (targetDir == null || !targetDir.isDirectory()) targetDir = new File(ROOT);
                String tmpFilePath = null; String originalName = null;
                for (Map.Entry<String, String> e : files.entrySet()) {
                    String val = e.getValue();
                    if (val != null && new File(val).exists()) { tmpFilePath = val; originalName = e.getKey(); break; }
                }
                if (tmpFilePath == null) return text(Response.Status.BAD_REQUEST, "No file");
                if (originalName == null) originalName = new File(tmpFilePath).getName();
                File dest = new File(targetDir, new File(originalName).getName());
                copy(new File(tmpFilePath), dest);
                new File(tmpFilePath).delete();
                return text(Response.Status.OK, "OK");
            }

            if ("/api/rename".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                File oldFile = safeFile(files.get("path"));
                String newName = files.get("newName");
                if (oldFile != null && newName != null && role == 1) {
                    File newFile = new File(oldFile.getParent(), newName);
                    if (oldFile.renameTo(newFile)) return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/mkdir".equals(uri) || "/api/delete".equals(uri) || "/api/save".equals(uri)) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                if ("/api/mkdir".equals(uri)) {
                    File dir = safeFile(files.get("path"));
                    String name = files.get("name");
                    if (dir != null && name != null && role == 1) {
                        if (new File(dir, name).mkdirs()) return text(Response.Status.OK, "OK");
                    }
                } else if ("/api/delete".equals(uri)) {
                    File f = safeFile(files.get("path"));
                    if (f != null && !f.getAbsolutePath().equals(ROOT) && role == 1) {
                        if (deleteRecursive(f)) return text(Response.Status.OK, "OK");
                    }
                } else if ("/api/save".equals(uri)) {
                    File file = safeFile(files.get("path"));
                    String content = files.get("content");
                    if (file != null && content != null && file.exists() && role == 1) {
                        FileWriter writer = new FileWriter(file); writer.write(content); writer.close();
                        return text(Response.Status.OK, "OK");
                    }
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/share".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String path = files.get("path");
                if (path != null && role == 1) {
                    String token = UUID.randomUUID().toString().substring(0, 8);
                    sharedLinks.put(token, path);
                    return text(Response.Status.OK, token);
                }
                return text(Response.Status.FORBIDDEN, "Fail");
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

    private String getLocalIpAddress() {
        try {
            java.net.NetworkInterface ni = java.net.NetworkInterface.getByName("wlan0");
            if (ni != null) {
                java.util.Enumeration<java.net.InetAddress> ips = ni.getInetAddresses();
                while (ips.hasMoreElements()) {
                    java.net.InetAddress ip = ips.nextElement();
                    if (!ip.isLoopbackAddress() && ip instanceof java.net.Inet4Address) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {}
        return "127.0.0.1";
    }

    private void searchRecursive(File dir, String q, List<File> results) {
        if (dir == null || !dir.exists() || results.size() >= 100) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().contains(q.toLowerCase())) results.add(f);
            if (f.isDirectory()) searchRecursive(f, q, results);
        }
    }

    private String getMimeType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private Response text(Response.Status status, String msg) { return newFixedLengthResponse(status, "text/plain; charset=utf-8", msg); }

    private String buildJson(File dir, String currentPath) {
        File[] files = dir.listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + escape(currentPath) + "\",\"files\":[");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (i > 0) sb.append(",");
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
            boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("java") || ext.equals("html");
            boolean isImg = ext.equals("jpg") || ext.equals("png");
            boolean isMedia = (isImg || ext.equals("mp4") || ext.equals("mp3")) && SettingsManager.isStreamEnabled();
            boolean isZip = ext.equals("zip");
            sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"dir\":").append(f.isDirectory())
              .append(",\"size\":").append(f.length()).append(",\"isTxt\":").append(isTxt)
              .append(",\"isMedia\":").append(isMedia).append(",\"isZip\":").append(isZip).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) if (!deleteRecursive(x)) return false; }
        return f.delete();
    }
    private void copy(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int len; while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}
