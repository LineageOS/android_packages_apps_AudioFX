
package org.cyanogenmod.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import org.cyanogenmod.audiofx.service.AudioFxService;

import cyanogenmod.media.AudioSessionInfo;
import cyanogenmod.media.CMAudioManager;

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

        } else if (action.equals(CMAudioManager.ACTION_AUDIO_SESSIONS_CHANGED)) {

            // callback from CMAudioService
            final AudioSessionInfo info = (AudioSessionInfo) intent.getParcelableExtra(
                    CMAudioManager.EXTRA_SESSION_INFO);
            boolean added = intent.getBooleanExtra(CMAudioManager.EXTRA_SESSION_ADDED, false);
            service.putExtra(CMAudioManager.EXTRA_SESSION_INFO, info);
            service.putExtra(CMAudioManager.EXTRA_SESSION_ADDED, added);
        }

        service.setAction(action);
        context.startService(service);
        if (AudioFxService.DEBUG) {
            Log.d("AudioFX-Dispatcher", "Received " + action);
        }

    }
}
