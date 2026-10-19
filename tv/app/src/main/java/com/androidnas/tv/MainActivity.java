package com.androidnas.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private ListView listView;
    private TextView tvPath;
    private Button btnUp, btnConnect;
    
    private String baseUrl = "";
    private String currentPath = "/";
    private String authHeader = "";
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.androidnas.tv.R.layout.activity_main);

        listView = findViewById(com.androidnas.tv.R.id.listView);
        tvPath = findViewById(com.androidnas.tv.R.id.tvPath);
        btnUp = findViewById(com.androidnas.tv.R.id.btnUp);
        btnConnect = findViewById(com.androidnas.tv.R.id.btnConnect);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        
        // 监听遥控器回车键
        listView.setOnItemClickListener((parent, view, position, id) -> {
            FileItem f = files.get(position);
            if (f.isDir) {
                if (currentPath.endsWith("/")) currentPath += f.name;
                else currentPath += "/" + f.name;
                loadFiles();
            } else {
                playVideo(f);
            }
        });

        btnUp.setOnClickListener(v -> {
            if (currentPath.equals("/") || currentPath.isEmpty()) return;
            String[] parts = currentPath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
            currentPath = sb.length() == 0 ? "/" : sb.toString();
            loadFiles();
        });

        btnConnect.setOnClickListener(v -> showConnectDialog());

        // 尝试读取已保存的 NAS 信息
        SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
        baseUrl = sp.getString("url", "");
        String user = sp.getString("user", "admin");
        String pass = sp.getString("pass", "admin");
        
        if (!baseUrl.isEmpty()) {
            authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            loadFiles();
        } else {
            showConnectDialog();
        }
    }

    private void showConnectDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        layout.setBackgroundColor(0xFF2A2A2A);

        EditText etIp = new EditText(this);
        etIp.setHint("NAS IP (如 192.168.1.100:8080)");
        etIp.setTextColor(0xFFFFFFFF);
        etIp.setHintTextColor(0xFF888888);
        layout.addView(etIp);

        EditText etUser = new EditText(this);
        etUser.setHint("账号 (默认 admin)");
        etUser.setTextColor(0xFFFFFFFF);
        etUser.setHintTextColor(0xFF888888);
        layout.addView(etUser);

        EditText etPass = new EditText(this);
        etPass.setHint("密码 (默认 admin)");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setTextColor(0xFFFFFFFF);
        etPass.setHintTextColor(0xFF888888);
        layout.addView(etPass);

        new AlertDialog.Builder(this)
            .setTitle("连接到 NAS")
            .setView(layout)
            .setPositiveButton("连接", (dialog, which) -> {
                String ip = etIp.getText().toString().trim();
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString().trim();
                if (ip.isEmpty()) return;
                
                if (!ip.startsWith("http")) ip = "http://" + ip;
                if (ip.endsWith("/")) ip = ip.substring(0, ip.length() - 1);
                if (user.isEmpty()) user = "admin";
                if (pass.isEmpty()) pass = "admin";

                baseUrl = ip;
                authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
                
                SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
                sp.edit().putString("url", baseUrl).putString("user", user).putString("pass", pass).apply();

                currentPath = "/";
                loadFiles();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void loadFiles() {
        files.clear();
        adapter.notifyDataSetChanged();
        tvPath.setText("当前路径: " + currentPath);

        new Thread(() -> {
            try {
                String apiUrl = baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8");
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
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
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "连接失败: " + conn.getResponseCode(), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "加载出错: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void playVideo(FileItem f) {
        String fullPath = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("url", baseUrl + "/api/download?path=" + URLEncoder.encode(fullPath));
        intent.putExtra("auth", authHeader);
        intent.putExtra("title", f.name);
        startActivity(intent);
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
            tv.setPadding(24, 24, 24, 24);
            tv.setTextSize(20);
            tv.setTextColor(0xFFEEEEEE);
            
            FileItem f = files.get(position);
            tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            return tv;
        }
    }
}
