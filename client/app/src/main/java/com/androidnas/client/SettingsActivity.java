package com.androidnas.client;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences sp;
    private TextInputEditText etPort, etTimeout, etAlias, etWebhook, etPin;
    private MaterialSwitch switchAutoScan, switchPin, switchAudit;
    private OkHttpClient client = new OkHttpClient();
    private Handler handler = new Handler(Looper.getMainLooper());
    private String baseUrl, authHeader;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        sp = getSharedPreferences("settings", Context.MODE_PRIVATE);
        MaterialToolbar toolbar = findViewById(R.id.toolbar); setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 1. 基础设置 (读取本地)
        RadioGroup g = findViewById(R.id.themeGroup);
        int t = sp.getInt("theme", 0);
        if (t == 1) g.check(R.id.radioLight); else if (t == 2) g.check(R.id.radioDark); else g.check(R.id.radioSystem);
        g.setOnCheckedChangeListener((gg, id) -> {
            int m = 0;
            if (id == R.id.radioLight) { m = 1; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); }
            else if (id == R.id.radioDark) { m = 2; AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); }
            else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            sp.edit().putInt("theme", m).apply();
        });
        etPort = findViewById(R.id.etScanPort); etTimeout = findViewById(R.id.etScanTimeout);
        switchAutoScan = findViewById(R.id.switchAutoScan);
        etPort.setText(String.valueOf(sp.getInt("scan_port", 8080)));
        etTimeout.setText(String.valueOf(sp.getInt("scan_timeout", 500)));
        if (switchAutoScan != null) {
            switchAutoScan.setChecked(sp.getBoolean("auto_scan", true));
            switchAutoScan.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean("auto_scan", c).apply());
        }

        // 2. 动态获取服务端设置 (如果存在设备)
        SharedPreferences nasSp = getSharedPreferences("nas", Context.MODE_PRIVATE);
        String devJson = nasSp.getString("devices", "");
        if (!devJson.isEmpty()) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(devJson);
                JSONObject d = arr.getJSONObject(0);
                baseUrl = d.getString("url");
                if (!baseUrl.startsWith("http")) baseUrl = "http://" + baseUrl;
                authHeader = Credentials.basic(d.getString("username"), d.getString("password"));
                loadServerSettings();
            } catch (Exception ignored) {}
        }
    }
    
    private void loadServerSettings() {
        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(baseUrl + "/api/settings").header("Authorization", authHeader).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    JSONObject s = new JSONObject(resp.body().string());
                    handler.post(() -> injectServerUI(s));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void injectServerUI(JSONObject s) {
        LinearLayout layout = findViewById(android.R.id.content);
        if (layout == null) return;
        
        TextView title = new TextView(this);
        title.setText("服务端深层控制");
        title.setTextSize(18);
        title.setPadding(0, 32, 0, 16);
        layout.addView(title);

        TextInputLayout tilAlias = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilAlias.setHint("设备名称");
        etAlias = new TextInputEditText(this); tilAlias.addView(etAlias);
        try { etAlias.setText(s.optString("alias")); } catch (Exception ignored) {}
        layout.addView(tilAlias);

        switchPin = new MaterialSwitch(this);
        switchPin.setText("🚨 敏感操作需要安全码");
        try { switchPin.setChecked(s.optBoolean("pinRequired")); } catch (Exception ignored) {}
        layout.addView(switchPin);
        
        switchAudit = new MaterialSwitch(this);
        switchAudit.setText("📝 开启安全审计日志");
        try { switchAudit.setChecked(s.optBoolean("auditEnabled")); } catch (Exception ignored) {}
        layout.addView(switchAudit);

        TextInputLayout tilWebhook = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilWebhook.setHint("Webhook 地址");
        etWebhook = new TextInputEditText(this); tilWebhook.addView(etWebhook);
        try { etWebhook.setText(s.optString("webhookUrl")); } catch (Exception ignored) {}
        layout.addView(tilWebhook);

        TextInputLayout tilPin = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilPin.setHint("输入安全码以保存修改");
        etPin = new TextInputEditText(this); tilPin.addView(etPin);
        layout.addView(tilPin);

        MaterialButton btnSave = new MaterialButton(this);
        btnSave.setText("保存服务端配置");
        btnSave.setOnClickListener(v -> saveServerSettings());
        layout.addView(btnSave);

        // ☢️ 自毁区域
        TextView dangerTitle = new TextView(this);
        dangerTitle.setText("☢️ 终极自毁程序");
        dangerTitle.setTextColor(0xFFB3261E);
        dangerTitle.setTextSize(18);
        dangerTitle.setPadding(0, 48, 0, 16);
        layout.addView(dangerTitle);

        MaterialButton btnWipeApp = new MaterialButton(this);
        btnWipeApp.setText("执行自毁 (摧毁所有数据)");
        btnWipeApp.setBackgroundColor(0xFFB3261E);
        btnWipeApp.setTextColor(0xFFFFFFFF);
        btnWipeApp.setOnClickListener(v -> startWipeProcess());
        layout.addView(btnWipeApp);
    }

    private void saveServerSettings() {
        String pin = etPin.getText() != null ? etPin.getText().toString() : "";
        if (switchPin.isChecked() && pin.isEmpty()) {
            Toast.makeText(this, "请输入安全码", Toast.LENGTH_SHORT).show(); return;
        }
        
        new Thread(() -> {
            try {
                okhttp3.FormBody.Builder b = new okhttp3.FormBody.Builder()
                        .add("alias", etAlias.getText() != null ? etAlias.getText().toString() : "")
                        .add("webhookUrl", etWebhook.getText() != null ? etWebhook.getText().toString() : "")
                        .add("pinRequired", String.valueOf(switchPin.isChecked()))
                        .add("auditEnabled", String.valueOf(switchAudit.isChecked()))
                        .add("pin", pin)
                        // 补齐必填字段以免服务端报错
                        .add("webPort", "8080").add("davPort", "8081").add("ftpPort", "2121")
                        .add("webdav", "true").add("ftp", "true").add("dlna", "true").add("stream", "true")
                        .add("clipboard", "true").add("lowPower", "false").add("globalMode", "false")
                        .add("fail2ban", "true").add("ipWhitelistEnabled", "false").add("ipWhitelist", "");
                        
                Request req = new Request.Builder().url(baseUrl + "/api/settings")
                        .post(b.build()).header("Authorization", authHeader).build();
                Response resp = client.newCall(req).execute();
                handler.post(() -> {
                    if (resp.isSuccessful()) Toast.makeText(this, "服务端配置已保存", Toast.LENGTH_SHORT).show();
                    else Toast.makeText(this, "保存失败，安全码可能错误", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "网络异常", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startWipeProcess() {
        if (baseUrl == null || baseUrl.isEmpty()) {
            Toast.makeText(this, "请先添加并连接一个 NAS 设备", Toast.LENGTH_SHORT).show(); return;
        }
        
        // 第一步：输入 NAS 管理密码
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        til.setHint("请输入 NAS 管理员密码");
        TextInputEditText et = new TextInputEditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);
        LinearLayout c = new LinearLayout(this); c.setPadding(48, 24, 48, 0); c.addView(til);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ 自毁确认 (1/2)")
            .setMessage("警告：此操作将不可逆地销毁 NAS 上的所有数据！请确认操作。")
            .setView(c)
            .setPositiveButton("下一步：验证手机密码", (d, w) -> {
                final String nasPwd = et.getText() != null ? et.getText().toString() : "";
                if (nasPwd.isEmpty()) return;
                triggerDeviceCredential(nasPwd);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void triggerDeviceCredential(final String nasPwd) {
        // 第二步：拉起系统级锁屏密码/指纹验证
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardSecure()) {
            Intent intent = km.createConfirmDeviceCredentialIntent("☢️ 终极确认", "请验证手机锁屏密码以启动自毁程序");
            if (intent != null) {
                startActivityForResult(intent, 9999);
                // 把 nasPwd 暂存起来，回调时用
                this._tempNasPwd = nasPwd;
            }
        } else {
            // 如果手机没设锁屏密码，直接提示并终止
            Toast.makeText(this, "手机未设置锁屏密码，无法执行高危操作！", Toast.LENGTH_LONG).show();
        }
    }

    private String _tempNasPwd = "";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9999) {
            if (resultCode == RESULT_OK) {
                // 手机密码验证成功
                doWipe(_tempNasPwd);
            } else {
                Toast.makeText(this, "手机密码验证失败，自毁程序已中止。", Toast.LENGTH_LONG).show();
            }
            _tempNasPwd = "";
        }
    }

    private void doWipe(final String nasPwd) {
        Toast.makeText(this, "正在执行毁灭...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                okhttp3.FormBody body = new okhttp3.FormBody.Builder()
                        .add("password", nasPwd)
                        .add("root", "false") // 手机端默认不执行 Root 级别擦除，只擦外部存储
                        .build();
                Request req = new Request.Builder().url(baseUrl + "/api/system/wipe")
                        .post(body).header("Authorization", authHeader).build();
                Response resp = client.newCall(req).execute();
                handler.post(() -> {
                    if (resp.isSuccessful()) {
                        Toast.makeText(this, "☢️ 自毁程序已启动，连接即将断开...", Toast.LENGTH_LONG).show();
                        finishAffinity(); // 关闭所有页面
                    } else {
                        Toast.makeText(this, "自毁失败：NAS 密码错误", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(this, "连接已断开，设备正在自毁中。", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @Override protected void onPause() {
        super.onPause();
        try {
            if (etPort != null) {
                String s = etPort.getText() != null ? etPort.getText().toString().trim() : "";
                if (!s.isEmpty()) { int p = Integer.parseInt(s); if (p >= 1 && p <= 65535) sp.edit().putInt("scan_port", p).apply(); }
            }
            if (etTimeout != null) {
                String s = etTimeout.getText() != null ? etTimeout.getText().toString().trim() : "";
                if (!s.isEmpty()) { int t = Integer.parseInt(s); if (t > 0 && t <= 60000) sp.edit().putInt("scan_timeout", t).apply(); }
            }
        } catch (Exception ignored) {}
    }
}
