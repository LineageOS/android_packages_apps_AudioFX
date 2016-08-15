package com.cyngn.audiofx.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.cyngn.audiofx.Constants;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        final Intent service = new Intent(context.getApplicationContext(), AudioFxService.class);
        context.startService(service);
    }
}
