package com.androidnas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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

    @Override protected void onCreate(Bundle savedInstanceState) {
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
            @Override public void onCheckedChanged(CompoundButton b, boolean isChecked) {
                AuthManager.setAuthEnabled(MainActivity.this, isChecked);
                if (HttpServerService.isRunning()) {
                    stopService(new Intent(MainActivity.this, HttpServerService.class));
                    new Handler().postDelayed(new Runnable() {
                        @Override public void run() { startService(new Intent(MainActivity.this, HttpServerService.class)); }
                    }, 500);
                    Toast.makeText(MainActivity.this, "账号验证已切换，服务正在重启…", Toast.LENGTH_SHORT).show();
                }
                updateServiceStatus();
            }
        });

        // 移除动态添加，直接在 XML 里排版好

        adapter = new FileAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showFileMenu(items.get(pos)); return true;
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                File f = items.get(pos);
                if (f.isDirectory()) { currentDir = f; refresh(); }
                else showFileMenu(f);
            }
        });
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (HttpServerService.isRunning()) stopService(new Intent(MainActivity.this, HttpServerService.class));
                else startService(new Intent(MainActivity.this, HttpServerService.class));
                updateServiceStatus();
            }
        });
        findViewById(R.id.upBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File p = currentDir.getParentFile();
                if (p != null && p.getAbsolutePath().startsWith(ROOT)) { currentDir = p; refresh(); }
            }
        });
        findViewById(R.id.refreshBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        if (!HttpServerService.isRunning()) {
            startService(new Intent(this, HttpServerService.class));
            checkAllFilesPermission();
            checkBatteryOptimization();
        }
    }

    private void checkAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!android.os.Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this).setTitle("全盘权限提示")
                    .setMessage("你需要开启“所有文件访问权限”才能正常读取手机存储和开启全盘模式。")
                    .setPositiveButton("去开启", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    }).setNegativeButton("暂不", null).show();
            }
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this).setTitle("后台保活核心设置")
                    .setMessage("为保证 NAS 息屏不断网，必须进行以下设置：\n\n1. 忽略电池优化 (原生 Android)\n2. 允许自启动/后台运行 (国产 ROM)\n3. 锁定后台任务卡片")
                    .setPositiveButton("去忽略电池优化", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            try {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    })
                    .setNeutralButton("去自启动管理", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { gotoAutoStart(); }
                    })
                    .setNegativeButton("暂不", null).show();
            }
        }
    }

    // 尝试跳转各大国产 ROM 的自启动管理页
    private void gotoAutoStart() {
        Intent intent = new Intent();
        String brand = Build.BRAND.toLowerCase();
        try {
            if (brand.contains("huawei") || brand.contains("honor")) {
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
            } else if (brand.contains("xiaomi") || brand.contains("redmi")) {
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity");
            } else if (brand.contains("oppo") || brand.contains("realme")) {
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity");
            } else if (brand.contains("vivo") || brand.contains("iqoo")) {
                intent.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(intent);
        } catch (Exception e) {
            try {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "新建文件夹");
        menu.add(0, 6, 0, "NAS 驾驶舱");
        menu.add(0, 3, 0, "内网穿透");
        menu.add(0, 4, 0, "运行日志");
        menu.add(0, 7, 0, "关于");
        menu.add(0, 8, 0, "高级保活 (Root/ADB)");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 1) {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setPositiveButton("创建", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String name = input.getText().toString().trim();
                    if (name.length() == 0) return;
                    if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
                        Toast.makeText(MainActivity.this, "名称含非法字符", Toast.LENGTH_SHORT).show(); return;
                    }
                    if (new File(currentDir, name).mkdirs()) refresh();
                }
            }).setNegativeButton("取消", null).show();
            return true;
        } else if (id == 6) { showControlCenter(); return true; }
        else if (id == 3) {
            if (SettingsManager.isLowPowerMode()) {
                new AlertDialog.Builder(this).setTitle("极寒模式已开启")
                    .setMessage("为保证续航，极寒模式下已禁止内网穿透。请先在控制中心关闭极寒模式。")
                    .setPositiveButton("去关闭", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { showControlCenter(); }
                    }).setNegativeButton("取消", null).show();
                return true;
            }
            showTunnelCenter(); return true;
        } else if (id == 4) {
            new AlertDialog.Builder(this).setTitle("最近日志").setMessage(LogManager.getAllLogs()).setPositiveButton("关闭", null).show();
            return true;
        } else if (id == 8) {
            showAdvancedKeepAlive();
            return true;
        } else if (id == 7) {
            new AlertDialog.Builder(this).setTitle("关于 Android NAS")
                .setMessage("版本: v3.2.0 旗舰版\n\n支持流媒体、剪贴板、内网穿透、FTP、DLNA。\n\n⚠️ 大量代码由 AI 辅助生成。")
                .setPositiveButton("确定", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static Integer parsePortSafe(String s) {
        try {
            int p = Integer.parseInt(s.trim());
            if (p < 1 || p > 65535) return null;
            return p;
        } catch (Exception e) { return null; }
    }


    private void showAdvancedKeepAlive() {
        boolean root = AdvancedKeepAliveManager.hasRoot();
        boolean adb = AdvancedKeepAliveManager.hasAdbPermission(this);
        boolean doze = AdvancedKeepAliveManager.isDozeWhitelisted(this);

        String status = "当前状态：\n";
        status += "Root 权限：" + (root ? "✅ 已获取" : "❌ 未获取") + "\n";
        status += "ADB 权限：" + (adb ? "✅ 已获取" : "❌ 未获取") + "\n";
        status += "Doze 白名单：" + (doze ? "✅ 已加入" : "❌ 未加入") + "\n\n";

        status += "【终极特权：已 Root】\n点击按钮执行神级保活：\n";
        status += "1. 写入内核 OOM -1000 免死金牌\n";
        status += "2. 强制篡改系统活跃度分桶\n";
        status += "3. 暴力冻结各厂商的省电守护进程（可能略微增加全局耗电）\n\n";

        new AlertDialog.Builder(this)
            .setTitle("系统级护法控制台")
            .setMessage(status)
            .setPositiveButton("一键免死注入", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    if (AdvancedKeepAliveManager.hasRoot()) {
                        AdvancedKeepAliveManager.applyExtremeWhitelist(MainActivity.this);
                        Toast.makeText(MainActivity.this, "OOM免死+系统服务冻结 执行成功！", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "需要 Root 权限！", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNeutralButton("生成特权模块", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    if (AdvancedKeepAliveManager.hasRoot()) {
                        AdvancedKeepAliveManager.generateMagiskModule(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Magisk模块已生成，重启手机后本App将变为系统特权应用！", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "需要 Root 权限！", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("还原系统服务", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    if (AdvancedKeepAliveManager.hasRoot()) {
                        AdvancedKeepAliveManager.restoreSystemServices();
                        Toast.makeText(MainActivity.this, "已解除厂商服务的冻结", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .show();
    }

    private void showControlCenter() {
        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        sv.addView(layout);

        EditText etAlias = new EditText(this); etAlias.setHint("设备名称"); etAlias.setText(SettingsManager.getDeviceAlias()); layout.addView(etAlias);

        // --- 分页选择器 ---
        RadioGroup tabGroup = new RadioGroup(this);
        tabGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbBasic = new RadioButton(this); rbBasic.setText("基础"); rbBasic.setId(1); rbBasic.setChecked(true);
        RadioButton rbServices = new RadioButton(this); rbServices.setText("服务"); rbServices.setId(2);
        RadioButton rbAdvanced = new RadioButton(this); rbAdvanced.setText("高级"); rbAdvanced.setId(3);
        tabGroup.addView(rbBasic); tabGroup.addView(rbServices); tabGroup.addView(rbAdvanced);
        layout.addView(tabGroup);

        // --- 基础设置组 ---
        LinearLayout groupBasic = new LinearLayout(this);
        groupBasic.setOrientation(LinearLayout.VERTICAL);
        EditText etWebPort = new EditText(this); etWebPort.setHint("Web 端口"); etWebPort.setInputType(InputType.TYPE_CLASS_NUMBER); etWebPort.setText(String.valueOf(SettingsManager.getWebPort())); groupBasic.addView(etWebPort);
        EditText etDavPort = new EditText(this); etDavPort.setHint("WebDAV 端口"); etDavPort.setInputType(InputType.TYPE_CLASS_NUMBER); etDavPort.setText(String.valueOf(SettingsManager.getWebDavPort())); groupBasic.addView(etDavPort);
        EditText etFtpPort = new EditText(this); etFtpPort.setHint("FTP 端口"); etFtpPort.setInputType(InputType.TYPE_CLASS_NUMBER); etFtpPort.setText(String.valueOf(SettingsManager.getFtpPort())); groupBasic.addView(etFtpPort);
        layout.addView(groupBasic);

        // --- 服务开关组 ---
        LinearLayout groupServices = new LinearLayout(this);
        groupServices.setOrientation(LinearLayout.VERTICAL);
        groupServices.setVisibility(View.GONE);
        CheckBox cbWebDav = new CheckBox(this); cbWebDav.setText("开启 WebDAV"); cbWebDav.setChecked(SettingsManager.isWebDavEnabled()); groupServices.addView(cbWebDav);
        CheckBox cbFtp = new CheckBox(this); cbFtp.setText("开启 FTP 服务"); cbFtp.setChecked(SettingsManager.isFtpEnabled()); groupServices.addView(cbFtp);
        CheckBox cbDlna = new CheckBox(this); cbDlna.setText("开启 DLNA 投屏"); cbDlna.setChecked(SettingsManager.isDlnaEnabled()); groupServices.addView(cbDlna);
        CheckBox cbStream = new CheckBox(this); cbStream.setText("允许网页流媒体"); cbStream.setChecked(SettingsManager.isStreamEnabled()); groupServices.addView(cbStream);
        CheckBox cbClipboard = new CheckBox(this); cbClipboard.setText("开启剪贴板"); cbClipboard.setChecked(SettingsManager.isClipboardEnabled()); groupServices.addView(cbClipboard);
        layout.addView(groupServices);

        // --- 高级设置组 ---
        LinearLayout groupAdvanced = new LinearLayout(this);
        groupAdvanced.setOrientation(LinearLayout.VERTICAL);
        groupAdvanced.setVisibility(View.GONE);
        CheckBox cbLowPower = new CheckBox(this); cbLowPower.setText("极寒省电模式"); cbLowPower.setChecked(SettingsManager.isLowPowerMode()); groupAdvanced.addView(cbLowPower);
        CheckBox cbGlobal = new CheckBox(this); cbGlobal.setText("🦅 全盘模式 (需 Root)"); cbGlobal.setChecked(SettingsManager.isGlobalMode()); groupAdvanced.addView(cbGlobal);
        CheckBox cbSilent = new CheckBox(this); cbSilent.setText("🎵 静音保活"); cbSilent.setChecked(SettingsManager.isSilentKeepAlive()); groupAdvanced.addView(cbSilent);
        CheckBox cbLs = new CheckBox(this); cbLs.setText("开启 LocalSend"); cbLs.setChecked(SettingsManager.isLocalSendEnabled()); groupAdvanced.addView(cbLs);
        EditText etPin = new EditText(this); etPin.setHint("LocalSend PIN"); etPin.setText(SettingsManager.getLsPin()); groupAdvanced.addView(etPin);
        layout.addView(groupAdvanced);

        tabGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                groupBasic.setVisibility(checkedId == 1 ? View.VISIBLE : View.GONE);
                groupServices.setVisibility(checkedId == 2 ? View.VISIBLE : View.GONE);
                groupAdvanced.setVisibility(checkedId == 3 ? View.VISIBLE : View.GONE);
            }
        });

        new AlertDialog.Builder(this).setTitle("NAS 驾驶舱").setView(sv)
            .setPositiveButton("保存并重启", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Integer webPort = parsePortSafe(etWebPort.getText().toString());
                    Integer davPort = parsePortSafe(etDavPort.getText().toString());
                    Integer ftpPort = parsePortSafe(etFtpPort.getText().toString());
                    if (webPort == null || davPort == null || ftpPort == null) {
                        Toast.makeText(MainActivity.this, "端口必须为 1-65535 的数字", Toast.LENGTH_LONG).show();
                        return;
                    }
                    SettingsManager.setDeviceAlias(etAlias.getText().toString().trim());
                    SettingsManager.setWebPort(webPort);
                    SettingsManager.setWebDavPort(davPort);
                    SettingsManager.setFtpPort(ftpPort);

                    SettingsManager.setWebDavEnabled(cbWebDav.isChecked());
                    SettingsManager.setFtpEnabled(cbFtp.isChecked());
                    SettingsManager.setDlnaEnabled(cbDlna.isChecked());
                    SettingsManager.setStreamEnabled(cbStream.isChecked());
                    SettingsManager.setClipboardEnabled(cbClipboard.isChecked());

                    SettingsManager.setLowPowerMode(cbLowPower.isChecked());
                    SettingsManager.setGlobalMode(cbGlobal.isChecked());
                    SettingsManager.setSilentKeepAlive(cbSilent.isChecked());
                    SettingsManager.setLocalSendEnabled(cbLs.isChecked());
                    SettingsManager.setLsPin(etPin.getText().toString().trim());

                    if (HttpServerService.isRunning()) {
                        stopService(new Intent(MainActivity.this, HttpServerService.class));
                        new Handler().postDelayed(new Runnable() {
                            @Override public void run() {
                                startService(new Intent(MainActivity.this, HttpServerService.class));
                            }
                        }, 500);
                    }
                    updateServiceStatus();
                }
            }).setNegativeButton("取消", null).show();
    }

    private void showTunnelCenter() {
        boolean running = FreeTunnelManager.isRunning();
        String title = running ? "内网穿透 (已连接)" : "内网穿透 (未连接)";
        List<String> opts = new ArrayList<String>();
        if (!running) opts.add("启动免费穿透");
        else { opts.add("复制当前公网链接"); opts.add("停止当前穿透"); }
        opts.add("自定义 SSH 穿透");
        new AlertDialog.Builder(this).setTitle(title)
            .setItems(opts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String c = opts.get(w);
                    if (c.contains("启动")) { FreeTunnelManager.start(SettingsManager.getWebPort(), 0); Toast.makeText(MainActivity.this, "正在连接...", Toast.LENGTH_SHORT).show(); }
                    else if (c.contains("复制")) { android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); cm.setPrimaryClip(android.content.ClipData.newPlainText("url", FreeTunnelManager.getPublicUrl())); }
                    else if (c.contains("停止")) { FreeTunnelManager.stop(); updateServiceStatus(); }
                    else if (c.contains("自定义")) { showSshConfigDialog(); }
                }
            }).setNegativeButton("关闭", null).show();
    }

    private void showSshConfigDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);
        sv.addView(layout);
        EditText etHost = new EditText(this); etHost.setHint("公网服务器 IP"); layout.addView(etHost);
        EditText etPort = new EditText(this); etPort.setHint("SSH 端口"); etPort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("SSH 用户名"); layout.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("SSH 密码"); etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etPass);
        EditText etRemotePort = new EditText(this); etRemotePort.setHint("公网映射端口"); etRemotePort.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(etRemotePort);
        new AlertDialog.Builder(this).setTitle("自定义 SSH 穿透").setView(sv)
            .setPositiveButton("连接", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String host = etHost.getText().toString().trim();
                    Integer port = parsePortSafe(etPort.getText().toString());
                    String user = etUser.getText().toString().trim();
                    String pass = etPass.getText().toString();
                    Integer rp = parsePortSafe(etRemotePort.getText().toString());
                    if (port == null) port = 22;
                    if (rp == null) rp = 8080;
                    SshTunnelManager.start(host, port, user, pass, rp, SettingsManager.getWebPort());
                }
            }).setNegativeButton("取消", null).show();
    }

    private void showFileMenu(final File f) {
        final String[] actions = f.isDirectory() ? new String[]{"重命名", "删除", "属性"} : new String[]{"删除", "属性"};
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                String a = actions[w];
                if (a.equals("重命名")) {
                    final EditText input = new EditText(MainActivity.this);
                    input.setText(f.getName());
                    new AlertDialog.Builder(MainActivity.this).setTitle("重命名").setView(input).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d2, int w2) {
                            String n = input.getText().toString().trim();
                            if (n.length() == 0) return;
                            if (n.contains("/") || n.contains("\\") || n.equals(".") || n.equals("..")) {
                                Toast.makeText(MainActivity.this, "名称含非法字符", Toast.LENGTH_SHORT).show(); return;
                            }
                            if (f.renameTo(new File(f.getParentFile(), n))) refresh();
                        }
                    }).setNegativeButton("取消", null).show();
                } else if (a.equals("删除")) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("删除").setMessage("确定删除 " + f.getName() + "？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d2, int w2) { if (deleteRecursive(f)) refresh(); }
                        }).setNegativeButton("取消", null).show();
                } else {
                    String info = "路径：" + f.getAbsolutePath() + "\n大小：" + (f.length() / 1024) + " KB";
                    new AlertDialog.Builder(MainActivity.this).setTitle("属性").setMessage(info).setPositiveButton("确定", null).show();
                }
            }
        }).show();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) if (!deleteRecursive(x)) return false; }
        return f.delete();
    }

    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }

    private void updateServiceStatus() {
        boolean running = HttpServerService.isRunning();
        statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止");
        toggleBtn.setText(running ? "停止服务" : "启动服务");
        String ip = getIpAddress();
        String text = "核心访问: http://" + ip + ":" + SettingsManager.getWebPort();
        if (SettingsManager.isLowPowerMode()) text += "\n❄️ 极寒模式运行中";
        if (FreeTunnelManager.isRunning() && !FreeTunnelManager.getPublicUrl().isEmpty()) text += "\n公网映射: " + FreeTunnelManager.getPublicUrl();
        if (!HttpServerService.lastError.isEmpty()) text += "\n⚠️ " + HttpServerService.lastError;
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
            long avail = (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
            long total = (long) stat.getBlockSize() * (long) stat.getBlockCount();
            storageView.setText("存储空间: 可用 " + (avail / 1024 / 1024) + " MB / 总计 " + (total / 1024 / 1024) + " MB");
        } catch (Throwable t) { storageView.setText(""); }
        items.clear();
        File[] files = currentDir.listFiles();
        if (files != null) Collections.addAll(items, files);
        Collections.sort(items, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        adapter.notifyDataSetChanged();
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            TextView tv;
            if (cv == null) {
                tv = new TextView(MainActivity.this);
                tv.setPadding(16, 24, 16, 24);
                tv.setTextSize(16);
                tv.setTextColor(0xFF1D1B20);
                tv.setBackgroundColor(0xFFFFFFFF);
            } else tv = (TextView) cv;
            File f = items.get(pos);
            tv.setText((f.isDirectory() ? "📁 " : "📄 ") + f.getName());
            return tv;
        }
    }
}
