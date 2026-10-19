package com.androidnas.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FileListActivity extends AppCompatActivity {
    private String baseUrl;
    private String currentPath = "/";
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
        if (baseUrl != null && baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        authHeader = Credentials.basic(user != null ? user : "admin", pass != null ? pass : "admin");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        updateTitle();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        loadFiles();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                searchFiles(query);
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) { return false; }
        });
        return true;
    }

    private void searchFiles(String q) {
        files.clear();
        adapter.notifyDataSetChanged();
        new Thread(() -> {
            try {
                String url = baseUrl + "/api/search?q=" + URLEncoder.encode(q, "UTF-8");
                Request request = new Request.Builder().url(url).header("Authorization", authHeader).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        files.add(new FileItem(obj.getString("name"), obj.getString("path"), obj.getBoolean("dir"), 0));
                    }
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void updateTitle() {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentPath);
    }

    private void loadFiles() {
        files.clear();
        adapter.notifyDataSetChanged();
        updateTitle();

        new Thread(() -> {
            try {
                String apiUrl = baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8");
                Request request = new Request.Builder().url(apiUrl).header("Authorization", authHeader).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    org.json.JSONObject obj = new org.json.JSONObject(json);
                    org.json.JSONArray arr = obj.getJSONArray("files");
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject f = arr.getJSONObject(i);
                        files.add(new FileItem(f.getString("name"), 
                                currentPath.endsWith("/") ? currentPath + f.getString("name") : currentPath + "/" + f.getString("name"),
                                f.getBoolean("dir"), f.getLong("size")));
                    }
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_file, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            FileItem f = files.get(position);
            holder.tvName.setText(f.name);
            holder.tvIcon.setText(f.isDir ? "📁" : "📄");
            holder.tvDetail.setText(f.isDir ? "文件夹" : f.size + " B");
            
            holder.itemView.setOnClickListener(v -> {
                if (f.isDir) {
                    currentPath = f.path;
                    loadFiles();
                } else {
                    String ext = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf(".") + 1).toLowerCase() : "";
                    if (ext.equals("jpg") || ext.equals("png") || ext.equals("mp4") || ext.equals("mp3")) {
                        // 直接调用系统组件预览
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            String mediaUrl = baseUrl + f.path;
                            // 部分系统播放器不支持 Header 认证，可能需要公开链接
                            intent.setDataAndType(Uri.parse(mediaUrl), ext.equals("mp4") ? "video/*" : (ext.equals("mp3") ? "audio/*" : "image/*"));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(FileListActivity.this, "无可用播放器", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        new MaterialAlertDialogBuilder(FileListActivity.this)
                            .setTitle(f.name)
                            .setItems(new String[]{"下载"}, (dialog, which) -> {
                                Toast.makeText(FileListActivity.this, "下载功能请使用旧版或网页", Toast.LENGTH_SHORT).show();
                            }).show();
                    }
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
