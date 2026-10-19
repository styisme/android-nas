package com.androidnas.client;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

public class FileListActivity extends AppCompatActivity {
    private String baseUrl;
    private int type;
    private String currentPath = "/";
    private String authHeader;
    private String username, password;

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<FileItem> files = new ArrayList<>();
    private OkHttpClient client;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
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
        
        type = getIntent().getIntExtra("type", NasDevice.TYPE_ANDROID);
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        authHeader = Credentials.basic(username != null ? username : "admin", password != null ? password : "admin");

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
            if (isMultiSelect) exitMultiSelect();
            isGridMode = !isGridMode;
            recyclerView.setLayoutManager(isGridMode ? new GridLayoutManager(this, 3) : new LinearLayoutManager(this));
            adapter = new FileAdapter();
            recyclerView.setAdapter(adapter);
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFiles() {
        files.clear();
        adapter.notifyDataSetChanged();
        updateTitle();

        new Thread(() -> {
            try {
                if (type == NasDevice.TYPE_ANDROID) loadAndroidNasFiles();
                else if (type == NasDevice.TYPE_WEBDAV) loadWebDavFiles();
                else if (type == NasDevice.TYPE_FTP) loadFtpFiles();
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
        if (response.isSuccessful() && response.body() != null) {
            String xml = response.body().string();
            Pattern responsePattern = Pattern.compile("<D:response>(.*?)</D:response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher responseMatcher = responsePattern.matcher(xml);
            Pattern hrefPattern = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern isDirPattern = Pattern.compile("<D:collection\\s*/>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Pattern sizePattern = Pattern.compile("<D:getcontentlength>(.*?)</D:getcontentlength>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

            while (responseMatcher.find()) {
                String block = responseMatcher.group(1);
                Matcher hrefMatcher = hrefPattern.matcher(block);
                if (!hrefMatcher.find()) continue;
                String href = hrefMatcher.group(1);
                if (href.startsWith("http")) href = href.replace(baseUrl, "");
                String name = href.substring(href.lastIndexOf("/") + 1);
                if (name.isEmpty()) continue;
                boolean isDir = isDirPattern.matcher(block).find();
                long size = 0;
                Matcher sizeMatcher = sizePattern.matcher(block);
                if (sizeMatcher.find()) { try { size = Long.parseLong(sizeMatcher.group(1)); } catch (Exception ignored) {} }
                files.add(new FileItem(name, href, isDir, size));
            }
        }
    }

    private void loadFtpFiles() throws Exception {
        String ip = baseUrl.replace("http://", "").replace("https://", "");
        int port = 21;
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            ip = parts[0];
            port = Integer.parseInt(parts[1]);
        }
        
        FTPClient ftp = new FTPClient();
        ftp.connect(ip, port);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            ftp.disconnect();
            throw new Exception("FTP 拒绝连接");
        }
        if (!ftp.login(username, password)) {
            ftp.logout();
            throw new Exception("FTP 账号或密码错误");
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        FTPFile[] ftpFiles = ftp.listFiles(currentPath);
        if (ftpFiles != null) {
            for (FTPFile f : ftpFiles) {
                if (f.getName().equals(".") || f.getName().equals("..")) continue;
                String fullPath = currentPath.endsWith("/") ? currentPath + f.getName() : currentPath + "/" + f.getName();
                files.add(new FileItem(f.getName(), fullPath, f.isDirectory(), f.getSize()));
            }
        }
        ftp.logout();
        ftp.disconnect();
    }

    private void downloadFile(FileItem f) {
        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("下载中").setView(progress).setCancelable(false).show();
        
        new Thread(() -> {
            try {
                File destDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (destDir == null) destDir = new File(Environment.getExternalStorageDirectory(), "Android/data/com.androidnas.client/files/Download");
                if (!destDir.exists()) destDir.mkdirs();
                File dest = new File(destDir, f.name);

                if (type == NasDevice.TYPE_ANDROID) {
                    String encodedPath = URLEncoder.encode(f.path, "UTF-8");
                    String downloadUrl = baseUrl + "/api/download?path=" + encodedPath;
                    Request req = new Request.Builder().url(downloadUrl).header("Authorization", authHeader).build();
                    Response response = client.newCall(req).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(dest);
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        fos.close(); is.close();
                    } else throw new Exception("HTTP " + response.code());
                } else if (type == NasDevice.TYPE_WEBDAV) {
                    Request req = new Request.Builder().url(baseUrl + f.path).header("Authorization", authHeader).build();
                    Response response = client.newCall(req).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        InputStream is = response.body().byteStream();
                        FileOutputStream fos = new FileOutputStream(dest);
                        byte[] buf = new byte[8192]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        fos.close(); is.close();
                    } else throw new Exception("HTTP " + response.code());
                } else if (type == NasDevice.TYPE_FTP) {
                    String ip = baseUrl.replace("http://", "");
                    FTPClient ftp = new FTPClient();
                    ftp.connect(ip, 21);
                    ftp.login(username, password);
                    ftp.enterLocalPassiveMode();
                    ftp.setFileType(FTP.BINARY_FILE_TYPE);
                    OutputStream os = new FileOutputStream(dest);
                    ftp.retrieveFile(f.path, os);
                    os.close();
                    ftp.logout();
                    ftp.disconnect();
                }
                
                mainHandler.post(() -> { 
                    if (isFinishing()) return;
                    dialog.dismiss(); 
                    Toast.makeText(this, "已保存至: " + dest.getAbsolutePath(), Toast.LENGTH_LONG).show(); 
                });
            } catch (Exception e) {
                mainHandler.post(() -> { 
                    if (isFinishing()) return;
                    dialog.dismiss(); 
                    Toast.makeText(this, "下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); 
                });
            }
        }).start();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(Intent.createChooser(intent, "选择要上传的文件"));
    }

    private void uploadFile(Uri uri) {
        String fileName = getFileName(uri);
        if (fileName == null) fileName = "upload_" + System.currentTimeMillis();
        final String finalFileName = fileName;

        long tempSize = -1;
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) tempSize = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {}
        final long finalSize = tempSize;

        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(false);
        progress.setMax(100);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("上传中").setView(progress).setCancelable(false).show();

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                if (type == NasDevice.TYPE_ANDROID) {
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
                    if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
                } else if (type == NasDevice.TYPE_WEBDAV) {
                    String url = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/") + finalFileName;
                    RequestBody body = new RequestBody() {
                        @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                        @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                            byte[] buf = new byte[8192]; int len;
                            while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                        }
                    };
                    Request req = new Request.Builder().url(url).put(body).header("Authorization", authHeader).build();
                    client.newCall(req).execute();
                } else if (type == NasDevice.TYPE_FTP) {
                    String ip = baseUrl.replace("http://", "");
                    FTPClient ftp = new FTPClient();
                    ftp.connect(ip, 21);
                    ftp.login(username, password);
                    ftp.enterLocalPassiveMode();
                    ftp.setFileType(FTP.BINARY_FILE_TYPE);
                    ftp.storeFile((currentPath.endsWith("/") ? currentPath : currentPath + "/") + finalFileName, is);
                    ftp.logout();
                    ftp.disconnect();
                }
                
                mainHandler.post(() -> {
                    if (isFinishing()) return;
                    dialog.dismiss();
                    loadFiles();
                });
            } catch (Exception e) {
                mainHandler.post(() -> { 
                    if (isFinishing()) return;
                    dialog.dismiss(); 
                    Toast.makeText(FileListActivity.this, "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); 
                });
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

    private void deleteSelected() {
        if (type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "外部协议暂不支持批量删除", Toast.LENGTH_SHORT).show();
            exitMultiSelect();
            return;
        }
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

    private void showMkdirDialog() {
        if (type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "外部协议暂不支持新建文件夹", Toast.LENGTH_SHORT).show();
            return;
        }
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
        if (type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "外部协议暂不支持搜索", Toast.LENGTH_SHORT).show();
            return;
        }
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("搜索关键字");
        TextInputEditText et = new TextInputEditText(this);
        til.addView(et);
        
        LinearLayout container = new LinearLayout(this);
        container.setPadding(48, 24, 48, 0);
        container.addView(til);

        new MaterialAlertDialogBuilder(this).setTitle("全局搜索").setView(container)
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

    private void showFileMenu(FileItem f) {
        String[] actions;
        String ext = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf(".") + 1).toLowerCase() : "";
        boolean isMedia = ext.equals("jpg") || ext.equals("png") || ext.equals("mp4") || ext.equals("mp3");
        
        if (isMedia && type == NasDevice.TYPE_ANDROID) {
            actions = new String[]{"预览/播放", "下载", "重命名", "删除", "属性"};
        } else if (type == NasDevice.TYPE_ANDROID) {
            actions = new String[]{"下载", "重命名", "删除", "属性"};
        } else {
            actions = new String[]{"下载"};
        }

        new MaterialAlertDialogBuilder(this).setTitle(f.name).setItems(actions, (dialog, which) -> {
            String action = actions[which];
            if (action.equals("预览/播放")) {
                new Thread(() -> {
                    try {
                        RequestBody body = new okhttp3.FormBody.Builder().add("path", f.path).build();
                        Request req = new Request.Builder().url(baseUrl + "/api/share").post(body).header("Authorization", authHeader).build();
                        Response res = client.newCall(req).execute();
                        if (res.isSuccessful() && res.body() != null) {
                            String token = res.body().string();
                            String shareUrl = baseUrl + "/share/" + token;
                            runOnUiThread(() -> openSystemPlayer(shareUrl, ext));
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(FileListActivity.this, "获取播放链接失败", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            } else if (action.equals("下载")) {
                downloadFile(f);
            } else if (action.equals("重命名")) {
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
            } else if (action.equals("删除")) {
                new MaterialAlertDialogBuilder(this).setTitle("确认删除").setMessage("确定要删除此文件吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        new Thread(() -> {
                            try {
                                RequestBody body = new okhttp3.FormBody.Builder().add("path", f.path).build();
                                Request req = new Request.Builder().url(baseUrl + "/api/delete").post(body).header("Authorization", authHeader).build();
                                client.newCall(req).execute();
                                runOnUiThread(this::loadFiles);
                            } catch (Exception e) {}
                        }).start();
                    }).setNegativeButton("取消", null).show();
            } else if (action.equals("属性")) {
                String info = "路径: " + f.path + "\n大小: " + (f.size / 1024) + " KB";
                new MaterialAlertDialogBuilder(this).setTitle("文件属性").setMessage(info).setPositiveButton("确定", null).show();
            }
        }).show();
    }

    private void openSystemPlayer(String url, String ext) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String mime = ext.equals("mp4") ? "video/*" : (ext.equals("mp3") ? "audio/*" : "image/*");
            intent.setDataAndType(Uri.parse(url), mime);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无可用播放器", Toast.LENGTH_SHORT).show();
        }
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
                else showFileMenu(f);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isMultiSelect && type == NasDevice.TYPE_ANDROID) {
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
