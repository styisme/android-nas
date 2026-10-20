package com.androidnas.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileListActivity extends Activity {
    private RecyclerView recyclerView;
    private TextView tvTitle;
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    private WatchDevice device;
    private String baseUrl = "", currentPath = "/", authHeader = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    interface OnItemClick { void onClick(FileItem f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        device = (WatchDevice) getIntent().getSerializableExtra("device");
        if (device == null) { finish(); return; }
        
        if (device.type != WatchDevice.TYPE_FTP) baseUrl = "http://" + device.ip + ":" + device.port;
        authHeader = "Basic " + android.util.Base64.encodeToString((device.user + ":" + device.pass).getBytes(), android.util.Base64.NO_WRAP);
        
        setContentView(com.androidnas.watch.R.layout.activity_file_list);
        recyclerView = findViewById(com.androidnas.watch.R.id.recyclerView);
        tvTitle = findViewById(com.androidnas.watch.R.id.tvTitle);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);
        
        // 点击文件/文件夹
        adapter.setOnItemClickListener(f -> {
            if (f.isDir) {
                if (currentPath.endsWith("/")) currentPath += f.name;
                else currentPath += "/" + f.name;
                loadFiles();
            } else {
                showFileOptions(f);
            }
        });
        
        loadFiles();
    }

    private void showFileOptions(FileItem f) {
        String ext = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf(".") + 1).toLowerCase() : "";
        boolean isMedia = ext.matches("jpg|jpeg|png|gif|webp|mp4|mp3|mkv|webm|wav");

        List<String> options = new ArrayList<>();
        if (isMedia) options.add("预览/播放");
        options.add("下载到手表");
        if (device.type == WatchDevice.TYPE_ANDROID) options.add("投屏到电视");

        String[] items = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(items, (dialog, which) -> {
                String action = items[which];
                if (action.equals("预览/播放")) previewFile(f, ext);
                else if (action.equals("下载到手表")) downloadFile(f);
                else if (action.equals("投屏到电视")) sendToTv(f);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void previewFile(FileItem f, String ext) {
        if (device.type != WatchDevice.TYPE_ANDROID) {
            Toast.makeText(this, "仅支持自家 NAS 预览", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "获取播放链接...", Toast.LENGTH_SHORT).show();
        String fullPath = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;
        
        new Thread(() -> {
            try {
                // 获取免密 Token
                URL url = new URL(baseUrl + "/api/share");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", authHeader);
                String body = "path=" + URLEncoder.encode(fullPath, "UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();

                if (conn.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String token = r.readLine();
                    r.close();
                    
                    String shareUrl = baseUrl + "/share/" + token;
                    mainHandler.post(() -> openSystemPlayer(shareUrl, ext));
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "获取链接失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "网络异常", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void openSystemPlayer(String url, String ext) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            String mime = (ext.equals("mp4") || ext.equals("mkv") || ext.equals("webm")) ? "video/*"
                : ((ext.equals("mp3") || ext.equals("wav")) ? "audio/*" : "image/*");
            i.setDataAndType(Uri.parse(url), mime);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "手表未安装可用的播放器", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadFile(FileItem f) {
        if (device.type != WatchDevice.TYPE_ANDROID) {
            Toast.makeText(this, "暂不支持外部协议下载", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始下载: " + f.name, Toast.LENGTH_SHORT).show();
        String fullPath = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;

        new Thread(() -> {
            try {
                // 手表专属沙盒目录
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = new File(Environment.getExternalStorageDirectory(), "Android/data/com.androidnas.watch/files/Download");
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(dir, f.name);

                URL url = new URL(baseUrl + "/api/download?path=" + URLEncoder.encode(fullPath, "UTF-8"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                conn.setConnectTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(dest);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                    fos.close(); is.close();
                    mainHandler.post(() -> Toast.makeText(this, "下载完成: " + f.name, Toast.LENGTH_LONG).show());
                } else {
                    final int finalCode = code;
                    mainHandler.post(() -> Toast.makeText(this, "下载失败: HTTP " + finalCode, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "下载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadFiles() {
        files.clear(); tvTitle.setText(currentPath); adapter.notifyDataSetChanged();
        new Thread(() -> {
            try {
                if (device.type == WatchDevice.TYPE_ANDROID) loadAndroid();
                else if (device.type == WatchDevice.TYPE_WEBDAV) loadWebDav();
                else if (device.type == WatchDevice.TYPE_FTP) loadFtp();
                mainHandler.post(() -> adapter.notifyDataSetChanged());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadAndroid() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/api/list?path=" + URLEncoder.encode(currentPath, "UTF-8")).openConnection();
        c.setConnectTimeout(5000); c.setRequestProperty("Authorization", authHeader);
        if (c.getResponseCode() == 200) {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            r.close();
            JSONArray arr = new JSONObject(sb.toString()).getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                files.add(new FileItem(f.getString("name"), f.getBoolean("dir")));
            }
        }
    }

    private void loadWebDav() throws Exception {
        String urlStr = baseUrl + (currentPath.endsWith("/") ? currentPath : currentPath + "/");
        URL url = new URL(urlStr);
        String host = url.getHost();
        int port = url.getPort() > 0 ? url.getPort() : (url.getProtocol().equals("https") ? 443 : 80);
        Socket sock = new Socket(host, port);
        sock.setSoTimeout(10000);
        String xmlBody = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
        byte[] bodyBytes = xmlBody.getBytes("UTF-8");
        OutputStream os = sock.getOutputStream();
        String req = "PROPFIND " + url.getPath() + " HTTP/1.1\r\n" +
            "Host: " + host + ":" + port + "\r\nDepth: 1\r\nAuthorization: " + authHeader + "\r\n" +
            "Content-Type: application/xml\r\nContent-Length: " + bodyBytes.length + "\r\n\r\n";
        os.write(req.getBytes("UTF-8")); os.write(bodyBytes); os.flush();
        InputStream is = sock.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192]; int len;
        while ((len = is.read(buf)) > 0) {
            sb.append(new String(buf, 0, len, "UTF-8"));
            if (sb.length() > 2 * 1024 * 1024) break;
        }
        try { sock.close(); } catch (Exception ignored) {}
        String xml = sb.toString();
        Pattern rp = Pattern.compile("<D:response>(.*?)</D:response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern hp = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Pattern dp = Pattern.compile("<D:collection\\s*/>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher rm = rp.matcher(xml);
        String curNorm = currentPath.endsWith("/") && currentPath.length() > 1 ? currentPath.substring(0, currentPath.length() - 1) : currentPath;
        while (rm.find()) {
            String blk = rm.group(1); Matcher hm = hp.matcher(blk); if (!hm.find()) continue;
            String href = hm.group(1);
            if (href.startsWith("http")) try { href = href.substring(href.indexOf("/", 7)); } catch (Exception ignored) {}
            String norm = href.endsWith("/") && href.length() > 1 ? href.substring(0, href.length() - 1) : href;
            if (norm.equals(curNorm) || norm.isEmpty()) continue;
            String name = href.endsWith("/")
                ? (href.substring(0, href.length() - 1)).substring((href.substring(0, href.length() - 1)).lastIndexOf("/") + 1)
                : href.substring(href.lastIndexOf("/") + 1);
            if (name.isEmpty()) continue;
            try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
            files.add(new FileItem(name, dp.matcher(blk).find()));
        }
    }

    private void loadFtp() throws Exception {
        FTPClient ftp = new FTPClient();
        ftp.connect(device.ip, Integer.parseInt(device.port));
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) { ftp.disconnect(); throw new Exception("FTP 拒绝连接"); }
        if (!ftp.login(device.user, device.pass)) { ftp.logout(); throw new Exception("账号密码错误"); }
        ftp.enterLocalPassiveMode(); ftp.setFileType(FTP.BINARY_FILE_TYPE);
        FTPFile[] arr = ftp.listFiles(currentPath);
        if (arr != null) for (FTPFile f : arr) {
            if (f.getName().equals(".") || f.getName().equals("..")) continue;
            files.add(new FileItem(f.getName(), f.isDirectory()));
        }
        ftp.logout(); ftp.disconnect();
    }

    private void sendToTv(FileItem f) {
        Toast.makeText(this, "正在投屏...", Toast.LENGTH_SHORT).show();
        String full = currentPath.endsWith("/") ? currentPath + f.name : currentPath + "/" + f.name;
        final String videoUrl = baseUrl + "/api/download?path=" + URLEncoder.encode(full);
        new Thread(() -> {
            try {
                DatagramSocket s = new DatagramSocket();
                s.setBroadcast(true); s.setSoTimeout(2000);
                byte[] sd = "DISCOVER_TV".getBytes();
                s.send(new DatagramPacket(sd, sd.length, InetAddress.getByName("255.255.255.255"), 8090));
                byte[] rd = new byte[1024];
                DatagramPacket rp = new DatagramPacket(rd, rd.length);
                s.receive(rp);
                String tvIp = rp.getAddress().getHostAddress(); s.close();
                HttpURLConnection c = (HttpURLConnection) new URL("http://" + tvIp + ":8090/play?" + URLEncoder.encode(videoUrl)).openConnection();
                if (c.getResponseCode() == 200) mainHandler.post(() -> Toast.makeText(this, "投屏成功", Toast.LENGTH_SHORT).show());
                else throw new Exception("TV refused");
            } catch (Exception e) { mainHandler.post(() -> Toast.makeText(this, "未找到电视", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    public static class FileItem implements java.io.Serializable {
        String name; boolean isDir;
        FileItem(String n, boolean d) { name = n; isDir = d; }
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private OnItemClick clickListener;
        void setOnItemClickListener(OnItemClick l) { clickListener = l; }

        @Override public int getItemCount() { return files.size(); }

        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(com.androidnas.watch.R.layout.item_watch_file, parent, false));
        }

        @Override public void onBindViewHolder(VH h, int pos) {
            FileItem f = files.get(pos);
            h.tvName.setText(f.name);
            h.tvIcon.setText(f.isDir ? "📁" : "🎬");

            h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(f); });

            h.itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
                return false;
            });
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon;
            VH(View v) { 
                super(v); 
                tvName = v.findViewById(com.androidnas.watch.R.id.tvName); 
                tvIcon = v.findViewById(com.androidnas.watch.R.id.tvIcon); 
            }
        }
    }
}
