package com.androidnas.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.widget.LinearLayout;

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

        client = getUnsafeOkHttpClient();

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

    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                    @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                }
            };
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) { throw new RuntimeException(e); }
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
            if (isMultiSelect) exitMultiSelect(); // 防抖
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
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("文件夹名称");
        TextInputEditText et = new TextInputEditText(this);
        til.addView(et);
        
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 24, 48, 0);
        container.addView(til);

        new MaterialAlertDialogBuilder(this).setTitle("新建文件夹").setView(container)
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
                if (isAndroidNas) loadAndroidNasFiles();
                else loadWebDavFiles();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadAndroidNasFiles() throws Exception {
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
        }
    }

    private void loadWebDavFiles() throws Exception {
        String url = baseUrl + currentPath;
        if (!url.endsWith("/")) url += "/";
        String xmlBody = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        Request.Builder builder = new Request.Builder().url(url).method("PROPFIND", RequestBody.create(xmlBody, MediaType.parse("application/xml"))).header("Depth", "1");
        if (authHeader != null) builder.header("Authorization", authHeader);
        Response response = client.newCall(builder.build()).execute();
        if (response.isSuccessful() && response.body() != null) parseWebDavXml(response.body().string());
    }

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
            if (name.isEmpty()) continue;
            boolean isDir = isDirPattern.matcher(responseBlock).find();
            long size = 0;
            Matcher sizeMatcher = sizePattern.matcher(responseBlock);
            if (sizeMatcher.find()) { try { size = Long.parseLong(sizeMatcher.group(1)); } catch (Exception ignored) {} }
            files.add(new FileItem(name, href, isDir, size));
        }
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
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "upload_" + System.currentTimeMillis();
            final String finalFileName = fileName;

            // 获取文件大小，必须传给 OkHttp
            long tempSize = -1;
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) tempSize = cursor.getLong(sizeIndex);
                }
            } catch (Exception ignored) {}
            final long finalSize = tempSize;

            com.google.android.material.progressindicator.LinearProgressIndicator progress = new com.google.android.material.progressindicator.LinearProgressIndicator(this);
            progress.setIndeterminate(false);
            progress.setMax(100);
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("上传中").setView(progress).setCancelable(false).show();

            new Thread(() -> {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) return;

                    String encodedName = URLEncoder.encode(finalFileName, "UTF-8").replace("+", "%20");
                    String targetUrl = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/") + encodedName;
                    
                    RequestBody body = new RequestBody() {
                        @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                        @Override public long contentLength() { return finalSize; }
                        @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                            byte[] buf = new byte[8192]; int len;
                            while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                        }
                    };
                    Request req = new Request.Builder().url(targetUrl).put(body).header("Authorization", authHeader).build();
                    Response response = client.newCall(req).execute();
                    
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        if (response.isSuccessful()) loadFiles();
                        else Toast.makeText(FileListActivity.this, "上传失败", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> { dialog.dismiss(); Toast.makeText(FileListActivity.this, "上传失败", Toast.LENGTH_SHORT).show(); });
                }
            }).start();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
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
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("新名称");
        TextInputEditText et = new TextInputEditText(this);
        et.setText(f.name);
        til.addView(et);
        
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 24, 48, 0);
        container.addView(til);

        new MaterialAlertDialogBuilder(this).setTitle("重命名").setView(container)
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
                String encodedPath = URLEncoder.encode(f.path, "UTF-8");
                String downloadUrl = baseUrl + "/api/download?path=" + encodedPath;
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
