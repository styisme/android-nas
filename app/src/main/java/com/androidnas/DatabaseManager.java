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
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('admin', 'admin', 1, '/')");
        db.execSQL("INSERT INTO users (username, password, role, root_dir) VALUES ('guest', 'guest', 2, '/Public')");
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

    public boolean addUser(String user, String pass, int role, String root) {
        try {
            getWritableDatabase().execSQL("INSERT INTO users (username, password, role, root_dir) VALUES (?, ?, ?, ?)", new Object[]{user, pass, role, root});
            return true;
        } catch (Exception e) { return false; }
    }

    public String getAllUsersJson() {
        Cursor c = getReadableDatabase().rawQuery("SELECT id, username, role, root_dir FROM users", null);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        while (c.moveToNext()) {
            if (!first) sb.append(",");
            sb.append("{\"id\":").append(c.getInt(0))
              .append(",\"user\":\"").append(c.getString(1)).append("\"")
              .append(",\"role\":").append(c.getInt(2))
              .append(",\"root\":\"").append(c.getString(3)).append("\"}");
            first = false;
        }
        c.close();
        sb.append("]");
        return sb.toString();
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
