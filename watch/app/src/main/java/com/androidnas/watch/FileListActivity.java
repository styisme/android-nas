package com.androidnas.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
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
    private ListView listView;
    private TextView tvTitle;
    private List<FileItem> files = new ArrayList<>();
    private FileAdapter adapter;
    private WatchDevice device;
    private String baseUrl = "", currentPath = "/", authHeader = "";
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        device = (WatchDevice) getIntent().getSerializableExtra("device");
        if (device == null) { finish(); return; }
        if (device.type != WatchDevice.TYPE_FTP) baseUrl = "http://" + device.ip + ":" + device.port;
        authHeader = "Basic " + android.util.Base64.encodeToString((device.user + ":" + device.pass).getBytes(), android.util.Base64.NO_WRAP);
        setContentView(com.androidnas.watch.R.layout.activity_main);
        listView = findViewById(com.androidnas.watch.R.id.deviceListView);
        tvTitle = findViewById(com.androidnas.watch.R.id.tvTitle);
        findViewById(com.androidnas.watch.R.id.btnAddDevice).setVisibility(View.GONE);
        adapter = new FileAdapter(); listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, pos, id) -> {
            FileItem f = files.get(pos);
            if (f.isDir) { if (currentPath.endsWith("/")) currentPath += f.name; else currentPath += "/" + f.name; loadFiles(); }
            else sendToTv(f);
        });
        listView.setOnItemLongClickListener((p, v, pos, id) -> {
            if (!currentPath.equals("/")) {
                String[] parts = currentPath.split("/");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length - 1; i++) sb.append("/").append(parts[i]);
                currentPath = sb.length() == 0 ? "/" : sb.toString(); loadFiles();
            }
            return true;
        });
        loadFiles();
    }

    private void loadFiles() {
        files.clear(); tvTitle.setText("路径: " + currentPath); adapter.notifyDataSetChanged();
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

    // #15: HttpURLConnection 不支持自定义 method（PROPFIND），改用裸 Socket 手写 HTTP
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
        if (device.type != WatchDevice.TYPE_ANDROID) { Toast.makeText(this, "仅支持自家 NAS 投屏", Toast.LENGTH_SHORT).show(); return; }
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

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return files.size(); }
        @Override public Object getItem(int p) { return files.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            TextView tv = new TextView(FileListActivity.this);
            tv.setPadding(24, 24, 24, 24); tv.setTextSize(18); tv.setTextColor(0xFFE6E0E9);
            FileItem f = files.get(pos);
            tv.setText((f.isDir ? "📁 " : "🎬 ") + f.name);
            return tv;
        }
    }
}
