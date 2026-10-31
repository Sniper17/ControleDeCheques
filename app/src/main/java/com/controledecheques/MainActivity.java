package com.controledecheques;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.os.ParcelFileDescriptor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView web;
    private static final int REQ_BACKUP_FOLDER = 902;
    private static final int REQ_RESTORE_FILE = 903;
    private static final String BACKUP_PREFS = "cc_backup_prefs";
    private static final String BACKUP_TREE = "backup_tree_uri";
    private static final String BACKUP_FILE = "backup_controle.json";

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

    private void chooseBackupFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_BACKUP_FOLDER);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_RESTORE_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_BACKUP_FOLDER) {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
            getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).edit().putString(BACKUP_TREE, uri.toString()).apply();
            runJs("window.backupFolderChosen(true)");
        } else if (requestCode == REQ_RESTORE_FILE) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Arquivo indisponível");
                byte[] bytes = readAll(in);
                String json = new String(bytes, StandardCharsets.UTF_8);
                runJs("window.onNativeBackupLoaded(" + JSONObject.quote(json) + ")");
            } catch (Exception e) {
                runJs("window.nativeRestoreFailed()");
            }
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private void runJs(String script) {
        if (web != null) runOnUiThread(() -> web.evaluateJavascript(script, null));
    }

    private boolean writeBackup(String json) {
        try {
            String treeText = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).getString(BACKUP_TREE, null);
            if (treeText == null) return false;
            Uri tree = Uri.parse(treeText);
            String treeId = DocumentsContract.getTreeDocumentId(tree);
            Uri parentDocument = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId);
            Uri file = null;
            String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME };
            try (Cursor cursor = getContentResolver().query(children, projection, null, null, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    if (BACKUP_FILE.equals(cursor.getString(1))) {
                        file = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0));
                        break;
                    }
                }
            }
            if (file == null) {
                file = DocumentsContract.createDocument(getContentResolver(), parentDocument, "application/json", BACKUP_FILE);
            }
            if (file == null) return false;
            try (OutputStream out = getContentResolver().openOutputStream(file, "wt")) {
                if (out == null) return false;
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (Exception e) { return false; }
    }

    private class AndroidBridge {
        @JavascriptInterface public boolean hasBackupFolder() {
            return getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).contains(BACKUP_TREE);
        }
        @JavascriptInterface public void chooseBackupFolder() {
            runOnUiThread(() -> MainActivity.this.chooseBackupFolder());
        }
        @JavascriptInterface public void saveBackupNow(String json) {
            boolean ok = json != null && writeBackup(json);
            runJs("window.backupWriteResult(" + (ok ? "true" : "false") + ")");
        }
        @JavascriptInterface public void autoBackup(String json) {
            if (getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE).contains(BACKUP_TREE)) {
                boolean ok = json != null && writeBackup(json);
                runJs("window.backupWriteResult(" + (ok ? "true" : "false") + ")");
            }
        }
        @JavascriptInterface public void restoreBackupFile() {
            runOnUiThread(() -> MainActivity.this.chooseRestoreFile());
        }
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
