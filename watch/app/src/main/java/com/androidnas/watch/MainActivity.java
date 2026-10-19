package com.androidnas.watch;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.app.AlertDialog;

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

public class MainActivity extends Activity {

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
        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = isRound() ? 48 : 16; 
        root.setPadding(padding, padding, padding, padding);

        tvTitle = new TextView(this);
        tvTitle.setText("NAS 控制台");
        tvTitle.setTextSize(16);
        tvTitle.setPadding(0, 0, 0, 16);
        root.addView(tvTitle);

        listView = new ListView(this);
        listView.setFocusable(true);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

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

        SharedPreferences sp = getSharedPreferences("watch_nas", Context.MODE_PRIVATE);
        baseUrl = sp.getString("url", "");
        String user = sp.getString("user", "admin");
        String pass = sp.getString("pass", "admin");

        if (baseUrl.isEmpty()) {
            showConfigDialog();
        } else {
            authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            loadFiles();
        }
    }

    private boolean isRound() {
        return getResources().getConfiguration().isScreenRound();
    }

    private void showConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText etIp = new EditText(this);
        etIp.setHint("NAS IP (如 192.168.1.100)");
        layout.addView(etIp);
        
        new AlertDialog.Builder(this)
            .setTitle("连接 NAS")
            .setView(layout)
            .setPositiveButton("连接", (dialog, which) -> {
                String ip = etIp.getText().toString().trim();
                if (!ip.startsWith("http")) ip = "http://" + ip + ":8080";
                baseUrl = ip;
                authHeader = "Basic " + android.util.Base64.encodeToString(("admin:admin").getBytes(), android.util.Base64.NO_WRAP);
                SharedPreferences sp = getSharedPreferences("watch_nas", Context.MODE_PRIVATE);
                sp.edit().putString("url", baseUrl).apply();
                loadFiles();
            }).show();
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
                conn.setRequestProperty("Authorization", authHeader);
                
                if (conn.getResponseCode() == 200) {
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
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendToTv(FileItem f) {
        Toast.makeText(this, "正在投屏到电视...", Toast.LENGTH_SHORT).show();
        String fullPath = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;
        final String videoUrl = baseUrl + "/api/download?path=" + URLEncoder.encode(fullPath);
        
        new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(1500);
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
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "未找到电视或投屏失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static class FileItem {
        String name;
        boolean isDir;
        FileItem(String name, boolean isDir) { this.name = name; this.isDir = isDir; }
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int position) { return files.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setPadding(16, 16, 16, 16);
            tv.setTextSize(18);
            FileItem f = files.get(position);
            tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            return tv;
        }
    }
}
