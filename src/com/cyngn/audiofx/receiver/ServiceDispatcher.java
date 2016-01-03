package com.cyngn.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import com.cyngn.audiofx.service.AudioFxService;

public class ServiceDispatcher extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        Intent service = new Intent(context, AudioFxService.class);
        String action = intent.getAction();

        // We can also get AUDIO_BECOMING_NOISY, which means a device change is
        // coming and we should wake up to handle it.
        if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) ||
                action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            service.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
            service.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, pkg);
        }

        service.setAction(action);
        context.startService(service);
        Log.d("AudioFX-Dispatcher", "Received " + action);

    }
}
