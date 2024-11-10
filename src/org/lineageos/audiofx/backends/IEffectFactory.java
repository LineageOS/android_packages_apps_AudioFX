/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.backends;

import android.content.Context;
import android.media.AudioDeviceInfo;

interface IEffectFactory {

    /**
     * Create a new EffectSet based on current stream parameters.
     *
     * @param context       context to create the effect with
     * @param sessionId     session id to attach the effect to
     * @param currentDevice current device that the effect should initially setup for
     * @return an {@link EffectSet}
     */
    EffectSet createEffectSet(Context context, int sessionId, AudioDeviceInfo currentDevice);
}
