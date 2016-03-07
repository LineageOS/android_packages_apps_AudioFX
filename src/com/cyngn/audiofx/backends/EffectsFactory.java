package com.cyngn.audiofx.backends;

import android.content.Context;
import android.content.SharedPreferences;
import com.cyngn.audiofx.Constants;

import java.io.File;

/**
 * Creates an EffectSet appropriate for the current device
 *
 * Currently handles basic Android effects and MaxxAudio.
 * Extend for DTS in the future.
 */
public class EffectsFactory {

    public static final int ANDROID = 0;
    public static final int MAXXAUDIO = 1;
    public static final int DTS = 2;

    public static EffectSet createEffectSet(Context context, int sessionId) {
        final SharedPreferences prefs = Constants.getGlobalPrefs(context);

        // dts?
        final boolean hasDts = prefs.getBoolean(Constants.AUDIOFX_GLOBAL_HAS_DTS, hasDts());
        if (hasDts) {
            return new DtsEffects(context, sessionId);
        }

        // maxx audio? if it's the very first time we try to init this, the pref won't exist
        // and so we assume we have it, because it will get destroyed and cleaned up and regular
        // effects will be returned
        boolean hasMaxxAudio = prefs.getBoolean(Constants.AUDIOFX_GLOBAL_HAS_MAXXAUDIO, true);

        if (hasMaxxAudio) {
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
        }

        return new AndroidEffects(sessionId);
    }

    public static boolean hasDts() {
        return new File("***REMOVED***").exists();
    }

}
