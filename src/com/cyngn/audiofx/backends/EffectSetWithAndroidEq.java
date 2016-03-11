package com.cyngn.audiofx.backends;

import android.media.audiofx.Equalizer;
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

    /*
     * Take lots of care to not poke values that don't need
     * to be poked- this can cause audible pops.
     */
    private boolean mEqualizerEnabled = false;

    public EffectSetWithAndroidEq(int sessionId) {
        super(sessionId);
        try {
            mEqualizer = new Equalizer(1000, sessionId);
        } catch (Exception e) {
            release();
            throw e;
        }
    }


    public void enableEqualizer(boolean enable) {
        if (enable != mEqualizerEnabled) {
            mEqualizerEnabled = enable;
            mEqualizer.setEnabled(enable);
        }
    }

    @Override
    public void setEqualizerLevelsDecibels(float[] levels) {
        if (mEqualizerEnabled) {
            final short[] equalizerLevels = EqUtils.convertDecibelsToMillibelsInShorts(levels);
            for (short i = 0; i < equalizerLevels.length; i++) {
                mEqualizer.setBandLevel(i, equalizerLevels[i]);
            }
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
        if (mEqualizerEnabled) {
            mEqualizer.setBandLevel(band, (short) level);
        }
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

    @Override
    public void release() {
        if (mEqualizer != null) {
            mEqualizer.release();
        }
    }
}
