/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package org.cyanogenmod.audiofx.service;

import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_FILE;
import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_HAS_BASSBOOST;
import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_HAS_DTS;
import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_HAS_MAXXAUDIO;
import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_HAS_REVERB;
import static org.cyanogenmod.audiofx.Constants.AUDIOFX_GLOBAL_HAS_VIRTUALIZER;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_BASS_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_BASS_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_EQ_PRESET;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_HEADSET;
import static org.cyanogenmod.audiofx.Constants.DEVICE_SPEAKER;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_BAND_LEVEL_RANGE;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_CENTER_FREQS;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_NUMBER_OF_BANDS;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_NUMBER_OF_PRESETS;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_PRESET;
import static org.cyanogenmod.audiofx.Constants.EQUALIZER_PRESET_NAMES;
import static org.cyanogenmod.audiofx.Constants.SAVED_DEFAULTS;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.text.TextUtils;
import android.util.Log;

import org.cyanogenmod.audiofx.Constants;
import org.cyanogenmod.audiofx.R;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import org.cyanogenmod.audiofx.backends.EffectSet;
import org.cyanogenmod.audiofx.backends.EffectsFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class DevicePreferenceManager
        implements AudioOutputChangeListener.AudioOutputChangedCallback {

    // Current pref version, bump to rebuild prefs
    public static final int CURRENT_PREFS_INT_VERSION = 4;

    private static final String TAG = AudioFxService.TAG;
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;

    private AudioDeviceInfo mCurrentDevice;

    public DevicePreferenceManager(Context context, AudioDeviceInfo device) {
        mContext = context;
        mCurrentDevice = device;
    }

    public boolean initDefaults() {
        try {
            saveAndApplyDefaults(false);
        } catch (Exception e) {
            SharedPreferences prefs = Constants.getGlobalPrefs(mContext);
            prefs.edit().clear().commit();
            Log.e(TAG, "Failed to initialize defaults!", e);
            return false;
        }
        return true;
    }

    @Override
    public void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputDevice) {
        mCurrentDevice = outputDevice;
    }

    public SharedPreferences getCurrentDevicePrefs() {
        return mContext.getSharedPreferences(
                MasterConfigControl.getDeviceIdentifierString(mCurrentDevice), 0);
    }

    public SharedPreferences prefsFor(final String name) {
        return mContext.getSharedPreferences(name, 0);
    }

    private boolean hasPrefs(final String name) {
        return mContext.getSharedPrefsFile(name).exists();
    }

    public boolean isGlobalEnabled() {
        return getCurrentDevicePrefs().getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, false);
    }

    /**
     * This method sets some sane defaults for presets, device defaults, etc
     * <p/>
     * First we read presets from the system, then adjusts some setting values
     * for some better defaults!
     */
    private void saveAndApplyDefaults(boolean overridePrevious) {
        if (DEBUG) {
            Log.d(TAG, "saveAndApplyDefaults() called with overridePrevious = " +
                    "[" + overridePrevious + "]");
        }
        SharedPreferences prefs = Constants.getGlobalPrefs(mContext);

        final int currentPrefVer = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0);
        boolean needsPrefsUpdate = currentPrefVer < CURRENT_PREFS_INT_VERSION
                || overridePrevious;

        if (needsPrefsUpdate) {
            Log.d(TAG, "rebuilding presets due to preference upgrade from " + currentPrefVer
                    + " to " + CURRENT_PREFS_INT_VERSION);
        }

        if (prefs.getBoolean(SAVED_DEFAULTS, false) && !needsPrefsUpdate) {
            if (DEBUG) {
                Log.e(TAG, "we've already saved defaults and don't need a pref update. aborting.");
            }
            return;
        }
        EffectSet temp = new EffectsFactory().createEffectSet(mContext, 0, null);

        final int numBands = temp.getNumEqualizerBands();
        final int numPresets = temp.getNumEqualizerPresets();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(EQUALIZER_NUMBER_OF_PRESETS, String.valueOf(numPresets));
        editor.putString(EQUALIZER_NUMBER_OF_BANDS, String.valueOf(numBands));

        // range
        short[] rangeShortArr = temp.getEqualizerBandLevelRange();
        editor.putString(EQUALIZER_BAND_LEVEL_RANGE, rangeShortArr[0]
                + ";" + rangeShortArr[1]);

        // center freqs
        StringBuilder centerFreqs = new StringBuilder();
        // audiofx.global.centerfreqs
        for (short i = 0; i < numBands; i++) {
            centerFreqs.append(temp.getCenterFrequency(i));
            centerFreqs.append(";");

        }
        centerFreqs.deleteCharAt(centerFreqs.length() - 1);
        editor.putString(EQUALIZER_CENTER_FREQS, centerFreqs.toString());

        // populate preset names
        StringBuilder presetNames = new StringBuilder();
        for (int i = 0; i < numPresets; i++) {
            String presetName = temp.getEqualizerPresetName((short) i);
            presetNames.append(presetName);
            presetNames.append("|");

            // populate preset band values
            StringBuilder presetBands = new StringBuilder();
            temp.useEqualizerPreset((short) i);

            for (int j = 0; j < numBands; j++) {
                // loop through preset bands
                presetBands.append(temp.getEqualizerBandLevel((short) j));
                presetBands.append(";");
            }
            presetBands.deleteCharAt(presetBands.length() - 1);
            editor.putString(EQUALIZER_PRESET + i, presetBands.toString());
        }
        if (presetNames.length() > 0) {
            presetNames.deleteCharAt(presetNames.length() - 1);
        }
        editor.putString(EQUALIZER_PRESET_NAMES, presetNames.toString());

        editor.putBoolean(AUDIOFX_GLOBAL_HAS_VIRTUALIZER, temp.hasVirtualizer());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_REVERB, temp.hasReverb());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_BASSBOOST, temp.hasBassBoost());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, temp.getBrand() == Constants.EFFECT_TYPE_MAXXAUDIO);
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_DTS, temp.getBrand() == Constants.EFFECT_TYPE_DTS);
        editor.commit();
        temp.release();

        applyDefaults(needsPrefsUpdate);

        prefs
                .edit()
                .putInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT,
                            CURRENT_PREFS_INT_VERSION)
                .putBoolean(Constants.SAVED_DEFAULTS, true)
                .commit();
    }

    private static int findInList(String needle, List<String> haystack) {
        for (int i = 0; i < haystack.size(); i++) {
            if (haystack.get(i).equalsIgnoreCase(needle)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * This method sets up some *persisted* defaults.
     * Prereq: saveDefaults() must have been run before this can apply its defaults properly.
     */
    private void applyDefaults(boolean overridePrevious) {
        if (DEBUG) {
            Log.d(TAG, "applyDefaults() called with overridePrevious = [" + overridePrevious + "]");
        }

        if (!(overridePrevious || !hasPrefs(DEVICE_SPEAKER) ||
                !hasPrefs(AUDIOFX_GLOBAL_FILE))) {
            return;
        }

        final SharedPreferences globalPrefs = Constants.getGlobalPrefs(mContext);

        // Nothing to see here for EFFECT_TYPE_DTS
        if (globalPrefs.getBoolean(AUDIOFX_GLOBAL_HAS_DTS, false)) {
            return;
        }

        // set up the builtin speaker configuration
        final String smallSpeakers = getNonLocalizedString(R.string.small_speakers);
        final List<String> presetNames = new ArrayList<String>(Arrays.asList(
                globalPrefs.getString(EQUALIZER_PRESET_NAMES, "").split("\\|")));
        final SharedPreferences speakerPrefs = prefsFor(DEVICE_SPEAKER);

        if (globalPrefs.getBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, false)) {
            // MaxxAudio defaults for builtin speaker:
            // maxxvolume: on  maxxbass: 40%  maxxtreble: 32%
            speakerPrefs.edit()
                    .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                    .putBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, true)
                    .putBoolean(DEVICE_AUDIOFX_BASS_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_BASS_STRENGTH, "400")
                    .putBoolean(DEVICE_AUDIOFX_TREBLE_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_TREBLE_STRENGTH, "32")
                    .commit();

            // Defaults for headphones
            // maxxvolume: on  maxxbass: 20%  maxxtreble: 40%  maxxspace: 20%
            prefsFor(DEVICE_HEADSET).edit()
                    .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                    .putBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, true)
                    .putBoolean(DEVICE_AUDIOFX_BASS_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_BASS_STRENGTH, "200")
                    .putBoolean(DEVICE_AUDIOFX_TREBLE_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_TREBLE_STRENGTH, "40")
                    .putBoolean(DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH, "200")
                    .commit();
        } else {
            // Defaults for headphones
            // bass boost: 15%  virtualizer: 20%  preset: FLAT
            int flat = findInList(getNonLocalizedString(R.string.flat), presetNames);
            prefsFor(DEVICE_HEADSET).edit()
                    .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                    .putBoolean(DEVICE_AUDIOFX_BASS_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_BASS_STRENGTH, "150")
                    .putBoolean(DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, true)
                    .putString(DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH, "200")
                    .putString(DEVICE_AUDIOFX_EQ_PRESET, (flat >= 0 ? String.valueOf(flat) : "0"))
                    .commit();
        }

        // for 5 band configs, let's add a `Small Speaker` configuration if one
        // doesn't exist ( from oss AudioFX: -170;270;50;-220;200 )
        if (Integer.parseInt(globalPrefs.getString(EQUALIZER_NUMBER_OF_BANDS, "0")) == 5 &&
                findInList(smallSpeakers, presetNames) < 0) {

            int currentPresets = Integer.parseInt(
                    globalPrefs.getString(EQUALIZER_NUMBER_OF_PRESETS, "0"));

            presetNames.add(smallSpeakers);
            String newPresetNames = TextUtils.join("|", presetNames);
            globalPrefs.edit()
                    .putString(EQUALIZER_PRESET + currentPresets, "-170;270;50;-220;200")
                    .putString(EQUALIZER_PRESET_NAMES, newPresetNames)
                    .putString(EQUALIZER_NUMBER_OF_PRESETS, Integer.toString(++currentPresets))
                    .commit();

        }

        // set the small speakers preset as the default
        int idx = findInList(smallSpeakers, presetNames);
        if (idx >= 0) {
            speakerPrefs.edit()
                .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                .putString(DEVICE_AUDIOFX_EQ_PRESET, String.valueOf(idx))
                .commit();
        }
    }

    private String getNonLocalizedString(int res) {
        Configuration config = new Configuration(mContext.getResources().getConfiguration());
        config.setLocale(Locale.ROOT);
        return mContext.createConfigurationContext(config).getString(res);
    }
}

