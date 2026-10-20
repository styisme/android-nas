package com.androidnas;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootManager {
    // 执行 Root 命令并返回结果
    public static String execCommand(String cmd) {
        StringBuilder sb = new StringBuilder();
        Process p = null;
        DataOutputStream os = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(p.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            sb.append("Error: ").append(e.getMessage());
        } finally {
            if (os != null) try { os.close(); } catch (Exception ignored) {}
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
            if (p != null) try { p.destroy(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }
}
