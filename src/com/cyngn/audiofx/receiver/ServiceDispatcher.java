package com.cyngn.audiofx.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import com.cyngn.audiofx.service.AudioFxService;

public class ServiceDispatcher extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
        String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        Intent service = new Intent(context, AudioFxService.class);
        service.setAction(intent.getAction());
        service.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
        service.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, pkg);
        context.startService(service);
    }
}
