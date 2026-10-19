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

public class FileListActivity extends Activity {

    private ListView listView;
    private TextView tvTitle;
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    private String baseUrl = "";
    private String currentPath = "/";
    private String authHeader = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WatchDevice device = (WatchDevice) getIntent().getSerializableExtra("device");
        if (device == null) {
            finish();
            return;
        }

        baseUrl = "http://" + device.ip + ":" + device.port;
        authHeader = "Basic " + android.util.Base64.encodeToString((device.user + ":" + device.pass).getBytes(), android.util.Base64.NO_WRAP);

        setContentView(com.androidnas.watch.R.layout.activity_main);

        listView = findViewById(com.androidnas.watch.R.id.deviceListView);
        tvTitle = findViewById(com.androidnas.watch.R.id.tvTitle);

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
                String apiUrl = baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8");
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("Authorization", authHeader);

                int responseCode = conn.getResponseCode(); // 提取出来，解决 lambda 异常
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject obj = new JSONObject(sb.toString());
                    JSONArray arr = obj.getJSONArray("files");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        files.add(new FileItem(f.getString("name"), f.getBoolean("dir")));
                    }
                    mainHandler.post(() -> adapter.notifyDataSetChanged());
                } else {
                    final int finalResponseCode = responseCode;
                    mainHandler.post(() -> Toast.makeText(FileListActivity.this, "加载失败: " + finalResponseCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(FileListActivity.this, "请检查网络", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendToTv(FileItem f) {
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
