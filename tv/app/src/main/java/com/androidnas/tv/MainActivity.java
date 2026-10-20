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
    private String baseUrl = "", currentPath = "/", authHeader = "";
    private List<FileItem> files = new ArrayList<>(), searchFiles = new ArrayList<>();
    private FileAdapter adapter, searchAdapter;
    private StringBuilder searchQuery = new StringBuilder();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private TvSyncServer syncServer;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        tvSyncHint = new TextView(this); tvSyncHint.setTextColor(0xFF888888); tvSyncHint.setTextSize(14);
        RelativeLayout root = findViewById(com.androidnas.tv.R.id.mainRoot);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(-2, -2);
        p.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); p.addRule(RelativeLayout.CENTER_HORIZONTAL); p.bottomMargin = 16;
        root.addView(tvSyncHint, p);
        adapter = new FileAdapter(files, false); listView.setAdapter(adapter);
        searchAdapter = new FileAdapter(searchFiles, true); searchResults.setAdapter(searchAdapter);
        setupKeyboard(); setupListeners();
        syncServer = new TvSyncServer(this); syncServer.start();
        tvSyncHint.setText("📱 手机协同 端口: 8090 | IP: " + getLocalIpAddress());
        SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
        baseUrl = sp.getString("url", "");
        String u = sp.getString("user", "admin"), pw = sp.getString("pass", "admin");
        if (!baseUrl.isEmpty()) {
            authHeader = "Basic " + android.util.Base64.encodeToString((u + ":" + pw).getBytes(), android.util.Base64.NO_WRAP);
            loadFiles();
        } else showConnectDialog();
    }

    public void updateNasConfig(String ip, String user, String pass) {
        runOnUiThread(() -> {
            baseUrl = ip;
            authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
            sp.edit().putString("url", baseUrl).putString("user", user).putString("pass", pass).apply();
            currentPath = "/"; loadFiles();
            Toast.makeText(this, "手机已下发配置", Toast.LENGTH_LONG).show();
        });
    }

    public void playVideoDirectly(String url) {
        runOnUiThread(() -> {
            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra("url", url); i.putExtra("auth", authHeader); i.putExtra("title", "手机投屏播放");
            startActivity(i);
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
        listView.setOnItemClickListener((p, v, pos, id) -> {
            FileItem f = files.get(pos);
            if (f.isDir) { if (currentPath.endsWith("/")) currentPath += f.name; else currentPath += "/" + f.name; loadFiles(); }
            else playVideo(f, currentPath);
        });
        btnUp.setOnClickListener(v -> {
            if (currentPath.equals("/") || currentPath.isEmpty()) return;
            String[] parts = currentPath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
            currentPath = sb.length() == 0 ? "/" : sb.toString(); loadFiles();
        });
        btnConnect.setOnClickListener(v -> showConnectDialog());
        btnSearch.setOnClickListener(v -> openSearch());
        btnBackFromSearch.setOnClickListener(v -> { searchOverlay.setVisibility(View.GONE); btnSearch.requestFocus(); });
        searchResults.setOnItemClickListener((p, v, pos, id) -> {
            FileItem f = searchFiles.get(pos);
            if (f.isDir) { searchOverlay.setVisibility(View.GONE); currentPath = f.path; loadFiles(); }
            else { String pd = f.path.substring(0, f.path.lastIndexOf("/")); playVideo(f, pd); }
        });
    }

    private void openSearch() {
        searchQuery.setLength(0); updateSearchInput();
        searchFiles.clear(); searchAdapter.notifyDataSetChanged();
        searchOverlay.setVisibility(View.VISIBLE);
        if (keyboardContainer.getChildCount() > 0) {
            LinearLayout row = (LinearLayout) keyboardContainer.getChildAt(0);
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        }
    }

    private void setupKeyboard() {
        String[] keys = {"1","2","3","4","5","6","7","8","9","0","Q","W","E","R","T","Y","U","I","O","P",
            "A","S","D","F","G","H","J","K","L","空格","Z","X","C","V","B","N","M",".","-","删除"};
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.bottomMargin = 8; row.setLayoutParams(rp);
            for (int j = 0; j < 10 && idx < keys.length; j++) {
                String k = keys[idx++]; Button btn = new Button(this); btn.setText(k); btn.setTextSize(16); btn.setFocusable(true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(4, 0, 4, 0); btn.setLayoutParams(p);
                btn.setOnClickListener(v -> handleKeyPress(k)); row.addView(btn);
            }
            keyboardContainer.addView(row);
        }
    }

    private void handleKeyPress(String key) {
        if (key.equals("删除")) { if (searchQuery.length() > 0) { searchQuery.deleteCharAt(searchQuery.length() - 1); updateSearchInput(); } }
        else if (key.equals("空格")) { searchQuery.append(" "); updateSearchInput(); }
        else { searchQuery.append(key.toLowerCase()); updateSearchInput(); performSearch(); }
    }

    private void updateSearchInput() { tvSearchInput.setText(searchQuery.toString() + "|"); }

    private void performSearch() {
        String q = searchQuery.toString().trim();
        if (q.isEmpty()) { searchFiles.clear(); searchAdapter.notifyDataSetChanged(); return; }
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/search?q=" + URLEncoder.encode(q, "UTF-8")).openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    JSONArray arr = new JSONArray(sb.toString());
                    final List<FileItem> nr = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        nr.add(new FileItem(f.getString("name"), f.getString("path"), f.getBoolean("dir")));
                    }
                    mainHandler.post(() -> { searchFiles.clear(); searchFiles.addAll(nr); searchAdapter.notifyDataSetChanged(); });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showConnectDialog() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 24, 48, 24); l.setBackgroundColor(0xFF2A2A2A);
        EditText etIp = new EditText(this); etIp.setHint("NAS IP (如 192.168.1.100:8080)");
        etIp.setTextColor(0xFFFFFFFF); etIp.setHintTextColor(0xFF888888); l.addView(etIp);
        EditText etUser = new EditText(this); etUser.setHint("账号 (默认 admin)");
        etUser.setTextColor(0xFFFFFFFF); etUser.setHintTextColor(0xFF888888); l.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("密码 (默认 admin)");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setTextColor(0xFFFFFFFF); etPass.setHintTextColor(0xFF888888); l.addView(etPass);
        new AlertDialog.Builder(this).setTitle("连接到 NAS").setView(l)
            .setPositiveButton("连接", (d, w) -> {
                String ip = etIp.getText().toString().trim();
                String u = etUser.getText().toString().trim();
                String pw = etPass.getText().toString().trim();
                if (ip.isEmpty()) return;
                if (!ip.startsWith("http")) ip = "http://" + ip;
                if (ip.endsWith("/")) ip = ip.substring(0, ip.length() - 1);
                if (u.isEmpty()) u = "admin"; if (pw.isEmpty()) pw = "admin";
                baseUrl = ip;
                authHeader = "Basic " + android.util.Base64.encodeToString((u + ":" + pw).getBytes(), android.util.Base64.NO_WRAP);
                SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
                sp.edit().putString("url", baseUrl).putString("user", u).putString("pass", pw).apply();
                currentPath = "/"; loadFiles();
            }).setNegativeButton("取消", null).show();
    }

    private void loadFiles() {
        files.clear(); adapter.notifyDataSetChanged(); tvPath.setText("当前路径: " + currentPath);
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8")).openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    JSONObject obj = new JSONObject(sb.toString());
                    JSONArray arr = obj.getJSONArray("files");
                    final List<FileItem> nf = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        nf.add(new FileItem(f.getString("name"), currentPath, f.getBoolean("dir")));
                    }
                    mainHandler.post(() -> { files.clear(); files.addAll(nf); adapter.notifyDataSetChanged(); });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void playVideo(FileItem f, String dir) {
        String full = dir.endsWith("/") ? dir + f.name : dir + "/" + f.name;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", baseUrl + "/api/download?path=" + URLEncoder.encode(full));
        i.putExtra("auth", authHeader); i.putExtra("title", f.name);
        startActivity(i);
    }

    @Override protected void onDestroy() { super.onDestroy(); if (syncServer != null) syncServer.stopServer(); }

    private static class FileItem {
        String name, path; boolean isDir;
        FileItem(String n, String p, boolean d) { name = n; path = p; isDir = d; }
    }

    private class FileAdapter extends BaseAdapter {
        private List<FileItem> data; private boolean isSearch;
        FileAdapter(List<FileItem> d, boolean s) { data = d; isSearch = s; }
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int p) { return data.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setPadding(24, 24, 24, 24); tv.setTextSize(20); tv.setTextColor(0xFFEEEEEE);
            FileItem f = data.get(pos);
            if (isSearch && !f.isDir) tv.setText("🎬 " + f.name + "  (位于: " + f.path + ")");
            else tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            return tv;
        }
    }
}
