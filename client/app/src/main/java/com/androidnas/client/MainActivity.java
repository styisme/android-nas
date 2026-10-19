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
            devices.add(new NasDevice("扫码设备", result.getContents(), true, "admin", "admin"));
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
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        if (!json.isEmpty()) {
            devices = gson.fromJson(json, new TypeToken<List<NasDevice>>(){}.getType());
        }
        adapter.notifyDataSetChanged();
    }

    private void saveDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        sp.edit().putString("devices", gson.toJson(devices)).apply();
    }

    private void showAddDeviceDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("添加设备")
            .setItems(new String[]{"手动输入 IP", "扫描二维码"}, (dialog, which) -> {
                if (which == 0) showManualDialog();
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
        // 设置适当的边距
        layout.setPadding(48, 32, 48, 16);
        scrollView.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 设备名称
        TextInputLayout tilName = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("设备名称 (如: 我的NAS)");
        TextInputEditText etName = new TextInputEditText(this);
        tilName.addView(etName);
        layout.addView(tilName);

        // 地址
        TextInputLayout tilUrl = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUrl.setHint("地址 (如 192.168.1.100:8080)");
        TextInputEditText etUrl = new TextInputEditText(this);
        tilUrl.addView(etUrl);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        layout.addView(tilUrl, params);

        // 账号
        TextInputLayout tilUser = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUser.setHint("服务端账号 (默认 admin)");
        TextInputEditText etUser = new TextInputEditText(this);
        tilUser.addView(etUser);
        layout.addView(tilUser, params);

        // 密码
        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("服务端密码 (默认 admin)");
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
                if (!url.startsWith("http")) url = "http://" + url;
                
                devices.add(new NasDevice(name, url, true, user, pass));
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
                    int count = 0;
                    OkHttpClient client = new OkHttpClient();
                    final String authHeader = Credentials.basic(device.username, device.password);

                    while (cursor.moveToNext() && count < 5) { // 测试传 5 张
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
                        count++;
                    }
                    cursor.close();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "相册备份完成！", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "备份失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "局域网剪贴板");
        menu.add(0, 2, 0, "设置");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showClipboardDialog();
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
            holder.tvIcon.setText("📱");
            
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(d.name)
                        .setItems(new String[]{"进入文件管理", "一键备份相册"}, (dialog, which) -> {
                            if (which == 0) {
                                Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                                intent.putExtra("url", d.getBaseUrl());
                                intent.putExtra("isAndroidNas", true);
                                intent.putExtra("username", d.username);
                                intent.putExtra("password", d.password);
                                startActivity(intent);
                            } else {
                                startPhotoBackup(d);
                            }
                        }).show();
                }
            });

            // 新增：长按编辑设备信息（解决扫码/扫描后无法修改密码的问题）
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

    // 新增：修改设备信息弹窗
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
        tilUrl.setHint("地址 (如 192.168.1.100:8080)");
        final TextInputEditText etUrl = new TextInputEditText(this);
        etUrl.setText(d.url);
        tilUrl.addView(etUrl);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 24;
        layout.addView(tilUrl, params);

        TextInputLayout tilUser = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUser.setHint("服务端账号 (默认 admin)");
        final TextInputEditText etUser = new TextInputEditText(this);
        etUser.setText(d.username);
        tilUser.addView(etUser);
        layout.addView(tilUser, params);

        TextInputLayout tilPass = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPass.setHint("服务端密码 (默认 admin)");
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
                if (!url.startsWith("http")) url = "http://" + url;

                devices.set(position, new NasDevice(name, url, true, user, pass));
                saveDevices();
                adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }

