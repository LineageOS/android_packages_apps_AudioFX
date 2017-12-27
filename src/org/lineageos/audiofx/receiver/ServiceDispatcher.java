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
import android.media.audiofx.AudioEffect;
import android.util.Log;

import org.lineageos.audiofx.service.AudioFxService;

import lineageos.media.AudioSessionInfo;
import lineageos.media.LineageAudioManager;

public class ServiceDispatcher extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        Intent service = new Intent(context.getApplicationContext(), AudioFxService.class);
        String action = intent.getAction();

        // We can also get AUDIO_BECOMING_NOISY, which means a device change is
        // coming and we should wake up to handle it.
        if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) ||
                action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            service.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
            service.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, pkg);

        } else if (action.equals(LineageAudioManager.ACTION_AUDIO_SESSIONS_CHANGED)) {

            // callback from LineageAudioService
            final AudioSessionInfo info = (AudioSessionInfo) intent.getParcelableExtra(
                    LineageAudioManager.EXTRA_SESSION_INFO);
            boolean added = intent.getBooleanExtra(LineageAudioManager.EXTRA_SESSION_ADDED, false);
            service.putExtra(LineageAudioManager.EXTRA_SESSION_INFO, info);
            service.putExtra(LineageAudioManager.EXTRA_SESSION_ADDED, added);
        }

        service.setAction(action);
        context.startService(service);
        if (AudioFxService.DEBUG) {
            Log.d("AudioFX-Dispatcher", "Received " + action);
        }

    }
}
