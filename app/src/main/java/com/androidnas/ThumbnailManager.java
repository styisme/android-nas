package com.androidnas;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;

public class ThumbnailManager {
    public static File getThumbnail(File originalFile) {
        try {
            File thumbDir = new File(originalFile.getParent(), ".thumbnails");
            if (!thumbDir.exists()) thumbDir.mkdirs();
            File thumbFile = new File(thumbDir, originalFile.getName() + ".jpg");

            // 如果缩略图已存在且比原图新，直接返回
            if (thumbFile.exists() && thumbFile.lastModified() > originalFile.lastModified()) {
                return thumbFile;
            }

            // 解析原图并缩放
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalFile.getAbsolutePath(), options);
            
            int scale = 1;
            int targetSize = 300;
            while (options.outWidth / scale > targetSize && options.outHeight / scale > targetSize) {
                scale *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;
            Bitmap bitmap = BitmapFactory.decodeFile(originalFile.getAbsolutePath(), options);
            
            if (bitmap != null) {
                FileOutputStream fos = new FileOutputStream(thumbFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.close();
                bitmap.recycle();
                return thumbFile;
            }
        } catch (Exception e) {}
        return originalFile; // 失败则返回原图
    }
}
