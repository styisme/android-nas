package com.androidnas;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Locale;

public class WebDavServer extends Thread {
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private String rootDir;

    public WebDavServer(String baseRoot) { this.rootDir = baseRoot; }

    public void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(); serverSocket.setReuseAddress(true); serverSocket.bind(new InetSocketAddress(port));
        this.start();
    }

    public void stopServer() { running = false; try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {} }

    @Override public void run() {
        while (running) {
            try { Socket s = serverSocket.accept(); new Thread(new ClientHandler(s)).start(); } catch (Exception e) {}
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket s) { socket = s; }

        @Override public void run() {
            InputStream in = null; OutputStream out = null;
            try {
                in = new BufferedInputStream(socket.getInputStream());
                out = socket.getOutputStream();
                String line = readLine(in);
                if (line == null || line.isEmpty()) return;
                String[] parts = line.split(" ");
                if (parts.length < 2) return;
                String method = parts[0], uri = parts[1];
                if (uri.contains("?")) uri = uri.substring(0, uri.indexOf("?"));
                uri = URLDecoder.decode(uri.replace("+", "%2B"), "UTF-8"); // 修复 + 变空格
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
                    String clientIp = socket.getInetAddress().getHostAddress();
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
                        if (!allowed) { out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes()); return; }
                    }
                    if (SettingsManager.isFail2BanEnabled() && SecurityFilter.isBlocked(clientIp)) { out.write("HTTP/1.1 429 Too Many Requests\r\n\r\n".getBytes()); return; }
                    String auth = headers.get("authorization");
                    if (AuthManager.verifyRole(auth) == 0) {
                        SecurityFilter.recordFail(clientIp);
                        out.write("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"WebDAV\"\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8")); return;
                    }
                    SecurityFilter.recordSuccess(clientIp, AuthManager.getCurrentUser());
                }
                
                String user = AuthManager.getCurrentUser();
                String userRoot = DatabaseManager.getInstance().getUserRoot(user);
                String activeRoot = (SettingsManager.isGlobalMode() && user.equals("admin")) ? "/" : new File(rootDir, userRoot).getAbsolutePath();
                File activeRootFile = new File(activeRoot);
                if (!activeRootFile.exists()) activeRootFile.mkdirs();

                // 核心修复：移除 uri 前导斜杠，防止 new File(parent, child) 忽略 parent
                File target = new File(activeRootFile, uri.replaceAll("^/+", ""));
                if (!(SettingsManager.isGlobalMode() && user.equals("admin"))) {
                    String tc = target.getCanonicalPath(); String rc = activeRootFile.getCanonicalPath();
                    if (!tc.equals(rc) && !tc.startsWith(rc + File.separator)) { out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes()); return; }
                }

                if ("OPTIONS".equalsIgnoreCase(method)) out.write("HTTP/1.1 200 OK\r\nDAV: 1\r\nAllow: OPTIONS,GET,HEAD,PUT,DELETE,PROPFIND,MKCOL\r\nContent-Length: 0\r\n\r\n".getBytes());
                else if ("PROPFIND".equalsIgnoreCase(method)) {
                    if (!target.exists()) { out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes()); return; }
                    String xml = buildXml(target, uri, "0".equals(headers.get("depth")));
                    byte[] xb = xml.getBytes("UTF-8");
                    out.write(("HTTP/1.1 207 Multi-Status\r\nContent-Type: application/xml; charset=\"utf-8\"\r\nContent-Length: " + xb.length + "\r\n\r\n").getBytes()); out.write(xb);
                } else if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                    if (!target.exists() || target.isDirectory()) { out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes()); return; }
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + target.length() + "\r\n\r\n").getBytes());
                    if (method.equalsIgnoreCase("GET")) try (FileInputStream fis = new FileInputStream(target)) { byte[] buf = new byte[8192]; int len; while ((len = fis.read(buf)) > 0) out.write(buf, 0, len); }
                } else if ("PUT".equalsIgnoreCase(method)) {
                    boolean existed = target.exists();
                    if (target.getParentFile() != null) target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192]; long total = 0;
                        while (total < contentLength) { int toRead = (int) Math.min(buf.length, contentLength - total); int r = in.read(buf, 0, toRead); if (r == -1) break; fos.write(buf, 0, r); total += r; }
                    }
                    out.write((existed ? "HTTP/1.1 204 No Content\r\n\r\n" : "HTTP/1.1 201 Created\r\n\r\n").getBytes());
                } else if ("MKCOL".equalsIgnoreCase(method)) {
                    if (target.exists()) out.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".getBytes());
                    else if (target.mkdirs()) out.write("HTTP/1.1 201 Created\r\n\r\n".getBytes()); else out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes());
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    if (deleteRec(target)) out.write("HTTP/1.1 204 No Content\r\n\r\n".getBytes()); else out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                } else out.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n".getBytes());
            } catch (Exception ignored) {
            } finally { try { if (out != null) out.flush(); } catch (Exception ignored) {} try { if (in != null) in.close(); } catch (Exception ignored) {} try { socket.close(); } catch (Exception ignored) {} AuthManager.clearCurrentUser(); }
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int b;
        while ((b = in.read()) != -1) { if (b == '\n') break; if (b != '\r') sb.append((char) b); }
        return sb.length() == 0 ? null : sb.toString();
    }
    private String xmlEsc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private String buildXml(File file, String uri, boolean d0) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<D:multistatus xmlns:D=\"DAV:\">\n");
        appendResp(sb, file, uri);
        if (!d0 && file.isDirectory()) { File[] ch = file.listFiles(); if (ch != null) for (File c : ch) appendResp(sb, c, uri.endsWith("/") ? uri + c.getName() : uri + "/" + c.getName()); }
        sb.append("</D:multistatus>"); return sb.toString();
    }
    private void appendResp(StringBuilder sb, File file, String uri) {
        sb.append("<D:response>\n<D:href>").append(xmlEsc(uri)).append("</D:href>\n<D:propstat>\n<D:prop>\n");
        sb.append("<D:displayname>").append(xmlEsc(file.getName().isEmpty() ? "root" : file.getName())).append("</D:displayname>\n");
        if (!file.isDirectory()) sb.append("<D:getcontentlength>").append(file.length()).append("</D:getcontentlength>\n");
        sb.append("<D:resourcetype>").append(file.isDirectory() ? "<D:collection/>" : "").append("</D:resourcetype>\n");
        sb.append("</D:prop>\n<D:status>HTTP/1.1 200 OK</D:status>\n</D:propstat>\n</D:response>\n");
    }
    private boolean deleteRec(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) { File[] ch = f.listFiles(); if (ch != null) for (File c : ch) if (!deleteRec(c)) return false; }
        return f.delete();
    }
}
