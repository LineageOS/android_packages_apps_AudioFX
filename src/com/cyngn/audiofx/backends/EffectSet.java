package com.cyngn.audiofx.backends;

import android.content.Context;
import android.media.audiofx.Equalizer;

import java.util.List;

/**
 * Helper class representing the full complement of effects attached to one
 * audio session.
 */
public abstract class EffectSet {

    private final int mSessionId;

    public EffectSet(int sessionId) {
        mSessionId = sessionId;
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

    public abstract void enableEqualizer(boolean enable);

    /**
     * @param levels in decibels
     */
    public void setEqualizerLevelsDecibels(float[] levels) { }

    public abstract short getNumEqualizerBands();

    /**
     * @param band
     * @param level in millibels
     */
    public abstract void setEqualizerBandLevel(short band, float level);

    /**
     * @return level in millibels
     */
    public abstract int getEqualizerBandLevel(short band);

    public abstract String getEqualizerPresetName(short preset);

    public abstract void useEqualizerPreset(short preset);

    public abstract short getNumEqualizerPresets();

    public abstract short[] getEqualizerBandLevelRange();

    /**
     * @param band
     * @return center frequency of the band in millihertz
     */
    public abstract int getCenterFrequency(short band);

    public abstract void enableBassBoost(boolean enable);

    /**
     * @param strength with range [0-1000]
     */
    public abstract void setBassBoostStrength(short strength);

    public abstract void enableVirtualizer(boolean enable);

    /**
     * @param strength with range [0-1000]
     */
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

    /**
     * @param strength with range [0-100]
     */
    public void setTrebleBoostStrength(short strength) {
        return;
    }

    public void enableVolumeBoost(boolean enable) {
        return;
    }

    public abstract void release();

    public void disableAll() {
        enableBassBoost(false);
        enableVirtualizer(false);
        enableEqualizer(false);
        enableReverb(false);
        enableTrebleBoost(false);
        enableVolumeBoost(false);
    }

    public abstract int getBrand();

    public void setGlobalEnabled(boolean globalEnabled) { }
}
