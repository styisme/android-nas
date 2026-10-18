package com.androidnas;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.security.cert.Certificate;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.math.BigInteger;
import java.util.Date;
import java.security.cert.X509Certificate;
import java.io.ByteArrayInputStream;

public class HttpServerService extends Service {

    public static final int PORT = 8080;
    public static final int HTTPS_PORT = 8443;
    public static final int WEBDAV_PORT = 8081;
    public static final int WEBDAV_HTTPS_PORT = 8444;
    
    private static final int NOTIFICATION_ID = 1001;
    private static volatile boolean running = false;
    public static volatile String lastError = "";

    public static boolean isRunning() {
        return running;
    }

    private NasHttpServer serverHttp;
    private NasHttpServer serverHttps;
    private WebDavServer webDavHttp;
    private WebDavServer webDavHttps;
    
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        AuthManager.init(this);
        
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidNAS::CpuLock");
            wakeLock.acquire();
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidNAS::WifiLock");
            wifiLock.acquire();
        } catch (Exception e) { e.printStackTrace(); }

        lastError = "";
        String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();

        // 1. 启动 HTTP 普通服务
        try {
            serverHttp = new NasHttpServer(PORT, false, null);
            serverHttp.start();
        } catch (Exception e) { lastError += "HTTP启动失败: " + e.getMessage() + "\n"; }
        
        try {
            webDavHttp = new WebDavServer(rootDir);
            webDavHttp.startServer(WEBDAV_PORT, false, null);
        } catch (Exception e) { lastError += "WebDAV启动失败: " + e.getMessage() + "\n"; }

        // 2. 启动 HTTPS 加密服务
        SSLServerSocketFactory sslFactory = createSelfSignedSSL();
        if (sslFactory != null) {
            try {
                serverHttps = new NasHttpServer(HTTPS_PORT, true, sslFactory);
                serverHttps.start();
            } catch (Exception e) { lastError += "HTTPS启动失败: " + e.getMessage() + "\n"; }
            
            try {
                webDavHttps = new WebDavServer(rootDir);
                webDavHttps.startServer(WEBDAV_HTTPS_PORT, true, sslFactory);
            } catch (Exception e) { lastError += "WebDAV-HTTPS启动失败: " + e.getMessage() + "\n"; }
        } else {
            lastError += "自签证书生成失败，HTTPS服务不可用\n";
        }
        
        running = lastError.isEmpty();
    }

    // 动态生成一个自签名证书用于 HTTPS
    private SSLServerSocketFactory createSelfSignedSSL() {
        try {
            KeyStore ks = KeyStore.getInstance("BKS");
            ks.load(null, null);

            // 生成密钥对
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair keyPair = kpg.generateKeyPair();

            // 伪造证书 (为兼容低版本，这里采用反射或简化的X509构造)
            // 考虑到 Android 2.2 的限制，直接使用内存中的 BKS 库
            // 实际上在低版本中动态生成证书极其复杂，这里为了通用性，如果失败则返回 null
            // 为保证代码能在老机器上跑，我们通常需要预先打包一个证书文件。
            // 但这里为了单脚本实现，使用简易的 KeyManager。
            
            // 注：由于 Android 2.2 缺少某些算法，生成自签名证书非常容易崩溃。
            // 这里为了演示 HTTPS 开启，如果失败不影响 HTTP 主功能。
            return null; // 安全回退：如果你需要真实的 HTTPS，需要预先放入 keystore。
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private void goForeground() {
        try {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n;
            if (Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        "nas_service_channel", "NAS 后台服务", android.app.NotificationManager.IMPORTANCE_LOW);
                android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(channel);
                n = new Notification.Builder(this, "nas_service_channel")
                        .setContentTitle("Android NAS")
                        .setContentText("安全服务运行中 (Auth Enabled)")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else if (Build.VERSION.SDK_INT >= 16) {
                n = new Notification.Builder(this)
                        .setContentTitle("Android NAS")
                        .setContentText("安全服务运行中 (Auth Enabled)")
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentIntent(pi).setOngoing(true).build();
            } else {
                n = new Notification(android.R.drawable.stat_sys_download_done,
                        "Android NAS 运行中", System.currentTimeMillis());
                try {
                    java.lang.reflect.Method method = n.getClass().getMethod("setLatestEventInfo",
                            android.content.Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
                    method.invoke(n, this, "Android NAS", "安全服务运行中", pi);
                } catch (Exception e) { e.printStackTrace(); }
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        if (serverHttp != null) serverHttp.stop();
        if (serverHttps != null) serverHttps.stop();
        if (webDavHttp != null) webDavHttp.stopServer();
        if (webDavHttps != null) webDavHttps.stopServer();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        running = false;
        try { stopForeground(true); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
