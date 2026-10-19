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
    private CheckBox cbAuth;
    private FileAdapter adapter;
    private final List<File> items = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SettingsManager.init(this);
        AuthManager.init(this);
        currentDir = new File(ROOT);
        pathView = (TextView) findViewById(R.id.pathView);
        statusView = (TextView) findViewById(R.id.statusView);
        ipView = (TextView) findViewById(R.id.ipView);
        toggleBtn = (Button) findViewById(R.id.toggleBtn);
        listView = (ListView) findViewById(R.id.listView);
        cbAuth = (CheckBox) findViewById(R.id.cbAuth);

        SharedPreferences sp = getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        cbAuth.setChecked(sp.getBoolean("auth_enabled", true));
        cbAuth.setOnCheckedChangeListener((v, isChecked) -> {
            AuthManager.setAuthEnabled(this, isChecked);
            restartService();
        });

        storageView = new TextView(this);
        storageView.setTextSize(14);
        storageView.setTextColor(0xFF666666);
        LinearLayout rootLayout = (LinearLayout) findViewById(R.id.main_layout);
        if (rootLayout != null) rootLayout.addView(storageView, 3);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File f = items.get(position);
            if (f.isDirectory()) { currentDir = f; refresh(); }
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

    private void restartService() {
        if (HttpServerService.isRunning()) {
            stopService(new Intent(this, HttpServerService.class));
            new Handler().postDelayed(() -> startService(new Intent(MainActivity.this, HttpServerService.class)), 500);
        }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { 
        menu.add(0, 1, 0, "新建文件夹"); 
        menu.add(0, 6, 0, "服务端控制中心"); // 核心面板
        menu.add(0, 3, 0, "内网穿透 (公网访问)");
        menu.add(0, 4, 0, "运行日志");
        return true; 
    }
    
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setPositiveButton("创建", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.length() == 0) return;
                if (new File(currentDir, name).mkdirs()) refresh();
            }).setNegativeButton("取消", null).show();
            return true;
        } else if (item.getItemId() == 6) {
            showControlCenter();
            return true;
        } else if (item.getItemId() == 3) {
            showTunnelCenter();
            return true;
        } else if (item.getItemId() == 4) {
            new AlertDialog.Builder(this).setTitle("最近日志").setMessage(LogManager.getAllLogs()).setPositiveButton("关闭", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 服务端控制中心 (所有配置的集大成者)
    private void showControlCenter() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        // 1. 别名
        EditText etAlias = new EditText(this);
        etAlias.setHint("设备名称");
        etAlias.setText(SettingsManager.getDeviceAlias());
        layout.addView(etAlias);

        // 2. WebDAV 开关
        CheckBox cbWebDav = new CheckBox(this);
        cbWebDav.setText("开启 WebDAV 网络邻居 (8081)");
        cbWebDav.setChecked(SettingsManager.isWebDavEnabled());
        layout.addView(cbWebDav);

        // 3. LocalSend 开关
        CheckBox cbLs = new CheckBox(this);
        cbLs.setText("开启 LocalSend 接收 (53317)");
        cbLs.setChecked(SettingsManager.isLocalSendEnabled());
        layout.addView(cbLs);

        // 4. LocalSend PIN
        EditText etPin = new EditText(this);
        etPin.setHint("LocalSend 接收 PIN 码");
        etPin.setText(SettingsManager.getLsPin());
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etPin);

        // 5. 安全密码
        EditText etPwd = new EditText(this);
        etPwd.setHint("修改管理员密码 (留空不改)");
        etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPwd);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        new AlertDialog.Builder(this)
            .setTitle("服务端控制中心")
            .setView(scroll)
            .setPositiveButton("保存并重启服务", (dialog, which) -> {
                SettingsManager.setDeviceAlias(etAlias.getText().toString().trim());
                SettingsManager.setWebDavEnabled(cbWebDav.isChecked());
                SettingsManager.setLocalSendEnabled(cbLs.isChecked());
                SettingsManager.setLsPin(etPin.getText().toString().trim());
                
                String pwd = etPwd.getText().toString().trim();
                if (!pwd.isEmpty()) AuthManager.setAuth(this, "admin", pwd);
                
                Toast.makeText(this, "配置已保存，正在重启...", Toast.LENGTH_SHORT).show();
                restartService();
                updateServiceStatus();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    // 内网穿透面板
    private void showTunnelCenter() {
        boolean isRunning = FreeTunnelManager.isRunning();
        String title = isRunning ? "内网穿透 (已连接)" : "内网穿透 (未连接)";
        java.util.List<String> options = new java.util.ArrayList<String>();
        if (!isRunning) options.add("启动一键免注册穿透 (自动优选)");
        else {
            options.add("复制当前公网链接");
            options.add("停止当前穿透");
        }
        options.add("自定义 SSH 高级穿透");

        new AlertDialog.Builder(this).setTitle(title)
            .setItems(options.toArray(new String[0]), (dialog, which) -> {
                String choice = options.get(which);
                if (choice.contains("启动")) showFreeNodeSelection();
                else if (choice.contains("复制")) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", FreeTunnelManager.getPublicUrl()));
                    Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
                } else if (choice.contains("停止")) {
                    FreeTunnelManager.stop(); SshTunnelManager.stop(); updateServiceStatus();
                    Toast.makeText(this, "穿透已停止", Toast.LENGTH_SHORT).show();
                } else if (choice.contains("自定义")) showSshConfigDialog();
            }).setNegativeButton("关闭", null).show();
    }

    private void showFreeNodeSelection() {
        String[] nodes = FreeTunnelManager.getNodeNames();
        new AlertDialog.Builder(this).setTitle("选择穿透节点")
            .setSingleChoiceItems(nodes, 0, (dialog, which) -> {})
            .setPositiveButton("开始连接", (dialog, which) -> {
                FreeTunnelManager.start(SettingsManager.getWebPort(), 0);
                Toast.makeText(this, "正在自动寻找可用节点...", Toast.LENGTH_LONG).show();
                waitAndShowFreeUrl();
            }).setNegativeButton("取消", null).show();
    }

    private void waitAndShowFreeUrl() {
        new Handler().postDelayed(new Runnable() {
            int count = 0;
            @Override
            public void run() {
                String url = FreeTunnelManager.getPublicUrl();
                if (url != null && !url.isEmpty()) {
                    updateServiceStatus();
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(MainActivity.this).setTitle("穿透成功！")
                            .setMessage("公网链接:\n\n" + url + "\n\n发给朋友即可访问。")
                            .setPositiveButton("复制链接", (d, w) -> {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url));
                                Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                            }).setNegativeButton("关闭", null).show();
                    });
                } else if (count < 30 && FreeTunnelManager.isRunning()) {
                    count++;
                    new Handler().postDelayed(this, 1000);
                } else if (!FreeTunnelManager.isRunning()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接失败，请尝试选择其他节点", Toast.LENGTH_LONG).show());
                }
            }
        }, 1000);
    }

    private void showSshConfigDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        scrollView.addView(layout);

        EditText etHost = new EditText(this); etHost.setHint("公网服务器 IP"); layout.addView(etHost);
        EditText etPort = new EditText(this); etPort.setHint("SSH 端口 (默认 22)"); etPort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("SSH 用户名"); layout.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("SSH 密码"); etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etPass);
        EditText etRemotePort = new EditText(this); etRemotePort.setHint("公网映射端口"); etRemotePort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etRemotePort);
        
        new AlertDialog.Builder(this).setTitle("自定义 SSH 穿透").setView(scrollView)
            .setPositiveButton("连接", (dialog, which) -> {
                String host = etHost.getText().toString().trim();
                int port = etPort.getText().toString().isEmpty() ? 22 : Integer.parseInt(etPort.getText().toString());
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                int remotePort = etRemotePort.getText().toString().isEmpty() ? 8080 : Integer.parseInt(etRemotePort.getText().toString());
                SshTunnelManager.start(host, port, user, pass, remotePort, HttpServerService.PORT);
                Toast.makeText(this, "正在建立 SSH 隧道...", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消", null).show();
    }

    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }

    private void updateServiceStatus() {
        boolean running = HttpServerService.isRunning();
        statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止");
        toggleBtn.setText(running ? "停止服务" : "启动服务");
        
        String ip = getIpAddress();
        String text = "网页管理: http://" + ip + ":" + SettingsManager.getWebPort();
        if (SettingsManager.isWebDavEnabled()) text += "\n网络邻居: http://" + ip + ":" + SettingsManager.getWebDavPort();
        if (SettingsManager.isLocalSendEnabled()) text += "\nLocalSend PIN: " + SettingsManager.getLsPin();
        
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
