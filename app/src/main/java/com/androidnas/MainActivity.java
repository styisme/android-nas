package com.androidnas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
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
    private int selectedNode = 0;

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
        cbAuth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AuthManager.setAuthEnabled(MainActivity.this, isChecked);
                if (HttpServerService.isRunning()) {
                    stopService(new Intent(MainActivity.this, HttpServerService.class));
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() { startService(new Intent(MainActivity.this, HttpServerService.class)); }
                    }, 500);
                }
                updateServiceStatus();
            }
        });

        storageView = new TextView(this);
        storageView.setTextSize(14);
        storageView.setTextColor(0xFF666666);
        LinearLayout rootLayout = (LinearLayout) findViewById(R.id.main_layout);
        if (rootLayout != null) rootLayout.addView(storageView, 3);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showFileMenu(items.get(position));
                return true;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File f = items.get(position);
                if (f.isDirectory()) { currentDir = f; refresh(); }
            }
        });
        
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (HttpServerService.isRunning()) stopService(new Intent(MainActivity.this, HttpServerService.class));
                else startService(new Intent(MainActivity.this, HttpServerService.class));
                updateServiceStatus();
            }
        });
        
        findViewById(R.id.upBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File parent = currentDir.getParentFile();
                if (parent != null && parent.getAbsolutePath().startsWith(ROOT)) { currentDir = parent; refresh(); }
            }
        });
        findViewById(R.id.refreshBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { refresh(); }
        });
        
        if (!HttpServerService.isRunning()) {
            startService(new Intent(this, HttpServerService.class));
            checkBatteryOptimization();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                    .setTitle("后台保活提示")
                    .setMessage("为保证 NAS 服务在息屏后不断网，请允许忽略电池优化。\n\n如果系统有“锁屏清理”或“省电策略”，请将本应用设为“无限制”或“允许后台运行”。")
                    .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            try {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    })
                    .setNegativeButton("暂不", null)
                    .show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { 
        menu.add(0, 1, 0, "新建文件夹"); 
        menu.add(0, 6, 0, "驾驶舱设置");
        menu.add(0, 3, 0, "内网穿透");
        menu.add(0, 4, 0, "运行日志");
        menu.add(0, 7, 0, "关于");
        return true; 
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (name.length() == 0) return;
                    if (new File(currentDir, name).mkdirs()) refresh();
                }
            }).setNegativeButton("取消", null).show();
            return true;
        } else if (item.getItemId() == 6) {
            showControlCenter();
            return true;
        } else if (item.getItemId() == 3) {
            if (SettingsManager.isLowPowerMode()) {
                new AlertDialog.Builder(this)
                    .setTitle("极寒模式已开启")
                    .setMessage("为保证续航，极寒模式下已禁止建立内网穿透长连接。如需使用，请在设置中关闭极寒模式。")
                    .setPositiveButton("去关闭", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) { showControlCenter(); }
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return true;
            }
            showTunnelCenter();
            return true;
        } else if (item.getItemId() == 7) {
            new AlertDialog.Builder(this)
                .setTitle("关于 Android NAS")
                .setMessage("版本: v3.0.0 旗舰版\n\n支持流媒体、剪贴板、内网穿透。\n\n⚠️ 本项目大量代码由 AI 辅助生成。")
                .setPositiveButton("确定", null)
                .show();
            return true;
        } else if (item.getItemId() == 4) {
            new AlertDialog.Builder(this).setTitle("最近日志").setMessage(LogManager.getAllLogs()).setPositiveButton("关闭", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showControlCenter() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        scrollView.addView(layout);

        // --- 基础信息 ---
        EditText etAlias = new EditText(this); etAlias.setHint("设备名称"); etAlias.setText(SettingsManager.getDeviceAlias()); layout.addView(etAlias);
        EditText etWebPort = new EditText(this); etWebPort.setHint("Web 端口"); etWebPort.setInputType(InputType.TYPE_CLASS_NUMBER); etWebPort.setText(String.valueOf(SettingsManager.getWebPort())); layout.addView(etWebPort);
        EditText etDavPort = new EditText(this); etDavPort.setHint("WebDAV 端口"); etDavPort.setInputType(InputType.TYPE_CLASS_NUMBER); etDavPort.setText(String.valueOf(SettingsManager.getWebDavPort())); layout.addView(etDavPort);

        // --- 性能与安全 ---
        CheckBox cbLowPower = new CheckBox(this); cbLowPower.setText("极寒省电模式 (限制 Wi-Fi 并断开穿透)"); cbLowPower.setChecked(SettingsManager.isLowPowerMode()); layout.addView(cbLowPower);
        
        EditText etAdminUser = new EditText(this); etAdminUser.setHint("管理员账号"); etAdminUser.setText(AuthManager.getAdminUser()); layout.addView(etAdminUser);
        EditText etAdminPass = new EditText(this); etAdminPass.setHint("管理员密码 (留空不改)"); etAdminPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etAdminPass);

        // --- 服务开关 ---
        CheckBox cbWebDav = new CheckBox(this); cbWebDav.setText("开启 WebDAV"); cbWebDav.setChecked(SettingsManager.isWebDavEnabled()); layout.addView(cbWebDav);
        CheckBox cbLs = new CheckBox(this); cbLs.setText("开启 LocalSend"); cbLs.setChecked(SettingsManager.isLocalSendEnabled()); layout.addView(cbLs);
        EditText etPin = new EditText(this); etPin.setHint("LocalSend PIN"); etPin.setText(SettingsManager.getLsPin()); layout.addView(etPin);
        
        CheckBox cbHttps = new CheckBox(this); cbHttps.setText("开启 HTTPS 加密 (占位)"); layout.addView(cbHttps);
        CheckBox cbStream = new CheckBox(this); cbStream.setText("允许网页流媒体播放 (视频)"); cbStream.setChecked(SettingsManager.isStreamEnabled()); layout.addView(cbStream);
        CheckBox cbClipboard = new CheckBox(this); cbClipboard.setText("开启局域网剪贴板"); cbClipboard.setChecked(SettingsManager.isClipboardEnabled()); layout.addView(cbClipboard);

        // 极寒模式联动
        cbLowPower.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cbLs.setEnabled(!isChecked);
                cbStream.setEnabled(!isChecked);
                cbClipboard.setEnabled(!isChecked);
                if (isChecked) {
                    cbLs.setChecked(false);
                    cbStream.setChecked(false);
                    cbClipboard.setChecked(false);
                }
            }
        });
        if (SettingsManager.isLowPowerMode()) {
            cbLs.setEnabled(false);
            cbStream.setEnabled(false);
            cbClipboard.setEnabled(false);
        }

        new AlertDialog.Builder(this).setTitle("NAS 驾驶舱").setView(scrollView)
            .setPositiveButton("保存并重启", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    SettingsManager.setDeviceAlias(etAlias.getText().toString().trim());
                    SettingsManager.setLowPowerMode(cbLowPower.isChecked());
                    SettingsManager.setWebDavEnabled(cbWebDav.isChecked());
                    SettingsManager.setLocalSendEnabled(cbLs.isChecked());
                    SettingsManager.setStreamEnabled(cbStream.isChecked());
                    SettingsManager.setClipboardEnabled(cbClipboard.isChecked());
                    SettingsManager.setLsPin(etPin.getText().toString().trim());
                    
                    if (!etWebPort.getText().toString().isEmpty()) {
                        int p = Integer.parseInt(etWebPort.getText().toString());
                        if (p > 1024 && p < 65535) SettingsManager.setWebPort(p);
                    }
                    if (!etDavPort.getText().toString().isEmpty()) {
                        int p = Integer.parseInt(etDavPort.getText().toString());
                        if (p > 1024 && p < 65535) SettingsManager.setWebDavPort(p);
                    }
                    
                    String newUser = etAdminUser.getText().toString().trim();
                    String newPass = etAdminPass.getText().toString().trim();
                    if (!newUser.isEmpty()) {
                        if (newPass.isEmpty()) {
                            AuthManager.setAuth(MainActivity.this, newUser, AuthManager.getAdminPass());
                        } else {
                            AuthManager.setAuth(MainActivity.this, newUser, newPass);
                        }
                    } else if (!newPass.isEmpty()) {
                        AuthManager.setAuth(MainActivity.this, AuthManager.getAdminUser(), newPass);
                    }

                    if (HttpServerService.isRunning()) {
                        stopService(new Intent(MainActivity.this, HttpServerService.class));
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() { startService(new Intent(MainActivity.this, HttpServerService.class)); }
                        }, 500);
                    }
                    updateServiceStatus();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void showTunnelCenter() {
        boolean isRunning = FreeTunnelManager.isRunning();
        String title = isRunning ? "内网穿透 (已连接)" : "内网穿透 (未连接)";
        List<String> options = new ArrayList<String>();
        if (!isRunning) options.add("启动免费穿透");
        else {
            options.add("复制当前公网链接");
            options.add("停止当前穿透");
        }
        options.add("自定义 SSH 穿透");

        new AlertDialog.Builder(this).setTitle(title)
            .setItems(options.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String choice = options.get(which);
                    if (choice.contains("启动")) {
                        FreeTunnelManager.start(SettingsManager.getWebPort(), 0);
                        Toast.makeText(MainActivity.this, "正在连接...", Toast.LENGTH_SHORT).show();
                    } else if (choice.contains("复制")) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", FreeTunnelManager.getPublicUrl()));
                    } else if (choice.contains("停止")) {
                        FreeTunnelManager.stop(); updateServiceStatus();
                    } else if (choice.contains("自定义")) {
                        showSshConfigDialog();
                    }
                }
            }).setNegativeButton("关闭", null).show();
    }

    private void showSshConfigDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        scrollView.addView(layout);
        EditText etHost = new EditText(this); etHost.setHint("公网服务器 IP"); layout.addView(etHost);
        EditText etPort = new EditText(this); etPort.setHint("SSH 端口"); etPort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("SSH 用户名"); layout.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("SSH 密码"); etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etPass);
        EditText etRemotePort = new EditText(this); etRemotePort.setHint("公网映射端口"); etRemotePort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etRemotePort);
        new AlertDialog.Builder(this).setTitle("自定义 SSH 穿透").setView(scrollView)
            .setPositiveButton("连接", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String host = etHost.getText().toString().trim();
                    int port = etPort.getText().toString().isEmpty() ? 22 : Integer.parseInt(etPort.getText().toString());
                    String user = etUser.getText().toString().trim();
                    String pass = etPass.getText().toString();
                    int remotePort = etRemotePort.getText().toString().isEmpty() ? 8080 : Integer.parseInt(etRemotePort.getText().toString());
                    SshTunnelManager.start(host, port, user, pass, remotePort, SettingsManager.getWebPort());
                }
            }).setNegativeButton("取消", null).show();
    }

    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }

    private void updateServiceStatus() {
        boolean running = HttpServerService.isRunning();
        statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止");
        toggleBtn.setText(running ? "停止服务" : "启动服务");
        String ip = getIpAddress();
        String text = "网页管理: http://" + ip + ":" + SettingsManager.getWebPort();
        if (SettingsManager.isLowPowerMode()) text += "\n❄️ 极寒模式已开启";
        
        if (SettingsManager.isWebDavEnabled()) text += "\n网络邻居: http://" + ip + ":" + SettingsManager.getWebDavPort();
        if (SettingsManager.isLocalSendEnabled()) text += "\nLocalSend PIN: " + SettingsManager.getLsPin();
        
        if (FreeTunnelManager.isRunning() && !FreeTunnelManager.getPublicUrl().isEmpty()) text += "\n\n公网: " + FreeTunnelManager.getPublicUrl();
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


    private void showFileMenu(final File f) {
        final String[] actions = f.isDirectory() ? new String[]{"重命名", "删除", "属性"} : new String[]{"打开", "分享", "重命名", "删除", "属性"};
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(actions, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String action = actions[which];
                if (action.equals("打开")) openFile(f);
                else if (action.equals("分享")) shareFile(f);
                else if (action.equals("重命名")) renameFile(f);
                else if (action.equals("删除")) deleteFile(f);
                else if (action.equals("属性")) showInfo(f);
            }
        }).show();
    }

    private void openFile(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(f), getMimeType(f.getName()));
            startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show(); }
    }

    private void shareFile(File f) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getMimeType(f.getName()));
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
            startActivity(Intent.createChooser(intent, "分享文件"));
        } catch (Exception e) { Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show(); }
    }

    private void renameFile(final File f) {
        final EditText input = new EditText(this);
        input.setText(f.getName());
        new AlertDialog.Builder(this).setTitle("重命名").setView(input).setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.length() == 0) return;
                File target = new File(f.getParentFile(), name);
                if (target.exists()) { Toast.makeText(MainActivity.this, "目标已存在", Toast.LENGTH_SHORT).show(); return; }
                if (f.renameTo(target)) refresh(); else Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("取消", null).show();
    }

    private void deleteFile(final File f) {
        new AlertDialog.Builder(this).setTitle("删除").setMessage("确定删除 " + f.getName() + "？").setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                if (deleteRecursive(f)) refresh(); else Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("取消", null).show();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) if (!deleteRecursive(c)) return false;
        }
        return f.delete();
    }

    private void showInfo(File f) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        String info = "路径：" + f.getAbsolutePath() + "\n大小：" + (f.length()/1024) + " KB\n修改时间：" + sdf.format(f.lastModified());
        new AlertDialog.Builder(this).setTitle("属性").setMessage(info).setPositiveButton("确定", null).show();
    }

    private String getMimeType(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".pdf")) return "application/pdf";
        return "*/*";
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
        Collections.sort(items, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
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
