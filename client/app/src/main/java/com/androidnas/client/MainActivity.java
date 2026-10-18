package com.androidnas.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private List<DeviceConfig> devices = new ArrayList<>();
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 动态取色 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(getApplication());
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        FloatingActionButton fab = findViewById(R.id.fabAddDevice);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadDevices();

        fab.setOnClickListener(v -> showAddDeviceDialog());
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        if (!json.isEmpty()) {
            devices = gson.fromJson(json, new TypeToken<List<DeviceConfig>>(){}.getType());
        }
        updateUI();
    }

    private void updateUI() {
        if (devices.isEmpty()) {
            toolbar.setTitle("请添加 NAS 设备");
            recyclerView.setAdapter(null);
        } else {
            toolbar.setTitle(devices.get(0).name);
            toolbar.setOnClickListener(v -> showDeviceSelector());
            recyclerView.setAdapter(new FileAdapter());
        }
    }

    private void showDeviceSelector() {
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) names[i] = devices.get(i).name + " (" + devices.get(i).ip + ")";
        new MaterialAlertDialogBuilder(this)
            .setTitle("选择设备")
            .setItems(names, (dialog, which) -> {
                DeviceConfig selected = devices.get(which);
                devices.remove(which);
                devices.add(0, selected);
                saveDevices();
                updateUI();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showAddDeviceDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText etName = new EditText(this);
        etName.setHint("设备名称");
        layout.addView(etName);

        EditText etIp = new EditText(this);
        etIp.setHint("IP 地址");
        layout.addView(etIp);

        EditText etPort = new EditText(this);
        etPort.setHint("端口 (默认 8080)");
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etPort);

        new MaterialAlertDialogBuilder(this)
            .setTitle("添加设备")
            .setView(layout)
            .setPositiveButton("添加", (dialog, which) -> {
                String name = etName.getText().toString();
                String ip = etIp.getText().toString();
                String portStr = etPort.getText().toString();
                if (name.isEmpty() || ip.isEmpty()) return;
                int port = portStr.isEmpty() ? 8080 : Integer.parseInt(portStr);
                devices.add(new DeviceConfig(name, ip, port));
                saveDevices();
                updateUI();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void saveDevices() {
        SharedPreferences sp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        sp.edit().putString("devices", gson.toJson(devices)).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "关于");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("关于 NAS 客户端")
                .setMessage("版本: v1.0.0\n\n支持动态取色、MD3 及多设备管理。")
                .setPositiveButton("确定", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(getLayoutInflater().inflate(R.layout.item_file, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.tvName.setText("Demo_File.pdf");
            holder.tvIcon.setText("📄");
            holder.tvDetail.setText("1.2 MB");
        }
        @Override public int getItemCount() { return 10; }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon, tvDetail;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon); tvDetail = v.findViewById(R.id.tvDetail); }
        }
    }
}
