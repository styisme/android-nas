package com.androidnas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private File currentDir;
    private ListView listView;
    private TextView pathView, statusView, ipView, storageView;
    private Button toggleBtn;
    private CheckBox cbAuth, cbTunnel, cbFreeTunnel;
    private FileAdapter adapter;
    private final List<File> items = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthManager.init(this);
        currentDir = new File(ROOT);
        pathView = (TextView) findViewById(R.id.pathView);
        statusView = (TextView) findViewById(R.id.statusView);
        ipView = (TextView) findViewById(R.id.ipView);
        toggleBtn = (Button) findViewById(R.id.toggleBtn);
        listView = (ListView) findViewById(R.id.listView);
        cbAuth = (CheckBox) findViewById(R.id.cbAuth);
        cbTunnel = (CheckBox) findViewById(R.id.cbTunnel);
        cbFreeTunnel = (CheckBox) findViewById(R.id.cbFreeTunnel);

        SharedPreferences sp = getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        cbAuth.setChecked(sp.getBoolean("auth_enabled", true));
        cbAuth.setOnCheckedChangeListener((v, isChecked) -> {
            AuthManager.setAuthEnabled(this, isChecked);
            if (HttpServerService.isRunning()) {
                stopService(new Intent(this, HttpServerService.class));
                new Handler().postDelayed(() -> startService(new Intent(MainActivity.this, HttpServerService.class)), 500);
            }
            updateServiceStatus();
        });

        // 高级：SSH 内网穿透
        cbTunnel.setChecked(SshTunnelManager.isRunning());
        cbTunnel.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) showSshConfigDialog();
            else {
                SshTunnelManager.stop();
                Toast.makeText(this, "SSH内网穿透已关闭", Toast.LENGTH_SHORT).show();
            }
        });

        // 极简：一键免费公网穿透
        cbFreeTunnel.setChecked(FreeTunnelManager.isRunning());
        cbFreeTunnel.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                FreeTunnelManager.start(HttpServerService.PORT);
                Toast.makeText(this, "正在生成公网链接，请稍候...", Toast.LENGTH_LONG).show();
                waitAndShowFreeUrl();
            } else {
                FreeTunnelManager.stop();
                Toast.makeText(this, "一键穿透已关闭", Toast.LENGTH_SHORT).show();
            }
        });

        storageView = new TextView(this);
        storageView.setTextSize(14);
        storageView.setTextColor(0xFF666666);
        LinearLayout rootLayout = (LinearLayout) findViewById(R.id.main_layout);
        if (rootLayout != null) rootLayout.addView(storageView, 4);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File f = items.get(position);
            if (f.isDirectory()) { currentDir = f; refresh(); }
            else showFileMenu(f);
        });
        
        toggleBtn.setOnClickListener(v -> {
            if (HttpServerService.isRunning()) stopService(new Intent(MainActivity.this, HttpServerService.class));
            else startService(new Intent(MainActivity.this, HttpServerService.class));
            updateServiceStatus();
        });
        
        findViewById(R.id.upBtn).setOnClickListener(v -> {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.getAbsolutePath().startsWith(ROOT)) { currentDir = parent; refresh(); }
        });
        findViewById(R.id.refreshBtn).setOnClickListener(v -> refresh());
        
        if (!HttpServerService.isRunning()) startService(new Intent(this, HttpServerService.class));
    }

    // 轮询检测公网 URL 是否生成完毕
    private void waitAndShowFreeUrl() {
        new Handler().postDelayed(new Runnable() {
            int count = 0;
            @Override
            public void run() {
                String url = FreeTunnelManager.getPublicUrl();
                if (url != null && !url.isEmpty()) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("公网穿透成功！")
                            .setMessage("请复制以下链接发给你的同学：\n\n" + url)
                            .setPositiveButton("复制链接", (d, w) -> {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url));
                                Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                            }).setNegativeButton("关闭", null).show();
                    });
                } else if (count < 20 && FreeTunnelManager.isRunning()) {
                    count++;
                    new Handler().postDelayed(this, 1000);
                } else if (!FreeTunnelManager.isRunning()) {
                    runOnUiThread(() -> {
                        cbFreeTunnel.setChecked(false);
                        Toast.makeText(MainActivity.this, "穿透失败，请检查网络", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }, 1000);
    }

    private void showSshConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etHost = new EditText(this); etHost.setHint("公网服务器 IP"); layout.addView(etHost);
        EditText etPort = new EditText(this); etPort.setHint("SSH 端口 (默认 22)"); etPort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("SSH 用户名"); layout.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("SSH 密码"); etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etPass);
        EditText etRemotePort = new EditText(this); etRemotePort.setHint("公网映射端口 (如 8080)"); etRemotePort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etRemotePort);

        new AlertDialog.Builder(this).setTitle("自定义 SSH 穿透").setView(layout)
            .setPositiveButton("连接", (dialog, which) -> {
                String host = etHost.getText().toString().trim();
                int port = etPort.getText().toString().isEmpty() ? 22 : Integer.parseInt(etPort.getText().toString());
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                int remotePort = etRemotePort.getText().toString().isEmpty() ? 8080 : Integer.parseInt(etRemotePort.getText().toString());
                SshTunnelManager.start(host, port, user, pass, remotePort, HttpServerService.PORT);
                cbTunnel.setChecked(true);
            }).setNegativeButton("取消", (dialog, which) -> cbTunnel.setChecked(false)).show();
    }

    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }

    private void updateServiceStatus() {
        boolean running = HttpServerService.isRunning();
        statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止");
        toggleBtn.setText(running ? "停止服务" : "启动服务");
        String ip = getIpAddress();
        String text = "网页管理: http://" + ip + ":8080\n网络邻居: http://" + ip + ":8081";
        if (FreeTunnelManager.isRunning() && !FreeTunnelManager.getPublicUrl().isEmpty()) {
            text += "\n\n公网链接:\n" + FreeTunnelManager.getPublicUrl();
        }
        ipView.setText(text);
    }

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return "127.0.0.1";
            return String.format(Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Throwable t) { return "127.0.0.1"; }
    }

    private void refresh() {
        if (!currentDir.exists() || !currentDir.isDirectory()) currentDir = new File(ROOT);
        pathView.setText(currentDir.getAbsolutePath().replace(ROOT, "/"));
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
            long bytesTotal = (long)stat.getBlockSize() * (long)stat.getBlockCount();
            storageView.setText("存储空间: 可用 " + (bytesAvailable/1024/1024) + " MB / 总计 " + (bytesTotal/1024/1024) + " MB");
        } catch (Throwable t) { storageView.setText(""); }
        items.clear();
        File[] files = currentDir.listFiles();
        if (files != null) Collections.addAll(items, files);
        Collections.sort(items, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        adapter.notifyDataSetChanged();
    }

    private void showFileMenu(final File f) { /* 占位 */ }

    @Override public boolean onCreateOptionsMenu(Menu menu) { 
        menu.add(0, 1, 0, "新建文件夹"); 
        menu.add(0, 2, 0, "修改密码");
        menu.add(0, 4, 0, "运行日志");
        return true; 
    }
    
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 2) {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this).setTitle("修改密码").setView(input).setPositiveButton("保存", (dialog, which) -> {
                String p = input.getText().toString().trim();
                if (p.isEmpty()) return;
                AuthManager.setAuth(this, "admin", p);
                Toast.makeText(this, "密码已更新", Toast.LENGTH_SHORT).show();
                updateServiceStatus();
            }).setNegativeButton("取消", null).show();
            return true;
        } else if (item.getItemId() == 4) {
            new AlertDialog.Builder(this).setTitle("最近日志").setMessage(LogManager.getAllLogs()).setPositiveButton("关闭", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setPadding(16, 24, 16, 24);
            tv.setTextSize(16);
            File f = items.get(position);
            tv.setText((f.isDirectory() ? "📁 " : "📄 ") + f.getName());
            return tv;
        }
    }
}
