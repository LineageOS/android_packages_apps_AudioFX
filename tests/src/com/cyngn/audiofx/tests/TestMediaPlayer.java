package com.cyngn.audiofx.tests;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;

import com.cyngn.audiofx.tests.R;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by roman on 3/4/16.
 */
public class TestMediaPlayer {

    protected MediaPlayer mPlayer;

    public TestMediaPlayer(Context testContext) throws Exception {
        mPlayer = new MediaPlayer();
        assertNotNull("could not create mediaplayer", mPlayer);
        AssetFileDescriptor afd = testContext.getResources().openRawResourceFd(R.raw.testmp3);
        assertNotNull(afd);
        mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.prepare();
    }

    public void release() {
        mPlayer.release();
    }

    public int getSessionId() {
        return mPlayer.getAudioSessionId();
    }

}
