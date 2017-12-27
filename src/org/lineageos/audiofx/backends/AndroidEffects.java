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
package org.cyanogenmod.audiofx.backends;

import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.util.Log;

import org.cyanogenmod.audiofx.Constants;

/**
 * EffectSet which comprises standard Android effects
 */
class AndroidEffects extends EffectSetWithAndroidEq {

    /**
     * Session-specific bassboost
     */
    private BassBoost mBassBoost;

    /**
     * Session-specific virtualizer
     */
    private Virtualizer mVirtualizer;

    /**
     * Session-specific reverb
     */
    private PresetReverb mPresetReverb;

    public AndroidEffects(int sessionId, AudioDeviceInfo deviceInfo) {
        super(sessionId, deviceInfo);
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        mBassBoost = new BassBoost(100, mSessionId);
        mVirtualizer = new Virtualizer(100, mSessionId);
        mPresetReverb = new PresetReverb(100, mSessionId);
    }

    @Override
    public void release() {
        super.release();

        try {
            if (mBassBoost != null) {
                mBassBoost.release();
            }
        } catch (Exception e) {
            // ignored;
        }
        try {
            if (mVirtualizer != null) {
                mVirtualizer.release();
            }
        } catch (Exception e) {
            // ignored
        }
        try {
            if (mPresetReverb != null) {
                mPresetReverb.release();
            }
        } catch (Exception e) {
            // ignored
        }
        mBassBoost = null;
        mVirtualizer = null;
        mPresetReverb = null;
    }

    @Override
    public synchronized void setDevice(AudioDeviceInfo deviceInfo) {
        super.setDevice(deviceInfo);
    }

    @Override
    public void setGlobalEnabled(boolean globalEnabled) {
        super.setGlobalEnabled(globalEnabled);

        if (!globalEnabled) {
            // disable everything. it will get explictly enabled
            // individually when necessary.
            try {
                if (mVirtualizer != null) {
                    mVirtualizer.setEnabled(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to disable virtualizer!", e);
            }
            try {
                if (mBassBoost != null) {
                    mBassBoost.setEnabled(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to disable bass boost!", e);
            }
            try {
                if (mPresetReverb != null) {
                    mPresetReverb.setEnabled(false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to disable reverb!", e);
            }
        }
    }

    @Override
    public boolean hasVirtualizer() {
        return mVirtualizer != null;
    }

    @Override
    public boolean hasReverb() {
        return mPresetReverb != null;
    }

    @Override
    public boolean hasBassBoost() {
        return mBassBoost != null;
    }

    @Override
    public void enableBassBoost(boolean enable) {
        try {
            if (mBassBoost != null) {
                mBassBoost.setEnabled(enable);
            }
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
            if (mVirtualizer != null) {
                mVirtualizer.setEnabled(enable);
            }
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
            if (mPresetReverb != null) {
                mPresetReverb.setEnabled(enable);
            }
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
        return Constants.EFFECT_TYPE_ANDROID;
    }

    private void setParameterSafe(AudioEffect e, int p, short v) {
        if (e == null) {
            return;
        }
        try {
            e.setParameter(p, v);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to set param " + p + " for effect " + e.getDescriptor().name, ex);
        }
    }
}
