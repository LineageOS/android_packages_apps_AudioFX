package com.cyngn.audiofx.backends;

import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;

/**
 * EffectSet which comprises standard Android effects
 */
class AndroidEffects extends EffectSet {
    
    /**
     * Session-specific bassboost
     */
    public final BassBoost mBassBoost;
    
    /**
     * Session-specific virtualizer
     */
    public final Virtualizer mVirtualizer;

    /**
     * Session-specific reverb
     */
    public final PresetReverb mPresetReverb;

    public AndroidEffects(int sessionId) {
        super(sessionId);

        mBassBoost = new BassBoost(1000, sessionId);
        mVirtualizer = new Virtualizer(1000, sessionId);
        mPresetReverb = new PresetReverb(1000, sessionId);
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
        mBassBoost.setEnabled(enable);
    }

    @Override
    public void setBassBoostStrength(short strength) {
        mBassBoost.setStrength(strength);
    }

    @Override
    public void enableVirtualizer(boolean enable) {
        mVirtualizer.setEnabled(enable);
    }

    @Override
    public void setVirtualizerStrength(short strength) {
        mVirtualizer.setStrength(strength);
    }

    @Override
    public void enableReverb(boolean enable) {
        mPresetReverb.setEnabled(enable);
    }

    @Override
    public void setReverbPreset(short preset) {
        mPresetReverb.setPreset(preset);
    }

    @Override
    public void release() {
        super.release();
        mPresetReverb.release();
        mBassBoost.release();
        mVirtualizer.release();
    }
    
    @Override
    public int getBrand() {
        return EffectsFactory.ANDROID;
    }
}
