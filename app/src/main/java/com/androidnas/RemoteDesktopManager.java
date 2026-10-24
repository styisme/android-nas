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
            @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
                String resource = conn.getResourceDescriptor();
                if (resource == null || !resource.contains("token=")) {
                    conn.close(1008, "Unauthorized");
                    return;
                }
                // 核心修复：真正调用 AuthManager 鉴权
                String token = resource.substring(resource.indexOf("token=") + 6);
                if (token.contains("&")) token = token.substring(0, token.indexOf("&"));
                if (AuthManager.verifyRole("Basic " + token) == 0) {
                    conn.close(1008, "Invalid token");
                }
                AuthManager.clearCurrentUser(); // 防污染线程
            }
            @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                if (wsServer.getConnections().isEmpty()) {
                    stopStream();
                    stopScreenshotMode();
                }
            }
            
            @Override public void onMessage(WebSocket conn, String message) {
                try {
                    String[] parts = message.split(",");
                    String action = parts[0];

                    if (action.equals("start_screenshot")) { startScreenshotMode(); return; }
                    else if (action.equals("stop")) { stopScreenshotMode(); stopStream(); return; }

                    if (action.equals("tap") && parts.length == 3) {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        if (x < 0 || x > screenWidth || y < 0 || y > screenHeight) return;
                        
                        if (NasAccessibilityService.instance != null && Build.VERSION.SDK_INT >= 24) {
                            NasAccessibilityService.instance.dispatchTap(x, y); return;
                        }
                        if (AdvancedKeepAliveManager.hasRoot()) {
                            RootManager.execCommand("input tap " + (int)x + " " + (int)y);
                        }
                    } else if (action.equals("swipe") && parts.length == 5) {
                        float x1 = Float.parseFloat(parts[1]), y1 = Float.parseFloat(parts[2]);
                        float x2 = Float.parseFloat(parts[3]), y2 = Float.parseFloat(parts[4]);
                        if (x1 < 0 || x1 > screenWidth || y1 < 0 || y1 > screenHeight) return;
                        if (x2 < 0 || x2 > screenWidth || y2 < 0 || y2 > screenHeight) return;
                        
                        if (NasAccessibilityService.instance != null && Build.VERSION.SDK_INT >= 24) {
                            NasAccessibilityService.instance.dispatchSwipe(x1, y1, x2, y2); return;
                        }
                        if (AdvancedKeepAliveManager.hasRoot()) {
                            RootManager.execCommand("input swipe " + (int)x1 + " " + (int)y1 + " " + (int)x2 + " " + (int)y2);
                        }
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onError(WebSocket conn, Exception ex) {}
            @Override public void onStart() { LogManager.add("Remote", "WebSocket 启动 (8082)"); }
        };
        wsServer.start();
    }
    
    // ... startStream 和 startScreenshotMode 保持原样 ...
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
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
            virtualDisplay = mediaProjection.createVirtualDisplay("Remote", screenWidth, screenHeight, screenDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaCodec.createInputSurface(), null, null);
            new Thread(new Runnable() {
                @Override public void run() {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    while (isStreaming) {
                        int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        if (outIndex >= 0) {
                            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outIndex);
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.position(bufferInfo.offset); outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                outputBuffer.get(outData); broadcastBinary(outData);
                            }
                            mediaCodec.releaseOutputBuffer(outIndex, false);
                        }
                    }
                }
            }).start();
        } catch (Exception e) {}
    }

    private static Thread screenshotThread;
    public static void startScreenshotMode() {
        if (!AdvancedKeepAliveManager.hasRoot() || isScreenshotMode) return;
        isScreenshotMode = true; stopStream(); 
        screenshotThread = new Thread(new Runnable() {
            @Override public void run() {
                while (isScreenshotMode) {
                    try {
                        Process p = Runtime.getRuntime().exec("su -c screencap -p");
                        Bitmap bitmap = BitmapFactory.decodeStream(p.getInputStream());
                        if (bitmap != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            broadcastBinary(baos.toByteArray()); bitmap.recycle();
                        }
                        p.waitFor(); Thread.sleep(500);
                    } catch (Exception e) { Thread.currentThread().interrupt(); }
                }
            }
        });
        screenshotThread.start();
    }

    public static void stopScreenshotMode() {
        isScreenshotMode = false;
        if (screenshotThread != null) { screenshotThread.interrupt(); screenshotThread = null; }
    }
    private static void broadcastBinary(byte[] data) {
        if (wsServer != null && !wsServer.getConnections().isEmpty()) {
            for (WebSocket socket : wsServer.getConnections()) socket.send(data);
        }
    }
    public static void stopStream() {
        isStreaming = false;
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (mediaCodec != null) { mediaCodec.stop(); mediaCodec.release(); mediaCodec = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }
}
