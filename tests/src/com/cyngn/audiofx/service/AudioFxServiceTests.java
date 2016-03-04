package com.cyngn.audiofx.service;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.test.ServiceTestCase;

import com.cyngn.audiofx.backends.EffectSet;
import com.cyngn.audiofx.tests.TestMediaPlayer;

/**
 * Created by roman on 3/1/16.
 */
public class AudioFxServiceTests extends ServiceTestCase<AudioFxService> {

    private static final String TAG = AudioFxServiceTests.class.getSimpleName();
    private AudioFxService.LocalBinder mService;

    public AudioFxServiceTests() {
        super(AudioFxService.class);
    }

    TestMediaPlayer mPlayer;
    EffectSet mEffects;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Intent serviceIntent = new Intent(getTestContext(), AudioFxService.class);
        mService = (AudioFxService.LocalBinder) bindService(serviceIntent);
        assertNotNull(mService);
        mPlayer = new TestMediaPlayer(getTestContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mPlayer != null) {
            mPlayer.release();
        }
    }

    public void testServiceCreatesEffects() throws Exception {
        Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTestContext().getPackageName());

        // we call the service directly because it is mocked, otherwise the real broadcast receiver
        // would intercept and send it to the _actual_ service
        startService(intent);

        Thread.sleep(100);

        mEffects = mService.getEffect(mPlayer.getSessionId());
        assertNotNull(mEffects);
    }

    public void testServiceDestroysEffects() throws Exception {
        testServiceCreatesEffects();

        Thread.sleep(100);

        Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTestContext().getPackageName());
        startService(intent);

        // we keep effects around for a minimum of 10s
        Thread.sleep(11000);

        assertNull(mService.getEffect(mPlayer.getSessionId()));
    }


}
