/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cyngn.audiofx;

import android.content.Context;
import android.content.SharedPreferences;

public class Constants {

    // global settings
    public static final String AUDIOFX_GLOBAL_FILE = "global";
    public static final String DEVICE_SPEAKER = "speaker";

    public static final String SAVED_DEFAULTS = "saved_defaults";

    public static final String AUDIOFX_GLOBAL_USE_DTS = "audiofx.global.use_dts";
    public static final String AUDIOFX_GLOBAL_HAS_DTS = "audiofx.global.has_dts";
    public static final String AUDIOFX_GLOBAL_ENABLE_DTS = "audiofx.global.dts.enable";
    public static final String AUDIOFX_GLOBAL_HAS_MAXXAUDIO = "audiofx.global.hasmaxxaudio";
    public static final String AUDIOFX_GLOBAL_HAS_BASSBOOST = "audiofx.global.hasbassboost";
    public static final String AUDIOFX_GLOBAL_HAS_VIRTUALIZER = "audiofx.global.hasvirtualizer";

    // per-device settings
    public static final boolean DEVICE_DEFAULT_GLOBAL_ENABLE = false;

    /**
     * not really global enable, but really the device global enable...
     */
    public static final String DEVICE_AUDIOFX_GLOBAL_ENABLE = "audiofx.global.enable";
    public static final String DEVICE_AUDIOFX_BASS_ENABLE = "audiofx.bass.enable";
    public static final String DEVICE_AUDIOFX_BASS_STRENGTH = "audiofx.bass.strength";
    public static final String DEVICE_AUDIOFX_REVERB_PRESET = "audiofx.reverb.preset";
    public static final String DEVICE_AUDIOFX_VIRTUALIZER_ENABLE = "audiofx.virtualizer.enable";
    public static final String DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH = "audiofx.virtualizer.strength";
    public static final String DEVICE_AUDIOFX_TREBLE_ENABLE = "audiofx.treble.enable";
    public static final String DEVICE_AUDIOFX_TREBLE_STRENGTH = "audiofx.treble.strength";
    public static final String DEVICE_AUDIOFX_MAXXVOLUME_ENABLE = "audiofx.maxxvolume.enable";

    public static final String DEVICE_AUDIOFX_EQ_PRESET = "audiofx.eq.preset";
    public static final String DEVICE_AUDIOFX_EQ_PRESET_LEVELS = "audiofx.eq.preset.levels";

    // eq
    public static final String EQUALIZER_NUMBER_OF_PRESETS = "equalizer.number_of_presets";
    public static final String EQUALIZER_NUMBER_OF_BANDS = "equalizer.number_of_bands";
    public static final String EQUALIZER_BAND_LEVEL_RANGE = "equalizer.band_level_range";
    public static final String EQUALIZER_CENTER_FREQS = "equalizer.center_freqs";
    public static final String EQUALIZER_PRESET = "equalizer.preset.";
    public static final String EQUALIZER_PRESET_NAMES = "equalizer.preset_names";

    public static SharedPreferences getGlobalPrefs(Context context) {
        return context.getSharedPreferences(AUDIOFX_GLOBAL_FILE, 0);
    }
}
