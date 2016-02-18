package com.cyngn.audiofx.backends;

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

    public static EffectSet createEffectSet(int sessionId) {

        // try MaxxAudio first, this will throw an exception if unavailable
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
}
