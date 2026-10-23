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
        // 用户表
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT, role INTEGER, root_dir TEXT)");
        // 秒传记录表
        db.execSQL("CREATE TABLE file_hashes (hash TEXT PRIMARY KEY, path TEXT)");
        // 默认管理员
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('admin', 'admin', 1, '/')");
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('guest', 'guest', 2, '/Public')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    // 校验用户并返回角色 (0:失败, 1:Admin, 2:Guest/Normal)
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
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT root_dir FROM users WHERE username=?", new String[]{user});
        String root = "/";
        if (c.moveToFirst()) root = c.getString(0);
        c.close();
        return root;
    }

    public void saveHash(String hash, String path) {
        ContentValues cv = new ContentValues();
        cv.put("hash", hash);
        cv.put("path", path);
        getWritableDatabase().insertWithOnConflict("file_hashes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getPathByHash(String hash) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT path FROM file_hashes WHERE hash=?", new String[]{hash});
        String path = null;
        if (c.moveToFirst()) path = c.getString(0);
        c.close();
        return path;
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
