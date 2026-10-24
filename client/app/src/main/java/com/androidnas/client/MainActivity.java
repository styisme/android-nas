package com.androidnas.client;

import android.Manifest;
import android.content.Context;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView; 
    private SwipeRefreshLayout swipeRefresh;
    private List<NasDevice> devices = new ArrayList<>();
    private Gson gson = new Gson(); 
    private DeviceAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 规范化的菜单 ID
    private static final int MENU_SCAN = 101;
    private static final int MENU_BACKUP = 102;
    private static final int MENU_WEB = 103;
    private static final int MENU_SEND_TV = 104;
    private static final int MENU_CLIPBOARD = 105;
    private static final int MENU_SETTINGS = 106;
    private static final int MENU_ABOUT = 107;
    private static final int MENU_PHOTOS = 108;
    private static final int MENU_WALL = 109;

    private ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) {
            devices.add(new NasDevice("扫码设备", result.getContents(), NasDevice.TYPE_ANDROID, "admin", "admin"));
            saveDevices(); adapter.notifyDataSetChanged();
        }
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.toolbar); 
        setSupportActionBar(toolbar);
        
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        FloatingActionButton fab = findViewById(R.id.fabAddDevice);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(); 
        recyclerView.setAdapter(adapter);
        
        if (swipeRefresh != null) swipeRefresh.setOnRefreshListener(() -> {
            loadDevices();
            mainHandler.postDelayed(() -> { if (swipeRefresh != null) swipeRefresh.setRefreshing(false); }, 300);
        });
        loadDevices();
        fab.setOnClickListener(v -> showAddDeviceDialog());
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        devices.clear();
        if (!json.isEmpty()) try {
            List<NasDevice> l = gson.fromJson(json, new TypeToken<List<NasDevice>>(){}.getType());
            if (l != null) devices.addAll(l);
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
        checkDefaultPassword();
    }

    private void checkDefaultPassword() {
        if (devices.isEmpty()) return;
        final NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) return;
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request req = new Request.Builder().url(d.getBaseUrl() + "/api/status")
                    .header("Authorization", Credentials.basic(d.username, d.password)).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    org.json.JSONObject obj = new org.json.JSONObject(resp.body().string());
                    if (obj.optBoolean("isDefaultPwd", false)) {
                        mainHandler.post(() -> new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("⚠️ 安全警告")
                            .setMessage("当前 NAS 正在使用默认密码，极易被攻击。请立即前往网页端【系统设置】修改密码！")
                            .setPositiveButton("知道了", null).show());
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void saveDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        sp.edit().putString("devices", gson.toJson(devices)).apply();
    }

    private void showAddDeviceDialog() {
        new MaterialAlertDialogBuilder(this).setTitle("添加设备")
            .setItems(new String[]{"手动输入 IP", "扫描二维码"}, (d, w) -> {
                if (w == 0) showManualDialog();
                else { ScanOptions o = new ScanOptions(); o.setDesiredBarcodeFormats(ScanOptions.QR_CODE); barcodeLauncher.launch(o); }
            }).show();
    }

    private void showManualDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(48, 32, 48, 16);
        sv.addView(layout, new ViewGroup.LayoutParams(-1, -2));
        TextInputLayout tilName = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("设备名称"); TextInputEditText etName = new TextInputEditText(this); tilName.addView(etName); layout.addView(tilName);
        TextInputLayout tilUrl = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUrl.setHint("地址 (如 192.168.1.100:8080)"); TextInputEditText etUrl = new TextInputEditText(this); tilUrl.addView(etUrl);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2); pp.topMargin = 24;
        layout.addView(tilUrl, pp);
        RadioGroup rg = new RadioGroup(this);
        RadioButton rbA = new RadioButton(this); rbA.setText("自家 NAS"); rbA.setId(View.generateViewId()); rbA.setChecked(true);
        RadioButton rbW = new RadioButton(this); rbW.setText("WebDAV"); rbW.setId(View.generateViewId());
        RadioButton rbF = new RadioButton(this); rbF.setText("FTP"); rbF.setId(View.generateViewId());
        rg.addView(rbA); rg.addView(rbW); rg.addView(rbF); layout.addView(rg, pp);
        TextInputLayout tilUser = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUser.setHint("账号 (默认 admin)"); TextInputEditText etUser = new TextInputEditText(this); tilUser.addView(etUser); layout.addView(tilUser, pp);
        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("密码 (默认 admin)"); TextInputEditText etPass = new TextInputEditText(this); tilPass.addView(etPass); layout.addView(tilPass, pp);
        new MaterialAlertDialogBuilder(this).setTitle("手动添加设备").setView(sv)
            .setPositiveButton("添加", (d, w) -> {
                String name = etName.getText() != null ? etName.getText().toString() : "";
                String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                String user = etUser.getText() != null ? etUser.getText().toString() : "admin";
                String pass = etPass.getText() != null ? etPass.getText().toString() : "admin";
                if (name.isEmpty() || url.isEmpty()) { Toast.makeText(this, "名称和地址不能为空", Toast.LENGTH_SHORT).show(); return; }
                if (user.isEmpty()) user = "admin"; if (pass.isEmpty()) pass = "admin";
                int type = NasDevice.TYPE_ANDROID;
                if (rg.getCheckedRadioButtonId() == rbW.getId()) type = NasDevice.TYPE_WEBDAV;
                else if (rg.getCheckedRadioButtonId() == rbF.getId()) type = NasDevice.TYPE_FTP;
                devices.add(new NasDevice(name, url, type, user, pass)); saveDevices(); adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SCAN, 0, "局域网扫描").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_BACKUP, 0, "相册自动备份");
        menu.add(0, MENU_WEB, 0, "Web 远程管理");
        menu.add(0, MENU_PHOTOS, 0, "时光相册");
        menu.add(0, MENU_WALL, 0, "影视墙");
        menu.add(0, MENU_SEND_TV, 0, "发送配置到电视");
        menu.add(0, MENU_CLIPBOARD, 0, "局域网剪贴板");
        menu.add(0, MENU_SETTINGS, 0, "设置");
        menu.add(0, MENU_ABOUT, 0, "关于");
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_SCAN) { startLanScan(); return true; }
        if (id == MENU_SETTINGS) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        if (id == MENU_ABOUT) {
            new MaterialAlertDialogBuilder(this).setTitle("关于 NAS 客户端")
                .setMessage("版本: v4.3.0\n\n支持多设备管理、WebDAV、FTP。\n\n⚠️ 大量代码由 AI 辅助生成。")
                .setPositiveButton("确定", null).show();
            return true;
        }

        // 以下功能均依赖设备列表
        if (devices.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("提示")
                .setMessage("请先在首页点击右下角 [+] 按钮添加一个 NAS 设备。")
                .setPositiveButton("知道了", null).show();
            return true;
        }

        if (id == MENU_BACKUP) { checkAndStartBackup(); return true; }
        if (id == MENU_WEB) {
            Intent i = new Intent(MainActivity.this, WebViewActivity.class);
            i.putExtra("url", devices.get(0).getBaseUrl());
            i.putExtra("title", "Web 控制台");
            startActivity(i);
            return true;
        }
        if (id == MENU_PHOTOS || id == MENU_WALL) {
            Intent i = new Intent(MainActivity.this, MediaGridActivity.class);
            i.putExtra("url", devices.get(0).getBaseUrl());
            i.putExtra("type", id == MENU_PHOTOS ? "photos" : "wall");
            i.putExtra("username", devices.get(0).username);
            i.putExtra("password", devices.get(0).password);
            startActivity(i);
            return true;
        }
        if (id == MENU_SEND_TV) { sendConfigToTv(); return true; }
        if (id == MENU_CLIPBOARD) { showClipboardDialog(); return true; }

        return super.onOptionsItemSelected(item);
    }

    private void showClipboardDialog() {
        final NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "仅支持自家 NAS", Toast.LENGTH_SHORT).show(); return; }
        
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("剪贴板内容");
        TextInputEditText et = new TextInputEditText(this);
        et.setMinLines(3);
        til.addView(et);
        
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(til);
        final String base = d.getBaseUrl();
        final String auth = Credentials.basic(d.username, d.password);
        
        new MaterialAlertDialogBuilder(this).setTitle("局域网剪贴板").setView(c)
            .setPositiveButton("保存", (dlg, w) -> new Thread(() -> { try {
                OkHttpClient c2 = new OkHttpClient();
                RequestBody b = new okhttp3.FormBody.Builder().add("content", et.getText() != null ? et.getText().toString() : "").build();
                Request r = new Request.Builder().url(base + "/api/clipboard").post(b).header("Authorization", auth).build();
                c2.newCall(r).execute().close();
                mainHandler.post(() -> Toast.makeText(this, "已同步", Toast.LENGTH_SHORT).show());
            } catch (Exception e) { mainHandler.post(() -> Toast.makeText(this, "同步失败", Toast.LENGTH_SHORT).show()); } }).start())
            .setNeutralButton("加载", (dlg, w) -> new Thread(() -> { try {
                OkHttpClient c2 = new OkHttpClient();
                Request r = new Request.Builder().url(base + "/api/clipboard").header("Authorization", auth).build();
                Response resp = c2.newCall(r).execute();
                final String t = resp.isSuccessful() && resp.body() != null ? resp.body().string() : "";
                mainHandler.post(() -> et.setText(t));
            } catch (Exception ignored) {} }).start())
            .setNegativeButton("取消", null).show();
    }

    private void sendConfigToTv() {
        final NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "只能发送自家 NAS", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "正在寻找电视...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tvIp = SyncHelper.discoverTv();
            if (tvIp == null) { mainHandler.post(() -> Toast.makeText(this, "未找到电视", Toast.LENGTH_LONG).show()); return; }
            boolean ok = SyncHelper.sendConfig(tvIp, d.getBaseUrl(), d.username, d.password);
            mainHandler.post(() -> Toast.makeText(this, ok ? "配置已下发" : "发送失败", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(getLayoutInflater().inflate(R.layout.item_device, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, final int pos) {
            final NasDevice d = devices.get(pos);
            h.tvName.setText(d.name); h.tvIp.setText(d.url);
            if (d.type == NasDevice.TYPE_ANDROID) h.tvIcon.setText("📱");
            else if (d.type == NasDevice.TYPE_WEBDAV) h.tvIcon.setText("🖥️");
            else if (d.type == NasDevice.TYPE_FTP) h.tvIcon.setText("📂");
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, FileListActivity.class);
                i.putExtra("url", d.getBaseUrl()); i.putExtra("type", d.type);
                i.putExtra("username", d.username); i.putExtra("password", d.password);
                startActivity(i);
            });
            if (h.btnDelete != null) h.btnDelete.setOnClickListener(v -> {
                devices.remove(pos); saveDevices(); adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
            });
        }
        @Override public int getItemCount() { return devices.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvIp; Button btnDelete;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon);
                tvIp = v.findViewById(R.id.tvIp); btnDelete = v.findViewById(R.id.btnDelete); }
        }
    }

    private void startLanScan() {
        Toast.makeText(this, "正在扫描局域网...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                int ipInt = wm.getConnectionInfo().getIpAddress();
                if (ipInt == 0) {
                    runOnUiThread(() -> Toast.makeText(this, "请先连接 WiFi", Toast.LENGTH_SHORT).show());
                    return;
                }
                String ipStr = String.format("%d.%d.%d.%d", (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                String subnet = ipStr.substring(0, ipStr.lastIndexOf("."));
                
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(30);
                final boolean[] found = {false};
                for (int i = 1; i <= 254; i++) {
                    final String host = subnet + "." + i;
                    pool.execute(() -> {
                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(host, 8080), 300);
                            socket.close();
                            
                            java.net.URL url = new java.net.URL("http://" + host + ":8080/api/status");
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(500);
                            if (conn.getResponseCode() == 200) {
                                synchronized (found) {
                                    if (!found[0]) {
                                        found[0] = true;
                                        runOnUiThread(() -> {
                                            devices.add(new NasDevice("扫描到的设备", host + ":8080", NasDevice.TYPE_ANDROID, "admin", "admin"));
                                            saveDevices();
                                            adapter.notifyDataSetChanged();
                                            Toast.makeText(MainActivity.this, "发现设备: " + host, Toast.LENGTH_LONG).show();
                                        });
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                }
                pool.shutdown();
                Thread.sleep(3000);
                runOnUiThread(() -> {
                    if (!found[0]) Toast.makeText(this, "未发现局域网 NAS 设备", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "扫描失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void checkAndStartBackup() {
        NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "仅支持自家 NAS", Toast.LENGTH_SHORT).show(); return; }

        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, 100);
            return;
        }
        startBackup(d);
    }

    private void startBackup(NasDevice d) {
        Toast.makeText(this, "正在扫描相册...", Toast.LENGTH_SHORT).show();
        BackupManager bm = new BackupManager(this, d.getBaseUrl(), d.username, d.password);
        bm.startBackup(new BackupManager.BackupCallback() {
            @Override public void onProgress(int current, int total, String fileName) {
                if (current % 5 == 0 || current == 1) {
                    Toast.makeText(MainActivity.this, "正在备份 (" + current + "/" + total + "): " + fileName, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onComplete(int uploaded) {
                if (uploaded == 0) Toast.makeText(MainActivity.this, "相册已是最新，无需备份", Toast.LENGTH_LONG).show();
                else Toast.makeText(MainActivity.this, "备份完成！本次上传了 " + uploaded + " 张照片", Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String msg) {
                Toast.makeText(MainActivity.this, "备份失败: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                checkAndStartBackup();
            } else {
                Toast.makeText(this, "需要相册权限才能备份", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
