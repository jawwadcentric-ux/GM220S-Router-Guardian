package com.metawebdesigner.gm220rebooter;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public final class GuardianNotifications {
    private GuardianNotifications() {}
    public static void show(Context c, String title, String message) {
        if (!new SecurePrefs(c).raw().getBoolean("notifications", true)) return;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        String channel = "guardian_automation";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "Automatic restart results", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 90, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        notification.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle(title).setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(open).setAutoCancel(true);
        manager.notify(220, notification.build());
    }
}
