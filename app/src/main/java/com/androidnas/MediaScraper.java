package com.androidnas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class MediaScraper {

    public static JSONObject scrape(String fileName) {
        try {
            // 1. 解析文件名提取影片名 (如 Inception.2010.1080p.mp4 -> Inception)
            String cleanName = fileName.replaceAll("(?i)\\.(mp4|mkv|avi|rmvb|webm)$", "");
            Pattern p = Pattern.compile("(?i)^(.*?)[\\.\\s_-]+(19\\d{2}|20\\d{2})");
            Matcher m = p.matcher(cleanName);
            
            String title = cleanName.replaceAll("[._-]", " ");
            String year = "";
            if (m.find()) {
                title = m.group(1).replaceAll("[._-]", " ").trim();
                year = m.group(2);
            }

            String encodedTitle = URLEncoder.encode(title, "UTF-8");
            
            // 2. 调用免费聚合 API (无需注册，直接查)
            String urlStr = "https://api.vvhan.com/api/video/movie?id=" + encodedTitle;
            
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            // 伪装 UA 防拦截
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resJson = new JSONObject(sb.toString());
                if (resJson.optBoolean("success", false)) {
                    JSONObject info = resJson.getJSONObject("info");
                    
                    JSONObject result = new JSONObject();
                    result.put("title", info.optString("title", title));
                    result.put("overview", info.optString("intro", "暂无简介"));
                    result.put("rating", info.optString("score", "0.0"));
                    
                    String poster = info.optString("img", "");
                    if (!poster.isEmpty() && !poster.equals("null")) {
                        result.put("poster", poster);
                    } else {
                        result.put("poster", "");
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
