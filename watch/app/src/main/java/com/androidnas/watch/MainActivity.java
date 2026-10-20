package com.androidnas.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private ListView deviceListView;
    private List<WatchDevice> devices = new ArrayList<>();
    private DeviceAdapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(com.androidnas.watch.R.layout.activity_main);
        LinearLayout root = findViewById(com.androidnas.watch.R.id.rootLayout);
        // #23: isScreenRound 是 API 23+，minSdk 21 老手表上会 NoSuchMethodError
        boolean round = false;
        if (Build.VERSION.SDK_INT >= 23) {
            try { round = getResources().getConfiguration().isScreenRound(); } catch (Throwable ignored) {}
        }
        if (round) root.setPadding(40, 40, 40, 40); else root.setPadding(16, 16, 16, 16);
        deviceListView = findViewById(com.androidnas.watch.R.id.deviceListView);
        Button btnAdd = findViewById(com.androidnas.watch.R.id.btnAddDevice);
        adapter = new DeviceAdapter(); deviceListView.setAdapter(adapter);
        loadDevices();
        deviceListView.setOnItemClickListener((p, v, pos, id) -> {
            WatchDevice d = devices.get(pos);
            Intent i = new Intent(this, FileListActivity.class);
            i.putExtra("device", d); startActivity(i);
        });
        deviceListView.setOnItemLongClickListener((p, v, pos, id) -> { delDevice(pos); return true; });
        btnAdd.setOnClickListener(v -> addDevice());
    }

    @Override public boolean onCreateOptionsMenu(Menu m) { m.add(0, 1, 0, "设置"); m.add(0, 2, 0, "关于"); return true; }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this).setTitle("设置").setItems(new String[]{"清空所有设备"}, (d, w) -> {
                if (w == 0) { devices.clear(); saveDevices(); adapter.notifyDataSetChanged(); }
            }).show();
            return true;
        } else if (item.getItemId() == 2) {
            new AlertDialog.Builder(this).setTitle("关于手表端")
                .setMessage("版本: v3.2.0\n\n支持多协议与协同投屏。\n\n⚠️ 大量代码由 AI 辅助生成。")
                .setPositiveButton("确定", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadDevices() {
        SharedPreferences sp = getSharedPreferences("watch_devices", Context.MODE_PRIVATE);
        String json = sp.getString("devices", "");
        devices.clear();
        if (!json.isEmpty()) try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                devices.add(new WatchDevice(o.getString("name"), o.getString("ip"), o.getString("port"),
                    o.getString("user"), o.getString("pass"), o.optInt("type", WatchDevice.TYPE_ANDROID)));
            }
        } catch (Exception ignored) {}
        adapter.notifyDataSetChanged();
    }

    private void saveDevices() {
        JSONArray arr = new JSONArray();
        try {
            for (WatchDevice d : devices) {
                JSONObject o = new JSONObject();
                o.put("name", d.name); o.put("ip", d.ip); o.put("port", d.port);
                o.put("user", d.user); o.put("pass", d.pass); o.put("type", d.type);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        getSharedPreferences("watch_devices", Context.MODE_PRIVATE).edit().putString("devices", arr.toString()).apply();
    }

    private void addDevice() {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(32, 16, 32, 16); sv.addView(l);
        EditText etName = new EditText(this); etName.setHint("名称"); l.addView(etName);
        EditText etIp = new EditText(this); etIp.setHint("IP"); l.addView(etIp);
        EditText etPort = new EditText(this); etPort.setHint("端口 (默认 8080)"); l.addView(etPort);
        EditText etUser = new EditText(this); etUser.setHint("账号 (默认 admin)"); l.addView(etUser);
        EditText etPass = new EditText(this); etPass.setHint("密码 (默认 admin)");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); l.addView(etPass);
        RadioGroup rg = new RadioGroup(this);
        RadioButton rbA = new RadioButton(this); rbA.setText("自家 NAS"); rbA.setChecked(true); rbA.setId(View.generateViewId());
        RadioButton rbW = new RadioButton(this); rbW.setText("WebDAV"); rbW.setId(View.generateViewId());
        RadioButton rbF = new RadioButton(this); rbF.setText("FTP"); rbF.setId(View.generateViewId());
        rg.addView(rbA); rg.addView(rbW); rg.addView(rbF); l.addView(rg);
        new AlertDialog.Builder(this).setTitle("添加设备").setView(sv)
            .setPositiveButton("添加", (d, w) -> {
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
                if (rg.getCheckedRadioButtonId() == rbW.getId()) type = WatchDevice.TYPE_WEBDAV;
                else if (rg.getCheckedRadioButtonId() == rbF.getId()) type = WatchDevice.TYPE_FTP;
                devices.add(new WatchDevice(name, ip, port, user, pass, type)); saveDevices(); adapter.notifyDataSetChanged();
            }).setNegativeButton("取消", null).show();
    }

    private void delDevice(final int pos) {
        new AlertDialog.Builder(this).setTitle("删除设备").setMessage("确定删除 " + devices.get(pos).name + " 吗？")
            .setPositiveButton("删除", (d, w) -> { devices.remove(pos); saveDevices(); adapter.notifyDataSetChanged(); })
            .setNegativeButton("取消", null).show();
    }

    private class DeviceAdapter extends BaseAdapter {
        @Override public int getCount() { return devices.size(); }
        @Override public Object getItem(int p) { return devices.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout l = new LinearLayout(MainActivity.this);
            l.setOrientation(LinearLayout.VERTICAL); l.setPadding(24, 16, 24, 16);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF2B2930); bg.setCornerRadius(32); l.setBackground(bg);
            TextView tvName = new TextView(MainActivity.this);
            tvName.setText(devices.get(pos).name); tvName.setTextColor(0xFFD0BCFF); tvName.setTextSize(16);
            tvName.setGravity(android.view.Gravity.CENTER);
            TextView tvIp = new TextView(MainActivity.this);
            tvIp.setText(devices.get(pos).ip); tvIp.setTextColor(0xFFCAC4D0); tvIp.setTextSize(12);
            tvIp.setGravity(android.view.Gravity.CENTER);
            l.addView(tvName); l.addView(tvIp);
            return l;
        }
    }
}
