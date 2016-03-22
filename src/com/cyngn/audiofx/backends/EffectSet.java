package com.cyngn.audiofx.backends;

import android.media.audiofx.AudioEffect;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Helper class representing the full complement of effects attached to one
 * audio session.
 */
public abstract class EffectSet {

    protected static final String TAG = "AudioFx-EffectSet";

    protected final int mSessionId;

    protected final ArrayList<AudioEffect> mEffects = new ArrayList<AudioEffect>();

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
    public abstract void setEqualizerLevelsDecibels(float[] levels);

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

    public synchronized void release() {
        for (AudioEffect e : mEffects) {
            Log.d(TAG, "releasing effect: " + e.getDescriptor().name);
            try {
                e.release();
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }
        mEffects.clear();
    }

    public boolean isActive() {
        return mEffects.size() > 0;
    }

    public abstract int getBrand();

    public void setGlobalEnabled(boolean globalEnabled) {
        if (globalEnabled) {
            return;
        }
        for (AudioEffect e : mEffects) {
            try {
                e.setEnabled(false);
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }
    }

    protected void addEffects(AudioEffect... effects) {
        mEffects.addAll(Arrays.asList(effects));
    }
}
