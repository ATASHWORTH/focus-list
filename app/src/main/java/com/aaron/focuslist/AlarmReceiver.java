package com.aaron.focuslist;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at the end of a period and posts a high-priority notification with the hen or rooster sound. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Alarms.clearStored(ctx);
        Alarms.ensureChannels(ctx);

        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        String kind = intent.getStringExtra("kind");
        if (title == null || title.isEmpty()) title = "Focus List";
        if (body == null || body.isEmpty()) body = "Time's up.";

        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(ctx, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, Alarms.channelFor(kind))
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();

        ctx.getSystemService(NotificationManager.class).notify(1, n);
    }
}
