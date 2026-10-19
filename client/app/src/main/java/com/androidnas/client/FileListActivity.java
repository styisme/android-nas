package com.androidnas.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
    private ActivityResultLauncher<Intent> filePickerLauncher;

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
            if (!currentPath.equals("/")) {
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

        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) uploadFile(uri);
                }
            }
        );

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
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, "选择要上传的文件"));
    }

    private void uploadFile(Uri uri) {
        String fileName = getFileName(uri);
        if (fileName == null) fileName = "upload_" + System.currentTimeMillis();
        Toast.makeText(this, "开始上传 " + fileName, Toast.LENGTH_SHORT).show();
        final String finalFileName = fileName;

        // 获取文件大小，必须传给 OkHttp，否则 NanoHTTPD 2.2 会卡死
        long tempSize = -1;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    tempSize = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {}
        final long finalSize = tempSize;

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                // URL 编码，防止中文或空格导致路径错误
                String encodedName = URLEncoder.encode(finalFileName, "UTF-8").replace("+", "%20");
                String targetUrl = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/") + encodedName;

                RequestBody body = new RequestBody() {
                    @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                    @Override public long contentLength() { return finalSize; } // 明确指定大小
                    @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                    }
                };

                Request.Builder builder = new Request.Builder().url(targetUrl).put(body);
                if (authHeader != null) builder.header("Authorization", authHeader);

                Response response = client.newCall(builder.build()).execute();
                is.close();

                if (response.isSuccessful()) {
                    runOnUiThread(() -> { 
                        Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show(); 
                        loadFiles(); 
                    });
                } else {
                    throw new Exception("HTTP " + response.code());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
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

    private void updateTitle() {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("文件管理: " + currentPath);
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
                    try (InputStream is = response.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                    runOnUiThread(() -> Toast.makeText(this, "下载成功: " + dest.getAbsolutePath(), Toast.LENGTH_LONG).show());
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
                    String ext = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf(".") + 1).toLowerCase() : "";
                    if (ext.equals("jpg") || ext.equals("png") || ext.equals("mp4") || ext.equals("mp3")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(baseUrl + f.path), ext.equals("mp4") ? "video/*" : (ext.equals("mp3") ? "audio/*" : "image/*"));
                            startActivity(intent);
                        } catch (Exception e) { Toast.makeText(FileListActivity.this, "无可用播放器", Toast.LENGTH_SHORT).show(); }
                    } else {
                        new MaterialAlertDialogBuilder(FileListActivity.this)
                            .setTitle(f.name)
                            .setItems(new String[]{"下载", "查看属性"}, (dialog, which) -> {
                                if (which == 0) downloadFile(f);
                                else {
                                    String info = "路径: " + f.path + "\n大小: " + formatSize(f.size);
                                    new MaterialAlertDialogBuilder(FileListActivity.this).setTitle("属性").setMessage(info).setPositiveButton("确定", null).show();
                                }
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
