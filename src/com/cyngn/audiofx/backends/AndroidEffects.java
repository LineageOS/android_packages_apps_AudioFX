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
    private final BassBoost mBassBoost;
    private boolean mBassBoostEnabled = false;

    /**
     * Session-specific virtualizer
     */
    private final Virtualizer mVirtualizer;
    private boolean mVirtualizerEnabled = false;

    /**
     * Session-specific reverb
     */
    private final PresetReverb mPresetReverb;
    private boolean mPresetReverbEnabled = false;

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
        mBassBoostEnabled = enable;
    }

    @Override
    public void setBassBoostStrength(short strength) {
        if (mBassBoostEnabled) {
            mBassBoost.setStrength(strength);
        }
    }

    @Override
    public void enableVirtualizer(boolean enable) {
        mVirtualizer.setEnabled(enable);
        mVirtualizerEnabled = enable;
    }

    @Override
    public void setVirtualizerStrength(short strength) {
        if (mVirtualizerEnabled) {
            mVirtualizer.setStrength(strength);
        }
    }

    @Override
    public void enableReverb(boolean enable) {
        mPresetReverb.setEnabled(enable);
        mPresetReverbEnabled = enable;
    }

    @Override
    public void setReverbPreset(short preset) {
        if (mPresetReverbEnabled) {
            mPresetReverb.setPreset(preset);
        }
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
