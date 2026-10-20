package com.androidnas;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class WebDavServer extends Thread {
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private String rootDir;

    public WebDavServer(String rootDir) { this.rootDir = SettingsManager.isGlobalMode() ? "/" : rootDir; }

    public void startServer(int port) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        this.start();
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
    }

    @Override public void run() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                new Thread(new ClientHandler(s)).start();
            } catch (IOException e) { if (running) e.printStackTrace(); }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket s) { socket = s; }

        @Override public void run() {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new BufferedInputStream(socket.getInputStream());
                out = socket.getOutputStream();
                String line = readLine(in);
                if (line == null || line.isEmpty()) return;
                String[] parts = line.split(" ");
                if (parts.length < 2) return;
                String method = parts[0];
                String uri = parts[1];
                if (uri.contains("?")) uri = uri.substring(0, uri.indexOf("?"));
                uri = decUrl(uri);
                if (!uri.startsWith("/")) uri = "/" + uri;

                Map<String, String> headers = new HashMap<>();
                long contentLength = 0;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int idx = line.indexOf(":");
                    if (idx > 0) {
                        String k = line.substring(0, idx).trim().toLowerCase(Locale.US);
                        String v = line.substring(idx + 1).trim();
                        headers.put(k, v);
                        if (k.equals("content-length")) try { contentLength = Long.parseLong(v); } catch (Exception ignored) {}
                    }
                }

                if (!"OPTIONS".equalsIgnoreCase(method)) {
                    String auth = headers.get("authorization");
                    if (!AuthManager.verify(auth)) {
                        out.write("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Android NAS WebDAV\"\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                        return;
                    }
                }

                File target = new File(rootDir, uri);
                if (!SettingsManager.isGlobalMode()) {
                    String tc = target.getCanonicalPath();
                    String rc = new File(rootDir).getCanonicalPath();
                    if (!tc.equals(rc) && !tc.startsWith(rc + File.separator)) { sendErr(out, "403 Forbidden"); return; }
                }

                String depth = headers.get("depth");
                if (depth == null) depth = "1";

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    out.write("HTTP/1.1 200 OK\r\nDAV: 1\r\nAllow: OPTIONS,GET,HEAD,PUT,DELETE,PROPFIND,MKCOL\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                } else if ("PROPFIND".equalsIgnoreCase(method)) {
                    if (!target.exists()) { sendErr(out, "404 Not Found"); return; }
                    String xml = buildXml(target, uri, "0".equals(depth));
                    byte[] xb = xml.getBytes("UTF-8");
                    out.write(("HTTP/1.1 207 Multi-Status\r\nContent-Type: application/xml; charset=\"utf-8\"\r\nContent-Length: " + xb.length + "\r\n\r\n").getBytes("UTF-8"));
                    out.write(xb);
                } else if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                    if (!target.exists() || target.isDirectory()) { sendErr(out, "404 Not Found"); return; }
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + target.length() + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
                    if (method.equalsIgnoreCase("GET")) {
                        try (FileInputStream fis = new FileInputStream(target)) {
                            byte[] buf = new byte[8192]; int len;
                            while ((len = fis.read(buf)) > 0) out.write(buf, 0, len);
                        }
                    }
                } else if ("PUT".equalsIgnoreCase(method)) {
                    if (target.isDirectory()) { sendErr(out, "409 Conflict"); return; }
                    boolean existed = target.exists();
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192]; long total = 0;
                        while (total < contentLength) {
                            int toRead = (int) Math.min(buf.length, contentLength - total);
                            int r = in.read(buf, 0, toRead);
                            if (r == -1) break;
                            fos.write(buf, 0, r); total += r;
                        }
                    }
                    if (existed) out.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                    else out.write("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                } else if ("MKCOL".equalsIgnoreCase(method)) {
                    if (target.exists()) { sendErr(out, "405 Method Not Allowed"); return; }
                    if (target.mkdirs()) out.write("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                    else sendErr(out, "403 Forbidden");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    if (deleteRec(target)) out.write("HTTP/1.1 204 No Content\r\n\r\n".getBytes("UTF-8"));
                    else sendErr(out, "404 Not Found");
                } else sendErr(out, "405 Method Not Allowed");
            } catch (Exception ignored) {
            } finally {
                try { if (out != null) out.flush(); } catch (Exception ignored) {}
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String decUrl(String url) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < url.length(); i++) {
                char c = url.charAt(i);
                if (c == '+') sb.append(' ');
                else if (c == '%' && i + 2 < url.length()) {
                    try {
                        int code = Integer.parseInt(url.substring(i + 1, i + 3), 16);
                        sb.append((char) code); i += 2;
                    } catch (Exception e) { sb.append(c); }
                } else sb.append(c);
            }
            return sb.toString();
        } catch (Exception e) { return url; }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void sendErr(OutputStream out, String err) throws IOException {
        out.write(("HTTP/1.1 " + err + "\r\nContent-Length: 0\r\n\r\n").getBytes("UTF-8"));
    }

    private String xmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String buildXml(File file, String uri, boolean d0) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<D:multistatus xmlns:D=\"DAV:\">\n");
        appendResp(sb, file, uri);
        if (!d0 && file.isDirectory()) {
            File[] ch = file.listFiles();
            if (ch != null) for (File c : ch) {
                String cu = uri.endsWith("/") ? uri + c.getName() : uri + "/" + c.getName();
                appendResp(sb, c, cu);
            }
        }
        sb.append("</D:multistatus>");
        return sb.toString();
    }

    private void appendResp(StringBuilder sb, File file, String uri) {
        sb.append("<D:response>\n<D:href>").append(xmlEsc(uri)).append("</D:href>\n<D:propstat>\n<D:prop>\n");
        String dn = file.getName();
        if (dn.isEmpty()) dn = "SDCard";
        sb.append("<D:displayname>").append(xmlEsc(dn)).append("</D:displayname>\n");
        if (!file.isDirectory()) sb.append("<D:getcontentlength>").append(file.length()).append("</D:getcontentlength>\n");
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sb.append("<D:getlastmodified>").append(sdf.format(new Date(file.lastModified()))).append("</D:getlastmodified>\n");
        if (file.isDirectory()) sb.append("<D:resourcetype><D:collection/></D:resourcetype>\n");
        else sb.append("<D:resourcetype/>\n");
        sb.append("</D:prop>\n<D:status>HTTP/1.1 200 OK</D:status>\n</D:propstat>\n</D:response>\n");
    }

    private boolean deleteRec(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) if (!deleteRec(c)) return false;
        }
        return f.delete();
    }
}
