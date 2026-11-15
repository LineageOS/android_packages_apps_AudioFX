/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.backends;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import org.lineageos.audiofx.Constants;

/**
 * Creates an EffectSet appropriate for the current device
 */
public class EffectsFactory implements IEffectFactory {

    private static final String TAG = "AudioFx-EffectsFactory";

    private static int sBrand = -1; // cached value to not hit io every time we need a new effect

    public EffectSet createEffectSet(Context context, int sessionId,
            AudioDeviceInfo currentDevice) {
        if (getBrand() == Constants.EFFECT_TYPE_DOLBY) {
            try {
                // Dolby is a global effect, it always lives on session 0
                final DolbyEffects dolby = new DolbyEffects(0, currentDevice);
                dolby.registerPlaybackCallback(context.getSystemService(AudioManager.class));
                return dolby;
            } catch (Exception e) {
                Log.e(TAG, "Dolby effect unavailable, falling back to Android effects", e);
                sBrand = Constants.EFFECT_TYPE_ANDROID;
            }
        }
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
        final AudioEffect.Descriptor[] effects = AudioEffect.queryEffects();
        if (effects != null) {
            for (AudioEffect.Descriptor d : effects) {
                if (DolbyEffects.EFFECT_UUID_DAP.equals(d.uuid)) {
                    return Constants.EFFECT_TYPE_DOLBY;
                }
            }
        }
        return Constants.EFFECT_TYPE_ANDROID;
    }
}
