package com.androidnas.client;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Credentials;

public class MediaGridActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private String baseUrl, authHeader, type;
    private List<MediaItem> items = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService pool = Executors.newFixedThreadPool(8);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_file_list);
        baseUrl = getIntent().getStringExtra("url");
        type = getIntent().getStringExtra("type"); // "photos" or "wall"
        authHeader = Credentials.basic(getIntent().getStringExtra("username"), getIntent().getStringExtra("password"));
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(type.equals("photos") ? "时光相册" : "影视墙");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.fabUpload).setVisibility(View.GONE);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MediaAdapter();
        recyclerView.setAdapter(adapter);

        loadMedia();
    }

    private void loadMedia() {
        new Thread(() -> {
            try {
                String api = type.equals("photos") ? "/api/photos" : "/api/media/wall";
                URL url = new URL(baseUrl + api);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", authHeader);
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln);
                    r.close();
                    
                    JSONArray arr = new JSONArray(sb.toString());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        MediaItem item = new MediaItem();
                        item.name = o.optString("name");
                        item.path = o.optString("path");
                        item.title = o.optString("title", item.name);
                        item.poster = o.optString("poster", "");
                        items.add(item);
                    }
                    handler.post(() -> adapter.notifyDataSetChanged());
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.VH> {
        @Override public VH onCreateViewHolder(ViewGroup p, int vt) {
            View v = getLayoutInflater().inflate(R.layout.item_grid, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            MediaItem item = items.get(pos);
            h.tvName.setText(item.title);
            h.tvIcon.setVisibility(View.GONE);
            
            String imgUrl = type.equals("photos") ? baseUrl + "/api/thumb?path=" + URLEncoder.encode(item.path) : item.poster;
            if (imgUrl.isEmpty()) return;
            
            pool.execute(() -> {
                try {
                    URL url = new URL(imgUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Authorization", authHeader);
                    InputStream is = conn.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    handler.post(() -> h.img.setImageBitmap(bmp));
                } catch (Exception ignored) {}
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvIcon; ImageView img;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvIcon = v.findViewById(R.id.tvIcon); img = v.findViewById(R.id.imgView); }
        }
    }

    private static class MediaItem {
        String name, path, title, poster;
    }
}
