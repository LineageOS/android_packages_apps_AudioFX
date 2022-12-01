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
package org.lineageos.audiofx.activity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.CompoundButton;

import org.lineageos.audiofx.Constants;
import org.lineageos.audiofx.Preset;
import org.lineageos.audiofx.R;
import org.lineageos.audiofx.eq.EqUtils;
import org.lineageos.audiofx.service.AudioFxService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class EqualizerManager {

    private static final String TAG = EqualizerManager.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final MasterConfigControl mConfig;
    private final Context mContext;

    private float mMinFreq;
    private float mMaxFreq;

    private float mMinDB;
    private float mMaxDB;
    private int mNumBands;
    private CompoundButton.OnCheckedChangeListener mLockChangeListener;

    /*
     * presets from the library custom preset.
     */
    private int mPredefinedPresets;
    private float[] mCenterFreqs;
    private float[] mGlobalLevels;

    private final AtomicBoolean mAnimatingToCustom = new AtomicBoolean(false);

    // whether we are in between presets, animating them and such
    private boolean mChangingPreset = false;

    private int mCurrentPreset;

    private final ArrayList<Preset> mEqPresets = new ArrayList<Preset>();
    private int mEQCustomPresetPosition;

    private String mZeroedBandString;

    private static final int MSG_SAVE_PRESETS = 1;
    private static final int MSG_SEND_EQ_OVERRIDE = 2;

    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SAVE_PRESETS:
                    Constants.saveCustomPresets(mContext, mEqPresets);
                    break;
                case MSG_SEND_EQ_OVERRIDE:
                    mConfig.overrideEqLevels((short) msg.arg1, (short) msg.arg2);
                    break;
            }
            return true;
        }
    });

    public EqualizerManager(Context context, MasterConfigControl config) {
        mContext = context;
        mConfig = config;

        applyDefaults();
    }

    public void applyDefaults() {
        mEqPresets.clear();
        // setup eq
        int bands = Integer.parseInt(getGlobalPref("equalizer.number_of_bands", "5"));
        final int[] centerFreqs = Constants.getCenterFreqs(mContext, bands);
        final int[] bandLevelRange = Constants.getBandLevelRange(mContext);

        float[] centerFreqsKHz = new float[centerFreqs.length];
        for (int i = 0; i < centerFreqs.length; i++) {
            centerFreqsKHz[i] = (float) centerFreqs[i] / 1000.0f;
        }

        mMinDB = bandLevelRange[0] / 100;
        mMaxDB = bandLevelRange[1] / 100;

        mNumBands = centerFreqsKHz.length;
        mGlobalLevels = new float[mNumBands];
        for (int i = 0; i < mGlobalLevels.length; i++) {
            mGlobalLevels[i] = 0;
        }

        mZeroedBandString = EqUtils.getZeroedBandsString(getNumBands());

        mCenterFreqs = Arrays.copyOf(centerFreqsKHz, mNumBands);
        System.arraycopy(centerFreqsKHz, 0, mCenterFreqs, 0, mNumBands);
        mMinFreq = mCenterFreqs[0] / 2;
        mMaxFreq = (float) Math.pow(mCenterFreqs[mNumBands - 1], 2) / mCenterFreqs[mNumBands - 2]
                / 2;

        // setup equalizer presets
        final int numPresets = Integer.parseInt(getGlobalPref("equalizer.number_of_presets", "0"));

        if (numPresets > 0) {
            // add library-provided presets
            String[] presetNames = getGlobalPref("equalizer.preset_names", "").split("\\|");
            mPredefinedPresets =
                    presetNames.length + 1; // we consider first EQ to be part of predefined
            for (int i = 0; i < numPresets; i++) {
                mEqPresets.add(
                        new Preset.StaticPreset(presetNames[i], getPersistedPresetLevels(i)));
            }
        } else {
            mPredefinedPresets = 1; // custom is predefined
        }
        // add custom preset
        mEqPresets.add(new Preset.PermCustomPreset(mContext.getString(R.string.custom),
                getPersistedCustomLevels()));
        mEQCustomPresetPosition = mEqPresets.size() - 1;

        // restore custom prefs
        mEqPresets.addAll(Constants.getCustomPresets(mContext, mNumBands));

        // setup default preset for speaker
        mCurrentPreset = Integer.parseInt(getPref(Constants.DEVICE_AUDIOFX_EQ_PRESET, "0"));
        if (mCurrentPreset > mEqPresets.size() - 1) {
            mCurrentPreset = 0;
        }
        setPreset(mCurrentPreset);
    }

    public boolean isUserPreset() {
        boolean result = mCurrentPreset >= mPredefinedPresets;
        /*if (DEBUG) {
            Log.i(TAG, "isUserPreset(), current preset: " + mCurrentPreset);
            Log.i(TAG, "----> predefined presets: " + mPredefinedPresets);
            Log.d(TAG, "----> RESULT: " + result);
        }*/
        return result;
    }

    public boolean isCustomPreset() {
        return mCurrentPreset == mEQCustomPresetPosition;
    }

    public CompoundButton.OnCheckedChangeListener getLockChangeListener() {
        if (mLockChangeListener == null) {
            mLockChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isUserPreset()) {
                        ((Preset.CustomPreset) mEqPresets.get(mCurrentPreset)).setLocked(isChecked);
                    }
                }
            };
        }
        return mLockChangeListener;
    }

    public boolean isChangingPresets() {
        return mChangingPreset;
    }

    public void setChangingPresets(boolean changing) {
        if (mChangingPreset != changing) {
            mChangingPreset = changing;
            if (changing) {
                mConfig.getCallbacks().notifyEqControlStateChanged(false, false, false, false);
            } else {
                updateEqControls();
            }
        }
    }

    public boolean isAnimatingToCustom() {
        return mAnimatingToCustom.get();
    }

    public void setAnimatingToCustom(boolean animating) {
        mAnimatingToCustom.set(animating);
        if (!animating) {
            // finished animation
            updateEqControls();
        }
    }

    private void savePresetsDelayed() {
        mHandler.sendEmptyMessageDelayed(MSG_SAVE_PRESETS, 500);
    }

    public int indexOf(Preset p) {
        return mEqPresets.indexOf(p);
    }

    void onPreDeviceChanged() {
        // need to update the current preset based on the device here.
        int newPreset = Integer.parseInt(getPref(Constants.DEVICE_AUDIOFX_EQ_PRESET, "0"));
        if (newPreset > mEqPresets.size() - 1) {
            newPreset = 0;
        }

        // this should be ready to go for callbacks to query the new device preset below
        mCurrentPreset = newPreset;

    }

    void onPostDeviceChanged() {
        setPreset(mCurrentPreset, false);
    }

    public Preset getCurrentPreset() {
        return mEqPresets.get(mCurrentPreset);
    }

    /**
     * Copy current config levels from the current preset into custom values since the user has
     * initiated some change. Then update the current preset to 'custom'.
     */
    public int copyToCustom() {
        updateGlobalLevels(mCurrentPreset);
        if (DEBUG) {
            Log.w(TAG, "using levels from preset: " + mCurrentPreset + ": " + Arrays.toString(
                    mGlobalLevels));
        }

        String levels = EqUtils.floatLevelsToString(
                EqUtils.convertDecibelsToMillibels(
                        mEqPresets.get(mCurrentPreset).getLevels()));
        setGlobalPref("custom", levels);

        ((Preset.PermCustomPreset) mEqPresets.get(mEQCustomPresetPosition)).setLevels(
                mGlobalLevels);
        if (DEBUG) {
            Log.i(TAG, "copyToCustom() wrote current preset levels to index: "
                    + mEQCustomPresetPosition);
        }
        setPreset(mEQCustomPresetPosition);
        savePresetsDelayed();
        return mEQCustomPresetPosition;
    }

    public int addPresetFromCustom() {
        updateGlobalLevels(mEQCustomPresetPosition);
        if (DEBUG) {
            Log.w(TAG, "using levels from preset: " + mCurrentPreset + ": " + Arrays.toString(
                    mGlobalLevels));
        }

        int writtenToIndex = addPreset(mGlobalLevels);
        if (DEBUG) {
            Log.i(TAG, "addPresetFromCustom() wrote current preset levels to index: "
                    + writtenToIndex);
        }
        setPreset(writtenToIndex);
        savePresetsDelayed();
        return writtenToIndex;
    }

    /**
     * Loops through all presets. And finds the first preset that can be written to. If one is not
     * found, then one is inserted, and that new index is returned.
     *
     * @return the index that the levels were copied to
     */
    private int addPreset(float[] levels) {
        final int customPresets = Constants.getCustomPresets(mContext, mNumBands).size();
        // format the name so it's like "Custom <N>", start with "Custom 2"
        final String name = String.format(mContext.getString(R.string.custom_n), customPresets + 2);

        Preset.CustomPreset customPreset = new Preset.CustomPreset(name, levels, false);
        mEqPresets.add(customPreset);

        mConfig.getCallbacks().notifyPresetsChanged();

        return mEqPresets.size() - 1;
    }

    /**
     * Set a new level!
     * <p/>
     * This call will be propogated to all listeners registered with addEqStateChangeCallback().
     *
     * @param band         the band index the band index which changed
     * @param dB           the new decibel value
     * @param systemChange is this change generated by the system?
     */
    public void setLevel(final int band, final float dB, final boolean fromSystem) {
        if (DEBUG) Log.i(TAG, "setLevel(" + band + ", " + dB + ", " + fromSystem + ")");

        mGlobalLevels[band] = dB;

        if (fromSystem && !mConfig.isUserDeviceOverride()) {
            // quickly convert decibel to millibel and send away to the service
            mHandler.obtainMessage(MSG_SEND_EQ_OVERRIDE, band, (short) (dB * 100)).sendToTarget();
        }

        mConfig.getCallbacks().notifyBandLevelChangeChanged(band, dB, fromSystem);

        if (!fromSystem) { // user is touching
            // persist

            final Preset preset = mEqPresets.get(mCurrentPreset);
            if (preset instanceof Preset.CustomPreset) {
                if (mAnimatingToCustom.get()) {
                    if (DEBUG) {
                        Log.d(TAG, "setLevel() not persisting new custom band becuase animating.");
                    }
                } else {
                    ((Preset.CustomPreset) preset).setLevel(band, dB);
                    if (preset instanceof Preset.PermCustomPreset) {
                        // store these as millibels
                        String levels = EqUtils.floatLevelsToString(
                                EqUtils.convertDecibelsToMillibels(
                                        preset.getLevels()));
                        setGlobalPref("custom", levels);
                    }
                }
                // needs to be updated immediately here for the service.
                final String levels = EqUtils.floatLevelsToString(preset.getLevels());
                setPref(Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS, levels);

                mConfig.updateService(AudioFxService.EQ_CHANGED);
            }
            savePresetsDelayed();
        }
    }

    /**
     * Set a new preset index.
     * <p/>
     * This call will be propogated to all listeners registered with addEqStateChangeCallback().
     *
     * @param newPresetIndex the new preset index.
     */
    public void setPreset(final int newPresetIndex, boolean updateBackend) {
        mCurrentPreset = newPresetIndex;
        updateEqControls(); // do this before callback is propogated

        mConfig.getCallbacks().notifyPresetChanged(newPresetIndex);

        // persist
        setPref(Constants.DEVICE_AUDIOFX_EQ_PRESET, String.valueOf(newPresetIndex));

        // update mGlobalLevels
        float[] newlevels = getPresetLevels(newPresetIndex);
        for (int i = 0; i < newlevels.length; i++) {
            setLevel(i, newlevels[i], true);
        }

        setPref(Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS, EqUtils.floatLevelsToString(newlevels));

        if (updateBackend) {
            mConfig.updateService(AudioFxService.EQ_CHANGED);
        }
    }

    public void setPreset(final int newPresetIndex) {
        setPreset(newPresetIndex, true);
    }

    private void updateEqControls() {
        final boolean userPreset = isUserPreset();
        mConfig.getCallbacks().notifyEqControlStateChanged(
                mEQCustomPresetPosition == mCurrentPreset,
                userPreset, userPreset, userPreset);
    }

    /**
     * @return Get the current preset index
     */
    public int getCurrentPresetIndex() {
        return mCurrentPreset;
    }

    /*===============
     * eq methods
     *===============*/

    public float projectX(double freq) {
        double pos = Math.log(freq);
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return (float) ((pos - minPos) / (maxPos - minPos));
    }

    public double reverseProjectX(float pos) {
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return Math.exp(pos * (maxPos - minPos) + minPos);
    }

    public float projectY(double dB) {
        double pos = (dB - mMinDB) / (mMaxDB - mMinDB);
        return (float) (1 - pos);
    }

    public static double lin2dB(double rho) {
        return rho != 0 ? Math.log(rho) / Math.log(10) * 20 : -99.9;
    }

    public float getMinFreq() {
        return mMinFreq;
    }

    public float getMaxFreq() {
        return mMaxFreq;
    }

    public float getMinDB() {
        return mMinDB;
    }

    public float getMaxDB() {
        return mMaxDB;
    }

    public int getNumBands() {
        return mNumBands;
    }

    public float getCenterFreq(int band) {
        return mCenterFreqs[band];
    }

    public float[] getCenterFreqs() {
        return mCenterFreqs;
    }

    public float[] getLevels() {
        return mGlobalLevels;
    }

    public float getLevel(int band) {
        return mGlobalLevels[band];
    }

    /*===============
     * preset methods
     *===============*/

    public float[] getPersistedPresetLevels(int presetIndex) {
        String newLevels = null;

        if (mEqPresets.size() > presetIndex
                && mEqPresets.get(presetIndex) instanceof Preset.PermCustomPreset) {
            return getPersistedCustomLevels();
        } else {
            newLevels = getGlobalPref("equalizer.preset." + presetIndex, mZeroedBandString);
        }

        // stored as millibels, convert to decibels
        float[] levels = EqUtils.stringBandsToFloats(newLevels);
        return EqUtils.convertMillibelsToDecibels(levels);
    }

    private float[] getPersistedCustomLevels() {
        String newLevels = getGlobalPref("custom", mZeroedBandString);
        // stored as millibels, convert to decibels
        float[] levels = EqUtils.stringBandsToFloats(newLevels);
        return EqUtils.convertMillibelsToDecibels(levels);
    }

    /**
     * Get preset levels in decibels for a given index
     *
     * @param presetIndex index which to fetch preset levels for
     * @return an array of floats[] with the given index's preset levels
     */
    public float[] getPresetLevels(int presetIndex) {
        return mEqPresets.get(presetIndex).getLevels();
    }

    /**
     * Helper method which maps a preset index to a color value.
     *
     * @param index the preset index which to fetch a color for
     * @return a color which is associated with this preset.
     */
    public int getAssociatedPresetColorHex(int index) {
        int r = -1;
        index = index % mEqPresets.size();
        if (mEqPresets.get(index) instanceof Preset.CustomPreset) {
            r = R.color.preset_custom;
        } else {
            switch (index) {
                case 0:
                    r = R.color.preset_normal;
                    break;
                case 1:
                    r = R.color.preset_classical;
                    break;
                case 2:
                    r = R.color.preset_dance;
                    break;
                case 3:
                    r = R.color.preset_flat;
                    break;
                case 4:
                    r = R.color.preset_folk;
                    break;
                case 5:
                    r = R.color.preset_metal;
                    break;
                case 6:
                    r = R.color.preset_hiphop;
                    break;
                case 7:
                    r = R.color.preset_jazz;
                    break;
                case 8:
                    r = R.color.preset_pop;
                    break;
                case 9:
                    r = R.color.preset_rock;
                    break;
                case 10:
                    r = R.color.preset_electronic;
                    break;
                case 11:
                    r = R.color.preset_small_speakers;
                    break;
                default:
                    return r;
            }
        }
        return mContext.getResources().getColor(r);
    }

    /**
     * Get total number of presets
     *
     * @return int value with total number of presets
     */
    public int getPresetCount() {
        return mEqPresets.size();
    }

    public Preset getPreset(int index) {
        return mEqPresets.get(index);
    }

    public String getLocalizedPresetName(int index) {
        // already localized
        return localizePresetName(mEqPresets.get(index).getName());
    }

    private final String localizePresetName(final String name) {
        // missing electronic, multimedia, small speakers, custom
        final String[] names = {
                "Normal", "Classical", "Dance", "Flat", "Folk",
                "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock",
                "Electronic", "Small speakers", "Multimedia",
                "Custom"
        };
        final int[] ids = {
                R.string.normal, R.string.classical, R.string.dance, R.string.flat, R.string.folk,
                R.string.heavy_metal, R.string.hip_hop, R.string.jazz, R.string.pop, R.string.rock,
                R.string.electronic, R.string.small_speakers, R.string.multimedia,
                R.string.custom
        };

        for (int i = names.length - 1; i >= 0; --i) {
            if (names[i].equalsIgnoreCase(name)) {
                return mContext.getString(ids[i]);
            }
        }
        return name;
    }

    public boolean isEqualizerLocked() {
        return getCurrentPreset() instanceof Preset.CustomPreset
                && !(getCurrentPreset() instanceof Preset.PermCustomPreset)
                && ((Preset.CustomPreset) getCurrentPreset()).isLocked();
    }

    public void renameCurrentPreset(String s) {
        if (isUserPreset()) {
            ((Preset.CustomPreset) getCurrentPreset()).setName(s);
        }

        mConfig.getCallbacks().notifyPresetsChanged();

        savePresetsDelayed();
    }

    public boolean removePreset(int index) {
        if (index > mEQCustomPresetPosition) {
            mEqPresets.remove(index);
            mConfig.getCallbacks().notifyPresetsChanged();

            if (mCurrentPreset == index) {
                if (DEBUG) {
                    Log.w(TAG, "removePreset() called on current preset, changing preset");
                }
                updateGlobalLevels(mCurrentPreset - 1);
                setPreset(mCurrentPreset - 1);
            }
            savePresetsDelayed();
            return true;
        }
        return false;
    }

    private void updateGlobalLevels(int presetIndexToCopy) {
        final float[] presetLevels = getPresetLevels(presetIndexToCopy);
        for (int i = 0; i < mGlobalLevels.length; i++) {
            mGlobalLevels[i] = presetLevels[i];
        }
    }

    // I AM SO LAZY!
    private String getGlobalPref(String key, String defValue) {
        return mConfig.getGlobalPrefs().getString(key, defValue);
    }

    private void setGlobalPref(String key, String value) {
        mConfig.getGlobalPrefs().edit().putString(key, value).apply();
    }

    private String getPref(String key, String defValue) {
        return mConfig.getPrefs().getString(key, defValue);
    }

    private void setPref(String key, String value) {
        mConfig.getPrefs().edit().putString(key, value).apply();
    }
}
