package com.androidnas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    private TextView ipView;
    private CheckBox cbAuth;
    private String currentIp = "127.0.0.1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        AuthManager.init(this);
        ipView = (TextView) findViewById(R.id.ipView);
        cbAuth = (CheckBox) findViewById(R.id.cbAuth);
        
        SharedPreferences sp = getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        cbAuth.setChecked(sp.getBoolean("auth_enabled", true));
        cbAuth.setOnCheckedChangeListener((v, isChecked) -> {
            AuthManager.setAuthEnabled(this, isChecked);
            if (HttpServerService.isRunning()) {
                stopService(new Intent(this, HttpServerService.class));
                new android.os.Handler().postDelayed(() -> startService(new Intent(MainActivity.this, HttpServerService.class)), 500);
            }
        });

        currentIp = getIpAddress();
        updateServiceStatus();
        if (!HttpServerService.isRunning()) startService(new Intent(this, HttpServerService.class));
    }

    private void updateServiceStatus() {
        String text = "网页管理: http://" + currentIp + ":8080\n网络邻居: http://" + currentIp + ":8081";
        if (cbAuth.isChecked()) text += "\n管理员: " + AuthManager.getAuthInfo() + "\n访客: guest / guest (仅限Public目录)";
        ipView.setText(text);
    }

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return "127.0.0.1";
            return String.format(Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Throwable t) { return "127.0.0.1"; }
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { 
        menu.add(0, 1, 0, "连接二维码"); 
        menu.add(0, 2, 0, "运行日志");
        return true; 
    }
    
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            showQrCodeDialog();
            return true;
        } else if (item.getItemId() == 2) {
            new AlertDialog.Builder(this)
                .setTitle("最近访问日志")
                .setMessage(LogManager.getAllLogs())
                .setPositiveButton("关闭", null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showQrCodeDialog() {
        String url = "http://" + currentIp + ":8080";
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(url, BarcodeFormat.QR_CODE, 400, 400, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bitmap);
            iv.setPadding(32, 32, 32, 32);

            new AlertDialog.Builder(this)
                .setTitle("扫码连接")
                .setView(iv)
                .setPositiveButton("关闭", null)
                .show();
        } catch (WriterException e) {
            Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show();
        }
    }
}
