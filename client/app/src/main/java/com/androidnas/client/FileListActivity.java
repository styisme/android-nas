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
import okio.BufferedSink;

public class FileListActivity extends AppCompatActivity {
    private String baseUrl; private int type; private String currentPath = "/";
    private String authHeader; private String username, password;
    private RecyclerView recyclerView; private FileAdapter adapter;
    private List<FileItem> files = new ArrayList<>();
    private OkHttpClient client; private ActivityResultLauncher<Intent> filePickerLauncher;
    private boolean isGridMode = false, isMultiSelect = false, isSearchMode = false;
    private Set<String> selectedPaths = new HashSet<>();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
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
            if (isMultiSelect) { exitMultiSelect(); return; }
            if (isSearchMode) { isSearchMode = false; currentPath = "/"; loadFiles(); return; }
            if (!currentPath.equals("/")) {
                String[] parts = currentPath.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
                currentPath = sb.length() == 0 ? "/" : sb.toString(); loadFiles();
            } else finish();
        });
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(); recyclerView.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.fabUpload);
        fab.setOnClickListener(v -> openFilePicker());
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> { 
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        // 多选
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            uploadFile(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        // 单选
                        uploadFile(data.getData());
                    }
                } 
            });
        loadFiles();
    }

    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] tr = new TrustManager[]{ new X509TrustManager() {
                @Override public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                @Override public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; } } };
            final SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tr, new java.security.SecureRandom());
            return new OkHttpClient.Builder().sslSocketFactory(ssl.getSocketFactory(), (X509TrustManager) tr[0])
                .hostnameVerifier((h, s) -> true).connectTimeout(30, TimeUnit.SECONDS).build();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void updateTitle() {
        if (getSupportActionBar() == null) return;
        if (isMultiSelect) getSupportActionBar().setTitle("已选择 " + selectedPaths.size() + " 项");
        else if (isSearchMode) getSupportActionBar().setTitle("搜索结果");
        else getSupportActionBar().setTitle("文件管理: " + currentPath);
    }

    private void exitMultiSelect() {
        isMultiSelect = false; selectedPaths.clear(); updateTitle();
        adapter.notifyDataSetChanged(); invalidateOptionsMenu();
    }

    private void selectAll() {
        selectedPaths.clear();
        for (FileItem f : files) {
            selectedPaths.add(f.path);
        }
        updateTitle();
        adapter.notifyDataSetChanged();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (isMultiSelect) {
            menu.add(0, 1, 0, "删除").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 7, 0, "下载").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 2, 0, "全选").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 3, 0, "取消").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menu.add(0, 4, 0, "新建文件夹");
            menu.add(0, 5, 0, "搜索");
            menu.add(0, 6, 0, isGridMode ? "列表视图" : "网格视图");
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == 1) { deleteSelected(); return true; }
        if (id == 7) { batchDownload(); return true; }
        if (id == 2) { selectAll(); return true; }
        if (id == 3) { exitMultiSelect(); return true; }
        if (id == 4) { showMkdirDialog(); return true; }
        if (id == 5) { showSearchDialog(); return true; }
        if (id == 6) {
            isGridMode = !isGridMode;
            recyclerView.setLayoutManager(isGridMode ? new GridLayoutManager(this, 3) : new LinearLayoutManager(this));
            adapter = new FileAdapter(); recyclerView.setAdapter(adapter);
            invalidateOptionsMenu(); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadFiles() {
        isSearchMode = false; files.clear(); adapter.notifyDataSetChanged(); updateTitle();
        new Thread(() -> {
            try {
                if (type == NasDevice.TYPE_ANDROID) loadAndroidNasFiles();
                else if (type == NasDevice.TYPE_WEBDAV) loadWebDavFiles();
                else if (type == NasDevice.TYPE_FTP) loadFtpFiles();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadAndroidNasFiles() throws Exception {
        String apiUrl = baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8");
        Request req = new Request.Builder().url(apiUrl).header("Authorization", authHeader).build();
        Response resp = client.newCall(req).execute();
        if (resp.isSuccessful() && resp.body() != null) {
            org.json.JSONObject obj = new org.json.JSONObject(resp.body().string());
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
        String url = baseUrl + currentPath; if (!url.endsWith("/")) url += "/";
        String body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        Request.Builder b = new Request.Builder().url(url)
            .method("PROPFIND", RequestBody.create(body, MediaType.parse("application/xml"))).header("Depth", "1");
        if (authHeader != null) b.header("Authorization", authHeader);
        Response resp = client.newCall(b.build()).execute();
        if (!resp.isSuccessful() || resp.body() == null) return;
        String xml = resp.body().string();
        Pattern rp = Pattern.compile("<D:response>(.*?)</D:response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern hp = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern dp = Pattern.compile("<D:collection\\s*/>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern sp = Pattern.compile("<D:getcontentlength>(.*?)</D:getcontentlength>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher rm = rp.matcher(xml);
        String curNorm = currentPath.endsWith("/") && currentPath.length() > 1 ? currentPath.substring(0, currentPath.length() - 1) : currentPath;
        while (rm.find()) {
            String blk = rm.group(1);
            Matcher hm = hp.matcher(blk); if (!hm.find()) continue;
            String href = hm.group(1);
            if (href.startsWith("http")) try { href = href.substring(href.indexOf("/", 7)); } catch (Exception ignored) {}
            String norm = href.endsWith("/") && href.length() > 1 ? href.substring(0, href.length() - 1) : href;
            if (norm.equals(curNorm) || norm.isEmpty()) continue;
            boolean isDir = dp.matcher(blk).find();
            String name = href.endsWith("/")
                ? (href.substring(0, href.length() - 1)).substring((href.substring(0, href.length() - 1)).lastIndexOf("/") + 1)
                : href.substring(href.lastIndexOf("/") + 1);
            if (name.isEmpty()) continue;
            try { name = java.net.URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
            long size = 0;
            Matcher sm = sp.matcher(blk);
            if (sm.find()) try { size = Long.parseLong(sm.group(1)); } catch (Exception ignored) {}
            files.add(new FileItem(name, href, isDir, size));
        }
    }

    private FTPClient connectFtp() throws Exception {
        String host = baseUrl; int port = 21;
        if (host.startsWith("http://")) host = host.substring(7);
        else if (host.startsWith("https://")) host = host.substring(8);
        if (host.contains("/")) host = host.substring(0, host.indexOf("/"));
        if (host.contains(":")) { String[] p = host.split(":"); host = p[0]; try { port = Integer.parseInt(p[1]); } catch (Exception ignored) {} }
        FTPClient ftp = new FTPClient(); ftp.connect(host, port);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) { ftp.disconnect(); throw new Exception("FTP 拒绝连接"); }
        if (!ftp.login(username, password)) { ftp.logout(); throw new Exception("FTP 账号或密码错误"); }
        ftp.enterLocalPassiveMode(); ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }

    private void loadFtpFiles() throws Exception {
        FTPClient ftp = connectFtp();
        try {
            FTPFile[] arr = ftp.listFiles(currentPath);
            if (arr != null) for (FTPFile f : arr) {
                if (f.getName().equals(".") || f.getName().equals("..")) continue;
                String full = currentPath.endsWith("/") ? currentPath + f.getName() : currentPath + "/" + f.getName();
                files.add(new FileItem(f.getName(), full, f.isDirectory(), f.getSize()));
            }
        } finally { try { ftp.logout(); } catch (Exception ignored) {} try { ftp.disconnect(); } catch (Exception ignored) {} }
    }

    private void downloadFile(FileItem f) {
        LinearProgressIndicator p = new LinearProgressIndicator(this);
        p.setIndeterminate(true);
        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this).setTitle("下载中").setView(p).setCancelable(false).show();
        new Thread(() -> {
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = new File(Environment.getExternalStorageDirectory(), "Android/data/com.androidnas.client/files/Download");
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(dir, f.name);
                if (type == NasDevice.TYPE_ANDROID) {
                    String url = baseUrl + "/api/download?path=" + URLEncoder.encode(f.path, "UTF-8");
                    Request req = new Request.Builder().url(url).header("Authorization", authHeader).build();
                    Response r = client.newCall(req).execute();
                    if (r.isSuccessful() && r.body() != null) {
                        InputStream is = r.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest);
                        byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                        fos.close(); is.close();
                    } else throw new Exception("HTTP " + r.code());
                } else if (type == NasDevice.TYPE_WEBDAV) {
                    Request req = new Request.Builder().url(baseUrl + f.path).header("Authorization", authHeader).build();
                    Response r = client.newCall(req).execute();
                    if (r.isSuccessful() && r.body() != null) {
                        InputStream is = r.body().byteStream(); FileOutputStream fos = new FileOutputStream(dest);
                        byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                        fos.close(); is.close();
                    } else throw new Exception("HTTP " + r.code());
                } else if (type == NasDevice.TYPE_FTP) {
                    FTPClient ftp = connectFtp();
                    try { OutputStream os = new FileOutputStream(dest); ftp.retrieveFile(f.path, os); os.close(); }
                    finally { try { ftp.logout(); } catch (Exception ignored) {} try { ftp.disconnect(); } catch (Exception ignored) {} }
                }
                final File fd = dest;
                mainHandler.post(() -> { if (isFinishing() || isDestroyed()) return; dlg.dismiss();
                    Toast.makeText(this, "已保存至: " + fd.getAbsolutePath(), Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                final String m = e.getMessage();
                mainHandler.post(() -> { if (isFinishing() || isDestroyed()) return; dlg.dismiss();
                    Toast.makeText(this, "下载失败: " + m, Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        filePickerLauncher.launch(Intent.createChooser(i, "选择要上传的文件"));
    }

    private void uploadFile(Uri uri) {
        String name = getFileName(uri); if (name == null) name = "upload_" + System.currentTimeMillis();
        final String fname = name;
        long tmpSize = -1;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (si >= 0 && !c.isNull(si)) tmpSize = c.getLong(si);
            }
        } catch (Exception ignored) {}
        final long fsize = tmpSize;

        LinearProgressIndicator prog = new LinearProgressIndicator(this);
        prog.setIndeterminate(fsize <= 0); prog.setMax(100);
        androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(this).setTitle("上传中").setView(prog).setCancelable(false).show();

        new Thread(() -> {
            File cache = null;
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("无法打开文件");
                
                if (fsize <= 0) {
                    File cd = new File(getCacheDir(), "upload_cache"); if (!cd.exists()) cd.mkdirs();
                    cache = new File(cd, "tmp_" + System.currentTimeMillis());
                    try (FileOutputStream fos = new FileOutputStream(cache)) {
                        byte[] buf = new byte[8192]; int n; while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    is.close(); is = new java.io.FileInputStream(cache);
                }
                final long realSize = cache != null ? cache.length() : fsize;

                // 核心补齐：大于 2MB 走分片秒传
                if (realSize > 2 * 1024 * 1024 && type == NasDevice.TYPE_ANDROID) {
                    String hash = calcMD5(is);
                    is.close();
                    
                    // 秒传校验
                    String checkUrl = baseUrl + "/api/upload/check?hash=" + hash;
                    Request checkReq = new Request.Builder().url(checkUrl).post(new okhttp3.FormBody.Builder().build()).header("Authorization", authHeader).build();
                    Response checkResp = client.newCall(checkReq).execute();
                    if (checkResp.isSuccessful() && checkResp.body() != null) {
                        String res = checkResp.body().string();
                        if (res.contains("SECONDS_UPLOAD")) {
                            mainHandler.post(() -> { dlg.dismiss(); Toast.makeText(FileListActivity.this, "极速秒传成功！", Toast.LENGTH_SHORT).show(); loadFiles(); });
                            if (cache != null) cache.delete();
                            return;
                        }
                    }
                    
                    // 分片上传
                    is = new java.io.FileInputStream(cache != null ? cache : new File(uri.getPath())); // 如果是 content:// 需要重新打开
                    if (cache == null) {
                        is.close();
                        is = getContentResolver().openInputStream(uri);
                    }
                    
                    int chunkSize = 2 * 1024 * 1024;
                    int totalChunks = (int) Math.ceil((double) realSize / chunkSize);
                    byte[] buffer = new byte[chunkSize];
                    
                    for (int i = 0; i < totalChunks; i++) {
                        int read = 0;
                        while (read < chunkSize) {
                            int r = is.read(buffer, read, chunkSize - read);
                            if (r == -1) break;
                            read += r;
                        }
                        final int pct = (int) (((i + 1.0) / totalChunks) * 100);
                        mainHandler.post(() -> prog.setProgressCompat(pct, true));
                        
                        String url = baseUrl + "/api/upload/chunk?hash=" + hash + "&index=" + i + "&total=" + totalChunks + "&name=" + java.net.URLEncoder.encode(fname, "UTF-8") + "&path=" + java.net.URLEncoder.encode(currentPath, "UTF-8");
                        RequestBody chunkBody = RequestBody.create(buffer, MediaType.parse("application/octet-stream"));
                        Request req = new Request.Builder().url(url).post(chunkBody).header("Authorization", authHeader).build();
                        client.newCall(req).execute().close();
                    }
                    is.close();
                    mainHandler.post(() -> { dlg.dismiss(); Toast.makeText(FileListActivity.this, "分片上传完成", Toast.LENGTH_SHORT).show(); loadFiles(); });
                    
                } else {
                    // 走原有小文件 PUT 逻辑
                    ProgressRequestBody.ProgressListener l = (w, t) -> {
                        if (t > 0) { int pct = (int)(w * 100 / t); runOnUiThread(() -> prog.setProgressCompat(pct, true)); }
                    };
                    if (type == NasDevice.TYPE_ANDROID) {
                        String enc = URLEncoder.encode(fname, "UTF-8").replace("+", "%20");
                        String encPath = URLEncoder.encode(currentPath, "UTF-8").replace("+", "%20");
                        String url = baseUrl + "/api/upload?path=" + encPath + "&name=" + enc;
                        ProgressRequestBody body = new ProgressRequestBody(MediaType.parse("application/octet-stream"), is, realSize, l);
                        Request req = new Request.Builder().url(url).put(body).header("Authorization", authHeader).build();
                        Response r = client.newCall(req).execute();
                        if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
                    } else if (type == NasDevice.TYPE_WEBDAV) {
                        String url = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/") + fname;
                        ProgressRequestBody body = new ProgressRequestBody(MediaType.parse("application/octet-stream"), is, realSize, l);
                        Request req = new Request.Builder().url(url).put(body).header("Authorization", authHeader).build();
                        Response r = client.newCall(req).execute();
                        if (!r.isSuccessful()) throw new Exception("HTTP " + r.code());
                    } else if (type == NasDevice.TYPE_FTP) {
                        FTPClient ftp = connectFtp();
                        try { ftp.storeFile((currentPath.endsWith("/") ? currentPath : currentPath + "/") + fname, is); }
                        finally { try { ftp.logout(); } catch (Exception ignored) {} try { ftp.disconnect(); } catch (Exception ignored) {} }
                    }
                    try { is.close(); } catch (Exception ignored) {}
                    final File fc = cache;
                    mainHandler.post(() -> { if (isFinishing() || isDestroyed()) return; dlg.dismiss();
                        if (fc != null) fc.delete(); loadFiles(); });
                }
            } catch (Exception e) {
                if (cache != null) cache.delete();
                final String m = e.getMessage();
                mainHandler.post(() -> { if (isFinishing() || isDestroyed()) return; dlg.dismiss();
                    Toast.makeText(FileListActivity.this, "上传失败: " + m, Toast.LENGTH_SHORT).show(); });
            }
        }).start();
    }

    private String calcMD5(InputStream is) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] buf = new byte[8192]; int len;
            while ((len = is.read(buf)) > 0) md.update(buf, 0, len);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (i >= 0) result = c.getString(i);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) { int cut = result.lastIndexOf('/'); if (cut != -1) result = result.substring(cut + 1); }
        }
        return result;
    }

    private void batchDownload() {
        Toast.makeText(this, "开始批量下载...", Toast.LENGTH_SHORT).show();
        for (FileItem f : files) {
            if (selectedPaths.contains(f.path) && !f.isDir) {
                downloadFile(f); // 直接复用已有的单个下载逻辑
            }
        }
        exitMultiSelect();
    }

    private void deleteSelected() {
        if (type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "外部协议暂不支持批量删除", Toast.LENGTH_SHORT).show(); exitMultiSelect(); return; }
        new MaterialAlertDialogBuilder(this).setTitle("确认删除").setMessage("确定要删除选中的 " + selectedPaths.size() + " 个文件吗？")
            .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                for (String path : selectedPaths) try {
                    RequestBody b = new okhttp3.FormBody.Builder().add("path", path).build();
                    Request r = new Request.Builder().url(baseUrl + "/api/delete").post(b).header("Authorization", authHeader).build();
                    client.newCall(r).execute();
                } catch (Exception ignored) {}
                runOnUiThread(() -> { exitMultiSelect(); loadFiles(); });
            }).start()).setNegativeButton("取消", null).show();
    }

    private void showMkdirDialog() {
        if (type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "外部协议暂不支持", Toast.LENGTH_SHORT).show(); return; }
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("文件夹名称"); TextInputEditText et = new TextInputEditText(this); til.addView(et);
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(til);
        new MaterialAlertDialogBuilder(this).setTitle("新建文件夹").setView(c)
            .setPositiveButton("创建", (d, w) -> {
                String name = et.getText().toString(); if (name.isEmpty()) return;
                new Thread(() -> { try {
                    RequestBody b = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("path", currentPath).addFormDataPart("name", name).build();
                    Request r = new Request.Builder().url(baseUrl + "/api/mkdir").post(b).header("Authorization", authHeader).build();
                    client.newCall(r).execute(); runOnUiThread(this::loadFiles);
                } catch (Exception ignored) {} }).start();
            }).setNegativeButton("取消", null).show();
    }

    private void showSearchDialog() {
        if (type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "外部协议暂不支持", Toast.LENGTH_SHORT).show(); return; }
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("搜索关键字"); TextInputEditText et = new TextInputEditText(this); til.addView(et);
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(til);
        new MaterialAlertDialogBuilder(this).setTitle("全局搜索").setView(c)
            .setPositiveButton("搜索", (d, w) -> { String q = et.getText().toString(); if (q.isEmpty()) return; searchFiles(q); })
            .setNegativeButton("取消", null).show();
    }

    private void searchFiles(String q) {
        isSearchMode = true; files.clear(); adapter.notifyDataSetChanged(); updateTitle();
        new Thread(() -> { try {
            String url = baseUrl + "/api/search?q=" + URLEncoder.encode(q, "UTF-8");
            Request req = new Request.Builder().url(url).header("Authorization", authHeader).build();
            Response r = client.newCall(req).execute();
            if (r.isSuccessful() && r.body() != null) {
                org.json.JSONArray arr = new org.json.JSONArray(r.body().string());
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    files.add(new FileItem(o.getString("name"), o.getString("path"), o.getBoolean("dir"), o.optLong("size", 0)));
                }
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            }
        } catch (Exception e) { e.printStackTrace(); } }).start();
    }

    private void showFileMenu(FileItem f) {
        if (type != NasDevice.TYPE_ANDROID) {
            new MaterialAlertDialogBuilder(this).setTitle(f.name).setItems(new String[]{"下载"}, (d, w) -> downloadFile(f)).show();
            return;
        }
        
        String ext = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf(".") + 1).toLowerCase() : "";
        boolean isMedia = ext.matches("jpg|jpeg|png|gif|webp|mp4|mp3|mkv|webm|wav");
        boolean isText = ext.matches("txt|md|json|java|html|xml|js|css|py|log|ini");

        java.util.List<String> list = new java.util.ArrayList<>();
        if (isMedia) {
            list.add("预览/播放");
            list.add("投屏到电视");
        } else if (isText) {
            list.add("修改/编辑");
        }
        if (ext.equals("zip")) list.add("解压");
        
        list.add("下载");
        list.add("分享外链");
        list.add("重命名");
        list.add("删除");
        list.add("属性");
        
        String[] actions = list.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this).setTitle(f.name).setItems(actions, (dlg, w) -> {
            String a = actions[w];
            if (a.equals("预览/播放")) previewFile(f, ext);
            else if (a.equals("投屏到电视")) castToTv(f);
            else if (a.equals("解压")) unzipFile(f);
            else if (a.equals("修改/编辑")) editFile(f);
            else if (a.equals("下载")) downloadFile(f);
            else if (a.equals("分享外链")) shareLink(f);
            else if (a.equals("重命名")) renameFile(f);
            else if (a.equals("删除")) deleteFile(f);
            else if (a.equals("属性")) showProps(f);
        }).show();
    }

    private void castToTv(FileItem f) {
        Toast.makeText(this, "正在寻找电视...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tvIp = SyncHelper.discoverTv();
            if (tvIp == null) {
                runOnUiThread(() -> Toast.makeText(this, "未找到电视，请确保电视端打开", Toast.LENGTH_SHORT).show());
                return;
            }
            String url = "";
            try { url = baseUrl + "/api/download?path=" + URLEncoder.encode(f.path, "UTF-8"); } catch (Exception ignored) {}
            boolean ok = SyncHelper.playOnTv(tvIp, url);
            runOnUiThread(() -> Toast.makeText(this, ok ? "已发送投屏指令" : "投屏失败", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void previewFile(FileItem f, String ext) {
        new Thread(() -> {
            try {
                // 先申请免密分享 Token，避免系统播放器不支持 Header 导致 401 黑屏
                RequestBody b = new okhttp3.FormBody.Builder().add("path", f.path).build();
                Request r = new Request.Builder().url(baseUrl + "/api/share").post(b).header("Authorization", authHeader).build();
                Response resp = client.newCall(r).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String token = resp.body().string().trim();
                    String playUrl = baseUrl + "/share/" + token;
                    runOnUiThread(() -> openPlayer(playUrl, ext));
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "获取免密链接失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络异常", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void editFile(FileItem f) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("文件内容");
        TextInputEditText et = new TextInputEditText(this);
        et.setMinLines(10);
        et.setGravity(android.view.Gravity.TOP);
        til.addView(et);
        
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(til);
        LinearLayout c = new LinearLayout(this);
        c.setPadding(48, 24, 48, 0);
        c.addView(sv);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("修改: " + f.name)
                .setView(c)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .create();
        
        dialog.show();
        
        new Thread(() -> {
            try {
                String url = baseUrl + "/api/preview?path=" + URLEncoder.encode(f.path, "UTF-8");
                Request req = new Request.Builder().url(url).header("Authorization", authHeader).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String text = resp.body().string();
                    runOnUiThread(() -> et.setText(text));
                }
            } catch (Exception ignored) {}
        }).start();
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String contentText = et.getText().toString();
            new Thread(() -> {
                try {
                    RequestBody b = new okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("path", f.path)
                        .addFormDataPart("content", contentText)
                        .build();
                    Request r = new Request.Builder().url(baseUrl + "/api/save").post(b).header("Authorization", authHeader).build();
                    Response resp = client.newCall(r).execute();
                    if (resp.isSuccessful()) {
                        runOnUiThread(() -> { dialog.dismiss(); Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show(); });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });
    }

    private void unzipFile(FileItem f) {
        Toast.makeText(this, "正在解压...", Toast.LENGTH_SHORT).show();
        new Thread(() -> { try {
            RequestBody b = new okhttp3.FormBody.Builder().add("path", f.path).build();
            Request r = new Request.Builder().url(baseUrl + "/api/unzip").post(b).header("Authorization", authHeader).build();
            Response resp = client.newCall(r).execute();
            runOnUiThread(() -> {
                if (resp.isSuccessful()) { loadFiles(); Toast.makeText(this, "解压成功", Toast.LENGTH_SHORT).show(); }
                else Toast.makeText(this, "解压失败", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "解压异常", Toast.LENGTH_SHORT).show()); } }).start();
    }

    private void renameFile(FileItem f) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("新名称"); TextInputEditText et = new TextInputEditText(this); et.setText(f.name); til.addView(et);
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(til);
        new MaterialAlertDialogBuilder(this).setTitle("重命名").setView(c)
            .setPositiveButton("确定", (d, ww) -> {
                String nn = et.getText().toString(); if (nn.isEmpty()) return;
                new Thread(() -> { try {
                    RequestBody b = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM).addFormDataPart("path", f.path).addFormDataPart("newName", nn).build();
                    Request r = new Request.Builder().url(baseUrl + "/api/rename").post(b).header("Authorization", authHeader).build();
                    client.newCall(r).execute(); runOnUiThread(this::loadFiles);
                } catch (Exception ignored) {} }).start();
            }).setNegativeButton("取消", null).show();
    }

    private void deleteFile(FileItem f) {
        new MaterialAlertDialogBuilder(this).setTitle("确认删除").setMessage("确定要删除 " + f.name + " 吗？")
            .setPositiveButton("删除", (d, ww) -> new Thread(() -> { try {
                RequestBody b = new okhttp3.FormBody.Builder().add("path", f.path).build();
                Request r = new Request.Builder().url(baseUrl + "/api/delete").post(b).header("Authorization", authHeader).build();
                client.newCall(r).execute(); runOnUiThread(this::loadFiles);
            } catch (Exception ignored) {} }).start()).setNegativeButton("取消", null).show();
    }

    private void showProps(FileItem f) {
        String sizeStr;
        if (f.size < 1024) sizeStr = f.size + " B";
        else if (f.size < 1024 * 1024) sizeStr = String.format(java.util.Locale.US, "%.2f KB", f.size / 1024.0);
        else sizeStr = String.format(java.util.Locale.US, "%.2f MB", f.size / 1024.0 / 1024.0);
        String info = "名称: " + f.name + "\n路径: " + f.path + "\n大小: " + sizeStr + "\n类型: " + (f.isDir ? "文件夹" : "文件");
        new MaterialAlertDialogBuilder(this).setTitle("文件属性").setMessage(info).setPositiveButton("确定", null).show();
    }

    private void shareLink(FileItem f) {
        if (type != NasDevice.TYPE_ANDROID) return;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        
        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("提取码 (留空则免密)");
        TextInputEditText etPass = new TextInputEditText(this); tilPass.addView(etPass);
        layout.addView(tilPass);

        new MaterialAlertDialogBuilder(this).setTitle("分享文件").setView(layout)
            .setPositiveButton("生成链接", (d, w) -> {
                String pass = etPass.getText() != null ? etPass.getText().toString() : "";
                doShare(f, pass);
            }).setNegativeButton("取消", null).show();
    }

    private void doShare(FileItem f, String pass) {
        new Thread(() -> {
            try {
                okhttp3.FormBody.Builder builder = new okhttp3.FormBody.Builder().add("path", f.path);
                if (!pass.isEmpty()) builder.add("pass", pass);
                RequestBody body = builder.build();
                Request req = new Request.Builder().url(baseUrl + "/api/share").post(body).header("Authorization", authHeader).build();
                Response res = client.newCall(req).execute();
                if (res.isSuccessful() && res.body() != null) {
                    String token = res.body().string();
                    String link = baseUrl + "/share/" + token + (pass.isEmpty() ? "" : "\n提取码: " + pass);
                    runOnUiThread(() -> {
                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, "分享文件: " + f.name + "\n下载链接: " + link);
                        sendIntent.setType("text/plain");
                        startActivity(Intent.createChooser(sendIntent, "分享到..."));
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(FileListActivity.this, "生成分享链接失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void openPlayer(String url, String ext) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            String mime = (ext.equals("mp4") || ext.equals("mkv") || ext.equals("webm")) ? "video/*"
                : ((ext.equals("mp3") || ext.equals("wav")) ? "audio/*" : "image/*");
            i.setDataAndType(Uri.parse(url), mime); startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "无可用播放器", Toast.LENGTH_SHORT).show(); }
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(getLayoutInflater().inflate(isGridMode ? R.layout.item_grid : R.layout.item_file, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            FileItem f = files.get(pos);
            h.tvName.setText(f.name); h.tvIcon.setText(f.isDir ? "📁" : "📄");
            if (h.tvDetail != null) h.tvDetail.setText(f.isDir ? "文件夹" : (f.size / 1024) + " KB");
            h.cbSelect.setVisibility(isMultiSelect ? View.VISIBLE : View.GONE);
            h.cbSelect.setChecked(selectedPaths.contains(f.path));
            h.cbSelect.setOnClickListener(v -> toggleSelect(f.path));
            h.itemView.setOnClickListener(v -> {
                if (isMultiSelect) toggleSelect(f.path);
                else if (f.isDir) { isSearchMode = false; currentPath = f.path; if (!currentPath.endsWith("/")) currentPath += "/"; loadFiles(); }
                else showFileMenu(f);
            });
            h.itemView.setOnLongClickListener(v -> {
                if (!isMultiSelect && type == NasDevice.TYPE_ANDROID) { isMultiSelect = true; toggleSelect(f.path); return true; }
                return false;
            });
        }
        @Override public int getItemCount() { return files.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvDetail; CheckBox cbSelect;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon);
                tvDetail = v.findViewById(R.id.tvDetail); cbSelect = v.findViewById(R.id.cbSelect); }
        }
    }

    private void toggleSelect(String path) {
        if (selectedPaths.contains(path)) selectedPaths.remove(path); else selectedPaths.add(path);
        updateTitle(); 
        adapter.notifyDataSetChanged();
        invalidateOptionsMenu(); // 关键：每次勾选/取消都刷新菜单，确保“删除”按钮出现
    }

    static class ProgressRequestBody extends RequestBody {
        interface ProgressListener { void onProgress(long w, long t); }
        private final MediaType ct; private final InputStream in; private final long len; private final ProgressListener l;
        ProgressRequestBody(MediaType ct, InputStream in, long len, ProgressListener l) { this.ct = ct; this.in = in; this.len = len; this.l = l; }
        @Override public MediaType contentType() { return ct; }
        @Override public long contentLength() { return len; }
        @Override public void writeTo(BufferedSink sink) throws java.io.IOException {
            byte[] buf = new byte[8192]; long w = 0; int n;
            while ((n = in.read(buf)) > 0) { sink.write(buf, 0, n); w += n; if (l != null) l.onProgress(w, len); }
        }
    }
}
