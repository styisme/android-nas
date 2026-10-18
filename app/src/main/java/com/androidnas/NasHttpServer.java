package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();

    public NasHttpServer(int port) { super(port); }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if ("/".equals(uri) || "/index.html".equals(uri)) {
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                if (is == null) return text(Response.Status.INTERNAL_ERROR, "UI not found");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            } catch (Exception e) { return text(Response.Status.INTERNAL_ERROR, "UI load failed"); }
        }

        if ("/api/list".equals(uri)) {
            String path = session.getParms().get("path");
            if (path == null || path.isEmpty()) path = "/";
            File dir = new File(ROOT, path);
            if (!dir.exists() || !dir.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
            return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
        }

        if ("/api/download".equals(uri)) {
            String path = session.getParms().get("path");
            File file = new File(ROOT, path);
            if (!file.exists() || file.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
            try {
                FileInputStream fis = new FileInputStream(file);
                Response resp = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                return resp;
            } catch (IOException e) { return text(Response.Status.INTERNAL_ERROR, "Read error"); }
        }

        if ("/api/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
            try {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);

                String targetPath = "/";
                for (Map.Entry<String, String> e : files.entrySet()) {
                    if ("path".equals(e.getKey()) && e.getValue() != null) targetPath = e.getValue();
                }
                File targetDir = new File(ROOT, targetPath);
                if (!targetDir.exists() || !targetDir.isDirectory()) targetDir = new File(ROOT);

                String tmpFilePath = null;
                String originalName = null;
                for (Map.Entry<String, String> e : files.entrySet()) {
                    String val = e.getValue();
                    if (val != null && new File(val).exists()) {
                        tmpFilePath = val; originalName = e.getKey(); break;
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
                return text(Response.Status.OK, "OK: " + originalName);
            } catch (Exception e) { return text(Response.Status.INTERNAL_ERROR, "Upload failed: " + e.getMessage()); }
        }

        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response text(Response.Status status, String msg) {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", msg);
    }

    private String buildJson(File dir, String currentPath) {
        List<File> children = new ArrayList<File>();
        File[] files = dir.listFiles();
        if (files != null) Collections.addAll(children, files);
        Collections.sort(children, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
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
            sb.append("{\"name\":\"").append(escape(f.getName())).append("\",")
              .append("\"dir\":").append(f.isDirectory()).append(",")
              .append("\"size\":").append(f.length()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }
}
