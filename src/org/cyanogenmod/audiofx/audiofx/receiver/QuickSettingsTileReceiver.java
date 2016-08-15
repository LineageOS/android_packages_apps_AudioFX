package com.cyngn.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.AudioFxService;

/**
 * Created by roman on 1/13/16.
 */
public class QuickSettingsTileReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "QSTileReceiver";

    public static final String ACTION_TOGGLE_CURRENT_DEVICE
            = "com.cyngn.audiofx.action.TOGGLE_DEVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG) {
            Log.i(TAG, "onReceive() called with " + "context = [" + context + "], intent = [" + intent + "]");
        }
        if (ACTION_TOGGLE_CURRENT_DEVICE.equals(intent.getAction())) {
            final MasterConfigControl config = MasterConfigControl.getInstance(context);

            config.setCurrentDeviceEnabled(!config.isCurrentDeviceEnabled());

            // tell service explicitly to update the qs tile in case UI isn't up to let it know
            context.startService(new Intent(AudioFxService.ACTION_UPDATE_TILE)
                    .setClass(context, AudioFxService.class));
        }
    }
}
