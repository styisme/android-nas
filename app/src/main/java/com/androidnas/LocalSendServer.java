package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;

public class LocalSendServer extends NanoHTTPD {
    private static final String SAVE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/LocalSend/";
    
    private class Session {
        String sessionId;
        long lastActiveTime;
        Map<String, String> fileTokens = new HashMap<>(); 
        Map<String, String> fileNames = new HashMap<>();  
    }
    private Map<String, Session> sessions = new ConcurrentHashMap<>();
    private Timer cleanupTimer = new Timer();

    public LocalSendServer(int port) { 
        super(port); 
        // 定时清理 5 分钟无活动的过期 Session
        cleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
                while (it.hasNext()) {
                    Session s = it.next().getValue();
                    if (now - s.lastActiveTime > 5 * 60 * 1000) {
                        it.remove();
                    }
                }
            }
        }, 60000, 60000);
    }

    @Override
    public void stop() {
        cleanupTimer.cancel();
        super.stop();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        if ("/api/localsend/v2/register".equals(uri) && Method.POST.equals(session.getMethod())) {
            String info = "{\"alias\":\"" + SettingsManager.getDeviceAlias() + "\",\"version\":\"2.2\",\"deviceModel\":\"Android\",\"deviceType\":\"desktop\",\"fingerprint\":\"" + SettingsManager.getFingerprint() + "\",\"port\":" + getListeningPort() + ",\"protocol\":\"http\",\"download\":false}";
            return newFixedLengthResponse(Response.Status.OK, "application/json", info);
        }

        if ("/api/localsend/v2/prepare-upload".equals(uri) && Method.POST.equals(session.getMethod())) {
            String reqPin = session.getParms().get("pin");
            if (reqPin == null || !reqPin.equals(SettingsManager.getLsPin())) {
                Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "PIN Required");
                res.addHeader("X-LocalSend-Pin", "true");
                return res;
            }

            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String postData = files.get("postData");
                if (postData == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No data");
                
                JSONObject json = new JSONObject(postData);
                JSONObject filesJson = json.optJSONObject("files");
                if (filesJson == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No files");

                Session s = new Session();
                s.sessionId = UUID.randomUUID().toString();
                s.lastActiveTime = System.currentTimeMillis();
                JSONObject resFiles = new JSONObject();
                
                Iterator<String> keys = filesJson.keys();
                while (keys.hasNext()) {
                    String fileId = keys.next();
                    JSONObject fileObj = filesJson.getJSONObject(fileId);
                    String fileName = fileObj.optString("fileName", "unknown_" + fileId);
                    
                    String token = UUID.randomUUID().toString();
                    s.fileTokens.put(fileId, token);
                    s.fileNames.put(fileId, fileName);
                    resFiles.put(fileId, token); 
                }
                sessions.put(s.sessionId, s);

                JSONObject resObj = new JSONObject();
                resObj.put("sessionId", s.sessionId);
                resObj.put("files", resFiles);
                return newFixedLengthResponse(Response.Status.OK, "application/json", resObj.toString());
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        if ("/api/localsend/v2/upload".equals(uri) && Method.POST.equals(session.getMethod())) {
            String sessionId = session.getParms().get("sessionId");
            String fileId = session.getParms().get("fileId");
            String token = session.getParms().get("token");

            Session s = sessions.get(sessionId);
            if (s == null || token == null || !token.equals(s.fileTokens.get(fileId))) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid Token");
            }

            s.lastActiveTime = System.currentTimeMillis();
            try {
                File dir = new File(SAVE_DIR);
                if (!dir.exists()) dir.mkdirs();
                
                String fileName = s.fileNames.get(fileId);
                if (fileName == null || fileName.isEmpty()) fileName = "LS_" + System.currentTimeMillis();
                
                // 防覆盖逻辑
                File destFile = new File(dir, fileName);
                int count = 1;
                String baseName = fileName;
                String ext = "";
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    baseName = fileName.substring(0, dotIndex);
                    ext = fileName.substring(dotIndex);
                }
                while (destFile.exists()) {
                    destFile = new File(dir, baseName + "(" + count + ")" + ext);
                    count++;
                }

                try (InputStream is = session.getInputStream();
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
                LogManager.add("LocalSend", "收到文件: " + destFile.getName());
                s.fileTokens.remove(fileId);
                s.fileNames.remove(fileId);
                if (s.fileTokens.isEmpty()) sessions.remove(sessionId);
                
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Write failed");
            }
        }

        if ("/api/localsend/v2/cancel".equals(uri)) {
            String sessionId = session.getParms().get("sessionId");
            if (sessionId != null) sessions.remove(sessionId);
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "Cancelled");
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }
}
