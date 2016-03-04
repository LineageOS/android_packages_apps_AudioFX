package com.cyngn.audiofx.receiver;

import android.app.ActivityManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;

import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.tests.TestMediaPlayer;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Created by roman on 3/4/16.
 */
public class ServiceDispatcherTests extends InstrumentationTestCase {

    private ServiceDispatcher mServiceReceiver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mServiceReceiver = new ServiceDispatcher();
    }

    @Override
    protected void tearDown() throws Exception {
        getInstrumentation().getTargetContext().stopService(
                new Intent(getInstrumentation().getTargetContext(), AudioFxService.class));
        mServiceReceiver = null;

        Thread.sleep(100);
        assertFalse(isAudioFxServiceRunning());
        super.tearDown();
    }


    public void testOpenSessionStartsService() throws Throwable {
        assertFalse(isAudioFxServiceRunning());
        TestMediaPlayer mediaPlayer = new TestMediaPlayer(getInstrumentation().getContext());

        Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getInstrumentation().getContext().getPackageName());

        mServiceReceiver.onReceive(getInstrumentation().getTargetContext(), intent);
        Thread.sleep(100);
        assertTrue(isAudioFxServiceRunning());

        mediaPlayer.release();
    }

    public void testCloseSessionStartsService() throws Exception {
        assertFalse(isAudioFxServiceRunning());
        TestMediaPlayer mediaPlayer = new TestMediaPlayer(getInstrumentation().getContext());

        Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer.getSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getInstrumentation().getContext().getPackageName());

        mServiceReceiver.onReceive(getInstrumentation().getTargetContext(), intent);
        Thread.sleep(100);
        assertTrue(isAudioFxServiceRunning());

        mediaPlayer.release();
    }

    public void testAudioBecomingNoisyStartsService() throws InterruptedException {
        assertFalse(isAudioFxServiceRunning());

        Intent intent = new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        mServiceReceiver.onReceive(getInstrumentation().getTargetContext(), intent);
        Thread.sleep(100);
        assertTrue(isAudioFxServiceRunning());
    }

    private boolean isAudioFxServiceRunning() {
        ActivityManager manager = (ActivityManager) getInstrumentation().getContext().getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AudioFxService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
