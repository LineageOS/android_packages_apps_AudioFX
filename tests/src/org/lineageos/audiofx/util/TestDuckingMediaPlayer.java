package org.lineageos.audiofx.util;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import org.lineageos.audiofx.tests.R;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by roman on 3/4/16.
 */
public class TestDuckingMediaPlayer extends TestMediaPlayer {

    private static final String TAG = TestDuckingMediaPlayer.class.getSimpleName();
    private AudioManager mAudioManager;

    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "onAudioFocusChange() called with " + "focusChange = [" + focusChange + "]");
            switch(focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    setVolume(0.4f);
                    break;

                case AudioManager.AUDIOFOCUS_GAIN:
                    setVolume(1.f);
                    break;
            }
        }
    };

    public int start(int focus) throws IllegalStateException {
        int result = mAudioManager.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC,
                focus);
        super.start();
        return result;
    }

    @Override
    public void stop() throws IllegalStateException {
        mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
        super.stop();
    }

    public TestDuckingMediaPlayer(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public TestDuckingMediaPlayer(Context context, int withResource) throws Exception {
        super(context, withResource);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }



}
