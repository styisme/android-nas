package com.androidnas.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileListActivity extends Activity {

    private ListView listView;
    private TextView tvTitle;
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    
    private WatchDevice device;
    private String baseUrl = "";
    private String currentPath = "/";
    private String authHeader = "";
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        device = (WatchDevice) getIntent().getSerializableExtra("device");
        if (device == null) { finish(); return; }

        if (device.type != WatchDevice.TYPE_FTP) {
            baseUrl = "http://" + device.ip + ":" + device.port;
        }
        authHeader = "Basic " + android.util.Base64.encodeToString((device.user + ":" + device.pass).getBytes(), android.util.Base64.NO_WRAP);

        setContentView(com.androidnas.watch.R.layout.activity_main);

        listView = findViewById(com.androidnas.watch.R.id.deviceListView);
        tvTitle = findViewById(com.androidnas.watch.R.id.tvTitle);
        findViewById(com.androidnas.watch.R.id.btnAddDevice).setVisibility(View.GONE); // 隐藏添加按钮

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            FileItem f = files.get(position);
            if (f.isDir) {
                if (currentPath.endsWith("/")) currentPath += f.name;
                else currentPath += "/" + f.name;
                loadFiles();
            } else {
                sendToTv(f);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!currentPath.equals("/")) {
                String[] parts = currentPath.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
                currentPath = sb.length() == 0 ? "/" : sb.toString();
                loadFiles();
            }
            return true;
        });

        loadFiles();
    }

    private void loadFiles() {
        files.clear();
        tvTitle.setText("路径: " + currentPath);
        adapter.notifyDataSetChanged();

        new Thread(() -> {
            try {
                if (device.type == WatchDevice.TYPE_ANDROID) {
                    loadAndroidFiles();
                } else if (device.type == WatchDevice.TYPE_WEBDAV) {
                    loadWebDavFiles();
                } else if (device.type == WatchDevice.TYPE_FTP) {
                    loadFtpFiles();
                }
                mainHandler.post(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(FileListActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadAndroidFiles() throws Exception {
        String apiUrl = baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8");
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setRequestProperty("Authorization", authHeader);

        if (conn.getResponseCode() == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject obj = new JSONObject(sb.toString());
            JSONArray arr = obj.getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                files.add(new FileItem(f.getString("name"), f.getBoolean("dir")));
            }
        }
    }

    private void loadWebDavFiles() throws Exception {
        String urlStr = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/");
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PROPFIND");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Depth", "1");
        conn.setDoOutput(true);
        
        String xmlBody = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        conn.getOutputStream().write(xmlBody.getBytes("UTF-8"));

        if (conn.getResponseCode() == 207) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            Pattern responsePattern = Pattern.compile("<D:response>(.*?)</D:response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern hrefPattern = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern isDirPattern = Pattern.compile("<D:collection\\s*/>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            
            Matcher matcher = responsePattern.matcher(sb.toString());
            while (matcher.find()) {
                String block = matcher.group(1);
                Matcher hrefMatcher = hrefPattern.matcher(block);
                if (!hrefMatcher.find()) continue;
                String href = hrefMatcher.group(1);
                String name = href.substring(href.lastIndexOf("/") + 1);
                if (name.isEmpty()) continue;
                boolean isDir = isDirPattern.matcher(block).find();
                files.add(new FileItem(name, isDir));
            }
        }
    }

    private void loadFtpFiles() throws Exception {
        FTPClient ftp = new FTPClient();
        ftp.connect(device.ip, Integer.parseInt(device.port));
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            ftp.disconnect();
            throw new Exception("FTP 拒绝连接");
        }
        if (!ftp.login(device.user, device.pass)) {
            ftp.logout();
            throw new Exception("FTP 账号或密码错误");
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        FTPFile[] ftpFiles = ftp.listFiles(currentPath);
        if (ftpFiles != null) {
            for (FTPFile f : ftpFiles) {
                if (f.getName().equals(".") || f.getName().equals("..")) continue;
                files.add(new FileItem(f.getName(), f.isDirectory()));
            }
        }
        ftp.logout();
        ftp.disconnect();
    }

    private void sendToTv(FileItem f) {
        if (device.type != WatchDevice.TYPE_ANDROID) {
            Toast.makeText(this, "仅支持自家 NAS 投屏", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "正在投屏...", Toast.LENGTH_SHORT).show();
        String fullPath = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;
        final String videoUrl = baseUrl + "/api/download?path=" + URLEncoder.encode(fullPath);

        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(2000);
                byte[] sendData = "DISCOVER_TV".getBytes();
                socket.send(new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), 8090));

                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String tvIp = receivePacket.getAddress().getHostAddress();
                socket.close();

                URL url = new URL("http://" + tvIp + ":8090/play?" + URLEncoder.encode(videoUrl));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (conn.getResponseCode() == 200) {
                    mainHandler.post(() -> Toast.makeText(this, "投屏成功！", Toast.LENGTH_SHORT).show());
                } else {
                    throw new Exception("TV refused");
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "未找到电视", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static class FileItem implements java.io.Serializable {
        String name;
        boolean isDir;
        FileItem(String name, boolean isDir) { this.name = name; this.isDir = isDir; }
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int position) { return files.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = new TextView(FileListActivity.this);
            tv.setPadding(24, 24, 24, 24);
            tv.setTextSize(18);
            tv.setTextColor(0xFFE6E0E9);
            FileItem f = files.get(position);
            tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            return tv;
        }
    }
}
