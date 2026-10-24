package com.androidnas;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Environment;
import java.io.*;
import java.net.*;
import java.util.*;

public class FtpServer extends Thread {
    private int port;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private String rootDir;
    private Context context;

    public FtpServer(int port, Context context) {
        this.port = port;
        this.context = context;
        this.rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    @Override public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(new FtpSession(socket)).start();
            }
        } catch (Exception e) {}
    }

    private String getLocalIp() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Exception e) { return "127.0.0.1"; }
    }

    private class FtpSession implements Runnable {
        private Socket controlSocket;
        private BufferedReader reader;
        private PrintWriter writer;
        private File currentDir;
        private String currentUser = "";
        private String userRootDir = "";
        private ServerSocket dataServerSocket;
        private Socket dataSocket;
        private boolean loggedIn = false;

        public FtpSession(Socket socket) { this.controlSocket = socket; }

        @Override public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"), true);
                reply("220 Ready");
                String clientIp = controlSocket.getInetAddress().getHostAddress();
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
                            if (!allowed) { reply("421 IP Blocked by Whitelist"); break; }
                        }
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 2);
                    String cmd = parts[0].toUpperCase();
                    String arg = parts.length > 1 ? parts[1] : "";
                    
                    if (!loggedIn && !cmd.equals("USER") && !cmd.equals("PASS") && !cmd.equals("QUIT")) {
                        reply("530 Please login."); continue;
                    }
                    
                    if (cmd.equals("USER")) { currentUser = arg; reply("331 Password required"); }
                    else if (cmd.equals("PASS")) {
                        String enc = android.util.Base64.encodeToString((currentUser + ":" + arg).getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                        String clientIp = controlSocket.getInetAddress().getHostAddress();
                        if (SettingsManager.isFail2BanEnabled() && SecurityFilter.isBlocked(clientIp)) { reply("421 Too many attempts."); break; }
                        if (AuthManager.verifyRole("Basic " + enc) > 0) {
                            SecurityFilter.recordSuccess(clientIp, currentUser);
                            loggedIn = true;
                            userRootDir = DatabaseManager.getInstance().getUserRoot(currentUser);
                            if (SettingsManager.isGlobalMode() && currentUser.equals("admin")) this.currentDir = new File("/");
                            else { this.currentDir = new File(rootDir, userRootDir); if (!this.currentDir.exists()) this.currentDir.mkdirs(); }
                            reply("230 Login successful");
                        } else { SecurityFilter.recordFail(clientIp); reply("530 Login incorrect"); }
                    }
                    else if (cmd.equals("SYST")) reply("215 UNIX");
                    else if (cmd.equals("TYPE")) reply("200 OK");
                    else if (cmd.equals("PWD") || cmd.equals("XPWD")) reply("257 \"" + currentDir.getAbsolutePath() + "\"");
                    else if (cmd.equals("CWD")) {
                        File t = safeFile(arg);
                        if (t != null && t.isDirectory()) { currentDir = t; reply("250 OK"); }
                        else reply("550 Failed");
                    }
                    else if (cmd.equals("PASV")) enterPassiveMode();
                    else if (cmd.equals("LIST") || cmd.equals("NLST")) sendList(arg);
                    else if (cmd.equals("RETR")) sendFile(arg);
                    else if (cmd.equals("STOR")) receiveFile(arg);
                    else if (cmd.equals("DELE")) { File f = safeFile(arg); if (f != null && f.delete()) reply("250 OK"); else reply("550 Fail"); }
                    else if (cmd.equals("QUIT")) { reply("221 Bye"); break; }
                    else reply("502 Not implemented");
                }
            } catch (Exception ignored) {
            } finally { try { controlSocket.close(); } catch (Exception ignored) {} closeDataConnection(); AuthManager.clearCurrentUser(); }
        }

        private void reply(String msg) { writer.print(msg + "\r\n"); writer.flush(); }

        private void enterPassiveMode() {
            try {
                closeDataConnection(); dataServerSocket = new ServerSocket(0);
                int p = dataServerSocket.getLocalPort();
                // 修复：使用真实的局域网 IP
                reply("227 Entering Passive Mode (" + getLocalIp().replace('.', ',') + "," + (p / 256) + "," + (p % 256) + ")");
            } catch (Exception e) { reply("425 Fail"); }
        }

        private void waitDataConnection() throws Exception { dataServerSocket.setSoTimeout(10000); dataSocket = dataServerSocket.accept(); }
        private void closeDataConnection() {
            try { if (dataSocket != null) dataSocket.close(); } catch (Exception ignored) {}
            try { if (dataServerSocket != null) dataServerSocket.close(); } catch (Exception ignored) {}
        }

        private void sendList(String arg) {
            try {
                waitDataConnection();
                PrintWriter dw = new PrintWriter(dataSocket.getOutputStream(), true);
                File[] files = currentDir.listFiles();
                if (files != null) for (File f : files) {
                    dw.print((f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--") + " 1 ftp ftp " + f.length() + " Jan 01 00:00 " + f.getName().replace("\r","").replace("\n","") + "\r\n");
                }
                dw.flush(); closeDataConnection(); reply("226 OK");
            } catch (Exception e) { reply("425 Fail"); }
        }

        private void sendFile(String filename) {
            File f = safeFile(filename);
            if (f == null || !f.exists() || f.isDirectory()) { reply("550 Fail"); return; }
            try {
                waitDataConnection(); reply("150 OK");
                InputStream is = new FileInputStream(f); OutputStream os = dataSocket.getOutputStream();
                byte[] buf = new byte[8192]; int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                is.close(); os.close(); closeDataConnection(); reply("226 OK");
            } catch (Exception e) { reply("426 Abort"); }
        }

        private void receiveFile(String filename) {
            File f = safeFile(filename);
            if (f == null) { reply("550 Denied"); return; }
            try {
                waitDataConnection(); reply("150 OK");
                InputStream is = dataSocket.getInputStream(); OutputStream os = new FileOutputStream(f);
                byte[] buf = new byte[8192]; int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                os.close(); is.close(); closeDataConnection(); reply("226 OK");
            } catch (Exception e) { try { f.delete(); } catch (Exception ignored) {} reply("426 Abort"); }
        }

        private File safeFile(String path) {
            try {
                if (SettingsManager.isGlobalMode() && currentUser.equals("admin")) return new File(path).isAbsolute() ? new File(path) : new File(currentDir, path);
                File baseDir = new File(rootDir, userRootDir);
                if (!baseDir.exists()) baseDir.mkdirs();
                File f = new File(path).isAbsolute() ? new File(baseDir, path) : new File(currentDir, path);
                String c = f.getCanonicalPath();
                String r = baseDir.getCanonicalPath();
                if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
                return f;
            } catch (Exception e) { return null; }
        }
    }
}
