package com.androidnas;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLServerSocketFactory;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class WebDavServer extends Thread {
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private String rootDir;

    public WebDavServer(String rootDir) { this.rootDir = rootDir; }

    public void startServer(int port, boolean isSecure, SSLServerSocketFactory sslFactory) throws IOException {
        if (isSecure && sslFactory != null) {
            serverSocket = sslFactory.createServerSocket(port);
        } else {
            serverSocket = new ServerSocket(port);
        }
        this.start();
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            } catch (IOException e) { if (running) e.printStackTrace(); }
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new BufferedInputStream(socket.getInputStream());
                out = socket.getOutputStream();

                String requestLine = readLine(in);
                if (requestLine == null || requestLine.isEmpty()) return;
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) return;

                String method = parts[0];
                String uri = parts[1];
                if (uri.contains("?")) uri = uri.substring(0, uri.indexOf("?"));
                uri = java.net.URLDecoder.decode(uri, "UTF-8");
                if (!uri.startsWith("/")) uri = "/" + uri;

                Map<String, String> headers = new HashMap<String, String>();
                String line;
                int contentLength = 0;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int idx = line.indexOf(":");
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim().toLowerCase(Locale.US);
                        String value = line.substring(idx + 1).trim();
                        headers.put(key, value);
                        if (key.equals("content-length")) {
                            try { contentLength = Integer.parseInt(value); } catch (Exception e) {}
                        }
                    }
                }

                // WebDAV 身份认证拦截
                if (!"OPTIONS".equalsIgnoreCase(method)) {
                    String auth = headers.get("authorization");
                    if (!AuthManager.verify(auth)) {
                        String resp = "HTTP/1.1 401 Unauthorized\r\n" +
                                "WWW-Authenticate: Basic realm=\"Android NAS WebDAV\"\r\n" +
                                "Content-Length: 0\r\n\r\n";
                        out.write(resp.getBytes("UTF-8"));
                        return;
                    }
                }

                File targetFile = new File(rootDir, uri);
                if (!targetFile.getCanonicalPath().startsWith(new File(rootDir).getCanonicalPath())) {
                    sendError(out, "403 Forbidden"); return;
                }

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    String resp = "HTTP/1.1 200 OK\r\nDAV: 1, 2\r\nAllow: OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL\r\nContent-Length: 0\r\n\r\n";
                    out.write(resp.getBytes("UTF-8"));
                } else if ("PROPFIND".equalsIgnoreCase(method)) {
                    if (!targetFile.exists()) { sendError(out, "404 Not Found"); return; }
                    String xml = buildPropfindXml(targetFile, uri);
                    byte[] xmlBytes = xml.getBytes("UTF-8");
                    String resp = "HTTP/1.1 207 Multi-Status\r\nContent-Type: application/xml; charset=\"utf-8\"\r\nContent-Length: " + xmlBytes.length + "\r\n\r\n";
                    out.write(resp.getBytes("UTF-8"));
                    out.write(xmlBytes);
                } else if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                    if (!targetFile.exists() || targetFile.isDirectory()) { sendError(out, "404 Not Found"); return; }
                    String resp = "HTTP/1.1 200 OK\r\nContent-Length: " + targetFile.length() + "\r\nContent-Type: application/octet-stream\r\n\r\n";
                    out.write(resp.getBytes("UTF-8"));
                    if (method.equalsIgnoreCase("GET")) {
                        FileInputStream fis = new FileInputStream(targetFile);
                        byte[] buf = new byte[8192]; int len;
                        while ((len = fis.read(buf)) > 0) out.write(buf, 0, len);
                        fis.close();
                    }
                } else if ("PUT".equalsIgnoreCase(method)) {
                    if (targetFile.isDirectory()) { sendError(out, "409 Conflict"); return; }
                    if (targetFile.getParentFile() != null) targetFile.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    byte[] buf = new byte[8192]; long totalRead = 0;
                    while (totalRead < contentLength) {
                        int toRead = (int) Math.min(buf.length, contentLength - totalRead);
                        int read = in.read(buf, 0, toRead);
                        if (read == -1) break;
                        fos.write(buf, 0, read); totalRead += read;
                    }
                    fos.close();
                    out.write("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                } else if ("MKCOL".equalsIgnoreCase(method)) {
                    if (targetFile.exists()) { sendError(out, "405 Method Not Allowed"); return; }
                    if (targetFile.mkdirs()) out.write("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                    else sendError(out, "403 Forbidden");
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    if (deleteRecursive(targetFile)) out.write("HTTP/1.1 204 No Content\r\n\r\n".getBytes("UTF-8"));
                    else sendError(out, "404 Not Found");
                } else {
                    sendError(out, "405 Method Not Allowed");
                }
            } catch (Exception e) {
            } finally {
                try { if (out != null) out.flush(); } catch (Exception e) {}
                try { if (in != null) in.close(); } catch (Exception e) {}
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(); int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') sb.append((char) b);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void sendError(OutputStream out, String error) throws IOException {
        out.write(("HTTP/1.1 " + error + "\r\nContent-Length: 0\r\n\r\n").getBytes("UTF-8"));
    }

    private String buildPropfindXml(File file, String uri) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<D:multistatus xmlns:D=\"DAV:\">\n");
        appendResponse(sb, file, uri);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) {
                String childUri = uri.endsWith("/") ? uri + child.getName() : uri + "/" + child.getName();
                appendResponse(sb, child, childUri);
            }
        }
        sb.append("</D:multistatus>");
        return sb.toString();
    }

    private void appendResponse(StringBuilder sb, File file, String uri) {
        sb.append("<D:response>\n<D:href>").append(uri).append("</D:href>\n<D:propstat>\n<D:prop>\n");
        String displayName = file.getName();
        if (displayName.isEmpty()) displayName = "SDCard";
        sb.append("<D:displayname>").append(displayName).append("</D:displayname>\n");
        if (!file.isDirectory()) sb.append("<D:getcontentlength>").append(file.length()).append("</D:getcontentlength>\n");
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sb.append("<D:getlastmodified>").append(sdf.format(new Date(file.lastModified()))).append("</D:getlastmodified>\n");
        if (file.isDirectory()) sb.append("<D:resourcetype><D:collection/></D:resourcetype>\n");
        else sb.append("<D:resourcetype/>\n");
        sb.append("</D:prop>\n<D:status>HTTP/1.1 200 OK</D:status>\n</D:propstat>\n</D:response>\n");
    }

    private boolean deleteRecursive(File f) {
        if (!f.exists()) return true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) if (!deleteRecursive(c)) return false;
        }
        return f.delete();
    }
}
