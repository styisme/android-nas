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
        this.rootDir = SettingsManager.isGlobalMode() ? "/" : Environment.getExternalStorageDirectory().getAbsolutePath();
        this.context = context;
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
        } catch (Exception e) {
            LogManager.add("FTP", "FTP服务启动失败: " + e.getMessage());
        }
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
        private ServerSocket dataServerSocket;
        private Socket dataSocket;
        private String renameFrom = null;

        public FtpSession(Socket socket) {
            this.controlSocket = socket;
            this.currentDir = new File(rootDir);
        }

        @Override public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"), true);
                reply("220 Android NAS FTP Server Ready");
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 2);
                    String cmd = parts[0].toUpperCase();
                    String arg = parts.length > 1 ? parts[1] : "";
                    if (cmd.equals("USER")) { currentUser = arg; reply("331 Password required"); }
                    else if (cmd.equals("PASS")) {
                        String enc = android.util.Base64.encodeToString((currentUser + ":" + arg).getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                        if (AuthManager.verifyRole("Basic " + enc) > 0) reply("230 Login successful");
                        else reply("530 Login incorrect");
                    }
                    else if (cmd.equals("SYST")) reply("215 UNIX Type: L8");
                    else if (cmd.equals("FEAT")) reply("211-Features:\r\n SIZE\r\n MDTM\r\n UTF8\r\n211 End");
                    else if (cmd.equals("OPTS")) reply("200 OK");
                    else if (cmd.equals("TYPE")) reply("200 Type set to " + arg);
                    else if (cmd.equals("PWD") || cmd.equals("XPWD")) reply("257 \"" + vpath(currentDir) + "\" is current directory");
                    else if (cmd.equals("CWD")) {
                        File t = safeFile(arg);
                        if (t != null && t.isDirectory()) { currentDir = t; reply("250 Directory changed"); }
                        else reply("550 Directory not found");
                    }
                    else if (cmd.equals("CDUP")) {
                        File p = currentDir.getParentFile();
                        if (p != null && p.getCanonicalPath().startsWith(new File(rootDir).getCanonicalPath())) { currentDir = p; reply("250 OK"); }
                        else reply("550 Cannot go up");
                    }
                    else if (cmd.equals("PASV")) enterPassiveMode();
                    else if (cmd.equals("LIST") || cmd.equals("NLST")) sendList(arg);
                    else if (cmd.equals("RETR")) sendFile(arg);
                    else if (cmd.equals("STOR")) receiveFile(arg);
                    else if (cmd.equals("DELE")) deleteFile(arg);
                    else if (cmd.equals("MKD") || cmd.equals("XMKD")) {
                        File f = safeFile(arg);
                        if (f != null && f.mkdirs()) reply("257 \"" + vpath(f) + "\" created");
                        else reply("550 Create failed");
                    }
                    else if (cmd.equals("RMD") || cmd.equals("XRMD")) {
                        File f = safeFile(arg);
                        if (f != null && f.delete()) reply("250 Removed");
                        else reply("550 Remove failed");
                    }
                    else if (cmd.equals("RNFR")) {
                        File f = safeFile(arg);
                        if (f != null && f.exists()) { renameFrom = f.getAbsolutePath(); reply("350 Ready for RNTO"); }
                        else reply("550 Not found");
                    }
                    else if (cmd.equals("RNTO")) {
                        if (renameFrom != null) {
                            File to = safeFile(arg);
                            if (to != null && new File(renameFrom).renameTo(to)) reply("250 Renamed");
                            else reply("550 Rename failed");
                            renameFrom = null;
                        } else reply("503 RNFR required");
                    }
                    else if (cmd.equals("SIZE")) {
                        File f = safeFile(arg);
                        if (f != null && f.exists()) reply("213 " + f.length());
                        else reply("550 Not found");
                    }
                    else if (cmd.equals("QUIT")) { reply("221 Goodbye"); break; }
                    else reply("502 Command not implemented: " + cmd);
                }
            } catch (Exception ignored) {
            } finally {
                try { controlSocket.close(); } catch (Exception ignored) {}
                closeDataConnection();
            }
        }

        private void reply(String msg) { writer.println(msg); }

        private void enterPassiveMode() {
            try {
                closeDataConnection();
                dataServerSocket = new ServerSocket(0);
                int p = dataServerSocket.getLocalPort();
                reply("227 Entering Passive Mode (" + getLocalIp().replace('.', ',') + "," + (p / 256) + "," + (p % 256) + ")");
            } catch (Exception e) { reply("425 Can't open data connection"); }
        }

        private void waitDataConnection() throws Exception { dataSocket = dataServerSocket.accept(); }

        private void closeDataConnection() {
            try { if (dataSocket != null) dataSocket.close(); } catch (Exception ignored) {}
            try { if (dataServerSocket != null) dataServerSocket.close(); } catch (Exception ignored) {}
            dataSocket = null; dataServerSocket = null;
        }

        private void sendList(String arg) {
            try {
                waitDataConnection();
                PrintWriter dw = new PrintWriter(new OutputStreamWriter(dataSocket.getOutputStream(), "UTF-8"), true);
                File dir = currentDir;
                if (arg != null && !arg.trim().isEmpty() && !arg.startsWith("-")) {
                    File t = safeFile(arg);
                    if (t != null && t.exists()) dir = t;
                }
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) {
                    String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                    String date = new java.text.SimpleDateFormat("MMM dd HH:mm", Locale.US).format(new Date(f.lastModified()));
                    dw.println(perms + " 1 ftp ftp " + f.length() + " " + date + " " + f.getName());
                }
                dw.flush();
                closeDataConnection();
                reply("226 Transfer complete");
            } catch (Exception e) { reply("425 Data connection failed"); }
        }

        private void sendFile(String filename) {
            File f = safeFile(filename);
            if (f == null || !f.exists() || f.isDirectory()) { reply("550 File not found"); return; }
            try {
                waitDataConnection();
                reply("150 Opening data connection");
                InputStream is = new FileInputStream(f);
                OutputStream os = dataSocket.getOutputStream();
                byte[] buf = new byte[8192]; int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                is.close(); os.close();
                closeDataConnection();
                reply("226 Transfer complete");
            } catch (Exception e) { reply("426 Transfer aborted"); }
        }

        private void receiveFile(String filename) {
            File f = safeFile(filename);
            if (f == null) { reply("550 Access denied"); return; }
            File p = f.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            if (p != null && !p.isDirectory()) { reply("550 Parent not a directory"); return; }
            try {
                waitDataConnection();
                reply("150 Opening data connection");
                InputStream is = dataSocket.getInputStream();
                OutputStream os = new FileOutputStream(f);
                byte[] buf = new byte[8192]; int len;
                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                os.close(); is.close();
                closeDataConnection();
                reply("226 Transfer complete");
            } catch (Exception e) {
                try { f.delete(); } catch (Exception ignored) {}
                reply("426 Transfer aborted");
            }
        }

        private void deleteFile(String filename) {
            File f = safeFile(filename);
            if (f != null && f.delete()) reply("250 File deleted");
            else reply("550 Delete failed");
        }

        private File safeFile(String path) {
            try {
                if (path == null) return null;
                if (SettingsManager.isGlobalMode()) {
                    File f = new File(path).isAbsolute() ? new File(path) : new File(currentDir, path);
                    return f;
                }
                File f = new File(path).isAbsolute() ? new File(rootDir, path) : new File(currentDir, path);
                String c = f.getCanonicalPath();
                String r = new File(rootDir).getCanonicalPath();
                if (!c.equals(r) && !c.startsWith(r + File.separator)) return null;
                return f;
            } catch (Exception e) { return null; }
        }

        private String vpath(File f) {
            try {
                String abs = f.getCanonicalPath();
                String root = new File(rootDir).getCanonicalPath();
                if (abs.equals(root)) return "/";
                if (abs.startsWith(root)) {
                    String r = abs.substring(root.length());
                    return r.startsWith("/") ? r : "/" + r;
                }
                return "/";
            } catch (Exception e) { return "/"; }
        }
    }
}
