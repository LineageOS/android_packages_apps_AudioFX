package org.lineageos.audiofx.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

import org.lineageos.audiofx.tests.R;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by roman on 3/4/16.
 */
public class TestMediaPlayer extends MediaPlayer {

    public TestMediaPlayer() {
        setAudioStreamType(AudioManager.STREAM_MUSIC);
    }

    public TestMediaPlayer(Context testContext, int withResource) throws Exception {
        this();
        AssetFileDescriptor afd = testContext.getResources().openRawResourceFd(withResource);
        assertNotNull(afd);
        setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        prepare();
    }

}
