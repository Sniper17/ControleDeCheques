package com.controledecheques;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.AlarmManager;
import android.app.PendingIntent;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new AndroidBridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        BootReceiver.rescheduleAll(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 901);
        }
    }

    private class AndroidBridge {
        @JavascriptInterface public void shareText(String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "Controle de Cheques");
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                startActivity(Intent.createChooser(send, "Compartilhar operação"));
            });
        }

        @JavascriptInterface public void scheduleReminder(String id, String date, String title) {
            if (id == null || date == null) return;
            SharedPreferences prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
            JSONArray old;
            try { old = new JSONArray(prefs.getString(BootReceiver.KEY, "[]")); }
            catch (Exception e) { old = new JSONArray(); }
            JSONArray next = new JSONArray();
            try {
                for (int i=0; i<old.length(); i++) {
                    JSONObject item=old.getJSONObject(i);
                    if (!id.equals(item.optString("id"))) next.put(item);
                }
                JSONObject item = new JSONObject();
                item.put("id", id); item.put("date", date); item.put("title", title == null ? "Cheque com vencimento hoje" : title);
                next.put(item);
                prefs.edit().putString(BootReceiver.KEY, next.toString()).apply();
            } catch (Exception ignored) { }
            BootReceiver.schedule(MainActivity.this, id, date, title);
        }

        @JavascriptInterface public void cancelReminder(String id) {
            if (id == null) return;
            Intent intent = new Intent(MainActivity.this, ReminderReceiver.class);
            intent.setAction("com.controledecheques.REMINDER");
            PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager)getSystemService(ALARM_SERVICE);
            if (alarms != null) alarms.cancel(pi);
            SharedPreferences prefs = getSharedPreferences(BootReceiver.PREFS, MODE_PRIVATE);
            try {
                JSONArray old = new JSONArray(prefs.getString(BootReceiver.KEY, "[]"));
                JSONArray next = new JSONArray();
                for (int i=0; i<old.length(); i++) {
                    JSONObject item=old.getJSONObject(i);
                    if (!id.equals(item.optString("id"))) next.put(item);
                }
                prefs.edit().putString(BootReceiver.KEY, next.toString()).apply();
            } catch (Exception ignored) { }
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
