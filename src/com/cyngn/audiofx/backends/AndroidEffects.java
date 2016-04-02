package com.cyngn.audiofx.backends;

import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.util.Log;

/**
 * EffectSet which comprises standard Android effects
 */
class AndroidEffects extends EffectSetWithAndroidEq {

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

    public AndroidEffects(int sessionId, AudioDeviceInfo deviceInfo) {
        super(sessionId, deviceInfo);

        try {
            mBassBoost = new BassBoost(1000, sessionId);
            mVirtualizer = new Virtualizer(1000, sessionId);
            mPresetReverb = new PresetReverb(1000, sessionId);
        } catch (Exception e) {
            release();
            throw e;
        }
    }

    @Override
    public void release() {
        super.release();
        mBassBoost.release();
        mVirtualizer.release();
        mPresetReverb.release();
    }

    @Override
    public void setGlobalEnabled(boolean globalEnabled) {
        if (globalEnabled != isGlobalEnabled()) {
            if (!globalEnabled) {
                // disable everything. it will get explictly enabled
                // individually when necessary.
                try {
                    mVirtualizer.setEnabled(false);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to disable virtualizer!", e);
                }
                try {
                    mBassBoost.setEnabled(false);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to disable bass boost!", e);
                }
                try {
                    mPresetReverb.setEnabled(false);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to disable reverb!", e);
                }
            }
        }
        super.setGlobalEnabled(globalEnabled);
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

    private void setParameterSafe(AudioEffect e, int p, short v) {
        if (!e.hasControl()) {
            return;
        }
        try {
            e.setParameter(p, v);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to set param " + p + " for effect " + e.getDescriptor().name, ex);
        }
    }
}
