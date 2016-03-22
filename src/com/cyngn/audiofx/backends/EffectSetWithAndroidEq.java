package com.cyngn.audiofx.backends;

import android.media.audiofx.Equalizer;
import android.util.Log;
import android.util.SparseArray;

import com.cyngn.audiofx.eq.EqUtils;

/**
 * Created by roman on 3/1/16.
 */
public abstract class EffectSetWithAndroidEq extends EffectSet {
    /**
     * Session-specific equalizer
     */
    private final Equalizer mEqualizer;

    private short mEqNumPresets = -1;
    private short mEqNumBands = -1;

    private final SparseArray<Short> mLevelCache = new SparseArray<Short>();

    public EffectSetWithAndroidEq(int sessionId) {
        super(sessionId);
        try {
            mEqualizer = new Equalizer(1000, sessionId);

            addEffects(mEqualizer);
        } catch (Exception e) {
            release();
            throw e;
        }
    }


    public void enableEqualizer(boolean enable) {
        if (enable == mEqualizer.getEnabled()) {
            return;
        }
        try {
            mEqualizer.setEnabled(enable);
        } catch (Exception e) {
            Log.e(TAG, "enableEqualizer failed! enable=" + enable + " sessionId=" + mSessionId, e);
        }
    }

    @Override
    public void setEqualizerLevelsDecibels(float[] levels) {
        final short[] equalizerLevels = EqUtils.convertDecibelsToMillibelsInShorts(levels);
        for (short i = 0; i < equalizerLevels.length; i++) {
            setBandLevelSafe(i, equalizerLevels[i]);
        }
    }

    public short getNumEqualizerBands() {
        if (mEqNumBands < 0) {
            mEqNumBands = mEqualizer.getNumberOfBands();
        }
        return mEqNumBands;
    }

    @Override
    public void setEqualizerBandLevel(short band, float level) {
        setBandLevelSafe(band, (short)level);
    }

    public int getEqualizerBandLevel(short band) {
        return mEqualizer.getBandLevel(band);
    }

    public String getEqualizerPresetName(short preset) {
        return mEqualizer.getPresetName(preset);
    }

    public void useEqualizerPreset(short preset) {
        mEqualizer.usePreset(preset);
    }

    public short getNumEqualizerPresets() {
        if (mEqNumPresets < 0) {
            mEqNumPresets = mEqualizer.getNumberOfPresets();
        }
        return mEqNumPresets;
    }
    public short[] getEqualizerBandLevelRange() {
        return mEqualizer.getBandLevelRange();
    }

    public int getCenterFrequency(short band) {
        return mEqualizer.getCenterFreq(band);
    }

    private synchronized void setBandLevelSafe(short band, short level) {
        if (!mEqualizer.hasControl()) {
            return;
        }
        if (mLevelCache.indexOfKey((int)band) >= 0 && level == mLevelCache.get((int)band)) {
            return;
        }
        try {
            mEqualizer.setBandLevel(band, level);
            mLevelCache.put((int)band, level);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set eq band=" + band + " level=" + level, e);
        }
    }
}
