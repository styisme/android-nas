package com.androidnas;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;

public class DatabaseManager extends SQLiteOpenHelper {
    private static final String DB_NAME = "nas_core.db";
    private static final int DB_VERSION = 1;
    private static DatabaseManager instance;

    public static void init(Context context) {
        if (instance == null) instance = new DatabaseManager(context.getApplicationContext());
    }

    public static DatabaseManager getInstance() { return instance; }

    private DatabaseManager(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, role INTEGER, root_dir TEXT)");
        db.execSQL("CREATE TABLE file_hashes (hash TEXT PRIMARY KEY, path TEXT)");
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('admin', 'admin', 1, '')");
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('guest', 'guest', 2, 'Public')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public int verifyUser(String user, String pass) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT role FROM users WHERE username=? AND password=?", new String[]{user, pass});
        if (c.moveToFirst()) {
            int role = c.getInt(0);
            c.close();
            return role;
        }
        c.close();
        return 0;
    }

    public String getUserRoot(String user) {
        if (user == null) return "";
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT root_dir FROM users WHERE username=?", new String[]{user});
        String root = "";
        if (c.moveToFirst()) root = c.getString(0);
        c.close();
        return root.replaceAll("^/+", "");
    }

    public void saveHash(String hash, String path) {
        if (hash == null || path == null) return;
        ContentValues cv = new ContentValues();
        cv.put("hash", hash);
        cv.put("path", path);
        getWritableDatabase().insertWithOnConflict("file_hashes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getPathByHash(String hash, String userRoot) {
        if (hash == null) return null;
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT path FROM file_hashes WHERE hash=?";
        String[] args;
        // 核心修复：admin (userRoot="") 也要限定在自己的根目录下(通常是/)，不能随意跨用户秒传
        if (userRoot == null || userRoot.isEmpty()) {
            // admin 的路径通常以 / 开头且不包含特定 User 目录，为了防止匹配到 guest，
            // 这里简单处理：要求路径不以 /Public/ 开头。
            query += " AND path NOT LIKE '/Public/%'";
            args = new String[]{hash};
        } else {
            query += " AND path LIKE ?";
            args = new String[]{hash, "%/" + userRoot + "/%"};
        }
        Cursor c = db.rawQuery(query, args);
        String path = null;
        if (c.moveToFirst()) path = c.getString(0);
        c.close();
        return path;
    }

    public boolean addUser(String user, String pass, int role, String root) {
        try {
            root = root.replaceAll("^/+", "");
            getWritableDatabase().execSQL("INSERT INTO users (username, password, role, root_dir) VALUES (?, ?, ?, ?)", new Object[]{user, pass, role, root});
            return true;
        } catch (Exception e) { return false; }
    }

    public void changePassword(String user, String newPass) {
        ContentValues cv = new ContentValues();
        cv.put("password", newPass);
        getWritableDatabase().update("users", cv, "username=?", new String[]{user});
    }

    public String getAllUsersJson() {
        Cursor c = getReadableDatabase().rawQuery("SELECT id, username, role, root_dir FROM users", null);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        while (c.moveToNext()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":").append(c.getInt(0))
              .append(",\"user\":\"").append(esc(c.getString(1))).append("\"")
              .append(",\"role\":").append(c.getInt(2))
              .append(",\"root\":\"").append(esc(c.getString(3))).append("\"}");
            first = false;
        }
        c.close();
        sb.append("]");
        return sb.toString();
    }
    
    private String esc(String s) { 
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
