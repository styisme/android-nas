package com.androidnas.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
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
    private TextView tvPath, tvSearchInput, tvSyncHint;
    private Button btnUp, btnConnect, btnSearch, btnBackFromSearch;
    private LinearLayout searchOverlay, keyboardContainer;
    private ListView searchResults;
    
    private String baseUrl = "";
    private String currentPath = "/";
    private String authHeader = "";
    
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    
    private List<FileItem> searchFiles = new ArrayList<>();
    private FileAdapter searchAdapter;
    
    private StringBuilder searchQuery = new StringBuilder();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private TvSyncServer syncServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.androidnas.tv.R.layout.activity_main);

        listView = findViewById(com.androidnas.tv.R.id.listView);
        tvPath = findViewById(com.androidnas.tv.R.id.tvPath);
        btnUp = findViewById(com.androidnas.tv.R.id.btnUp);
        btnConnect = findViewById(com.androidnas.tv.R.id.btnConnect);
        btnSearch = findViewById(com.androidnas.tv.R.id.btnSearch);
        
        searchOverlay = findViewById(com.androidnas.tv.R.id.searchOverlay);
        keyboardContainer = findViewById(com.androidnas.tv.R.id.keyboardContainer);
        tvSearchInput = findViewById(com.androidnas.tv.R.id.tvSearchInput);
        searchResults = findViewById(com.androidnas.tv.R.id.searchResults);
        btnBackFromSearch = findViewById(com.androidnas.tv.R.id.btnBackFromSearch);

        // 底部协同提示
        tvSyncHint = new TextView(this);
        tvSyncHint.setTextColor(0xFF888888);
        tvSyncHint.setTextSize(14);
        RelativeLayout root = findViewById(com.androidnas.tv.R.id.mainRoot);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        p.addRule(RelativeLayout.CENTER_HORIZONTAL);
        p.bottomMargin = 16;
        root.addView(tvSyncHint, p);

        adapter = new FileAdapter(files, false);
        listView.setAdapter(adapter);
        
        searchAdapter = new FileAdapter(searchFiles, true);
        searchResults.setAdapter(searchAdapter);

        setupKeyboard();
        setupListeners();
        
        syncServer = new TvSyncServer(this);
        syncServer.start();
        
        String ip = getLocalIpAddress();
        tvSyncHint.setText("📱 手机协同服务已开启，端口: 8090 | 本机IP: " + ip);

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

    public void updateNasConfig(String ip, String user, String pass) {
        runOnUiThread(() -> {
            baseUrl = ip;
            authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
            sp.edit().putString("url", baseUrl).putString("user", user).putString("pass", pass).apply();
            currentPath = "/";
            loadFiles();
            Toast.makeText(this, "手机已下发配置，自动连接成功！", Toast.LENGTH_LONG).show();
        });
    }

    public void playVideoDirectly(String fullVideoUrl) {
        runOnUiThread(() -> {
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("url", fullVideoUrl);
            intent.putExtra("auth", authHeader);
            intent.putExtra("title", "手机投屏播放");
            startActivity(intent);
        });
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Exception e) { return "127.0.0.1"; }
    }

    private void setupListeners() {
        listView.setOnItemClickListener((parent, view, position, id) -> {
            FileItem f = files.get(position);
            if (f.isDir) {
                if (currentPath.endsWith("/")) currentPath += f.name;
                else currentPath += "/" + f.name;
                loadFiles();
            } else {
                playVideo(f, currentPath);
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
        btnSearch.setOnClickListener(v -> openSearch());
        
        btnBackFromSearch.setOnClickListener(v -> {
            searchOverlay.setVisibility(View.GONE);
            btnSearch.requestFocus();
        });

        searchResults.setOnItemClickListener((parent, view, position, id) -> {
            FileItem f = searchFiles.get(position);
            if (f.isDir) {
                searchOverlay.setVisibility(View.GONE);
                currentPath = f.path;
                loadFiles();
            } else {
                String parentDir = f.path.substring(0, f.path.lastIndexOf("/"));
                playVideo(f, parentDir);
            }
        });
    }

    private void openSearch() {
        searchQuery.setLength(0);
        updateSearchInput();
        searchFiles.clear();
        searchAdapter.notifyDataSetChanged();
        searchOverlay.setVisibility(View.VISIBLE);
        if (keyboardContainer.getChildCount() > 0) {
            LinearLayout row = (LinearLayout) keyboardContainer.getChildAt(0);
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        }
    }

    private void setupKeyboard() {
        String[] keys = {
            "1","2","3","4","5","6","7","8","9","0",
            "Q","W","E","R","T","Y","U","I","O","P",
            "A","S","D","F","G","H","J","K","L","空格",
            "Z","X","C","V","B","N","M",".","-","删除"
        };

        int rowCount = 4;
        int colCount = 10;
        int index = 0;

        for (int i = 0; i < rowCount; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = 8;
            row.setLayoutParams(rowParams);

            for (int j = 0; j < colCount && index < keys.length; j++) {
                String key = keys[index++];
                Button btn = new Button(this);
                btn.setText(key);
                btn.setTextSize(16);
                btn.setFocusable(true);
                
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                p.setMargins(4, 0, 4, 0);
                btn.setLayoutParams(p);

                btn.setOnClickListener(v -> handleKeyPress(key));
                row.addView(btn);
            }
            keyboardContainer.addView(row);
        }
    }

    private void handleKeyPress(String key) {
        if (key.equals("删除")) {
            if (searchQuery.length() > 0) {
                searchQuery.deleteCharAt(searchQuery.length() - 1);
                updateSearchInput();
            }
        } else if (key.equals("空格")) {
            searchQuery.append(" ");
            updateSearchInput();
        } else {
            searchQuery.append(key.toLowerCase());
            updateSearchInput();
            performSearch();
        }
    }

    private void updateSearchInput() {
        tvSearchInput.setText(searchQuery.toString() + "|");
    }

    private void performSearch() {
        String q = searchQuery.toString().trim();
        if (q.isEmpty()) {
            searchFiles.clear();
            searchAdapter.notifyDataSetChanged();
            return;
        }

        new Thread(() -> {
            try {
                String apiUrl = baseUrl + "/api/search?q=" + URLEncoder.encode(q, "UTF-8");
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
                    
                    JSONArray arr = new JSONArray(sb.toString());
                    final List<FileItem> newResults = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        newResults.add(new FileItem(f.getString("name"), f.getString("path"), f.getBoolean("dir")));
                    }
                    mainHandler.post(() -> {
                        searchFiles.clear();
                        searchFiles.addAll(newResults);
                        searchAdapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
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
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    
                    JSONObject obj = new JSONObject(sb.toString());
                    JSONArray arr = obj.getJSONArray("files");
                    final List<FileItem> newFiles = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        newFiles.add(new FileItem(f.getString("name"), currentPath, f.getBoolean("dir")));
                    }
                    mainHandler.post(() -> {
                        files.clear();
                        files.addAll(newFiles);
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void playVideo(FileItem f, String dir) {
        String fullPath = dir.endsWith("/") ? dir + f.name : dir + "/" + f.name;
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("url", baseUrl + "/api/download?path=" + URLEncoder.encode(fullPath));
        intent.putExtra("auth", authHeader);
        intent.putExtra("title", f.name);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncServer != null) syncServer.stopServer();
    }

    private static class FileItem {
        String name;
        String path;
        boolean isDir;
        FileItem(String name, String path, boolean isDir) { 
            this.name = name; this.path = path; this.isDir = isDir; 
        }
    }

    private class FileAdapter extends BaseAdapter {
        private List<FileItem> data;
        private boolean isSearchResult;

        FileAdapter(List<FileItem> data, boolean isSearchResult) {
            this.data = data;
            this.isSearchResult = isSearchResult;
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setPadding(24, 24, 24, 24);
            tv.setTextSize(20);
            tv.setTextColor(0xFFEEEEEE);
            
            FileItem f = data.get(position);
            if (isSearchResult && !f.isDir) {
                tv.setText("🎬 " + f.name + "  (位于: " + f.path + ")");
            } else {
                tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            }
            return tv;
        }
    }
}
