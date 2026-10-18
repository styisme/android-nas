package com.androidnas.client;

import android.os.Bundle;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

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
        // 清理首尾多余的斜杠
        if (baseUrl != null) {
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
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
            getSupportActionBar().setTitle("文件管理");
        }
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

        loadFiles();
    }

    private void loadFiles() {
        files.clear();
        adapter.notifyDataSetChanged();
        
        new Thread(() -> {
            try {
                if (isAndroidNas) loadAndroidNasFiles();
                else loadWebDavFiles();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage();
                if (msg != null && msg.contains("Failed to connect")) {
                    msg = "无法连接，如果是同一台手机测试请用 127.0.0.1，或检查是否在同一WiFi";
                }
                String finalMsg = msg;
                runOnUiThread(() -> Toast.makeText(this, "加载失败: " + finalMsg, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadAndroidNasFiles() throws Exception {
        // 修复 URL 拼接，对路径进行 URLEncode
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
        } else {
            throw new Exception("服务器返回错误: " + response.code());
        }
    }

    private void loadWebDavFiles() throws Exception {
        String url = baseUrl + currentPath;
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

    private void parseWebDavXml(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        int eventType = parser.getEventType();
        FileItem current = null;
        String currentTag = "";
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                currentTag = parser.getName();
                if (currentTag.equalsIgnoreCase("response")) {
                    current = new FileItem("", "", false, 0);
                } else if (currentTag.equalsIgnoreCase("href") && current != null) {
                    String href = parser.nextText();
                    String path = href;
                    if (path.startsWith("http")) path = path.replace(baseUrl, "");
                    String name = path.substring(path.lastIndexOf("/") + 1);
                    if (name.isEmpty() && path.split("/").length > 1) continue;
                    current.path = path;
                    current.name = name;
                } else if (currentTag.equalsIgnoreCase("collection") && current != null) {
                    current.isDir = true;
                } else if (currentTag.equalsIgnoreCase("getcontentlength") && current != null) {
                    try { current.size = Long.parseLong(parser.nextText()); } catch (Exception ignored) {}
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equalsIgnoreCase("response") && current != null) {
                    if (!current.path.equals(currentPath) && !current.path.isEmpty() && !current.path.endsWith("/" + currentPath + "/")) {
                        files.add(current);
                    }
                    current = null;
                }
            }
            eventType = parser.next();
        }
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
