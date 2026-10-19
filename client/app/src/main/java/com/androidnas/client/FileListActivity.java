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

// SMB 依赖
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.FileAccessor;

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
                else if (type == NasDevice.TYPE_SMB) loadSmbFiles();
                runOnUiThread(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // --- 协议 1: 原生 Android NAS ---
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

    // --- 协议 2: WebDAV ---
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

    // --- 协议 3: FTP ---
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

    // --- 协议 4: SMB (Windows) ---
    private void loadSmbFiles() throws Exception {
        // baseUrl 格式: smb://192.168.1.100/ShareName
        String cleanUrl = baseUrl.replace("smb://", "");
        String[] parts = cleanUrl.split("/", 2);
        String host = parts[0];
        String shareName = parts.length > 1 ? parts[1] : "";
        
        SMBClient smbClient = new SMBClient();
        try (Connection connection = smbClient.connect(host)) {
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), null);
            Session session = connection.authenticate(ac);
            
            try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                String path = currentPath.equals("/") ? "" : currentPath;
                for (FileIdBothDirectoryInformation f : share.list(path)) {
                    String name = f.getFileName();
                    if (name.equals(".") || name.equals("..")) continue;
                    boolean isDir = EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
                    String fullPath = currentPath.endsWith("/") ? currentPath + name : currentPath + "/" + name;
                    files.add(new FileItem(name, fullPath, isDir, f.getEndOfFile()));
                }
            }
        }
    }

    // --- 下载 (通用与专用) ---
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
                } else if (type == NasDevice.TYPE_SMB) {
                    String cleanUrl = baseUrl.replace("smb://", "");
                    String[] parts = cleanUrl.split("/", 2);
                    String shareName = parts.length > 1 ? parts[1] : "";
                    
                    SMBClient smbClient = new SMBClient();
                    try (Connection connection = smbClient.connect(parts[0])) {
                        AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), null);
                        Session session = connection.authenticate(ac);
                        try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                            FileAccessor file = share.openFile(f.path, 
                                java.util.EnumSet.of(AccessMask.GENERIC_READ), 
                                null, 
                                java.util.EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), 
                                SMB2CreateDisposition.FILE_OPEN, 
                                null);
                            try (InputStream is = file.getInputStream(); FileOutputStream fos = new FileOutputStream(dest)) {
                                byte[] buf = new byte[8192]; int len;
                                while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                            }
                        }
                    }
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

    // --- 上传 ---
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        filePickerLauncher.launch(Intent.createChooser(intent, "选择要上传的文件"));
    }

    private void uploadFile(Uri uri) {
        String fileName = getFileName(uri);
        if (fileName == null) fileName = "upload_" + System.currentTimeMillis();
        final String finalFileName = fileName;

        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true);
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
                } else if (type == NasDevice.TYPE_SMB) {
                    String cleanUrl = baseUrl.replace("smb://", "");
                    String[] parts = cleanUrl.split("/", 2);
                    String shareName = parts.length > 1 ? parts[1] : "";
                    
                    SMBClient smbClient = new SMBClient();
                    try (Connection connection = smbClient.connect(parts[0])) {
                        AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), null);
                        Session session = connection.authenticate(ac);
                        try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                            String targetPath = (currentPath.endsWith("/") ? currentPath : currentPath + "/") + finalFileName;
                            if (targetPath.startsWith("/")) targetPath = targetPath.substring(1);
                            FileAccessor file = share.openFile(targetPath, 
                                java.util.EnumSet.of(AccessMask.GENERIC_WRITE), 
                                null, 
                                java.util.EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE), 
                                SMB2CreateDisposition.FILE_OVERWRITE_IF, 
                                null);
                            try (OutputStream os = file.getOutputStream()) {
                                byte[] buf = new byte[8192]; int len;
                                while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                            }
                        }
                    }
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

    // --- 其他文件操作 (防止外部协议不支持报错，做兜底) ---
    private void deleteSelected() {
        Toast.makeText(this, "外部协议暂不支持批量删除", Toast.LENGTH_SHORT).show();
        exitMultiSelect();
    }
    private void showMkdirDialog() { Toast.makeText(this, "外部协议暂不支持新建文件夹", Toast.LENGTH_SHORT).show(); }
    private void showSearchDialog() { Toast.makeText(this, "外部协议暂不支持搜索", Toast.LENGTH_SHORT).show(); }

    private void showFileMenu(FileItem f) {
        String[] actions = new String[]{"下载"};
        new MaterialAlertDialogBuilder(this).setTitle(f.name).setItems(actions, (dialog, which) -> {
            if (actions[which].equals("下载")) downloadFile(f);
        }).show();
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
            
            holder.cbSelect.setVisibility(View.GONE);
            
            holder.itemView.setOnClickListener(v -> {
                if (f.isDir) { 
                    currentPath = f.path; 
                    if (!currentPath.endsWith("/")) currentPath += "/"; 
                    loadFiles(); 
                } else {
                    showFileMenu(f);
                }
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
}
