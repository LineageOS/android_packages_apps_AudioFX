package com.cyngn.audiofx.backends;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * Creates an EffectSet appropriate for the current device
 *
 * Currently handles basic Android effects and MaxxAudio.
 * Extend for DTS in the future.
 */
public class EffectsFactory {

    private static final String TAG = "AudioFx-EffectsFactory";

    public static final int ANDROID = 1;
    public static final int MAXXAUDIO = 2;
    public static final int DTS = 3;


    public static EffectSet createEffectSet(Context context, int sessionId) {

        EffectSet effects = null;
        int brand = getBrand();

        // dts?
        if (brand == DTS) {
            try {
                effects = new DtsEffects(context, sessionId);
            } catch (Exception e) {
                Log.e(TAG,  "Unable to create DTS effects!", e);
                effects = null;
            }
        } else if (brand == MAXXAUDIO) {
            // try MaxxAudio next, this will throw an exception if unavailable
            try {
                effects = new MaxxAudioEffects(sessionId);
            } catch (Exception e) {
                Log.e(TAG, "Unable to create MaxxAudio effects!", e);
                effects = null;
            }
        }

        if (effects == null) {
            // if this throws, we're screwed, don't bother to recover. these
            // are the standard effects that every android device must have,
            // and if they don't exist we have bigger problems.
            effects = new AndroidEffects(sessionId);
        }

        return effects;
    }

    public static boolean hasDts() {
        return new File("***REMOVED***").exists();
    }

    public static boolean hasMaxxAudio() {
        return new File("***REMOVED***").exists();
    }

    public static int getBrand() {
        if (hasDts()) {
            return DTS;
        } else if (hasMaxxAudio()) {
            return MAXXAUDIO;
        }
        return ANDROID;
    }
}
