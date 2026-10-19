package com.androidnas.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    private OkHttpClient client;

    private boolean isGridMode = false;
    private boolean isMultiSelect = false;
    private Set<String> selectedPaths = new HashSet<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int filePickerAction = 0; // 1=upload

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        baseUrl = getIntent().getStringExtra("url");
        if (baseUrl != null && baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        
        isAndroidNas = getIntent().getBooleanExtra("isAndroidNas", true);
        String user = getIntent().getStringExtra("username");
        String pass = getIntent().getStringExtra("password");
        authHeader = Credentials.basic(user != null ? user : "admin", pass != null ? pass : "admin");

        client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        updateTitle();

        toolbar.setNavigationOnClickListener(v -> {
            if (isMultiSelect) {
                exitMultiSelect();
            } else if (!currentPath.equals("/")) {
                String[] parts = currentPath.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
                currentPath = sb.length() == 0 ? "/" : sb.toString();
                loadFiles();
            } else finish();
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabUpload);
        fab.setOnClickListener(v -> openFilePicker());

        loadFiles();
    }

    private void updateTitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(isMultiSelect ? "已选择 " + selectedPaths.size() + " 项" : "文件管理: " + currentPath);
        }
    }

    private void exitMultiSelect() {
        isMultiSelect = false;
        selectedPaths.clear();
        updateTitle();
        adapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (isMultiSelect) {
            menu.add(0, 1, 0, "删除");
            menu.add(0, 2, 0, "取消");
        } else {
            menu.add(0, 3, 0, "新建文件夹");
            menu.add(0, 4, 0, "搜索");
            menu.add(0, 5, 0, isGridMode ? "列表视图" : "网格视图");
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == 1) { deleteSelected(); return true; }
        if (id == 2) { exitMultiSelect(); return true; }
        if (id == 3) { showMkdirDialog(); return true; }
        if (id == 4) { showSearchDialog(); return true; }
        if (id == 5) {
            isGridMode = !isGridMode;
            recyclerView.setLayoutManager(isGridMode ? new GridLayoutManager(this, 3) : new LinearLayoutManager(this));
            adapter = new FileAdapter();
            recyclerView.setAdapter(adapter);
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMkdirDialog() {
        EditText et = new EditText(this);
        et.setHint("文件夹名称");
        new MaterialAlertDialogBuilder(this).setTitle("新建文件夹").setView(et)
            .setPositiveButton("创建", (d, w) -> {
                String name = et.getText().toString();
                if (name.isEmpty()) return;
                new Thread(() -> {
                    try {
                        RequestBody body = new okhttp3.FormBody.Builder().add("path", currentPath).add("name", name).build();
                        Request req = new Request.Builder().url(baseUrl + "/api/mkdir").post(body).header("Authorization", authHeader).build();
                        client.newCall(req).execute();
                        runOnUiThread(this::loadFiles);
                    } catch (Exception e) {}
                }).start();
            }).setNegativeButton("取消", null).show();
    }

    private void showSearchDialog() {
        EditText et = new EditText(this);
        et.setHint("搜索关键字");
        new MaterialAlertDialogBuilder(this).setTitle("全局搜索").setView(et)
            .setPositiveButton("搜索", (d, w) -> {
                String q = et.getText().toString();
                if (q.isEmpty()) return;
                searchFiles(q);
            }).setNegativeButton("取消", null).show();
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
                        files.add(new FileItem(obj.getString("name"), obj.getString("path"), obj.getBoolean("dir"), obj.optLong("size", 0)));
                    }
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
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

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "选择文件"), 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String fileName = "upload_" + System.currentTimeMillis();
            new Thread(() -> {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    String targetUrl = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/") + fileName;
                    RequestBody body = new RequestBody() {
                        @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                        @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                            byte[] buf = new byte[8192]; int len;
                            while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                        }
                    };
                    Request req = new Request.Builder().url(targetUrl).put(body).header("Authorization", authHeader).build();
                    client.newCall(req).execute();
                    runOnUiThread(this::loadFiles);
                } catch (Exception e) {}
            }).start();
        }
    }

    private void deleteSelected() {
        new MaterialAlertDialogBuilder(this).setTitle("确认删除").setMessage("确定要删除选中的 " + selectedPaths.size() + " 个文件吗？")
            .setPositiveButton("删除", (d, w) -> {
                new Thread(() -> {
                    for (String path : selectedPaths) {
                        try {
                            RequestBody body = new okhttp3.FormBody.Builder().add("path", path).build();
                            Request req = new Request.Builder().url(baseUrl + "/api/delete").post(body).header("Authorization", authHeader).build();
                            client.newCall(req).execute();
                        } catch (Exception e) {}
                    }
                    runOnUiThread(() -> { exitMultiSelect(); loadFiles(); });
                }).start();
            }).setNegativeButton("取消", null).show();
    }

    private void renameFile(FileItem f) {
        EditText et = new EditText(this);
        et.setText(f.name);
        new MaterialAlertDialogBuilder(this).setTitle("重命名").setView(et)
            .setPositiveButton("确定", (d, w) -> {
                String newName = et.getText().toString();
                if (newName.isEmpty()) return;
                new Thread(() -> {
                    try {
                        RequestBody body = new okhttp3.FormBody.Builder().add("path", f.path).add("newName", newName).build();
                        Request req = new Request.Builder().url(baseUrl + "/api/rename").post(body).header("Authorization", authHeader).build();
                        client.newCall(req).execute();
                        runOnUiThread(this::loadFiles);
                    } catch (Exception e) {}
                }).start();
            }).setNegativeButton("取消", null).show();
    }

    private void downloadFile(FileItem f) {
        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(false);
        progress.setMax(100);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("下载中").setView(progress).setCancelable(false).show();
        
        new Thread(() -> {
            try {
                String downloadUrl = baseUrl + f.path;
                Request req = new Request.Builder().url(downloadUrl).header("Authorization", authHeader).build();
                Response response = client.newCall(req).execute();
                if (response.isSuccessful() && response.body() != null) {
                    File dest = new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), f.name);
                    long total = response.body().contentLength();
                    InputStream is = response.body().byteStream();
                    FileOutputStream fos = new FileOutputStream(dest);
                    byte[] buf = new byte[8192]; int len; long downloaded = 0;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                        downloaded += len;
                        if (total > 0) {
                            int p = (int) (downloaded * 100 / total);
                            mainHandler.post(() -> progress.setProgressCompat(p, true));
                        }
                    }
                    fos.close(); is.close();
                    mainHandler.post(() -> { dialog.dismiss(); Toast.makeText(this, "已保存至 Download 目录", Toast.LENGTH_LONG).show(); });
                }
            } catch (Exception e) {
                mainHandler.post(dialog::dismiss);
            }
        }).start();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(isGridMode ? R.layout.item_grid : R.layout.item_file, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            FileItem f = files.get(position);
            holder.tvName.setText(f.name);
            holder.tvIcon.setText(f.isDir ? "📁" : "📄");
            if (!isGridMode) holder.tvDetail.setText(f.isDir ? "文件夹" : (f.size / 1024) + " KB");
            
            holder.cbSelect.setVisibility(isMultiSelect ? View.VISIBLE : View.GONE);
            holder.cbSelect.setChecked(selectedPaths.contains(f.path));
            holder.cbSelect.setOnClickListener(v -> toggleSelect(f.path));

            holder.itemView.setOnClickListener(v -> {
                if (isMultiSelect) toggleSelect(f.path);
                else if (f.isDir) { currentPath = f.path; if (!currentPath.endsWith("/")) currentPath += "/"; loadFiles(); }
                else downloadFile(f);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isMultiSelect) {
                    isMultiSelect = true;
                    toggleSelect(f.path);
                    return true;
                }
                return false;
            });
        }
        @Override public int getItemCount() { return files.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvDetail;
            CheckBox cbSelect;
            VH(View v) { 
                super(v); 
                tvName = v.findViewById(R.id.tvName); 
                tvIcon = v.findViewById(R.id.tvIcon); 
                tvDetail = v.findViewById(R.id.tvDetail); 
                cbSelect = v.findViewById(R.id.cbSelect); 
            }
        }
    }
    
    private void toggleSelect(String path) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path);
        else selectedPaths.add(path);
        updateTitle();
        adapter.notifyDataSetChanged();
    }
}
