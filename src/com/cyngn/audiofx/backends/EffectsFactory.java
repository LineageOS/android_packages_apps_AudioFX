package com.cyngn.audiofx.backends;

import android.content.Context;
import com.cyngn.audiofx.Constants;

import java.io.File;

/**
 * Creates an EffectSet appropriate for the current device
 *
 * Currently handles basic Android effects and MaxxAudio.
 * Extend for DTS in the future.
 */
public class EffectsFactory {

    private static Boolean sHasDts;

    public static final int ANDROID = 0;
    public static final int MAXXAUDIO = 1;
    public static final int DTS = 2;

    public static EffectSet createEffectSet(Context context, int sessionId) {

        if (hasDts()) {
            return new DtsEffects(context, sessionId);
        }

        // try MaxxAudio next, this will throw an exception if unavailable
        MaxxAudioEffects fx = null;
        try {
            fx = new MaxxAudioEffects(sessionId);
        } catch (Exception e) {
            fx = null;
        }

        // good to go!
        if (fx != null) {
            return fx;
        }

        return new AndroidEffects(sessionId);
    }

    public static boolean hasDts() {
        if (sHasDts == null) {
            sHasDts = new File("***REMOVED***").exists();
        }
        return sHasDts;
    }

}
