package com.androidnas.client;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private List<NasDevice> devices = new ArrayList<>();
    private Gson gson = new Gson();
    private DeviceAdapter adapter;
    private ExecutorService executor = Executors.newFixedThreadPool(20);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        FloatingActionButton fab = findViewById(R.id.fabAddDevice);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter();
        recyclerView.setAdapter(adapter);

        loadDevices();
        swipeRefresh.setOnRefreshListener(this::autoScan);
        fab.setOnClickListener(v -> showAddDeviceDialog());
        
        if (devices.isEmpty()) autoScan();
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

    private void autoScan() {
        swipeRefresh.setRefreshing(true);
        devices.clear();
        loadDevices();

        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            String subnet = String.format("%d.%d.%d.", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff));

            new Thread(() -> {
                for (int i = 1; i < 255; i++) {
                    final String targetIp = subnet + i;
                    executor.execute(() -> {
                        try {
                            URL url = new URL("http://" + targetIp + ":8080/api/list?path=/");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(300);
                            conn.setReadTimeout(300);
                            if (conn.getResponseCode() == 200) {
                                runOnUiThread(() -> addScannedDevice(targetIp));
                            }
                        } catch (Exception ignored) {}
                    });
                }
                runOnUiThread(() -> {
                    try { Thread.sleep(800); } catch (Exception e) {}
                    swipeRefresh.setRefreshing(false);
                });
            }).start();
        } catch (Exception e) {
            swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "扫描失败，请检查 WiFi", Toast.LENGTH_SHORT).show();
        }
    }

    private void addScannedDevice(String ip) {
        for (NasDevice d : devices) if (d.url.contains(ip)) return;
        devices.add(new NasDevice("Android NAS (" + ip + ")", "http://" + ip + ":8080", true, null, null));
        saveDevices();
        adapter.notifyDataSetChanged();
    }

    private void showAddDeviceDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etName = new EditText(this);
        etName.setHint("设备名称");
        layout.addView(etName);

        EditText etUrl = new EditText(this);
        etUrl.setHint("地址 (如: 192.168.1.100:8080)");
        layout.addView(etUrl);

        RadioGroup rgType = new RadioGroup(this);
        RadioButton rbAndroid = new RadioButton(this);
        rbAndroid.setText("Android NAS 客户端");
        rbAndroid.setId(View.generateViewId());
        rbAndroid.setChecked(true);
        RadioButton rbOther = new RadioButton(this);
        rbOther.setText("外部 NAS (WebDAV 协议)");
        rbOther.setId(View.generateViewId());
        rgType.addView(rbAndroid);
        rgType.addView(rbOther);
        layout.addView(rgType);

        EditText etUser = new EditText(this);
        etUser.setHint("账号 (仅外部 NAS 需要)");
        layout.addView(etUser);
        
        EditText etPass = new EditText(this);
        etPass.setHint("密码 (仅外部 NAS 需要)");
        layout.addView(etPass);

        new MaterialAlertDialogBuilder(this)
            .setTitle("添加设备")
            .setView(layout)
            .setPositiveButton("添加", (dialog, which) -> {
                String name = etName.getText().toString();
                String url = etUrl.getText().toString().trim();
                if (name.isEmpty() || url.isEmpty()) return;
                if (!url.startsWith("http")) url = "http://" + url;
                
                boolean isAndroid = rgType.getCheckedRadioButtonId() == rbAndroid.getId();
                String user = etUser.getText().toString();
                String pass = etPass.getText().toString();
                
                devices.add(new NasDevice(name, url, isAndroid, user, pass));
                saveDevices();
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "设置");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_device, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            NasDevice d = devices.get(position);
            holder.tvName.setText(d.name);
            holder.tvIp.setText(d.url);
            holder.tvIcon.setText(d.isAndroidNas ? "📱" : "🖥️");
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                intent.putExtra("url", d.url);
                intent.putExtra("isAndroidNas", d.isAndroidNas);
                intent.putExtra("username", d.username);
                intent.putExtra("password", d.password);
                startActivity(intent);
            });

            holder.btnDelete.setOnClickListener(v -> {
                devices.remove(position);
                saveDevices();
                notifyDataSetChanged();
            });
        }
        @Override public int getItemCount() { return devices.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvIp;
            View btnDelete;
            VH(View v) { 
                super(v); 
                tvName = v.findViewById(R.id.tvName); 
                tvIcon = v.findViewById(R.id.tvIcon); 
                tvIp = v.findViewById(R.id.tvIp); 
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}
