package com.aaron.focuslist;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

/** Schedules the end-of-period alarm with the Android system so it fires even when the app is closed. */
final class Alarms {
    static final String CHANNEL = "period_end";
    private static final String PREFS = "alarm";

    private Alarms() {}

    static void schedule(Context ctx, long atMillis, String title, String body) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("at", atMillis).putString("title", title).putString("body", body).apply();
        arm(ctx, atMillis, title, body);
    }

    static void cancel(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(firePending(ctx, "", ""));
    }

    /** Re-arms a pending alarm after a reboot. */
    static void restore(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = p.getLong("at", 0);
        if (at <= 0) return;
        arm(ctx, Math.max(at, System.currentTimeMillis() + 1000),
                p.getString("title", "Focus List"), p.getString("body", "Time's up."));
    }

    static void clearStored(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void arm(Context ctx, long atMillis, String title, String body) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent fire = firePending(ctx, title, body);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) {
            // setAlarmClock is exempt from Doze and battery restrictions: the most reliable timer Android offers.
            Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent show = PendingIntent.getActivity(ctx, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(atMillis, show), fire);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fire);
        }
    }

    private static PendingIntent firePending(Context ctx, String title, String body) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .putExtra("title", title).putExtra("body", body);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Timer alerts",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Rings when a focus or break period ends");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 800});
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        ch.setBypassDnd(false);
        nm.createNotificationChannel(ch);
    }
}
