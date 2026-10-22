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
    private RecyclerView recyclerView; private SwipeRefreshLayout swipeRefresh;
    private List<NasDevice> devices = new ArrayList<>();
    private Gson gson = new Gson(); private DeviceAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) {
            devices.add(new NasDevice("扫码设备", result.getContents(), NasDevice.TYPE_ANDROID, "admin", "admin"));
            saveDevices(); adapter.notifyDataSetChanged();
        }
    });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MaterialToolbar toolbar = findViewById(R.id.toolbar); setSupportActionBar(toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        FloatingActionButton fab = findViewById(R.id.fabAddDevice);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(); recyclerView.setAdapter(adapter);
        if (swipeRefresh != null) swipeRefresh.setOnRefreshListener(() -> {
            loadDevices();
            mainHandler.postDelayed(() -> { if (swipeRefresh != null) swipeRefresh.setRefreshing(false); }, 300);
        });
        loadDevices();
        fab.setOnClickListener(v -> showAddDeviceDialog());
        
        // 动态添加右上角的扫描按钮
        toolbar.inflateMenu(R.menu.menu_search); // 借用一下已有的菜单或者直接添加
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                startLanScan();
                return true;
            }
            return false;
        });
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
        menu.add(0, 5, 0, "相册自动备份");
        menu.add(0, 6, 0, "Web 远程管理");
        menu.add(0, 4, 0, "发送配置到电视");
        menu.add(0, 1, 0, "局域网剪贴板");
        menu.add(0, 2, 0, "设置");
        menu.add(0, 3, 0, "关于");
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 5) { checkAndStartBackup(); return true; }
        else if (item.getItemId() == 6) {
            if (devices.isEmpty()) { Toast.makeText(this, "请先添加一个设备", Toast.LENGTH_SHORT).show(); return true; }
            Intent i = new Intent(MainActivity.this, WebViewActivity.class);
            i.putExtra("url", devices.get(0).getBaseUrl());
            i.putExtra("title", "Web 控制台");
            startActivity(i);
            return true;
        }
        else if (item.getItemId() == 4) { sendConfigToTv(); return true; }
        else if (item.getItemId() == 1) { showClipboardDialog(); return true; }
        else if (item.getItemId() == 2) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        else if (item.getItemId() == 3) {
            new MaterialAlertDialogBuilder(this).setTitle("关于 NAS 客户端")
                .setMessage("版本: v3.2.0\n\n支持多设备管理、WebDAV、FTP。\n\n⚠️ 大量代码由 AI 辅助生成。")
                .setPositiveButton("确定", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClipboardDialog() {
        if (devices.isEmpty()) { Toast.makeText(this, "请先添加一个自家 NAS", Toast.LENGTH_SHORT).show(); return; }
        final NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) { Toast.makeText(this, "仅支持自家 NAS", Toast.LENGTH_SHORT).show(); return; }
        final TextInputEditText et = new TextInputEditText(this); et.setMinLines(3);
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(et);
        final String base = d.getBaseUrl();
        final String auth = Credentials.basic(d.username, d.password);
        new MaterialAlertDialogBuilder(this).setTitle("局域网剪贴板").setView(c)
            .setPositiveButton("保存", (dlg, w) -> new Thread(() -> { try {
                OkHttpClient c2 = new OkHttpClient();
                RequestBody b = new okhttp3.FormBody.Builder().add("content", et.getText().toString()).build();
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
        if (devices.isEmpty()) { Toast.makeText(this, "请先添加一个自家 NAS", Toast.LENGTH_SHORT).show(); return; }
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
                
                // 并发扫描 1-254
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(30);
                final boolean[] found = {false};
                for (int i = 1; i <= 254; i++) {
                    final String host = subnet + "." + i;
                    pool.execute(() -> {
                        try {
                            java.net.Socket socket = new java.net.Socket();
                            socket.connect(new java.net.InetSocketAddress(host, 8080), 300);
                            socket.close();
                            
                            // 连通性测试成功，发送 HTTP 请求验证是不是 NAS
                            SyncHelper.sendConfig(host, "", "", ""); // 借用这个测试
                            // 简单点，直接用请求测
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
        if (devices.isEmpty()) {
            Toast.makeText(this, "请先添加一个自家 NAS 设备", Toast.LENGTH_SHORT).show();
            return;
        }
        NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "仅支持自家 NAS", Toast.LENGTH_SHORT).show();
            return;
        }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
