package com.controledecheques;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "cheque_due_reminders";
    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        if (title == null || title.trim().isEmpty()) title = "Cheque com vencimento hoje";
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lembretes de cheques", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avisos às 9h no dia do vencimento dos cheques");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 7001, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(context.getApplicationInfo().icon)
            .setContentTitle("Controle de Cheques")
            .setContentText(title)
            .setStyle(new Notification.BigTextStyle().bigText(title + " — confira o recebimento."))
            .setAutoCancel(true).setContentIntent(content).setPriority(Notification.PRIORITY_HIGH);
        manager.notify(intent.getIntExtra("notificationId", (int)System.currentTimeMillis()), builder.build());
    }
}
