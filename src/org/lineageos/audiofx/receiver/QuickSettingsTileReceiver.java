/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.service.AudioFxService;

public class QuickSettingsTileReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "QSTileReceiver";

    public static final String ACTION_TOGGLE_CURRENT_DEVICE
            = "org.lineageos.audiofx.action.TOGGLE_DEVICE";

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
