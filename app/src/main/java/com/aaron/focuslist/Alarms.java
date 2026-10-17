package com.aaron.focuslist;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

/** Schedules the end-of-period alarm with the Android system so it fires even when the app is closed. */
final class Alarms {
    /** Focus ends with hens clucking; a break ends with the rooster. One notification channel per sound. */
    static final String CH_CLUCKS = "clucks_v1";
    static final String CH_ROOSTER = "rooster_v1";
    private static final String PREFS = "alarm";

    private Alarms() {}

    static void schedule(Context ctx, long atMillis, String title, String body, String kind) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("at", atMillis).putString("title", title).putString("body", body).putString("kind", kind).apply();
        arm(ctx, atMillis, title, body, kind);
    }

    static void cancel(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(firePending(ctx, "", "", ""));
    }

    /** Re-arms a pending alarm after a reboot. */
    static void restore(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = p.getLong("at", 0);
        if (at <= 0) return;
        arm(ctx, Math.max(at, System.currentTimeMillis() + 1000),
                p.getString("title", "Focus List"), p.getString("body", "Time's up."), p.getString("kind", "rooster"));
    }

    static void clearStored(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static void arm(Context ctx, long atMillis, String title, String body, String kind) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent fire = firePending(ctx, title, body, kind);
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

    private static PendingIntent firePending(Context ctx, String title, String body, String kind) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .putExtra("title", title).putExtra("body", body).putExtra("kind", kind);
        return PendingIntent.getBroadcast(ctx, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static String channelFor(String kind) {
        return "clucks".equals(kind) ? CH_CLUCKS : CH_ROOSTER;
    }

    static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel("period_end") != null) nm.deleteNotificationChannel("period_end");
        make(ctx, nm, CH_CLUCKS, "Focus done (hens)", "Clucks when a focus period ends", R.raw.clucks);
        make(ctx, nm, CH_ROOSTER, "Break over (rooster)", "Crows when a break ends", R.raw.rooster);
    }

    private static void make(Context ctx, NotificationManager nm, String id, String name, String desc, int raw) {
        if (nm.getNotificationChannel(id) != null) return;
        NotificationChannel ch = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(desc);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 800});
        Uri sound = Uri.parse("android.resource://" + ctx.getPackageName() + "/" + raw);
        ch.setSound(sound, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        nm.createNotificationChannel(ch);
    }
}
