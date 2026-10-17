package com.aaron.focuslist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are wiped on reboot; this puts a running timer's alarm back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Alarms.restore(ctx);
        }
    }
}
