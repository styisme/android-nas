package com.androidnas.client;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<NasDevice> devices = new ArrayList<NasDevice>();
    private Gson gson = new Gson();
    private DeviceAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result.getContents() != null) {
            devices.add(new NasDevice("扫码设备", result.getContents(), NasDevice.TYPE_ANDROID, "admin", "admin"));
            saveDevices();
            adapter.notifyDataSetChanged();
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fab = findViewById(R.id.fabAddDevice);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter();
        recyclerView.setAdapter(adapter);

        loadDevices();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showAddDeviceDialog(); }
        });
        
        requestPermissions();
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ActivityCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                permissions.add("android.permission.POST_NOTIFICATIONS");
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 100);
        }
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        if (!json.isEmpty()) {
            devices = gson.fromJson(json, new TypeToken<List<NasDevice>>(){}.getType());
            if (devices != null) {
                for (NasDevice d : devices) {
                    if (d.type == 0) d.type = d.url.contains("8080") ? NasDevice.TYPE_ANDROID : NasDevice.TYPE_WEBDAV;
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void saveDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        sp.edit().putString("devices", gson.toJson(devices)).apply();
    }

    private void autoScan() {
        Toast.makeText(this, "正在扫描局域网...", Toast.LENGTH_SHORT).show();
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            String subnet = String.format("%d.%d.%d.", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff));

            new Thread(new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newFixedThreadPool(20);
                    for (int i = 1; i < 255; i++) {
                        final String targetIp = subnet + i;
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    int scanPort = getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("scan_port", 8080);
                                    java.net.URL url = new java.net.URL("http://" + targetIp + ":" + scanPort + "/api/list?path=/");
                                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                                    conn.setConnectTimeout(500);
                                    conn.setReadTimeout(500);
                                    if (conn.getResponseCode() == 200 || conn.getResponseCode() == 401) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (NasDevice d : devices) if (d.url.contains(targetIp)) return;
                                                devices.add(new NasDevice("Android NAS (" + targetIp + ")", "http://" + targetIp + ":" + scanPort, NasDevice.TYPE_ANDROID, "admin", "admin"));
                                                saveDevices();
                                                adapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                    executor.shutdown();
                }
            }).start();
        } catch (Exception e) {}
    }

    private void showAddDeviceDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("添加设备")
            .setItems(new String[]{"手动输入", "扫描局域网 NAS", "扫描二维码"}, (dialog, which) -> {
                if (which == 0) showManualDialog();
                else if (which == 1) autoScan();
                else {
                    ScanOptions options = new ScanOptions();
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
                    barcodeLauncher.launch(options);
                }
            }).show();
    }

    private void showManualDialog() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        scrollView.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputLayout tilName = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("设备名称");
        TextInputEditText etName = new TextInputEditText(this);
        tilName.addView(etName);
        layout.addView(tilName);

        TextInputLayout tilUrl = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUrl.setHint("地址 (如 192.168.1.100:8080)");
        TextInputEditText etUrl = new TextInputEditText(this);
        tilUrl.addView(etUrl);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        layout.addView(tilUrl, params);

        RadioGroup rgType = new RadioGroup(this);
        RadioButton rbAndroid = new RadioButton(this);
        rbAndroid.setText("自家 NAS (原生接口)");
        rbAndroid.setId(View.generateViewId());
        rbAndroid.setChecked(true);
        
        RadioButton rbWebDav = new RadioButton(this);
        rbWebDav.setText("WebDAV 协议");
        rbWebDav.setId(View.generateViewId());
        
        RadioButton rbFtp = new RadioButton(this);
        rbFtp.setText("FTP 协议");
        rbFtp.setId(View.generateViewId());
        
        RadioButton rbSmb = new RadioButton(this);
        rbSmb.setText("SMB (Windows 共享)");
        rbSmb.setId(View.generateViewId());
        
        rgType.addView(rbAndroid); 
        rgType.addView(rbWebDav);
        rgType.addView(rbFtp);
        rgType.addView(rbSmb);
        layout.addView(rgType, params);

        TextInputLayout tilUser = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUser.setHint("账号 (默认 admin)");
        TextInputEditText etUser = new TextInputEditText(this);
        tilUser.addView(etUser);
        layout.addView(tilUser, params);

        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("密码 (默认 admin)");
        TextInputEditText etPass = new TextInputEditText(this);
        tilPass.addView(etPass);
        layout.addView(tilPass, params);

        new MaterialAlertDialogBuilder(this)
            .setTitle("手动添加设备")
            .setView(scrollView)
            .setPositiveButton("添加", (dialog, which) -> {
                String name = etName.getText() != null ? etName.getText().toString() : "";
                String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                String user = etUser.getText() != null ? etUser.getText().toString() : "admin";
                String pass = etPass.getText() != null ? etPass.getText().toString() : "admin";
                
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(MainActivity.this, "名称和地址不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                int type = NasDevice.TYPE_ANDROID;
                if (rgType.getCheckedRadioButtonId() == rbWebDav.getId()) type = NasDevice.TYPE_WEBDAV;
                else if (rgType.getCheckedRadioButtonId() == rbFtp.getId()) type = NasDevice.TYPE_FTP;
                else if (rgType.getCheckedRadioButtonId() == rbSmb.getId()) type = NasDevice.TYPE_SMB;
                
                devices.add(new NasDevice(name, url, type, user, pass));
                saveDevices();
                adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }

    private void startPhotoBackup(final NasDevice device) {
        Toast.makeText(this, "开始扫描相册...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
                    String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
                    Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder);
                    
                    if (cursor == null) return;
                    OkHttpClient client = new OkHttpClient();
                    final String authHeader = Credentials.basic(device.username, device.password);

                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                        final String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                        
                        final InputStream is = getContentResolver().openInputStream(uri);
                        if (is == null) continue;

                        String targetUrl = device.getBaseUrl() + "/Photos/" + name;
                        RequestBody body = new RequestBody() {
                            @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                            @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                                byte[] buf = new byte[8192]; int len;
                                while ((len = is.read(buf)) > 0) sink.write(buf, 0, len);
                            }
                        };
                        Request req = new Request.Builder().url(targetUrl).put(body).header("Authorization", authHeader).build();
                        client.newCall(req).execute();
                    }
                    cursor.close();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(MainActivity.this, "相册备份完成！", Toast.LENGTH_SHORT).show(); }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { Toast.makeText(MainActivity.this, "备份失败", Toast.LENGTH_SHORT).show(); }
                    });
                }
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "局域网剪贴板");
        menu.add(0, 2, 0, "设置");
        menu.add(0, 3, 0, "关于");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showClipboardDialog();
            return true;
        } else if (item.getItemId() == 3) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("关于 NAS 客户端")
                .setMessage("版本: v3.1.0 旗舰版\n\n支持多设备管理、WebDAV、FTP、SMB、相册备份。")
                .setPositiveButton("确定", null)
                .show();
            return true;
        } else if (item.getItemId() == 2) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClipboardDialog() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "请先添加设备", Toast.LENGTH_SHORT).show();
            return;
        }
        final NasDevice d = devices.get(0);
        if (d.type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "仅支持自家 NAS 的剪贴板", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request req = new Request.Builder().url(d.getBaseUrl() + "/api/clipboard").header("Authorization", Credentials.basic(d.username, d.password)).build();
                    Response res = client.newCall(req).execute();
                    if (res.isSuccessful() && res.body() != null) {
                        final String txt = res.body().string();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                TextInputEditText et = new TextInputEditText(MainActivity.this);
                                et.setText(txt);
                                new MaterialAlertDialogBuilder(MainActivity.this)
                                    .setTitle("局域网剪贴板")
                                    .setView(et)
                                    .setPositiveButton("保存", (dialog, which) -> {
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    OkHttpClient client = new OkHttpClient();
                                                    RequestBody body = new okhttp3.FormBody.Builder().add("content", et.getText().toString()).build();
                                                    Request post = new Request.Builder().url(d.getBaseUrl() + "/api/clipboard").post(body).header("Authorization", Credentials.basic(d.username, d.password)).build();
                                                    client.newCall(post).execute();
                                                    mainHandler.post(new Runnable() {
                                                        @Override public void run() { Toast.makeText(MainActivity.this, "已同步", Toast.LENGTH_SHORT).show(); }
                                                    });
                                                } catch (Exception e) {}
                                            }
                                        }).start();
                                    }).setNegativeButton("取消", null).show();
                            }
                        });
                    }
                } catch (Exception e) {}
            }
        }).start();
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_device, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, final int position) {
            final NasDevice d = devices.get(position);
            holder.tvName.setText(d.name);
            holder.tvIp.setText(d.url);
            if (d.type == NasDevice.TYPE_ANDROID) holder.tvIcon.setText("📱");
            else if (d.type == NasDevice.TYPE_WEBDAV) holder.tvIcon.setText("🖥️");
            else if (d.type == NasDevice.TYPE_FTP) holder.tvIcon.setText("📂");
            else if (d.type == NasDevice.TYPE_SMB) holder.tvIcon.setText("🪟");
            
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (d.type == NasDevice.TYPE_ANDROID) {
                        new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(d.name)
                            .setItems(new String[]{"进入文件管理", "一键备份相册"}, (dialog, which) -> {
                                if (which == 0) {
                                    Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                                    intent.putExtra("url", d.getBaseUrl());
                                    intent.putExtra("type", d.type);
                                    intent.putExtra("username", d.username);
                                    intent.putExtra("password", d.password);
                                    startActivity(intent);
                                } else {
                                    startPhotoBackup(d);
                                }
                            }).show();
                    } else {
                        Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                        intent.putExtra("url", d.getBaseUrl());
                        intent.putExtra("type", d.type);
                        intent.putExtra("username", d.username);
                        intent.putExtra("password", d.password);
                        startActivity(intent);
                    }
                }
            });

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("设备管理")
                        .setItems(new String[]{"修改连接信息", "删除设备"}, (dialog, which) -> {
                            if (which == 0) {
                                showEditDeviceDialog(position);
                            } else {
                                devices.remove(position);
                                saveDevices();
                                adapter.notifyDataSetChanged();
                            }
                        }).show();
                    return true;
                }
            });
        }
        @Override public int getItemCount() { return devices.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvIp;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon); tvIp = v.findViewById(R.id.tvIp); }
        }
    }

    private void showEditDeviceDialog(final int position) {
        final NasDevice d = devices.get(position);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);
        scrollView.addView(layout);

        TextInputLayout tilName = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("设备名称");
        final TextInputEditText etName = new TextInputEditText(this);
        etName.setText(d.name);
        tilName.addView(etName);
        layout.addView(tilName);

        TextInputLayout tilUrl = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUrl.setHint("地址");
        final TextInputEditText etUrl = new TextInputEditText(this);
        etUrl.setText(d.url);
        tilUrl.addView(etUrl);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        layout.addView(tilUrl, params);

        TextInputLayout tilUser = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUser.setHint("账号");
        final TextInputEditText etUser = new TextInputEditText(this);
        etUser.setText(d.username);
        tilUser.addView(etUser);
        layout.addView(tilUser, params);

        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("密码");
        final TextInputEditText etPass = new TextInputEditText(this);
        etPass.setText(d.password);
        tilPass.addView(etPass);
        layout.addView(tilPass, params);

        new MaterialAlertDialogBuilder(this)
            .setTitle("修改连接信息")
            .setView(scrollView)
            .setPositiveButton("保存", (dialog, which) -> {
                String name = etName.getText() != null ? etName.getText().toString() : "";
                String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
                String user = etUser.getText() != null ? etUser.getText().toString() : "admin";
                String pass = etPass.getText() != null ? etPass.getText().toString() : "admin";
                if (name.isEmpty() || url.isEmpty()) return;

                devices.set(position, new NasDevice(name, url, d.type, user, pass));
                saveDevices();
                adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }
}
