package com.androidnas;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class DatabaseManager extends SQLiteOpenHelper {
    public static boolean isDefaultPwd = true; // 用于前端提示
    private static final String DB_NAME = "nas_core.db";
    private static final int DB_VERSION = 2;
    private static DatabaseManager instance;

    public static void init(Context context) {
        if (instance == null) instance = new DatabaseManager(context.getApplicationContext());
    }

    public static DatabaseManager getInstance() { return instance; }

    private DatabaseManager(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, salt TEXT, pin TEXT, role INTEGER, root_dir TEXT)");
        db.execSQL("CREATE TABLE file_hashes (hash TEXT PRIMARY KEY, path TEXT)");
        
        String adminSalt = genSalt();
        db.execSQL("INSERT INTO users (username, password, salt, pin, role, root_dir) VALUES ('admin', ?, ?, '1234', 1, '')", 
                   new Object[]{hash("admin", adminSalt), adminSalt});
        
        String guestSalt = genSalt();
        db.execSQL("INSERT INTO users (username, password, salt, pin, role, root_dir) VALUES ('guest', ?, ?, '', 2, 'Public')", 
                   new Object[]{hash("guest", guestSalt), guestSalt});
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE users ADD COLUMN salt TEXT");
                db.execSQL("ALTER TABLE users ADD COLUMN pin TEXT");
                // 重置旧密码为加盐状态
                db.execSQL("UPDATE users SET salt='', password='admin' WHERE username='admin'");
                db.execSQL("UPDATE users SET salt='', password='guest' WHERE username='guest'");
                db.execSQL("UPDATE users SET pin='1234' WHERE username='admin'");
            } catch (Exception ignored) {}
        }
    }

    public static String genSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return bytesToHex(salt);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest((password + salt).getBytes("UTF-8")));
        } catch (Exception e) { return password; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public int verifyUser(String user, String pass) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT role, password, salt FROM users WHERE username=?", new String[]{user});
        if (c.moveToFirst()) {
            int role = c.getInt(0);
            String storedHash = c.getString(1);
            String salt = c.getString(2);
            c.close();
            if (salt == null) salt = ""; // 兼容旧版
            if (storedHash.equals(hash(pass, salt))) return role;
        } else {
            c.close();
        }
        return 0;
    }

    public boolean verifyPin(String pin) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT pin FROM users WHERE username='admin'", null);
        boolean ok = false;
        if (c.moveToFirst()) ok = pin != null && pin.equals(c.getString(0));
        c.close();
        return ok;
    }

    public void setPin(String newPin) {
        ContentValues cv = new ContentValues();
        cv.put("pin", newPin);
        getWritableDatabase().update("users", cv, "username='admin'", null);
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
        if (userRoot == null || userRoot.isEmpty()) {
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
            String salt = genSalt();
            getWritableDatabase().execSQL("INSERT INTO users (username, password, salt, pin, role, root_dir) VALUES (?, ?, ?, '', ?, ?)", 
                                          new Object[]{user, hash(pass, salt), salt, role, root});
            return true;
        } catch (Exception e) { return false; }
    }

    public void changePassword(String user, String newPass) {
        isDefaultPwd = false;
        String salt = genSalt();
        ContentValues cv = new ContentValues();
        cv.put("password", hash(newPass, salt));
        cv.put("salt", salt);
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
            return bytesToHex(digest);
        } catch (Exception e) { return ""; }
    }
}
