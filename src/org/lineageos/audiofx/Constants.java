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
package org.cyanogenmod.audiofx;

import android.content.Context;
import android.content.SharedPreferences;
import org.cyanogenmod.audiofx.eq.EqUtils;

import java.util.ArrayList;
import java.util.List;

public class Constants {

    // effect type identifiers
    public static final int EFFECT_TYPE_ANDROID = 1;
    public static final int EFFECT_TYPE_MAXXAUDIO = 2;
    public static final int EFFECT_TYPE_DTS = 3;

    // global settings
    public static final String AUDIOFX_GLOBAL_FILE = "global";

    public static final String DEVICE_SPEAKER = "speaker";
    public static final String DEVICE_HEADSET = "headset";
    public static final String DEVICE_LINE_OUT = "lineout";
    public static final String DEVICE_PREFIX_USB = "usb";
    public static final String DEVICE_PREFIX_CAST = "wireless";
    public static final String DEVICE_PREFIX_BLUETOOTH = "bluetooth";

    public static final String SAVED_DEFAULTS = "saved_defaults";

    public static final String AUDIOFX_GLOBAL_USE_DTS = "audiofx.global.use_dts";
    public static final String AUDIOFX_GLOBAL_HAS_DTS = "audiofx.global.has_dts";
    public static final String AUDIOFX_GLOBAL_ENABLE_DTS = "audiofx.global.dts.enable";
    public static final String AUDIOFX_GLOBAL_HAS_MAXXAUDIO = "audiofx.global.hasmaxxaudio";
    public static final String AUDIOFX_GLOBAL_HAS_BASSBOOST = "audiofx.global.hasbassboost";
    public static final String AUDIOFX_GLOBAL_HAS_REVERB = "audiofx.global.hasreverb";
    public static final String AUDIOFX_GLOBAL_HAS_VIRTUALIZER = "audiofx.global.hasvirtualizer";
    public static final String AUDIOFX_GLOBAL_PREFS_VERSION_INT = "audiofx.global.prefs.version";

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

    // musicfx constants
    public static final String MUSICFX_PREF_NAME = "musicfx";
    public static final String MUSICFX_DEFAULT_PACKAGE_KEY = "defaultpanelpackage";
    public static final String MUSICFX_DEFAULT_PANEL_KEY = "defaultpanelname";

    public static SharedPreferences getMusicFxPrefs(Context context) {
        return context.getSharedPreferences(MUSICFX_PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SharedPreferences getGlobalPrefs(Context context) {
        return context.getSharedPreferences(AUDIOFX_GLOBAL_FILE, 0);
    }

    public static List<Preset> getCustomPresets(Context ctx, int bands) {
        ArrayList<Preset> presets = new ArrayList<Preset>();
        final SharedPreferences presetPrefs = ctx.getSharedPreferences("custom_presets", 0);
        String[] presetNames = presetPrefs.getString("preset_names", "").split("\\|");

        for (int i = 0; i < presetNames.length; i++) {
            String storedPresetString = presetPrefs.getString(presetNames[i], null);
            if (storedPresetString == null) {
                continue;
            }
            Preset.CustomPreset p = Preset.CustomPreset.fromString(storedPresetString);
            presets.add(p);
        }

        return presets;
    }

    public static void saveCustomPresets(Context ctx, List<Preset> presets) {
        final SharedPreferences.Editor presetPrefs = ctx.getSharedPreferences("custom_presets", 0).edit();
        presetPrefs.clear();

        StringBuffer presetNames = new StringBuffer();
        for (int i = 0; i < presets.size(); i++) {
            final Preset preset = presets.get(i);
            if (preset instanceof Preset.CustomPreset
                    && !(preset instanceof Preset.PermCustomPreset)) {
                Preset.CustomPreset p = (Preset.CustomPreset) preset;
                presetNames.append(p.getName());
                presetNames.append("|");

                presetPrefs.putString(p.getName(), p.toString());
            }
        }
        if (presetNames.length() > 0) {
            presetNames.deleteCharAt(presetNames.length() - 1);
        }

        presetPrefs.putString("preset_names", presetNames.toString());
        presetPrefs.commit();
    }

    public static int[] getBandLevelRange(Context context) {
        String savedCenterFreqs = context.getSharedPreferences("global", 0).getString("equalizer.band_level_range", null);
        if (savedCenterFreqs == null || savedCenterFreqs.isEmpty()) {
            return new int[]{-1500, 1500};
        } else {
            String[] split = savedCenterFreqs.split(";");
            int[] freqs = new int[split.length];
            for (int i = 0; i < split.length; i++) {
                freqs[i] = Integer.valueOf(split[i]);
            }
            return freqs;
        }
    }

    public static int[] getCenterFreqs(Context context, int eqBands) {
        String savedCenterFreqs = context.getSharedPreferences("global", 0).getString("equalizer.center_freqs",
                EqUtils.getZeroedBandsString(eqBands));
        String[] split = savedCenterFreqs.split(";");
        int[] freqs = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            freqs[i] = Integer.valueOf(split[i]);
        }
        return freqs;
    }
}
