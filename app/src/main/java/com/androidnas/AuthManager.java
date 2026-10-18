package com.androidnas;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public class AuthManager {
    private static String username = "admin";
    private static String password = "admin";

    public static void init(Context context) {
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        username = sp.getString("username", "admin");
        password = sp.getString("password", "admin");
    }

    public static void setAuth(Context context, String user, String pass) {
        username = user;
        password = pass;
        SharedPreferences sp = context.getSharedPreferences("nas_auth", Context.MODE_PRIVATE);
        sp.edit().putString("username", user).putString("password", pass).apply();
    }

    public static String getAuthInfo() {
        return username + " / " + password;
    }

    public static boolean verify(String authHeader) {
        if (authHeader == null) return false;
        if (!authHeader.startsWith("Basic ")) return false;
        try {
            String expected = Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP);
            return authHeader.substring(6).equals(expected);
        } catch (Exception e) {
            return false;
        }
    }
}
