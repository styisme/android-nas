package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private Map<String, String> sharedLinks = new HashMap<String, String>();

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
            // 分享拦截，免密
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

            String clientIp = session.getHeaders().get("remote-addr");
            if (clientIp == null) clientIp = "unknown";
            
            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401 Unauthorized");
                    res.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return res;
                }
            }

            if (role == 2 && !uri.startsWith("/Public")) {
                return text(Response.Status.FORBIDDEN, "访客仅能访问 /Public 目录");
            }

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

            if ("/api/share".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
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

            if ("/api/list".equals(uri)) {
                String path = session.getParms().get("path");
                File dir = safeFile(path);
                if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
            }
            
            if ("/api/search".equals(uri)) {
                String q = session.getParms().get("q");
                List<File> results = new ArrayList<File>();
                searchRecursive(new File(ROOT), q, results);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) sb.append(",");
                    File f = results.get(i);
                    String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
                    boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("java") || ext.equals("html");
                    sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"path\":\"").append(escape(f.getAbsolutePath().replace(ROOT, ""))).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append(",\"isTxt\":").append(isTxt).append("}");
                }
                sb.append("]");
                return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
            }

            if ("/api/download".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                FileInputStream fis = new FileInputStream(file);
                Response resp = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                return resp;
            }

            if ("/api/preview".equals(uri)) {
                File file = safeFile(session.getParms().get("path"));
                if (file == null || !file.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();
                return text(Response.Status.OK, sb.toString());
            }

            if ("/api/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
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
                Map<String, String> files = new HashMap<String, String>();
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
                Map<String, String> files = new HashMap<String, String>();
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
            if (f.isDirectory()) searchRecursive(f, q, results);
        }
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
            boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("xml") || ext.equals("java") || ext.equals("html");
            boolean isImg = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif");
            sb.append("{\"name\":\"").append(escape(f.getName())).append("\",\"dir\":").append(f.isDirectory())
              .append(",\"size\":").append(f.length()).append(",\"isTxt\":").append(isTxt).append(",\"isImg\":").append(isImg).append("}");
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
