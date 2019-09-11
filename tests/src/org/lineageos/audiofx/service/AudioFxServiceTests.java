package org.lineageos.audiofx.service;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.lineageos.audiofx.util.BaseAudioFxServiceInstrumentationTest;
import org.lineageos.audiofx.util.TestMediaPlayer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.lineageos.audiofx.tests.R;

import static org.junit.Assert.*;

/**
 * Created by roman on 3/1/16.
 */
@RunWith(AndroidJUnit4.class)
public class AudioFxServiceTests extends BaseAudioFxServiceInstrumentationTest {

    private static final String TAG = "AudioFxServiceTests";

    private static final int SANE_MAX_LOOP_TIME = 1000 * 20; // 20 seconds !?
    private static final int LOOP_SLEEP_TIME = 25;

    TestMediaPlayer mPlayer;

    @Before
    public void setUp() throws Exception {
        mPlayer = new TestMediaPlayer(getContext(), R.raw.testmp3);
        assertNotNull(mPlayer);
    }

    @After
    public void tearDown() throws Exception {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Test
    public void testServiceCreatesEffectsDirect() throws Exception {
        setupService(); // this might be reused

        Intent intent = new Intent(getTargetContext(), AudioFxService.class);
        intent.setAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTargetContext().getPackageName());

        mServiceRule.startService(intent);
        Thread.sleep(100);
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));
    }

    @Test
    public void testServiceDestroysEffectsDirect() throws Exception {
        testServiceCreatesEffectsDirect();

        Intent intent = new Intent(getTargetContext(), AudioFxService.class);
        intent.setAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTargetContext().getPackageName());

        mServiceRule.startService(intent);

        // sleep for 10s to let effects die
        Thread.sleep(10100);
        assertNull(mService.getEffect(mPlayer.getAudioSessionId()));
    }

    @Test
    public void testServiceCreatesEffects() throws Exception {
        setupService();

        openFxSession(true);
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));
    }

    @Test
    @LargeTest
    public void testServiceDestroysEffects() throws Exception {
        testServiceCreatesEffects();

        closeFxSession(true);
        // sleep for 10s to let effects die
        assertNull(mService.getEffect(mPlayer.getAudioSessionId()));
    }

    @Test
    public void testServiceDoesNotDestroyDeferredEffect() throws Exception {
        setupService();
        assertNull(mService.getEffect(mPlayer.getAudioSessionId()));

        openFxSession(false);
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));

        closeFxSession(false);
        // shouldn't go away immediately after we close it
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));

        Thread.sleep(8000);

        // it should *still* be there not destroyed
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));

        // reopen the session
        openFxSession(false);

        // session should still be there
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));

        Thread.sleep(10000);

        // it's been more than 10s (deferred destroy time) and we re-opened it, so it should be
        // alive still
        assertNotNull(mService.getEffect(mPlayer.getAudioSessionId()));
    }

    private void openFxSession(boolean block) throws InterruptedException {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getAudioSessionId());
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTargetContext().getPackageName());
                getContext().sendBroadcast(intent);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        if (block) {
            int cnt = 0;
            while (mService.getEffect(mPlayer.getAudioSessionId()) == null
                    && (cnt * LOOP_SLEEP_TIME < SANE_MAX_LOOP_TIME)) {
                cnt++;
                Thread.sleep(LOOP_SLEEP_TIME);
            }
            Log.d(TAG, "took " + (cnt * LOOP_SLEEP_TIME) + "ms to open effect");
        } else {
            // TODO have a timeout here for failure based on time limits?
            Thread.sleep(300);
        }
    }

    private void closeFxSession(boolean block) throws InterruptedException {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mPlayer.getAudioSessionId());
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getTargetContext().getPackageName());
                getContext().sendBroadcast(intent);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        if (block) {
            int cnt = 0;
            while (mService.getEffect(mPlayer.getAudioSessionId()) != null
                    && (cnt * LOOP_SLEEP_TIME < SANE_MAX_LOOP_TIME)) {
                cnt++;
                Thread.sleep(LOOP_SLEEP_TIME);
            }
            Log.d(TAG, "took " + (cnt * LOOP_SLEEP_TIME) + "ms to close effect");
        } else {
            // TODO have a timeout here for failure based on time limits?
            Thread.sleep(300);
        }
    }

}
