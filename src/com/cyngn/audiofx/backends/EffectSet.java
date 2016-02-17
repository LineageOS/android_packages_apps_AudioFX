package com.cyngn.audiofx.backends;

import android.media.audiofx.Equalizer;

/**
 * Helper class representing the full complement of effects attached to one
 * audio session.
 */
public abstract class EffectSet {

    /**
     * Session-specific equalizer
     */
    private final Equalizer mEqualizer;

    private short mEqNumPresets = -1;
    private short mEqNumBands = -1;

    private final int mSessionId;

    public EffectSet(int sessionId) {
        mSessionId = sessionId;

        mEqualizer = new Equalizer(1000, sessionId);
    }

    // required effects
    public abstract boolean hasVirtualizer();

    public abstract boolean hasBassBoost();

    // optional effects
    public boolean hasTrebleBoost() {
        return false;
    }

    public boolean hasVolumeBoost() {
        return false;
    }

    public boolean hasReverb() {
        return false;
    }

    /*
     * Take lots of care to not poke values that don't need
     * to be poked- this can cause audible pops.
     */
    private boolean mEqualizerEnabled = false;

    public void enableEqualizer(boolean enable) {
        if (enable != mEqualizerEnabled) {
            mEqualizerEnabled = enable;
            mEqualizer.setEnabled(enable);
        }
    }

    public void setEqualizerLevels(short[] levels) {
        if (mEqualizerEnabled) {
            for (short i = 0; i < levels.length; i++) {
                mEqualizer.setBandLevel(i, levels[i]);
            }
        }
    }

    public short getNumEqualizerBands() {
        if (mEqNumBands < 0) {
            mEqNumBands = mEqualizer.getNumberOfBands();
        }
        return mEqNumBands;
    }

    public void setEqualizerBandLevel(short band, short level) {
        if (mEqualizerEnabled) {
            mEqualizer.setBandLevel(band, level);
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

    public abstract void enableBassBoost(boolean enable);

    public abstract void setBassBoostStrength(short strength);

    public abstract void enableVirtualizer(boolean enable);

    public abstract void setVirtualizerStrength(short strength);

    public void enableReverb(boolean enable) {
        return;
    }

    public void setReverbPreset(short preset) {
        return;
    }

    public void enableTrebleBoost(boolean enable) {
        return;
    }

    public void setTrebleBoostStrength(short strength) {
        return;
    }

    public void enableVolumeBoost(boolean enable) {
        return;
    }

    public void release() {
        mEqualizer.release();
    }

    public void disableAll() {
        enableBassBoost(false);
        enableVirtualizer(false);
        enableEqualizer(false);
        enableReverb(false);
        enableTrebleBoost(false);
        enableVolumeBoost(false);
    }

    public abstract int getBrand();
}
