package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.net.*;
import java.util.*;

public class FtpServer extends Thread {
    private int port;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private String rootDir;

    public FtpServer(int port) {
        this.port = port;
        this.rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(new FtpSession(socket)).start();
            }
        } catch (Exception e) {
            LogManager.add("FTP", "FTP服务启动失败: " + e.getMessage());
        }
    }

    private class FtpSession implements Runnable {
        private Socket controlSocket;
        private BufferedReader reader;
        private PrintWriter writer;
        private File currentDir;
        private String currentUser = "";
        private ServerSocket dataServerSocket;
        private Socket dataSocket;

        public FtpSession(Socket socket) {
            this.controlSocket = socket;
            this.currentDir = new File(rootDir);
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), "UTF-8"));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"), true);
                
                reply("220 Android NAS FTP Server Ready");

                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 2);
                    String cmd = parts[0].toUpperCase();
                    String arg = parts.length > 1 ? parts[1] : "";

                    if (cmd.equals("USER")) {
                        currentUser = arg;
                        reply("331 Password required");
                    } else if (cmd.equals("PASS")) {
                        if (AuthManager.verifyRole("Basic " + Base64.getEncoder().encodeToString((currentUser + ":" + arg).getBytes())) > 0) {
                            reply("230 Login successful");
                        } else {
                            reply("530 Login incorrect");
                        }
                    } else if (cmd.equals("SYST")) {
                        reply("215 UNIX Type: L8");
                    } else if (cmd.equals("PWD")) {
                        reply("257 \"" + getVirtualPath(currentDir) + "\" is current directory");
                    } else if (cmd.equals("CWD")) {
                        File target = safeFile(arg);
                        if (target != null && target.exists() && target.isDirectory()) {
                            currentDir = target;
                            reply("250 Directory changed");
                        } else {
                            reply("550 Directory not found");
                        }
                    } else if (cmd.equals("PASV")) {
                        enterPassiveMode();
                    } else if (cmd.equals("LIST")) {
                        sendList();
                    } else if (cmd.equals("RETR")) {
                        sendFile(arg);
                    } else if (cmd.equals("STOR")) {
                        receiveFile(arg);
                    } else if (cmd.equals("DELE")) {
                        deleteFile(arg);
                    } else if (cmd.equals("QUIT")) {
                        reply("221 Goodbye");
                        break;
                    } else {
                        reply("502 Command not implemented");
                    }
                }
            } catch (Exception e) {
                // ignore
            } finally {
                try { controlSocket.close(); } catch (Exception ignored) {}
                try { if (dataServerSocket != null) dataServerSocket.close(); } catch (Exception ignored) {}
            }
        }

        private void reply(String msg) {
            writer.println(msg);
        }

        private void enterPassiveMode() {
            try {
                if (dataServerSocket != null) dataServerSocket.close();
                dataServerSocket = new ServerSocket(0);
                int p1 = dataServerSocket.getLocalPort() / 256;
                int p2 = dataServerSocket.getLocalPort() % 256;
                String ip = controlSocket.getLocalAddress().getHostAddress().replace('.', ',');
                reply("227 Entering Passive Mode (" + ip + "," + p1 + "," + p2 + ")");
            } catch (Exception e) {
                reply("425 Can't open data connection");
            }
        }

        private void waitDataConnection() throws Exception {
            dataSocket = dataServerSocket.accept();
        }

        private void closeDataConnection() {
            try { if (dataSocket != null) dataSocket.close(); } catch (Exception ignored) {}
            try { if (dataServerSocket != null) dataServerSocket.close(); } catch (Exception ignored) {}
        }

        private void sendList() {
            try {
                waitDataConnection();
                PrintWriter dataWriter = new PrintWriter(new OutputStreamWriter(dataSocket.getOutputStream(), "UTF-8"), true);
                File[] files = currentDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                        String date = new java.text.SimpleDateFormat("MMM dd HH:mm", Locale.US).format(new Date(f.lastModified()));
                        dataWriter.println(perms + " 1 ftp ftp " + f.length() + " " + date + " " + f.getName());
                    }
                }
                dataWriter.flush();
                closeDataConnection();
                reply("226 Transfer complete");
            } catch (Exception e) {
                reply("425 Data connection failed");
            }
        }

        private void sendFile(String filename) {
            File f = safeFile(currentDir.getAbsolutePath(), filename);
            if (f == null || !f.exists() || f.isDirectory()) {
                reply("550 File not found");
                return;
            }
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
            } catch (Exception e) {
                reply("426 Connection closed; transfer aborted");
            }
        }

        private void receiveFile(String filename) {
            File f = safeFile(currentDir.getAbsolutePath(), filename);
            if (f == null) {
                reply("550 Access denied");
                return;
            }
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
                reply("426 Connection closed; transfer aborted");
            }
        }

        private void deleteFile(String filename) {
            File f = safeFile(currentDir.getAbsolutePath(), filename);
            if (f != null && f.delete()) reply("250 File deleted");
            else reply("550 Delete failed");
        }

        private File safeFile(String path) {
            return safeFile(currentDir.getAbsolutePath(), path);
        }

        private File safeFile(String base, String path) {
            try {
                File f = new File(path).isAbsolute() ? new File(rootDir, path) : new File(base, path);
                if (!f.getCanonicalPath().startsWith(new File(rootDir).getCanonicalPath())) return null;
                return f;
            } catch (Exception e) { return null; }
        }

        private String getVirtualPath(File f) {
            return f.getAbsolutePath().replace(rootDir, "/");
        }
    }
}
