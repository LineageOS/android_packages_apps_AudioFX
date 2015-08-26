package com.cyngn.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.OutputDevice;


public class DeviceOutputChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final OutputDevice device = intent.getParcelableExtra(AudioFxService.EXTRA_DEVICE);

        // set the new device
        MasterConfigControl.getInstance(context).setCurrentDevice(device, false);

        // clear out the user-persisted one if they changed it in the UI
        MasterConfigControl.getInstance(context).setCurrentDevice(null, true);

        // setting the preset won't update the service
        final Intent updateServiceIntent = new Intent(AudioFxService.ACTION_UPDATE_PREFERENCES);
        updateServiceIntent.setClass(context, AudioFxService.class);
        context.sendBroadcast(updateServiceIntent);
    }
}
