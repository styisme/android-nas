package com.androidnas.client;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class BackupManager {
    private Context context;
    private String baseUrl;
    private String authHeader;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    public interface BackupCallback {
        void onProgress(int current, int total, String fileName);
        void onComplete(int uploaded);
        void onError(String msg);
    }

    public BackupManager(Context context, String baseUrl, String user, String pass) {
        this.context = context;
        this.baseUrl = baseUrl;
        this.authHeader = Credentials.basic(user, pass);
    }

    public void startBackup(BackupCallback callback) {
        if (isRunning) {
            mainHandler.post(() -> callback.onError("备份正在进行中..."));
            return;
        }
        isRunning = true;

        new Thread(() -> {
            try {
                // 1. 获取 NAS 上已有的照片列表
                Set<String> remoteFiles = getRemotePhotos();
                if (remoteFiles == null) {
                    mainHandler.post(() -> callback.onError("无法连接 NAS 获取文件列表"));
                    isRunning = false;
                    return;
                }

                // 2. 扫描本地相册
                List<LocalPhoto> localPhotos = getLocalPhotos();
                
                // 3. 过滤出需要备份的
                List<LocalPhoto> toBackup = new ArrayList<>();
                for (LocalPhoto p : localPhotos) {
                    if (!remoteFiles.contains(p.name)) {
                        toBackup.add(p);
                    }
                }

                if (toBackup.isEmpty()) {
                    mainHandler.post(() -> callback.onComplete(0));
                    isRunning = false;
                    return;
                }

                // 4. 开始逐个上传
                int total = toBackup.size();
                int successCount = 0;
                for (int i = 0; i < total; i++) {
                    LocalPhoto p = toBackup.get(i);
                    final int current = i + 1;
                    mainHandler.post(() -> callback.onProgress(current, total, p.name));

                    boolean success = uploadPhoto(p);
                    if (success) successCount++;
                }

                final int finalSuccess = successCount;
                mainHandler.post(() -> callback.onComplete(finalSuccess));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("备份异常: " + e.getMessage()));
            } finally {
                isRunning = false;
            }
        }).start();
    }

    private Set<String> getRemotePhotos() {
        try {
            OkHttpClient client = new OkHttpClient();
            String url = baseUrl + "/api/list?path=/Photos/";
            Request req = new Request.Builder().url(url).header("Authorization", authHeader).build();
            Response resp = client.newCall(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                JSONObject obj = new JSONObject(resp.body().string());
                JSONArray arr = obj.getJSONArray("files");
                Set<String> set = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    set.add(arr.getJSONObject(i).getString("name"));
                }
                return set;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<LocalPhoto> getLocalPhotos() {
        List<LocalPhoto> list = new ArrayList<>();
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
        };
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    list.add(new LocalPhoto(name, uri));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private boolean uploadPhoto(LocalPhoto photo) {
        try {
            OkHttpClient client = new OkHttpClient();
            // 确保 /Photos 目录存在
            client.newCall(new Request.Builder().url(baseUrl + "/api/mkdir")
                    .post(new okhttp3.FormBody.Builder().add("path", "/").add("name", "Photos").build())
                    .header("Authorization", authHeader).build()).execute();

            InputStream is = context.getContentResolver().openInputStream(photo.uri);
            if (is == null) return false;

            RequestBody body = new RequestBody() {
                @Override public MediaType contentType() { return MediaType.parse("image/*"); }
                @Override public void writeTo(BufferedSink sink) throws java.io.IOException {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                }
            };

            String targetUrl = baseUrl + "/Photos/" + Uri.encode(photo.name);
            Request req = new Request.Builder().url(targetUrl).put(body).header("Authorization", authHeader).build();
            Response resp = client.newCall(req).execute();
            is.close();
            return resp.isSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private static class LocalPhoto {
        String name;
        Uri uri;
        LocalPhoto(String name, Uri uri) { this.name = name; this.uri = uri; }
    }
}
