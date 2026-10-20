package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import fi.iki.elonen.NanoHTTPD;

public class NasHttpServer extends NanoHTTPD {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private Map<String, String> sharedLinks = new ConcurrentHashMap<>();
    private static volatile String clipboardContent = "";

    public NasHttpServer(int port) { super(port); }

    private File safeFile(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) path = "/";
        File f = new File(ROOT, path);
        String c = f.getCanonicalPath();
        String r = new File(ROOT).getCanonicalPath();
        if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
        return f;
    }

    private boolean isSafeName(String n) {
        if (n == null || n.isEmpty()) return false;
        if (n.contains("/") || n.contains("\\")) return false;
        if (n.equals(".") || n.equals("..")) return false;
        return true;
    }

    @Override public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        try {
            if (uri.startsWith("/share/")) return handleShare(uri.substring(7));
            if (uri.startsWith("/dlna/")) return handleDlna(uri);

            int role = 1;
            if (!Method.OPTIONS.equals(session.getMethod())) {
                role = AuthManager.verifyRole(session.getHeaders().get("authorization"));
                if (role == 0) {
                    Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "401");
                    r.addHeader("WWW-Authenticate", "Basic realm=\"Android NAS\"");
                    return r;
                }
            }

            if ("/api/status".equals(uri))
                return newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"lowPower\":" + SettingsManager.isLowPowerMode() + ",\"clipboardEnabled\":" + SettingsManager.isClipboardEnabled() + "}");

            if ("/api/clipboard".equals(uri)) {
                if (!SettingsManager.isClipboardEnabled()) {
                    Response r = text(Response.Status.FORBIDDEN, "Clipboard disabled");
                    r.addHeader("X-Reason", "clipboard_disabled");
                    return r;
                }
                if (Method.POST.equals(session.getMethod())) {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    String c = session.getParms().get("content");
                    if (c == null) c = files.get("content");
                    clipboardContent = c != null ? c : "";
                    return text(Response.Status.OK, "OK");
                }
                return text(Response.Status.OK, clipboardContent);
            }

            if ("/api/unzip".equals(uri) && Method.POST.equals(session.getMethod())) return handleUnzip(session, role);
            if ("/api/list".equals(uri)) return handleList(session);
            if ("/api/search".equals(uri)) return handleSearch(session);
            if ("/api/download".equals(uri)) return handleDownload(session);
            if ("/api/preview".equals(uri)) return handlePreview(session);
            if ("/api/upload".equals(uri) && Method.POST.equals(session.getMethod())) return handleUpload(session);
            if ("/api/rename".equals(uri) && Method.POST.equals(session.getMethod())) return handleRename(session, role);
            if ("/api/mkdir".equals(uri) || "/api/delete".equals(uri) || "/api/save".equals(uri))
                return handleFileOps(uri, session, role);
            if ("/api/share".equals(uri) && Method.POST.equals(session.getMethod())) return handleShareCreate(session, role);
            if (Method.PUT.equals(session.getMethod())) {
                return handlePut(session, role);
            }
            if ("/".equals(uri) || "/index.html".equals(uri)) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("assets/index.html");
                if (is == null) return text(Response.Status.INTERNAL_ERROR, "UI not found");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", is, -1);
            }
        } catch (Exception e) {
            return text(Response.Status.INTERNAL_ERROR, "Error: " + e.getMessage());
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response handleShare(String token) throws IOException {
        String p = sharedLinks.get(token);
        if (p != null) {
            File f = safeFile(p);
            if (f != null && f.exists() && !f.isDirectory()) {
                FileInputStream fis = new FileInputStream(f);
                Response r = newChunkedResponse(Response.Status.OK, "application/octet-stream", fis);
                r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                return r;
            }
        }
        return text(Response.Status.NOT_FOUND, "Share expired");
    }

    private Response handleDlna(String uri) {
        if ("/dlna/description.xml".equals(uri)) {
            String xml = "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\">" +
                "<specVersion><major>1</major><minor>0</minor></specVersion>" +
                "<device><deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>" +
                "<friendlyName>" + xmlEsc(SettingsManager.getDeviceAlias()) + "</friendlyName>" +
                "<manufacturer>AndroidNAS</manufacturer><modelName>NAS</modelName>" +
                "<UDN>uuid:" + SettingsManager.getFingerprint() + "</UDN></device></root>";
            return newFixedLengthResponse(Response.Status.OK, "application/xml; charset=utf-8", xml);
        }
        return text(Response.Status.NOT_FOUND, "Not found");
    }

    private Response handleUnzip(IHTTPSession session, int role) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String p = session.getParms().get("path");
        if (p == null) p = files.get("path");
        File zf = safeFile(p);
        if (zf == null || !zf.exists() || role != 1) return text(Response.Status.FORBIDDEN, "Fail");
        File dest = new File(zf.getParent(), zf.getName().replace(".zip", ""));
        dest.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zf))) {
            ZipEntry e;
            String dc = dest.getCanonicalPath();
            while ((e = zis.getNextEntry()) != null) {
                File nf = new File(dest, e.getName());
                if (!nf.getCanonicalPath().startsWith(dc + File.separator)) continue;
                if (e.isDirectory()) nf.mkdirs();
                else {
                    nf.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(nf)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                }
            }
        }
        return text(Response.Status.OK, "OK");
    }

    private Response handleList(IHTTPSession session) throws Exception {
        String path = session.getParms().get("path");
        if (path == null || path.isEmpty()) path = "/";
        File dir = safeFile(path);
        if (dir == null || !dir.exists()) return text(Response.Status.NOT_FOUND, "Not found");
        return newFixedLengthResponse(Response.Status.OK, "application/json", buildJson(dir, path));
    }

    private Response handleSearch(IHTTPSession session) {
        String q = session.getParms().get("q");
        if (q == null || q.isEmpty()) return newFixedLengthResponse(Response.Status.OK, "application/json", "[]");
        List<File> results = new ArrayList<>();
        searchRec(new File(ROOT), q.toLowerCase(), results, 0);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(fileJson(results.get(i), true));
        }
        sb.append("]");
        return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
    }

    private Response handleDownload(IHTTPSession session) throws Exception {
        File f = safeFile(session.getParms().get("path"));
        if (f == null || !f.exists() || f.isDirectory()) return text(Response.Status.NOT_FOUND, "Not found");
        long len = f.length();
        String range = session.getHeaders().get("range");
        if (SettingsManager.isStreamEnabled() && range != null && range.startsWith("bytes=")) {
            String[] r = range.substring(6).split("-");
            long start = Long.parseLong(r[0]);
            long end = r.length > 1 && !r[1].isEmpty() ? Long.parseLong(r[1]) : len - 1;
            if (end >= len) end = len - 1;
            final long contentLen = end - start + 1;
            final RandomAccessFile raf = new RandomAccessFile(f, "r");
            raf.seek(start);
            final FileInputStream fis = new FileInputStream(raf.getFD());
            InputStream is = new InputStream() {
                long rem = contentLen;
                @Override public int read() throws IOException {
                    if (rem <= 0) return -1;
                    int b = fis.read(); if (b != -1) rem--; return b;
                }
                @Override public int read(byte[] b, int off, int ln) throws IOException {
                    if (rem <= 0) return -1;
                    if (ln > rem) ln = (int) rem;
                    int n = fis.read(b, off, ln); if (n > 0) rem -= n; return n;
                }
                @Override public void close() throws IOException {
                    try { fis.close(); } catch (Exception ignored) {}
                    try { raf.close(); } catch (Exception ignored) {}
                }
            };
            Response resp = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime(f.getName()), is, contentLen);
            resp.addHeader("Accept-Ranges", "bytes");
            resp.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + len);
            return resp;
        }
        FileInputStream fis = new FileInputStream(f);
        Response r = newChunkedResponse(Response.Status.OK, mime(f.getName()), fis);
        r.addHeader("Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
        return r;
    }

    private Response handlePreview(IHTTPSession session) throws Exception {
        File f = safeFile(session.getParms().get("path"));
        if (f == null || !f.exists()) return text(Response.Status.NOT_FOUND, "Not found");
        if (f.length() > 2 * 1024 * 1024) return text(Response.Status.BAD_REQUEST, "Too large");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        return text(Response.Status.OK, sb.toString());
    }

    private Response handlePut(IHTTPSession session, int role) throws Exception {
        if (role != 1) return text(Response.Status.FORBIDDEN, "Forbidden");
        String uri = session.getUri();
        File f = safeFile(uri);
        if (f == null) return text(Response.Status.FORBIDDEN, "Path invalid");
        if (f.getParentFile() != null && !f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        long contentLength = 0;
        try { contentLength = Long.parseLong(session.getHeaders().get("content-length")); } catch (Exception ignored) {}
        if (contentLength <= 0) return text(Response.Status.BAD_REQUEST, "Content-Length required");

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
             InputStream is = session.getInputStream()) {
            byte[] buf = new byte[8192];
            int len;
            long total = 0;
            while (total < contentLength && (len = is.read(buf, 0, (int)Math.min(buf.length, contentLength - total))) > 0) {
                fos.write(buf, 0, len);
                total += len;
            }
        }
        return text(Response.Status.OK, "OK");
    }

    private Response handleUpload(IHTTPSession session) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String tp = session.getParms().get("path");
        if (tp == null) tp = files.get("path");
        File dir = safeFile(tp);
        if (dir == null || !dir.isDirectory()) dir = new File(ROOT);
        String tmp = null, orig = null;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String v = e.getValue();
            if (v != null && new File(v).exists()) { tmp = v; orig = e.getKey(); break; }
        }
        if (tmp == null) return text(Response.Status.BAD_REQUEST, "No file");
        if (orig == null) orig = new File(tmp).getName();
        File dest = new File(dir, new File(orig).getName());
        copy(new File(tmp), dest);
        new File(tmp).delete();
        return text(Response.Status.OK, "OK: " + orig);
    }

    private Response handleRename(IHTTPSession session, int role) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String p = session.getParms().get("path");
        if (p == null) p = files.get("path");
        String nn = session.getParms().get("newName");
        if (nn == null) nn = files.get("newName");
        File old = safeFile(p);
        if (old != null && isSafeName(nn) && role == 1) {
            if (old.renameTo(new File(old.getParent(), nn))) return text(Response.Status.OK, "OK");
        }
        return text(Response.Status.INTERNAL_ERROR, "Fail");
    }

    private Response handleFileOps(String uri, IHTTPSession session, int role) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String p = session.getParms().get("path");
        if (p == null) p = files.get("path");
        String n = session.getParms().get("name");
        if (n == null) n = files.get("name");
        String c = session.getParms().get("content");
        if (c == null) c = files.get("content");
        if ("/api/mkdir".equals(uri)) {
            File d = safeFile(p);
            if (d != null && isSafeName(n) && role == 1) {
                if (new File(d, n).mkdirs()) return text(Response.Status.OK, "OK");
            }
        } else if ("/api/delete".equals(uri)) {
            File f = safeFile(p);
            if (f != null && !f.getAbsolutePath().equals(ROOT) && role == 1) {
                if (deleteRec(f)) return text(Response.Status.OK, "OK");
            }
        } else {
            File f = safeFile(p);
            if (f != null && c != null && f.exists() && role == 1) {
                try (FileWriter w = new FileWriter(f)) { w.write(c); }
                return text(Response.Status.OK, "OK");
            }
        }
        return text(Response.Status.INTERNAL_ERROR, "Fail");
    }

    private Response handleShareCreate(IHTTPSession session, int role) throws Exception {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String p = session.getParms().get("path");
        if (p == null) p = files.get("path");
        if (p != null && role == 1) {
            String t = UUID.randomUUID().toString().substring(0, 8);
            sharedLinks.put(t, p);
            return text(Response.Status.OK, t);
        }
        return text(Response.Status.FORBIDDEN, "Fail");
    }

    private void searchRec(File dir, String q, List<File> res, int depth) {
        if (dir == null || !dir.exists() || res.size() >= 100 || depth > 10) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.getName().toLowerCase().contains(q)) res.add(f);
            if (f.isDirectory()) searchRec(f, q, res, depth + 1);
        }
    }

    private String mime(String n) {
        n = n.toLowerCase();
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mkv")) return "video/x-matroska";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".json") || n.endsWith(".java") || n.endsWith(".html"))
            return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private Response text(Response.Status s, String m) {
        return newFixedLengthResponse(s, "text/plain; charset=utf-8", m);
    }

    private String buildJson(File dir, String path) {
        File[] fs = dir.listFiles();
        if (fs == null) fs = new File[0];
        Arrays.sort(fs, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder("{\"path\":\"" + esc(path) + "\",\"files\":[");
        for (int i = 0; i < fs.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(fileJson(fs[i], false));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String fileJson(File f, boolean withPath) {
        String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf(".") + 1).toLowerCase() : "";
        boolean isTxt = ext.equals("txt") || ext.equals("md") || ext.equals("json") || ext.equals("java") || ext.equals("html");
        boolean isImg = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif") || ext.equals("webp");
        boolean isMedia = (isImg || ext.equals("mp4") || ext.equals("mp3") || ext.equals("mkv") || ext.equals("webm")) && SettingsManager.isStreamEnabled();
        boolean isZip = ext.equals("zip");
        StringBuilder sb = new StringBuilder("{\"name\":\"" + esc(f.getName()) + "\"");
        if (withPath) sb.append(",\"path\":\"").append(esc(f.getAbsolutePath().replace(ROOT, ""))).append("\"");
        sb.append(",\"dir\":").append(f.isDirectory())
          .append(",\"size\":").append(f.length())
          .append(",\"isTxt\":").append(isTxt)
          .append(",\"isMedia\":").append(isMedia)
          .append(",\"isZip\":").append(isZip).append("}");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private boolean deleteRec(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) if (!deleteRec(x)) return false;
        }
        return f.delete();
    }

    private void copy(File s, File d) throws IOException {
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] buf = new byte[8192]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
    }
}
