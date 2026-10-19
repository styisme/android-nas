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

            if ("/api/clipboard".equals(uri)) {
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
                                    byte[] buffer = new byte[8192];
                                    int len;
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

            if ("/api/download".equals(uri)) {
                String path = session.getParms().get("path");
                File file = safeFile(path);
                if (file == null || !file.exists()) return text(Response.Status.NOT_FOUND, "Not found");
                
                long fileLen = file.length();
                String range = session.getHeaders().get("range");
                if (range != null && range.startsWith("bytes=")) {
                    String[] ranges = range.substring(6).split("-");
                    long start = Long.parseLong(ranges[0]);
                    long end = ranges.length > 1 && !ranges[1].isEmpty() ? Long.parseLong(ranges[1]) : fileLen - 1;
                    if (end >= fileLen) end = fileLen - 1;
                    final long contentLen = end - start + 1;
                    
                    RandomAccessFile raf = new RandomAccessFile(file, "r");
                    raf.seek(start);
                    final FileInputStream fis = new FileInputStream(raf.getFD());
                    InputStream is = new InputStream() {
                        @Override 
                        public int read() throws IOException {
                            return fis.read();
                        }
                        @Override 
                        public int read(byte[] b, int off, int len) throws IOException {
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

            if ("/".equals(uri) || "/index.html".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            }

        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
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
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + currentPath + "\",\"files\":[");
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (i > 0) sb.append(",");
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
            boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("java");
            boolean isImg = ext.equals("jpg") || ext.equals("png");
            boolean isMedia = isImg || ext.equals("mp4") || ext.equals("mp3");
            boolean isZip = ext.equals("zip");
            sb.append("{\"name\":\"").append(f.getName()).append("\",\"dir\":").append(f.isDirectory())
              .append(",\"size\":").append(f.length()).append(",\"isTxt\":").append(isTxt)
              .append(",\"isMedia\":").append(isMedia).append(",\"isZip\":").append(isZip).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
