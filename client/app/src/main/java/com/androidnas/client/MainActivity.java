package com.androidnas.client;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private List<NasDevice> devices = new ArrayList<>();
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
        fab.setOnClickListener(v -> showAddDeviceDialog());
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
        
        rgType.addView(rbAndroid); 
        rgType.addView(rbWebDav);
        rgType.addView(rbFtp);
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
                
                devices.add(new NasDevice(name, url, type, user, pass));
                saveDevices();
                adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 4, 0, "发送配置到电视");
        menu.add(0, 1, 0, "局域网剪贴板");
        menu.add(0, 2, 0, "设置");
        menu.add(0, 3, 0, "关于");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 4) {
            sendConfigToTv();
            return true;
        } else if (item.getItemId() == 1) {
            // 剪贴板逻辑保持原样
            return true;
        } else if (item.getItemId() == 3) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("关于 NAS 客户端")
                .setMessage("版本: v3.1.0 旗舰版\n\n支持多设备管理、WebDAV、FTP、相册备份。")
                .setPositiveButton("确定", null)
                .show();
            return true;
        } else if (item.getItemId() == 2) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void sendConfigToTv() {
        if (devices.isEmpty()) {
            Toast.makeText(this, "请先添加一个自家 NAS", Toast.LENGTH_SHORT).show();
            return;
        }
        final NasDevice targetDevice = devices.get(0);
        if (targetDevice.type != NasDevice.TYPE_ANDROID) {
            Toast.makeText(this, "只能发送自家 NAS 的配置", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在寻找电视...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tvIp = SyncHelper.discoverTv();
            if (tvIp == null) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "未找到电视，请确保电视端 App 已打开并在同一 WiFi", Toast.LENGTH_LONG).show());
                return;
            }
            
            boolean success = SyncHelper.sendConfig(tvIp, targetDevice.getBaseUrl(), targetDevice.username, targetDevice.password);
            if (success) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "配置已下发，电视端已自动连接！", Toast.LENGTH_LONG).show());
            } else {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "配置发送失败", Toast.LENGTH_SHORT).show());
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
            
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                    intent.putExtra("url", d.getBaseUrl());
                    intent.putExtra("type", d.type);
                    intent.putExtra("username", d.username);
                    intent.putExtra("password", d.password);
                    startActivity(intent);
                }
            });
            
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    devices.remove(position);
                    saveDevices();
                    adapter.notifyDataSetChanged();
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
}
