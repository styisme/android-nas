package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();

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
        
        // NanoHTTPD 2.2.0 获取 IP 的方式
        String clientIp = session.getHeaders().get("remote-addr");
        if (clientIp == null) clientIp = session.getHeaders().get("http-client-ip");
        if (clientIp == null) clientIp = "unknown";

        // 1. 身份校验
        int role = 1;
        if (!Method.OPTIONS.equals(session.getMethod())) {
            role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
            if (role == 0) {
                LogManager.add(clientIp, "未授权访问: " + uri);
                Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401 Unauthorized");
                res.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                return res;
            }
        }

        // 2. 访客权限限制
        if (role == 2) {
            if (!uri.startsWith("/Public") && !"/".equals(uri) && !"/index.html".equals(uri) && !"/api/list".equals(uri)) {
                return text(Response.Status.FORBIDDEN, "访客仅能访问 /Public 目录");
            }
        }

        // 3. 记录日志
        if (!uri.startsWith("/api/list") && !"/".equals(uri)) {
            LogManager.add(clientIp, method + " " + uri);
        }

        try {
            if (Method.PUT.equals(session.getMethod())) {
                File targetFile = safeFile(uri);
                if (targetFile != null && role == 1) {
                    if (targetFile.getParentFile() != null) targetFile.getParentFile().mkdirs();
                    try (InputStream is = session.getInputStream(); FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                    return newFixedLengthResponse(Response.Status.CREATED, "text/plain", "Created");
                }
                return text(Response.Status.FORBIDDEN, "无写入权限");
            }

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                if (is == null) return text(Response.Status.INTERNAL_ERROR, "UI not found");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            }

            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                if (path == null || path.isEmpty()) path = "/";
                File dir = safeFile(path);
                if (dir == null || !dir.exists() || !dir.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }

            if ("/api/search".equals(uri)) {
                String q = session.getParms().get("q");
                if (q == null || q.isEmpty()) return text(Response.Status.OK, "[]");
                List<File> results = new ArrayList<File>();
                if (role == 1) searchRecursive(new File(ROOT), q, results); 
                else searchRecursive(new File(ROOT + "/Public"), q, results);
                
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) sb.append(",");
                    File f = results.get(i);
                    String relPath = f.getAbsolutePath().replace(ROOT, "");
                    sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"path\":\"").append(escape(relPath)).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append("}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            if ("/api/download".equals(uri)) {
                String path = session.getParms().get("path");
                File file = safeFile(path);
                if (file == null || !file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                FileInputStream fis = new FileInputStream(file);
                Response resp = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                return resp;
            }

            if ("/api/preview".equals(uri)) {
                String path = session.getParms().get("path");
                File file = safeFile(path);
                if (file == null || !file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
                if (file.length() > 1024 * 1024) return text(Response.Status.BAD_REQUEST, "文件太大");
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();
                return text(Response.Status.OK, sb.toString());
            }

            if ("/api/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                String targetPath = "/";
                for (Map.Entry<String, String> e : files.entrySet()) {
                    if ("path".equals(e.getKey()) && e.getValue() != null) targetPath = e.getValue();
                }
                File targetDir = safeFile(targetPath);
                if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) targetDir = new File(ROOT);

                String tmpFilePath = null; String originalName = null;
                for (Map.Entry<String, String> e : files.entrySet()) {
                    String val = e.getValue();
                    if (val != null && new File(val).exists()) { tmpFilePath = val; originalName = e.getKey(); break; }
                }
                if (tmpFilePath == null) return text(Response.Status.BAD_REQUEST, "No file uploaded");
                if (originalName == null || originalName.length() == 0 || "file".equals(originalName)) originalName = new File(tmpFilePath).getName();
                originalName = new File(originalName).getName();
                File dest = new File(targetDir, originalName);
                copy(new File(tmpFilePath), dest);
                new File(tmpFilePath).delete();
                return text(Response.Status.OK, "OK: " + originalName);
            }

            if ("/api/mkdir".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                File dir = safeFile(files.get("path"));
                String name = files.get("name");
                if (dir != null && name != null && !name.isEmpty() && role == 1) {
                    if (new File(dir, name).mkdirs()) return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/delete".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                File file = safeFile(files.get("path"));
                if (file != null && !file.getAbsolutePath().equals(ROOT) && role == 1) {
                    if (deleteRecursive(file)) return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/save".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                File file = safeFile(files.get("path"));
                String content = files.get("content");
                if (file != null && content != null && file.exists() && role == 1) {
                    FileWriter writer = new FileWriter(file);
                    writer.write(content);
                    writer.close();
                    return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private void searchRecursive(File dir, String q, List<File> results) {
        if (dir == null || !dir.exists() || results.size() >= 100) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().toLowerCase().contains(q.toLowerCase())) results.add(f);
            if (f.isDirectory() && results.size() < 100) searchRecursive(f, q, results);
        }
    }

    private Response text(Response.Status status, String msg) { return newFixedLengthResponse(status, "text/plain; charset=utf-8", msg); }

    private String buildJson(File dir, String currentPath) {
        List<File> children = new ArrayList<File>();
        File[] files = dir.listFiles();
        if (files != null) Collections.addAll(children, files);
        Collections.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("{\"path\":\"").append(escape(currentPath)).append("\",\"files\":[");
        for (int i = 0; i < children.size(); i++) {
            File f = children.get(i);
            if (i > 0) sb.append(",");
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
            boolean isImg = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif");
            boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("xml") || ext.equals("java") || ext.equals("html");
            boolean isMedia = isImg || ext.equals("mp4") || ext.equals("mp3");
            sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"dir\":").append(f.isDirectory())
              .append(",\"size\":").append(f.length()).append(",\"isImg\":").append(isImg)
              .append(",\"isTxt\":").append(isTxt).append(",\"isMedia\":").append(isMedia).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) if (!deleteRecursive(c)) return false;
        }
        return f.delete();
    }
    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192]; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }
}
