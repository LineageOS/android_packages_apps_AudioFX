/*
 * SPDX-FileCopyrightText: 2023-2024 Paranoid Android
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.backends;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.lineageos.audiofx.Constants;

import java.util.List;
import java.util.UUID;

/**
 * EffectSet backed by the Dolby DAP effect. The effect is global (session 0), so there must only
 * be one live instance, see SessionManager.
 *
 * The UI exposes ten bands, the DAP  wants twenty (every odd one is the average of its neighbours), both
 * in 1/10 dB (+-100 = +-10 dB).
 */
class DolbyEffects extends EffectSet {

    static final UUID EFFECT_UUID_DAP = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");

    private static final int PRIORITY = 100;
    // Dynamic profile, the default of the Dolby service
    private static final int PROFILE = 0;

    private static final int PARAM_HEADPHONE_VIRTUALIZER = 101;
    private static final int PARAM_SPEAKER_VIRTUALIZER = 102;
    private static final int PARAM_GEQ_BAND_GAINS = 110;
    private static final int PARAM_BASS_ENHANCER_ENABLE = 111;
    private static final int PARAM_STEREO_WIDENING_AMOUNT = 113;

    private static final int[] STEREO_WIDENING_STEPS = {4, 24, 44, 64};

    private static final int[] BAND_FREQS_HZ = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000,
            16000};

    private static final String[] PRESET_NAMES = {"Flat", "Rock", "Jazz", "Pop", "Classical", "Hip Hop", "Blues", "Electronic", "Metal"};

    // ten band presets, 1/10 dB
    private static final int[][] PRESETS = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Flat (off)
        {60, 12, -36, -12, -4, -36, -4, -36, 4, 60}, // Rock
        {8, 8, 8, -8, -8, -40, 0, 8, 8, 8}, // Jazz
        {-13, 11, -61, 3, 19, 19, 11, -29, 11, 19}, // Pop
        {-32, -32, -32, -32, -24, 16, -16, 64, 0, 64}, // Classical
        {52, 4, -44, -4, -4, -44, -4, 4, 4, 36}, // Hip Hop
        {28, 28, -100, -36, 44, 12, -4, 12, -4, 28}, // Blues
        {50, 18, -14, 2, -6, -46, -6, 2, 2, 2}, // Electronic
        {40, 8, 8, -16, -8, -56, 8, 8, 8, 8}, // Metal
    };

    private Dap mDap;
    private int[] mGains;
    private boolean mDolbyOn;
    private AudioManager mAudioManager;
    private AudioManager.AudioPlaybackCallback mPlaybackCallback;

    DolbyEffects(int sessionId, AudioDeviceInfo deviceInfo) {
        super(sessionId, deviceInfo);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        mGains = new int[BAND_FREQS_HZ.length];
        mDap = new Dap(PRIORITY, 0);
    }

    /**
     * The service restores the profile on every playback start, as the DAP can lose it.
     */
    void registerPlaybackCallback(AudioManager audioManager) {
        mAudioManager = audioManager;
        mPlaybackCallback = new AudioManager.AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                if (!mDolbyOn) {
                    return;
                }
                for (AudioPlaybackConfiguration c : configs) {
                    if (c.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                        applyProfile();
                        return;
                    }
                }
            }
        };
        mAudioManager.registerAudioPlaybackCallback(mPlaybackCallback,
                new Handler(Looper.getMainLooper()));
    }

    @Override
    public synchronized void release() {
        if (mAudioManager != null && mPlaybackCallback != null) {
            mAudioManager.unregisterAudioPlaybackCallback(mPlaybackCallback);
        }
        mPlaybackCallback = null;
        mAudioManager = null;
        if (mDap != null) {
            try {
                mDap.release();
            } catch (Exception e) {
                // ignored
            }
            mDap = null;
        }
        super.release();
    }

    @Override
    public int getBrand() {
        return Constants.EFFECT_TYPE_DOLBY;
    }

    @Override
    public synchronized void setGlobalEnabled(boolean globalEnabled) {
        super.setGlobalEnabled(globalEnabled);
        mDolbyOn = globalEnabled;
        try {
            checkEffect();
            mDap.setDsOn(globalEnabled);
            if (globalEnabled) {
                applyProfile();
                pushGains();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to " + (globalEnabled ? "enable" : "disable") + " Dolby!", e);
        }
    }

    @Override
    public synchronized void setDevice(AudioDeviceInfo deviceInfo) {
        super.setDevice(deviceInfo);
        if (mDolbyOn) {
            applyProfile();
        }
    }

    @Override
    public void enableEqualizer(boolean enable) {
        // the graphic equalizer is always part of the Dolby profile, nothing to toggle
    }

    @Override
    public synchronized void setEqualizerLevelsDecibels(float[] levels) {
        for (int i = 0; i < mGains.length && i < levels.length; i++) {
            mGains[i] = Math.round(levels[i] * 10f);
        }
        pushGains();
    }

    @Override
    public short getNumEqualizerBands() {
        return (short) BAND_FREQS_HZ.length;
    }

    @Override
    public synchronized void setEqualizerBandLevel(short band, float level) {
        if (band < 0 || band >= mGains.length) {
            return;
        }
        mGains[band] = Math.round(level / 10f);
        pushGains();
    }

    @Override
    public synchronized int getEqualizerBandLevel(short band) {
        return mGains[band] * 10;
    }

    @Override
    public String getEqualizerPresetName(short preset) {
        return PRESET_NAMES[preset];
    }

    @Override
    public synchronized void useEqualizerPreset(short preset) {
        System.arraycopy(PRESETS[preset], 0, mGains, 0, mGains.length);
    }

    @Override
    public short getNumEqualizerPresets() {
        return (short) PRESETS.length;
    }

    @Override
    public short[] getEqualizerBandLevelRange() {
        return new short[]{-1000, 1000};
    }

    @Override
    public int getCenterFrequency(short band) {
        return BAND_FREQS_HZ[band] * 1000;
    }

    @Override
    public boolean hasBassBoost() {
        return true;
    }

    @Override
    public synchronized void enableBassBoost(boolean enable) {
        setDapSafe(PARAM_BASS_ENHANCER_ENABLE, enable ? 1 : 0);
    }

    @Override
    public void setBassBoostStrength(short strength) {
        // not supported by Dolby
    }

    @Override
    public boolean hasVirtualizer() {
        return true;
    }

    @Override
    public synchronized void enableVirtualizer(boolean enable) {
        // the DAP picks the one matching the active endpoint
        setDapSafe(PARAM_HEADPHONE_VIRTUALIZER, enable ? 1 : 0);
        setDapSafe(PARAM_SPEAKER_VIRTUALIZER, enable ? 1 : 0);
    }

    @Override
    public synchronized void setVirtualizerStrength(short strength) {
        final int step = Math.min(STEREO_WIDENING_STEPS.length - 1,
                Math.max(0, strength * STEREO_WIDENING_STEPS.length / 1001));
        setDapSafe(PARAM_STEREO_WIDENING_AMOUNT, STEREO_WIDENING_STEPS[step]);
    }

    private synchronized void checkEffect() {
        if (mDap == null || !mDap.hasControl()) {
            Log.w(TAG, "lost control of the Dolby effect, recreating");
            if (mDap != null) {
                mDap.release();
            }
            mDap = new Dap(PRIORITY, 0);
        }
    }

    private synchronized void applyProfile() {
        try {
            checkEffect();
            mDap.setProfile(PROFILE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set the Dolby profile", e);
        }
    }

    private void setDapSafe(int param, int value) {
        try {
            checkEffect();
            mDap.setDapParameter(param, new int[]{value}, PROFILE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set Dolby param " + param, e);
        }
    }

    private void pushGains() {
        // every odd band is the average of its neighbours, the last one is copied
        final int[] gains20 = new int[20];
        for (int i = 0; i < gains20.length; i++) {
            if (i % 2 == 1 && i < 19) {
                gains20[i] = (mGains[(i - 1) / 2] + mGains[(i + 1) / 2]) / 2;
            } else {
                gains20[i] = mGains[i / 2];
            }
        }
        try {
            checkEffect();
            mDap.setDapParameter(PARAM_GEQ_BAND_GAINS, gains20, PROFILE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set the Dolby equalizer", e);
        }
    }

    /**
     * The Dolby DAP effect.
     */
    private static class Dap extends AudioEffect {

        private static final int PARAM_ENABLE = 0;
        private static final int PARAM_CPDP_VALUES = 5;
        private static final int PARAM_PROFILE = 0xA000000;
        private static final int PARAM_SET_PROFILE_PARAMETER = 0x1000000;

        Dap(int priority, int audioSession) {
            super(EFFECT_TYPE_NULL, EFFECT_UUID_DAP, priority, audioSession);
        }

        void setDsOn(boolean on) {
            setIntParam(PARAM_ENABLE, on ? 1 : 0);
            setEnabled(on);
        }

        void setProfile(int profile) {
            setIntParam(PARAM_PROFILE, profile);
        }

        void setDapParameter(int param, int[] values, int profile) {
            final byte[] buf = new byte[(values.length + 4) * 4];
            putInt(buf, 0, PARAM_SET_PROFILE_PARAMETER);
            putInt(buf, 4, values.length + 1);
            putInt(buf, 8, profile);
            putInt(buf, 12, param);
            for (int i = 0; i < values.length; i++) {
                putInt(buf, 16 + i * 4, values[i]);
            }
            checkStatus(setParameter(PARAM_CPDP_VALUES, buf));
        }

        private void setIntParam(int param, int value) {
            final byte[] buf = new byte[12];
            putInt(buf, 0, param);
            putInt(buf, 4, 1);
            putInt(buf, 8, value);
            checkStatus(setParameter(PARAM_CPDP_VALUES, buf));
        }

        private static void putInt(byte[] dst, int index, int value) {
            dst[index] = (byte) (value & 0xff);
            dst[index + 1] = (byte) ((value >>> 8) & 0xff);
            dst[index + 2] = (byte) ((value >>> 16) & 0xff);
            dst[index + 3] = (byte) ((value >>> 24) & 0xff);
        }
    }
}
