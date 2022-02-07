package org.lineageos.audiofx.backends;

import android.content.Context;
import android.media.AudioDeviceInfo;

import org.lineageos.audiofx.Constants;

/**
 * Creates an EffectSet appropriate for the current device
 */
public class EffectsFactory implements IEffectFactory {

    private static final String TAG = "AudioFx-EffectsFactory";

    private static int sBrand = -1; // cached value to not hit io every time we need a new effect

    public EffectSet createEffectSet(Context context, int sessionId,
            AudioDeviceInfo currentDevice) {
        // if this throws, we're screwed, don't bother to recover. these
        // are the standard effects that every android device must have,
        // and if they don't exist we have bigger problems.
        return new AndroidEffects(sessionId, currentDevice);
    }

    public static int getBrand() {
        if (sBrand == -1) {
            sBrand = getBrandInternal();
        }
        return sBrand;
    }

    private static int getBrandInternal() {
        return Constants.EFFECT_TYPE_ANDROID;
    }
}
