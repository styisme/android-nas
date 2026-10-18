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
        if (!f.getCanonicalPath().startsWith(new File(ROOT).getCanonicalPath())) {
            return null;
        }
        return f;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().name();
        try {
            // WebDAV 支持 (网络邻居)
            if ("OPTIONS".equals(method)) {
                Response res = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
                res.addHeader("DAV", "1,2");
                res.addHeader("Allow", "OPTIONS, GET, HEAD, POST, DELETE, PROPFIND, PUT, MKCOL");
                return res;
            }
            if ("PROPFIND".equals(method)) {
                File file = safeFile(uri);
                if (file == null || !file.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
                String xml = buildWebDavXml(file, uri);
                Response res = newFixedLengthResponse(Response.Status.OK, "application/xml; charset=utf-8", xml);
                res.addHeader("DAV", "1,2");
                return res;
            }
            if ("PUT".equals(method)) {
                File file = safeFile(uri);
                if (file != null) {
                    Map<String, String> files = new HashMap<String, String>();
                    session.parseBody(files);
                    String tmpFilePath = files.get("content");
                    if (tmpFilePath != null) {
                        copy(new File(tmpFilePath), file);
                        new File(tmpFilePath).delete();
                    }
                    return newFixedLengthResponse(Response.Status.CREATED, "text/plain", "Created");
                }
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
            }
            if ("MKCOL".equals(method)) {
                File file = safeFile(uri);
                if (file != null) {
                    if (file.mkdirs()) return newFixedLengthResponse(Response.Status.CREATED, "text/plain", "Created");
                }
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
            }
            if ("DELETE".equals(method)) {
                File file = safeFile(uri);
                if (file != null && !file.getAbsolutePath().equals(ROOT)) {
                    if (deleteRecursive(file)) return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
                }
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
            }

            // 常规 Web UI 与 API
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
                if (file.length() > 1024 * 1024) return text(Response.Status.BAD_REQUEST, "文件太大，不支持预览");
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
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
            }

            if ("/api/mkdir".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                String path = files.get("path");
                String name = files.get("name");
                File dir = safeFile(path);
                if (dir != null && name != null && !name.isEmpty()) {
                    File newDir = new File(dir, name);
                    if (newDir.mkdirs()) return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/delete".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                String path = files.get("path");
                File file = safeFile(path);
                if (file != null && !file.getAbsolutePath().equals(ROOT)) {
                    if (deleteRecursive(file)) return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.INTERNAL_ERROR, "Fail");
            }

            if ("/api/save".equals(uri) && Method.POST.equals(session.getMethod())) {
                Map<String, String> files = new HashMap<String, String>();
                session.parseBody(files);
                String path = files.get("path");
                String content = files.get("content");
                File file = safeFile(path);
                if (file != null && content != null && file.exists()) {
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

    private String buildWebDavXml(File file, String uri) {
        String displayName = file.getName();
        if (displayName.isEmpty()) displayName = "SDCard";
        boolean isDir = file.isDirectory();
        String type = isDir ? "<D:collection/>" : "";
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<D:multistatus xmlns:D=\"DAV:\">\n" +
                "  <D:response>\n" +
                "    <D:href>" + uri + "</D:href>\n" +
                "    <D:propstat>\n" +
                "      <D:prop>\n" +
                "        <D:displayname>" + displayName + "</D:displayname>\n" +
                "        <D:getcontentlength>" + file.length() + "</D:getcontentlength>\n" +
                "        <D:getlastmodified>" + new Date(file.lastModified()).toGMTString() + "</D:getlastmodified>\n" +
                "        <D:resourcetype>" + type + "</D:resourcetype>\n" +
                "      </D:prop>\n" +
                "      <D:status>HTTP/1.1 200 OK</D:status>\n" +
                "    </D:propstat>\n" +
                "  </D:response>\n";
        if (isDir) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    String childUri = uri.endsWith("/") ? uri + child.getName() : uri + "/" + child.getName();
                    String childType = child.isDirectory() ? "<D:collection/>" : "";
                    xml += "  <D:response>\n" +
                           "    <D:href>" + childUri + "</D:href>\n" +
                           "    <D:propstat>\n" +
                           "      <D:prop>\n" +
                           "        <D:displayname>" + child.getName() + "</D:displayname>\n" +
                           "        <D:getcontentlength>" + child.length() + "</D:getcontentlength>\n" +
                           "        <D:getlastmodified>" + new Date(child.lastModified()).toGMTString() + "</D:getlastmodified>\n" +
                           "        <D:resourcetype>" + childType + "</D:resourcetype>\n" +
                           "      </D:prop>\n" +
                           "      <D:status>HTTP/1.1 200 OK</D:status>\n" +
                           "    </D:propstat>\n" +
                           "  </D:response>\n";
                }
            }
        }
        xml += "</D:multistatus>";
        return xml;
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
            String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
            boolean isImg = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif");
            boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("xml") || ext.equals("java") || ext.equals("html");
            sb.append("{\"name\":\"").append(escape(f.getName())).append("\",")
              .append("\"dir\":").append(f.isDirectory()).append(",")
              .append("\"size\":").append(f.length()).append(",")
              .append("\"isImg\":").append(isImg).append(",")
              .append("\"isTxt\":").append(isTxt).append("}");
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
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }
}
