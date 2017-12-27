package org.lineageos.audiofx.tests;

import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.util.Log;

import org.lineageos.audiofx.util.TestDuckingMediaPlayer;

/**
 * Created by roman on 3/8/16.
 */
public class DebugActivity extends TestActivity {

    @Override
    protected String tag() {
        return DebugActivity.class.getSimpleName();
    }

    @Override
    protected Test[] tests() {
        return new Test[]{
                testAudioDucking()
        };
    }

    private Test testAudioDucking() {
        return new Test("Test Audio Ducking") {
            @Override
            protected void run() {
                try {
                    final TestDuckingMediaPlayer songMediaPlayer = new TestDuckingMediaPlayer(getApplication());
                    songMediaPlayer.setAudioSessionId(AudioSystem.newAudioSessionId());
                    songMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    songMediaPlayer.setDataSource(getApplication(),
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                    songMediaPlayer.prepare();

                    final TestDuckingMediaPlayer interruptPlayer = new TestDuckingMediaPlayer(
                            DebugActivity.this, R.raw.testmp3);

                    final Integer focusResult = songMediaPlayer.start(AudioManager.AUDIOFOCUS_GAIN);
                    Log.d(tag(), "requestFocus returns: " + focusResult);

                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                Thread.sleep(400);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            interruptPlayer.start(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            interruptPlayer.stop();

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            songMediaPlayer.stop();

                            interruptPlayer.release();
                            songMediaPlayer.release();

                            return null;
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
                } catch (Exception e) {
                    e.printStackTrace();
                    finish();
                }
            }
        };
    }
}
