package com.cyngn.audiofx.backends;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.util.Log;
import android.util.SparseArray;

/**
 * EffectSet which comprises standard Android effects
 */
class AndroidEffects extends EffectSetWithAndroidEq {

    private final SparseArray<Short> mCache = new SparseArray<Short>();

    /**
     * Session-specific bassboost
     */
    private final BassBoost mBassBoost;

    /**
     * Session-specific virtualizer
     */
    private final Virtualizer mVirtualizer;

    /**
     * Session-specific reverb
     */
    private final PresetReverb mPresetReverb;

    public AndroidEffects(int sessionId) {
        super(sessionId);

        try {
            mBassBoost = new BassBoost(1000, sessionId);
            mVirtualizer = new Virtualizer(1000, sessionId);
            mPresetReverb = new PresetReverb(1000, sessionId);

            addEffects(mBassBoost, mVirtualizer, mPresetReverb);
        } catch (Exception e) {
            release();
            throw e;
        }
    }

    @Override
    public boolean hasVirtualizer() {
        return mVirtualizer.getStrengthSupported();
    }

    @Override
    public boolean hasBassBoost() {
        return mBassBoost.getStrengthSupported();
    }

    @Override
    public void enableBassBoost(boolean enable) {
        if (enable == mBassBoost.getEnabled()) {
            return;
        }
        try {
            mBassBoost.setEnabled(enable);
        } catch (Exception e) {
            Log.e(TAG, "Unable to " + (enable ? "enable" : "disable") + " bass boost!", e);
        }
    }

    @Override
    public void setBassBoostStrength(short strength) {
        setParameterSafe(mBassBoost, BassBoost.PARAM_STRENGTH, strength);
    }

    @Override
    public void enableVirtualizer(boolean enable) {
        if (enable == mVirtualizer.getEnabled()) {
            return;
        }
        try {
            mVirtualizer.setEnabled(enable);
        } catch (Exception e) {
            Log.e(TAG, "Unable to " + (enable ? "enable" : "disable") + " virtualizer!", e);
        }
    }

    @Override
    public void setVirtualizerStrength(short strength) {
        setParameterSafe(mVirtualizer, Virtualizer.PARAM_STRENGTH, strength);
    }

    @Override
    public void enableReverb(boolean enable) {
        if (enable == mPresetReverb.getEnabled()) {
            return;
        }
        try {
            mPresetReverb.setEnabled(enable);
        } catch (Exception e) {
            Log.e(TAG, "Unable to " + (enable ? "enable" : "disable") + " preset reverb!", e);
        }
    }

    @Override
    public void setReverbPreset(short preset) {
        setParameterSafe(mPresetReverb, PresetReverb.PARAM_PRESET, preset);
    }

    @Override
    public int getBrand() {
        return EffectsFactory.ANDROID;
    }

    private synchronized void setParameterSafe(AudioEffect e, int p, short v) {
        if (mCache.indexOfKey(p) >= 0 && v == mCache.get(p)) {
            return;
        }
        if (!e.hasControl()) {
            return;
        }
        try {
            e.setParameter(p, v);
            mCache.put(p, v);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to set param " + p + " for effect " + e.getDescriptor().name, ex);
        }
    }
}
