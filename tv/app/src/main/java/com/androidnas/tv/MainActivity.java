package com.androidnas.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private RecyclerView recyclerView;
    private TextView tvPath;
    private Button btnUp, btnConnect, btnSearch, btnMode;
    private String baseUrl = "", currentPath = "/", authHeader = "";
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService imagePool = Executors.newFixedThreadPool(6);
    
    // 模式：0=文件列表，1=影视墙，2=时光相册
    private int currentMode = 0;
    private TvSyncServer syncServer;

    interface OnItemClickListener { void onItemClick(FileItem f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(com.androidnas.tv.R.layout.activity_main);
        
        recyclerView = findViewById(com.androidnas.tv.R.id.recyclerView);
        tvPath = findViewById(com.androidnas.tv.R.id.tvPath);
        btnUp = findViewById(com.androidnas.tv.R.id.btnUp);
        btnConnect = findViewById(com.androidnas.tv.R.id.btnConnect);
        btnSearch = findViewById(com.androidnas.tv.R.id.btnSearch);
        
        // 找到原有的搜索按钮位置，我们要在这一行动态加一个"切换模式"按钮
        LinearLayout topBar = (LinearLayout) btnSearch.getParent();
        btnMode = new Button(this);
        btnMode.setText("切换影视墙");
        btnMode.setBackgroundResource(com.androidnas.tv.R.drawable.selector_btn);
        btnMode.setTextColor(0xFF381E72);
        btnMode.setTextSize(16);
        btnMode.setPadding(24, 12, 24, 12);
        btnMode.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 16, 0);
        btnMode.setLayoutParams(lp);
        topBar.addView(btnMode, topBar.getChildCount() - 1); // 插在连接设置前面

        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new FileAdapter(); 
        recyclerView.setAdapter(adapter);

        setupListeners();
        
        syncServer = new TvSyncServer(this); 
        syncServer.start();

        SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
        baseUrl = sp.getString("url", "");
        String u = sp.getString("user", "admin"), pw = sp.getString("pass", "admin");
        if (!baseUrl.isEmpty()) {
            authHeader = "Basic " + android.util.Base64.encodeToString((u + ":" + pw).getBytes(), android.util.Base64.NO_WRAP);
            loadFiles();
        } else {
            btnConnect.requestFocus();
            showConnectDialog();
        }
    }

    private void setupListeners() {
        adapter.setOnItemClickListener(f -> {
            if (currentMode == 0) {
                if (f.isDir) {
                    if (currentPath.endsWith("/")) currentPath += f.name; else currentPath += "/" + f.name;
                    loadFiles();
                } else {
                    playVideo(f, currentPath);
                }
            } else {
                // 影视墙/相册模式点击直接播放/预览
                playVideo(f, "");
            }
        });

        btnMode.setOnClickListener(v -> {
            currentMode = (currentMode + 1) % 3;
            if (currentMode == 0) {
                btnMode.setText("切换影视墙");
                btnUp.setVisibility(View.VISIBLE);
                loadFiles();
            } else if (currentMode == 1) {
                btnMode.setText("切换时光相册");
                btnUp.setVisibility(View.GONE);
                loadMediaWall();
            } else {
                btnMode.setText("返回文件列表");
                btnUp.setVisibility(View.GONE);
                loadPhotos();
            }
        });
        
        btnUp.setOnClickListener(v -> {
            if (currentPath.equals("/") || currentPath.isEmpty()) return;
            String[] parts = currentPath.split("/");
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
            currentPath = sb.length() == 0 ? "/" : sb.toString(); loadFiles();
        });
        
        btnConnect.setOnClickListener(v -> showConnectDialog());
        btnSearch.setOnClickListener(v -> Toast.makeText(this, "搜索功能可通过手机端协同完成", Toast.LENGTH_SHORT).show());
    }

    private void loadMediaWall() {
        tvPath.setText("🎬 影视海报墙");
        files.clear(); adapter.notifyDataSetChanged();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/media/wall").openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    JSONArray arr = new JSONArray(sb.toString());
                    final List<FileItem> nf = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        FileItem item = new FileItem(o.getString("title"), o.getString("path"), false);
                        item.imageUrl = o.optString("poster");
                        nf.add(item);
                    }
                    mainHandler.post(() -> { 
                        files.clear(); files.addAll(nf); adapter.notifyDataSetChanged();
                        if (files.size() > 0) recyclerView.requestFocus();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void loadPhotos() {
        tvPath.setText("🖼️ 时光相册");
        files.clear(); adapter.notifyDataSetChanged();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/photos").openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    JSONArray arr = new JSONArray(sb.toString());
                    final List<FileItem> nf = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        FileItem item = new FileItem(o.getString("name"), o.getString("path"), false);
                        item.imageUrl = baseUrl + "/api/thumb?path=" + URLEncoder.encode(item.path, "UTF-8");
                        nf.add(item);
                    }
                    mainHandler.post(() -> { 
                        files.clear(); files.addAll(nf); adapter.notifyDataSetChanged();
                        if (files.size() > 0) recyclerView.requestFocus();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void loadFiles() {
        files.clear(); adapter.notifyDataSetChanged(); tvPath.setText("路径: " + currentPath);
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
                    mainHandler.post(() -> { 
                        files.clear(); files.addAll(nf); adapter.notifyDataSetChanged(); 
                        if (files.size() > 0) recyclerView.requestFocus();
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void playVideo(FileItem f, String dir) {
        String full = f.path;
        if (!full.startsWith("http") && !f.isDir) {
            full = dir.endsWith("/") ? dir + f.name : dir + "/" + f.name;
        }
        if (!full.startsWith("http")) {
             full = baseUrl + "/api/download?path=" + URLEncoder.encode(full);
        }
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", full);
        i.putExtra("auth", authHeader); 
        i.putExtra("title", f.name);
        startActivity(i);
    }


    public void updateNasConfig(final String ip, final String user, final String pass) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                baseUrl = ip;
                authHeader = "Basic " + android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
                SharedPreferences sp = getSharedPreferences("tv_nas", Context.MODE_PRIVATE);
                sp.edit().putString("url", baseUrl).putString("user", user).putString("pass", pass).apply();
                currentPath = "/"; 
                if (currentMode != 0) {
                    currentMode = 0;
                    btnMode.setText("切换影视墙");
                    btnUp.setVisibility(View.VISIBLE);
                }
                loadFiles();
                Toast.makeText(MainActivity.this, "手机已下发配置", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void playVideoDirectly(final String url) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                i.putExtra("url", url); 
                i.putExtra("auth", authHeader); 
                i.putExtra("title", "手机投屏播放");
                startActivity(i);
            }
        });
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
                currentPath = "/"; currentMode = 0; loadFiles();
            }).setNegativeButton("取消", null).show();
    }

    @Override protected void onDestroy() { super.onDestroy(); if (syncServer != null) syncServer.stopServer(); }

    private static class FileItem {
        String name, path, imageUrl; boolean isDir;
        FileItem(String n, String p, boolean d) { name = n; path = p; isDir = d; }
    }

    // 终极 TV 适配器
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private OnItemClickListener listener;
        void setOnItemClickListener(OnItemClickListener l) { this.listener = l; }

        @Override public int getItemCount() { return files.size(); }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(com.androidnas.tv.R.layout.item_tv_file, parent, false);
            // 动态调整高度以适应图片模式
            if (currentMode != 0) {
                v.getLayoutParams().height = 300; 
            } else {
                v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            FileItem f = files.get(pos);
            h.tvName.setText(f.name);
            
            if (currentMode == 0) {
                h.tvIcon.setText(f.isDir ? "📁" : "🎬");
                h.tvIcon.setVisibility(View.VISIBLE);
                h.img.setVisibility(View.GONE);
            } else {
                h.tvIcon.setVisibility(View.GONE);
                h.img.setVisibility(View.VISIBLE);
                h.img.setImageResource(android.R.color.darker_gray); // 占位
                if (f.imageUrl != null && !f.imageUrl.isEmpty()) {
                    loadImage(f.imageUrl, h.img);
                }
            }

            h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.1f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            });

            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(f);
            });
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon; ImageView img;
            VH(View v) { 
                super(v); 
                tvName = v.findViewById(com.androidnas.tv.R.id.tvName); 
                tvIcon = v.findViewById(com.androidnas.tv.R.id.tvIcon); 
                // 动态创建一个 ImageView 塞进卡片
                img = new ImageView(v.getContext());
                LinearLayout parent = (LinearLayout) tvIcon.getParent();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -1);
                lp.weight = 1;
                img.setLayoutParams(lp);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                parent.addView(img, 0);
            }
        }
    }

    private void loadImage(final String urlStr, final ImageView imgView) {
        imagePool.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (authHeader != null && !authHeader.isEmpty()) {
                    conn.setRequestProperty("Authorization", authHeader);
                }
                InputStream is = conn.getInputStream();
                final Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                mainHandler.post(() -> {
                    if (bmp != null) imgView.setImageBitmap(bmp);
                });
            } catch (Exception ignored) {}
        });
    }
}
