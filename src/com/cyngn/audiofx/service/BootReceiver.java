package com.cyngn.audiofx.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.cyngn.audiofx.Constants;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        final Intent service = new Intent(context, AudioFxService.class);

        SharedPreferences prefs = context.getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);

        if (prefs.contains(Constants.SAVED_DEFAULTS)) {
            // only reset if we've already set this before - a reboot.
            // we don't want to run apply defaults on the first boot, since the service
            // will do the same thing the first time.
            prefs.edit().putBoolean(Constants.SAVED_DEFAULTS, false).commit();

            service.setAction(AudioFxService.ACTION_REAPPLY_DEFAULTS);
        }
        context.startService(service);
    }
}
