package com.androidnas.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private ListView deviceListView;
    private List<WatchDevice> devices = new ArrayList<>();
    private DeviceAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.androidnas.watch.R.layout.activity_main);

        LinearLayout rootLayout = findViewById(com.androidnas.watch.R.id.rootLayout);
        if (getResources().getConfiguration().isScreenRound()) {
            rootLayout.setPadding(40, 40, 40, 40);
        } else {
            rootLayout.setPadding(16, 16, 16, 16);
        }

        deviceListView = findViewById(com.androidnas.watch.R.id.deviceListView);
        Button btnAddDevice = findViewById(com.androidnas.watch.R.id.btnAddDevice);

        adapter = new DeviceAdapter();
        deviceListView.setAdapter(adapter);

        loadDevices();

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            WatchDevice device = devices.get(position);
            Intent intent = new Intent(MainActivity.this, FileListActivity.class);
            intent.putExtra("device", device);
            startActivity(intent);
        });

        deviceListView.setOnItemLongClickListener((parent, view, position, id) -> {
            showDeleteDeviceDialog(position);
            return true;
        });

        btnAddDevice.setOnClickListener(v -> showAddDeviceDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "设置");
        menu.add(0, 2, 0, "关于");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showSettingsDialog();
            return true;
        } else if (item.getItemId() == 2) {
            new AlertDialog.Builder(this)
                .setTitle("关于手表端")
                .setMessage("版本: v1.0.0\n\n支持多协议与局域网协同投屏。\n\n⚠️ 本项目由 AI 辅助生成。")
                .setPositiveButton("确定", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("watch_devices", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        devices.clear();
        if (!json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int type = obj.optInt("type", WatchDevice.TYPE_ANDROID);
                    devices.add(new WatchDevice(
                        obj.getString("name"), obj.getString("ip"), 
                        obj.getString("port"), obj.getString("user"), 
                        obj.getString("pass"), type
                    ));
                }
            } catch (Exception ignored) {}
        }
        adapter.notifyDataSetChanged();
    }

    private void saveDevices() {
        JSONArray arr = new JSONArray();
        try {
            for (WatchDevice d : devices) {
                JSONObject obj = new JSONObject();
                obj.put("name", d.name);
                obj.put("ip", d.ip);
                obj.put("port", d.port);
                obj.put("user", d.user);
                obj.put("pass", d.pass);
                obj.put("type", d.type);
                arr.put(obj);
            }
        } catch (Exception ignored) {}
        SharedPreferences sp = getSharedPreferences("watch_devices", Context.MODE_PRIVATE);
        sp.edit().putString("devices", arr.toString()).apply();
    }

    private void showAddDeviceDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etName = new EditText(this); etName.setHint("设备名称"); layout.addView(etName);
        EditText etIp = new EditText(this); etIp.setHint("IP (如 192.168.1.100)"); layout.addView(etIp);
        EditText etPort = new EditText(this); etPort.setHint("端口 (默认 8080)"); layout.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("账号 (默认 admin)"); layout.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("密码 (默认 admin)"); etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(etPass);

        RadioGroup rgType = new RadioGroup(this);
        RadioButton rbAndroid = new RadioButton(this); rbAndroid.setText("自家 NAS"); rbAndroid.setChecked(true); rbAndroid.setId(View.generateViewId());
        RadioButton rbWebDav = new RadioButton(this); rbWebDav.setText("WebDAV"); rbWebDav.setId(View.generateViewId());
        RadioButton rbFtp = new RadioButton(this); rbFtp.setText("FTP"); rbFtp.setId(View.generateViewId());
        rgType.addView(rbAndroid); rgType.addView(rbWebDav); rgType.addView(rbFtp);
        layout.addView(rgType);

        new AlertDialog.Builder(this)
            .setTitle("添加设备")
            .setView(layout)
            .setPositiveButton("添加", (dialog, which) -> {
                String name = etName.getText().toString().trim();
                String ip = etIp.getText().toString().trim();
                String port = etPort.getText().toString().trim();
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString().trim();
                
                if (name.isEmpty() || ip.isEmpty()) return;
                if (port.isEmpty()) port = "8080";
                if (user.isEmpty()) user = "admin";
                if (pass.isEmpty()) pass = "admin";

                int type = WatchDevice.TYPE_ANDROID;
                if (rgType.getCheckedRadioButtonId() == rbWebDav.getId()) type = WatchDevice.TYPE_WEBDAV;
                else if (rgType.getCheckedRadioButtonId() == rbFtp.getId()) type = WatchDevice.TYPE_FTP;

                devices.add(new WatchDevice(name, ip, port, user, pass, type));
                saveDevices();
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showDeleteDeviceDialog(final int position) {
        new AlertDialog.Builder(this)
            .setTitle("删除设备")
            .setMessage("确定删除 " + devices.get(position).name + " 吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                devices.remove(position);
                saveDevices();
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(new String[]{"清空所有设备"}, (dialog, which) -> {
                if (which == 0) {
                    devices.clear();
                    saveDevices();
                    adapter.notifyDataSetChanged();
                }
            }).show();
    }

    private class DeviceAdapter extends BaseAdapter {
        @Override public int getCount() { return devices.size(); }
        @Override public Object getItem(int position) { return devices.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout = new LinearLayout(MainActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(24, 16, 24, 16);
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF2B2930);
            bg.setCornerRadius(32);
            layout.setBackground(bg);

            TextView tvName = new TextView(MainActivity.this);
            tvName.setText(devices.get(position).name);
            tvName.setTextColor(0xFFD0BCFF);
            tvName.setTextSize(16);
            tvName.setGravity(android.view.Gravity.CENTER);

            TextView tvIp = new TextView(MainActivity.this);
            tvIp.setText(devices.get(position).ip);
            tvIp.setTextColor(0xFFCAC4D0);
            tvIp.setTextSize(12);
            tvIp.setGravity(android.view.Gravity.CENTER);

            layout.addView(tvName);
            layout.addView(tvIp);
            return layout;
        }
    }
}
