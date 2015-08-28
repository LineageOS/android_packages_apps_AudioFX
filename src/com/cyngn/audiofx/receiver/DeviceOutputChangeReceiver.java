package com.cyngn.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.OutputDevice;

/**
 * When the AudioFX UI is not active, this receiver will change the bands that the service
 * will read and apply for the proper device - essentially so all logic is routed through
 * the {@link MasterConfigControl}.
 */
public class DeviceOutputChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final OutputDevice device = intent.getParcelableExtra(AudioFxService.EXTRA_DEVICE);

        // set the new device
        MasterConfigControl.getInstance(context).setCurrentDevice(device, false);

        // setting the preset won't update the service
        final Intent updateServiceIntent = new Intent(AudioFxService.ACTION_UPDATE_PREFERENCES);
        updateServiceIntent.setClass(context, AudioFxService.class);
        context.sendBroadcast(updateServiceIntent);
    }
}
