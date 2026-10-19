package com.androidnas;

import android.os.Environment;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import fi.iki.elonen.NanoHTTPD;
import org.json.JSONObject;
import org.json.JSONException;

public class LocalSendServer extends NanoHTTPD {
    private static final String SAVE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/LocalSend/";
    
    private class Session {
        String sessionId;
        Map<String, String> fileTokens = new HashMap<>(); 
        Map<String, String> fileNames = new HashMap<>();  
    }
    private Map<String, Session> sessions = new ConcurrentHashMap<>();

    public LocalSendServer(int port) { super(port); }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod().name();

        if ("/api/localsend/v2/register".equals(uri) && Method.POST.equals(session.getMethod())) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", LocalSendAuth.getDeviceInfo(53317));
        }

        if ("/api/localsend/v2/prepare-upload".equals(uri) && Method.POST.equals(session.getMethod())) {
            String reqPin = session.getParms().get("pin");
            if (reqPin == null || !reqPin.equals(LocalSendAuth.getPin())) {
                Response res = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "PIN Required");
                res.addHeader("X-LocalSend-Pin", "true"); // 告知发送方必须弹窗输 PIN
                return res;
            }

            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String postData = files.get("postData");
                JSONObject json = new JSONObject(postData);
                JSONObject filesJson = json.optJSONObject("files");
                
                if (filesJson == null) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No files");

                Session s = new Session();
                s.sessionId = UUID.randomUUID().toString();
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
                
                // 单个文件接收完毕，清理 session 中对应的记录
                s.fileTokens.remove(fileId);
                s.fileNames.remove(fileId);
                
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK");
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error");
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }
}
