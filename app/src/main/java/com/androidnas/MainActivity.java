package com.androidnas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    private File currentDir;
    private ListView listView;
    private TextView pathView, statusView, ipView;
    private Button toggleBtn;
    private FileAdapter adapter;
    private final List<File> items = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentDir = new File(ROOT);
        pathView = (TextView) findViewById(R.id.pathView);
        statusView = (TextView) findViewById(R.id.statusView);
        ipView = (TextView) findViewById(R.id.ipView);
        toggleBtn = (Button) findViewById(R.id.toggleBtn);
        listView = (ListView) findViewById(R.id.listView);
        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File f = items.get(position);
                if (f.isDirectory()) { currentDir = f; refresh(); }
                else showFileMenu(f);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showFileMenu(items.get(position));
                return true;
            }
        });
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (HttpServerService.isRunning()) stopService(new Intent(MainActivity.this, HttpServerService.class));
                else startService(new Intent(MainActivity.this, HttpServerService.class));
                updateServiceStatus();
            }
        });
        findViewById(R.id.upBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File parent = currentDir.getParentFile();
                if (parent != null && parent.getAbsolutePath().startsWith(ROOT)) { currentDir = parent; refresh(); }
            }
        });
        findViewById(R.id.refreshBtn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refresh(); }
        });
        if (!HttpServerService.isRunning()) startService(new Intent(this, HttpServerService.class));
    }

    @Override protected void onResume() { super.onResume(); updateServiceStatus(); refresh(); }

    private void updateServiceStatus() {
        boolean running = HttpServerService.isRunning();
        statusView.setText(running ? "服务状态：运行中" : "服务状态：已停止");
        toggleBtn.setText(running ? "停止服务" : "启动服务");
        ipView.setText("访问地址：http://" + getIpAddress() + ":" + HttpServerService.PORT);
    }

    private String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip == 0) return "<手机IP>";
            return String.format(Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Throwable t) { return "<手机IP>"; }
    }

    private void refresh() {
        if (!currentDir.exists() || !currentDir.isDirectory()) currentDir = new File(ROOT);
        pathView.setText(currentDir.getAbsolutePath().replace(ROOT, "/"));
        items.clear();
        File[] files = currentDir.listFiles();
        if (files != null) Collections.addAll(items, files);
        Collections.sort(items, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        adapter.notifyDataSetChanged();
    }

    private void showFileMenu(final File f) {
        final String[] actions = new String[]{"打开", "重命名", "删除", "属性"};
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: openFile(f); break;
                    case 1: renameFile(f); break;
                    case 2: deleteFile(f); break;
                    case 3: showInfo(f); break;
                }
            }
        }).show();
    }

    private void openFile(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.fromFile(f), getMimeType(f.getName()));
            startActivity(i);
        } catch (Exception e) { Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show(); }
    }

    private void renameFile(final File f) {
        final EditText input = new EditText(this);
        input.setText(f.getName());
        new AlertDialog.Builder(this).setTitle("重命名").setView(input).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.length() == 0) return;
                File target = new File(f.getParentFile(), name);
                if (target.exists()) { Toast.makeText(MainActivity.this, "目标已存在", Toast.LENGTH_SHORT).show(); return; }
                if (f.renameTo(target)) refresh();
                else Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("取消", null).show();
    }

    private void deleteFile(final File f) {
        new AlertDialog.Builder(this).setTitle("删除").setMessage("确定删除 " + f.getName() + "？").setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (deleteRecursive(f)) refresh();
                else Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton("取消", null).show();
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) if (!deleteRecursive(c)) return false;
        }
        return f.delete();
    }

    private void showInfo(File f) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        String info = "路径：" + f.getAbsolutePath() + "\n大小：" + formatSize(f.length()) + "\n修改时间：" + sdf.format(f.lastModified());
        new AlertDialog.Builder(this).setTitle("属性").setMessage(info).setPositiveButton("确定", null).show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { menu.add(0, 1, 0, "新建文件夹"); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            final EditText input = new EditText(this);
            new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setPositiveButton("创建", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (name.length() == 0) return;
                    File target = new File(currentDir, name);
                    if (target.mkdirs()) refresh();
                    else Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("取消", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private String formatSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        return String.format(Locale.US, "%.1f MB", n / 1024.0 / 1024.0);
    }

    private String getMimeType(String name) {
        name = name.toLowerCase(Locale.US);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".pdf")) return "application/pdf";
        return "*/*";
    }

    private class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(16, 12, 16, 12);
                holder = new ViewHolder();
                holder.name = new TextView(MainActivity.this); holder.name.setTextSize(16);
                holder.detail = new TextView(MainActivity.this); holder.detail.setTextSize(12); holder.detail.setTextColor(0xFF888888);
                layout.addView(holder.name); layout.addView(holder.detail); layout.setTag(holder);
                convertView = layout;
            } else holder = (ViewHolder) convertView.getTag();
            File f = items.get(position);
            if (f.isDirectory()) { holder.name.setText("📁 " + f.getName()); holder.detail.setText("目录"); }
            else { holder.name.setText("📄 " + f.getName()); holder.detail.setText(formatSize(f.length())); }
            return convertView;
        }
    }
    private static class ViewHolder { TextView name, detail; }
}
