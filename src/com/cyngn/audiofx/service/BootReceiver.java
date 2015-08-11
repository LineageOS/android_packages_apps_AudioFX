package com.cyngn.audiofx.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.cyngn.audiofx.Constants;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        // reappply defaults on boot
        SharedPreferences prefs = context.getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);
        prefs.edit().putBoolean(Constants.SAVED_DEFAULTS, false).commit();

        context.startService(new Intent(context, AudioFxService.class));
    }
}
