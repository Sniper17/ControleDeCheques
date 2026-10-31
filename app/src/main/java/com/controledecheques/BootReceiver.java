package com.controledecheques;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
    static final String PREFS = "cheque_reminders";
    static final String KEY = "items";

    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            "android.intent.action.MY_PACKAGE_REPLACED".equals(intent.getAction())) {
            rescheduleAll(context);
        }
    }

    static void rescheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                schedule(context, item.optString("id"), item.optString("date"), item.optString("title"));
            }
        } catch (Exception ignored) { }
    }

    static void schedule(Context context, String id, String date, String title) {
        if (id == null || id.isEmpty() || date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return;
        try {
            String[] p = date.split("-");
            Calendar when = Calendar.getInstance();
            when.clear();
            when.set(Integer.parseInt(p[0]), Integer.parseInt(p[1])-1, Integer.parseInt(p[2]), 9, 0, 0);
            long trigger = when.getTimeInMillis();
            if (trigger <= System.currentTimeMillis()) return;
            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction("com.controledecheques.REMINDER");
            intent.putExtra("title", title);
            intent.putExtra("notificationId", id.hashCode());
            PendingIntent pi = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (Exception ignored) { }
    }
}
