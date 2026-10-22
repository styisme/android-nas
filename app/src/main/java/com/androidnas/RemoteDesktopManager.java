package com.androidnas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RemoteDesktopManager {
    private static WebSocketServer wsServer;
    private static MediaProjection mediaProjection;
    private static VirtualDisplay virtualDisplay;
    private static MediaCodec mediaCodec;
    private static boolean isStreaming = false;
    private static boolean isScreenshotMode = false;
    private static int screenWidth, screenHeight, screenDpi;
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= 17) wm.getDefaultDisplay().getRealMetrics(metrics);
        else wm.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDpi = metrics.densityDpi;

        if (wsServer != null) return;
        
        wsServer = new WebSocketServer(new InetSocketAddress(8082)) {
            @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {}
            @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                stopStream();
                stopScreenshotMode();
            }
            
            @Override public void onMessage(WebSocket conn, String message) {
                try {
                    String[] parts = message.split(",");
                    String action = parts[0];

                    // 1. 控制指令
                    if (action.equals("start_screenshot")) {
                        startScreenshotMode();
                        return;
                    } else if (action.equals("stop")) {
                        stopScreenshotMode();
                        stopStream();
                        return;
                    }

                    // 2. 触控指令
                    if (NasAccessibilityService.instance != null && Build.VERSION.SDK_INT >= 24) {
                        if (action.equals("tap")) {
                            NasAccessibilityService.instance.dispatchTap(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
                            return;
                        } else if (action.equals("swipe")) {
                            NasAccessibilityService.instance.dispatchSwipe(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4]));
                            return;
                        }
                    }

                    String cmd = "";
                    if (action.equals("tap")) cmd = "input tap " + parts[1] + " " + parts[2];
                    else if (action.equals("swipe")) cmd = "input swipe " + parts[1] + " " + parts[2] + " " + parts[3] + " " + parts[4];
                    
                    if (!cmd.isEmpty() && AdvancedKeepAliveManager.hasRoot()) {
                        RootManager.execCommand(cmd);
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onError(WebSocket conn, Exception ex) {}
            @Override public void onStart() { LogManager.add("Remote", "WebSocket 服务启动 (8082)"); }
        };
        wsServer.start();
    }

    // ===== 模式一：H264 视频推流 =====
    public static void startStream(Context context, int resultCode, Intent data) {
        if (mediaProjection != null) return;
        stopScreenshotMode();
        try {
            MediaProjectionManager pm = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = pm.getMediaProjection(resultCode, data);
            isStreaming = true;
            
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, screenWidth, screenHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();

            virtualDisplay = mediaProjection.createVirtualDisplay("RemoteScreen",
                    screenWidth, screenHeight, screenDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaCodec.createInputSurface(), null, null);

            new Thread(new Runnable() {
                @Override public void run() {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    while (isStreaming) {
                        int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        if (outIndex >= 0) {
                            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.position(bufferInfo.offset);
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                outputBuffer.get(outData);
                                broadcastBinary(outData);
                            }
                            mediaCodec.releaseOutputBuffer(outIndex, false);
                        }
                    }
                }
            }).start();
            LogManager.add("Remote", "H264 推流已启动");
        } catch (Exception e) {
            LogManager.add("Remote", "推流失败: " + e.getMessage());
        }
    }

    // ===== 模式二：Root 截图流 =====
    private static Thread screenshotThread;
    public static void startScreenshotMode() {
        if (!AdvancedKeepAliveManager.hasRoot()) {
            LogManager.add("Remote", "截图流启动失败：无 Root 权限");
            return;
        }
        if (isScreenshotMode) return;
        isScreenshotMode = true;
        stopStream(); // 确保不冲突

        screenshotThread = new Thread(() -> {
            while (isScreenshotMode) {
                try {
                    long start = System.currentTimeMillis();
                    // 使用 Root 截屏
                    Process p = Runtime.getRuntime().exec("su -c screencap -p");
                    InputStream is = p.getInputStream();
                    
                    // 转换为 Bitmap
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        // 压缩为 JPEG，质量 50，减小体积
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                        byte[] jpegData = baos.toByteArray();
                        
                        broadcastBinary(jpegData);
                        bitmap.recycle();
                    }
                    p.waitFor();
                    
                    // 控制在每秒 1-2 帧左右
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed < 800) Thread.sleep(800 - elapsed);
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        });
        screenshotThread.start();
        LogManager.add("Remote", "Root 截图流已启动");
    }

    public static void stopScreenshotMode() {
        isScreenshotMode = false;
        if (screenshotThread != null) {
            screenshotThread.interrupt();
            screenshotThread = null;
        }
    }

    private static void broadcastBinary(byte[] data) {
        if (wsServer != null && !wsServer.getConnections().isEmpty()) {
            for (WebSocket socket : wsServer.getConnections()) {
                socket.send(data);
            }
        }
    }

    public static void stopStream() {
        isStreaming = false;
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (mediaCodec != null) { mediaCodec.stop(); mediaCodec.release(); mediaCodec = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }
}
