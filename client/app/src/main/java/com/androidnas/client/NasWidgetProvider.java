package com.androidnas.client;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class NasWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_nas);
            
            // 此处为了演示，静态显示。真实需异步请求 /api/status
            views.setTextViewText(R.id.tvWidgetDetail, "运行中 | 状态正常");
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
