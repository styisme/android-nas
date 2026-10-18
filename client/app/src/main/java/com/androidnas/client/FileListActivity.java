package com.androidnas.client;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileListActivity extends AppCompatActivity {
    private String baseUrl;
    private String currentPath = "/";
    private boolean isAndroidNas;
    private String authHeader;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> files = new ArrayList<>();
    private OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        baseUrl = getIntent().getStringExtra("url");
        if (baseUrl != null) {
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        isAndroidNas = getIntent().getBooleanExtra("isAndroidNas", true);
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");

        if (user != null && !user.isEmpty()) {
            authHeader = Credentials.basic(user, pass);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        updateTitle();

        toolbar.setNavigationOnClickListener(v -> {
            if (!currentPath.equals("/")) {
                String[] parts = currentPath.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) {
                    sb.append("/").append(parts[i]);
                }
                currentPath = sb.length() == 0 ? "/" : sb.toString();
                loadFiles();
            } else {
                finish();
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        // 请求存储权限用于下载
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }

        loadFiles();
    }

    private void updateTitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("文件管理: " + currentPath);
        }
    }

    private void loadFiles() {
        files.clear();
        adapter.notifyDataSetChanged();
        updateTitle();

        new Thread(() -> {
            try {
                if (isAndroidNas) loadAndroidNasFiles();
                else loadWebDavFiles();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("Failed to connect")) {
                    msg = "无法连接，如果是同一台手机测试请用 127.0.0.1";
                }
                String finalMsg = msg;
                runOnUiThread(() -> Toast.makeText(this, "加载失败: " + finalMsg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadAndroidNasFiles() throws Exception {
        String encodedPath = URLEncoder.encode(currentPath, "UTF-8");
        String apiUrl = baseUrl + "/api/list?path=" + encodedPath;
        
        Request request = new Request.Builder().url(apiUrl).build();
        Response response = client.newCall(request).execute();
        if (response.isSuccessful() && response.body() != null) {
            String json = response.body().string();
            org.json.JSONObject obj = new org.json.JSONObject(json);
            org.json.JSONArray arr = obj.getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject f = arr.getJSONObject(i);
                String name = f.getString("name");
                boolean dir = f.getBoolean("dir");
                long size = f.getLong("size");
                String path = currentPath.endsWith("/") ? currentPath + name : currentPath + "/" + name;
                files.add(new FileItem(name, path, dir, size));
            }
        }
    }

    private void loadWebDavFiles() throws Exception {
        String url = baseUrl + currentPath;
        if (!url.endsWith("/")) url += "/";
        String xmlBody = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        Request.Builder builder = new Request.Builder()
                .url(url)
                .method("PROPFIND", RequestBody.create(xmlBody, MediaType.parse("application/xml")))
                .header("Depth", "1");
        if (authHeader != null) builder.header("Authorization", authHeader);
        Response response = client.newCall(builder.build()).execute();
        if (response.isSuccessful() && response.body() != null) {
            parseWebDavXml(response.body().string());
        } else if (response.code() == 401) {
            throw new Exception("需要认证或密码错误");
        } else {
            throw new Exception("WebDAV 连接失败: " + response.code());
        }
    }

    // 摒弃脆弱的 XML 解析器，使用正则提取（最稳定）
    private void parseWebDavXml(String xml) {
        Pattern responsePattern = Pattern.compile("<D:response>(.*?)</D:response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher responseMatcher = responsePattern.matcher(xml);

        Pattern hrefPattern = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern isDirPattern = Pattern.compile("<D:collection\\s*/>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern sizePattern = Pattern.compile("<D:getcontentlength>(.*?)</D:getcontentlength>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        while (responseMatcher.find()) {
            String responseBlock = responseMatcher.group(1);
            
            Matcher hrefMatcher = hrefPattern.matcher(responseBlock);
            if (!hrefMatcher.find()) continue;
            String href = hrefMatcher.group(1);
            if (href.startsWith("http")) href = href.replace(baseUrl, "");
            
            String name = href.substring(href.lastIndexOf("/") + 1);
            if (name.isEmpty()) {
                // 过滤掉父目录自身
                continue;
            }

            boolean isDir = isDirPattern.matcher(responseBlock).find();
            long size = 0;
            Matcher sizeMatcher = sizePattern.matcher(responseBlock);
            if (sizeMatcher.find()) {
                try { size = Long.parseLong(sizeMatcher.group(1)); } catch (Exception ignored) {}
            }

            files.add(new FileItem(name, href, isDir, size));
        }
    }

    private void downloadFile(FileItem f) {
        Toast.makeText(this, "开始下载 " + f.name, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String downloadUrl = baseUrl + f.path;
                Request.Builder builder = new Request.Builder().url(downloadUrl);
                if (authHeader != null) builder.header("Authorization", authHeader);
                
                Response response = client.newCall(builder.build()).execute();
                if (response.isSuccessful() && response.body() != null) {
                    File dest = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), f.name);
                    try (InputStream is = response.body().byteStream();
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "下载成功: " + dest.getAbsolutePath(), Toast.LENGTH_LONG).show());
                } else {
                    throw new Exception("HTTP " + response.code());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String formatSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return (n / 1024) + " KB";
        return String.format("%.1f MB", n / 1024.0 / 1024.0);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_file, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            FileItem f = files.get(position);
            holder.tvName.setText(f.name);
            holder.tvIcon.setText(f.isDir ? "📁" : "📄");
            holder.tvDetail.setText(f.isDir ? "文件夹" : formatSize(f.size));
            
            holder.itemView.setOnClickListener(v -> {
                if (f.isDir) {
                    currentPath = f.path;
                    if (!currentPath.endsWith("/")) currentPath += "/";
                    loadFiles();
                } else {
                    new MaterialAlertDialogBuilder(FileListActivity.this)
                        .setTitle(f.name)
                        .setItems(new String[]{"下载", "查看属性"}, (dialog, which) -> {
                            if (which == 0) {
                                downloadFile(f);
                            } else {
                                String info = "路径: " + f.path + "\n大小: " + formatSize(f.size);
                                new MaterialAlertDialogBuilder(FileListActivity.this)
                                    .setTitle("属性")
                                    .setMessage(info)
                                    .setPositiveButton("确定", null)
                                    .show();
                            }
                        })
                        .show();
                }
            });
        }
        @Override public int getItemCount() { return files.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvDetail;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon); tvDetail = v.findViewById(R.id.tvDetail); }
        }
    }
}
