/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.knobs;

import android.content.Context;

import org.lineageos.audiofx.Constants;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.service.AudioFxService;

public class KnobCommander {

    public static final int KNOB_BASS = 1;
    public static final int KNOB_VIRTUALIZER = 2;

    private static KnobCommander sInstance;

    private final Context mContext;
    private final MasterConfigControl mConfig;

    private KnobCommander(Context context) {
        mContext = context;
        mConfig = MasterConfigControl.getInstance(mContext);
    }

    public static KnobCommander getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KnobCommander(context);
        }
        return sInstance;
    }

    public RadialKnob.OnKnobChangeListener getRadialKnobCallback(int whichKnob) {
        switch (whichKnob) {
            case KNOB_BASS:
                return mBassKnobCallback;
            case KNOB_VIRTUALIZER:
                return mVirtualizerCallback;
            default:
                return null;
        }
    }

    public void updateBassKnob(RadialKnob bassKnob, boolean enabled) {
        if (bassKnob != null) {
            bassKnob.setValue(getBassStrength());
            bassKnob.setOn(isBassEffectEnabled());
            bassKnob.setEnabled(enabled);
        }
    }

    public void updateVirtualizerKnob(RadialKnob virtualizerKnob, boolean enabled) {
        if (virtualizerKnob != null) {
            virtualizerKnob.setValue(getVirtualizerStrength());
            virtualizerKnob.setOn(isVirtualizerEffectEnabled());
            virtualizerKnob.setEnabled(enabled);
        }
    }

    public boolean hasBassBoost() {
        return mConfig.hasBassBoost();
    }

    public boolean hasVirtualizer() {
        return mConfig.hasVirtualizer();
    }

    public boolean isBassEffectEnabled() {
        return mConfig.getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_BASS_ENABLE, false);
    }

    public boolean isVirtualizerEffectEnabled() {
        return mConfig.getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, false);
    }

    public int getVirtualizerStrength() {
        return Integer.valueOf(
                mConfig.getPrefs().getString(Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH, "0"))
                / 10;
    }

    public int getBassStrength() {
        return Integer.valueOf(
                mConfig.getPrefs().getString(Constants.DEVICE_AUDIOFX_BASS_STRENGTH, "0")) / 10;
    }

    public void setBassEnabled(boolean on) {
        mConfig.getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_BASS_ENABLE, on).apply();
        mConfig.updateService(AudioFxService.BASS_BOOST_CHANGED);
    }

    public void setBassStrength(int value) {
        // set parameter and state
        mConfig.getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_BASS_STRENGTH,
                String.valueOf(value * 10)).apply();
        mConfig.updateService(AudioFxService.BASS_BOOST_CHANGED);
    }

    public void setVirtualizerEnabled(boolean on) {
        mConfig.getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE,
                on).apply();
        mConfig.updateService(AudioFxService.VIRTUALIZER_CHANGED);
    }

    public void setVirtualiserStrength(int value) {
        // set parameter and state
        mConfig.getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH,
                String.valueOf(value * 10)).apply();
        mConfig.updateService(AudioFxService.VIRTUALIZER_CHANGED);
    }

    private final RadialKnob.OnKnobChangeListener mBassKnobCallback =
            new RadialKnob.OnKnobChangeListener() {
                @Override
                public void onValueChanged(RadialKnob knob, int value, boolean fromUser) {
                    if (fromUser) {
                        setBassStrength(value);
                    }
                }

                @Override
                public boolean onSwitchChanged(RadialKnob knob, boolean on) {
                    setBassEnabled(on);
                    return true;
                }
            };

    private final RadialKnob.OnKnobChangeListener mVirtualizerCallback =
            new RadialKnob.OnKnobChangeListener() {
                @Override
                public void onValueChanged(RadialKnob knob, int value, boolean fromUser) {
                    if (fromUser) {
                        setVirtualiserStrength(value);
                    }
                }

                @Override
                public boolean onSwitchChanged(RadialKnob knob, boolean on) {
                    setVirtualizerEnabled(on);
                    return true;
                }
            };
}
