/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package org.cyanogenmod.audiofx;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPort;
import android.media.AudioSystem;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import java.util.Arrays;

import cyanogenmod.media.AudioSessionInfo;
import cyanogenmod.media.CMAudioManager;

/**
 * <p>This calls listen to events that affect DSP function and responds to them.</p>
 * <ol>
 * <li>new audio session declarations</li>
 * <li>headset plug / unplug events</li>
 * <li>preference update events.</li>
 * </ol>
 *
 * @author alankila
 */
public class HeadsetService extends Service {

    public static final String ACTION_UPDATE_PREFERENCES = "org.cyanogenmod.audiofx.UPDATE_PREFS";
    public static final String[] DEFAULT_AUDIO_DEVICES = new String[]{
            "headset", "speaker", "usb", "bluetooth", "wireless", "lineout"
    };

    static String getZeroedBandsString(int length) {
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < length; i++) {
            buff.append("0;");
        }
        buff.deleteCharAt(buff.length() - 1);
        return buff.toString();
    }

    /**
     * Helper class representing the full complement of effects attached to one
     * audio session.
     *
     * @author alankila
     */
    private static class EffectSet {
        /**
         * Session-specific equalizer
         */
        private final Equalizer mEqualizer;
        /**
         * Session-specific bassboost
         */
        private final BassBoost mBassBoost;
        /**
         * Session-specific virtualizer
         */
        private final Virtualizer mVirtualizer;

        private final PresetReverb mPresetReverb;

        private short mEqNumPresets = -1;
        private short mEqNumBands = -1;

        public EffectSet(int sessionId) {
            mEqualizer = new Equalizer(0, sessionId);
            mBassBoost = new BassBoost(0, sessionId);
            mVirtualizer = new Virtualizer(0, sessionId);
            mPresetReverb = new PresetReverb(0, sessionId);
        }

        /*
         * Take lots of care to not poke values that don't need
         * to be poked- this can cause audible pops.
         */

        public void enableEqualizer(boolean enable) {
            if (enable != mEqualizer.getEnabled()) {
                if (!enable) {
                    for (short i = 0; i < getNumEqualizerBands(); i++) {
                        mEqualizer.setBandLevel(i, (short) 0);
                    }
                }
                mEqualizer.setEnabled(enable);
            }
        }

        public void setEqualizerLevels(short[] levels) {
            if (mEqualizer.getEnabled()) {
                for (short i = 0; i < levels.length; i++) {
                    if (mEqualizer.getBandLevel(i) != levels[i]) {
                        mEqualizer.setBandLevel(i, levels[i]);
                    }
                }
            }
        }

        public short getNumEqualizerBands() {
            if (mEqNumBands < 0) {
                mEqNumBands = mEqualizer.getNumberOfBands();
            }
            return mEqNumBands;
        }

        public short getNumEqualizerPresets() {
            if (mEqNumPresets < 0) {
                mEqNumPresets = mEqualizer.getNumberOfPresets();
            }
            return mEqNumPresets;
        }

        public void enableBassBoost(boolean enable) {
            if (enable != mBassBoost.getEnabled()) {
                if (!enable) {
                    mBassBoost.setStrength((short) 1);
                    mBassBoost.setStrength((short) 0);
                }
                mBassBoost.setEnabled(enable);
            }
        }

        public void setBassBoostStrength(short strength) {
            if (mBassBoost.getEnabled() && mBassBoost.getRoundedStrength() != strength) {
                mBassBoost.setStrength(strength);
            }
        }

        public void enableVirtualizer(boolean enable) {
            if (enable != mVirtualizer.getEnabled()) {
                if (!enable) {
                    mVirtualizer.setStrength((short) 1);
                    mVirtualizer.setStrength((short) 0);
                }
                mVirtualizer.setEnabled(enable);
            }
        }

        public void setVirtualizerStrength(short strength) {
            if (mVirtualizer.getEnabled() && mVirtualizer.getRoundedStrength() != strength) {
                mVirtualizer.setStrength(strength);
            }
        }

        public void enableReverb(boolean enable) {
            if (enable != mPresetReverb.getEnabled()) {
                if (!enable) {
                    mPresetReverb.setPreset((short) 0);
                }
                mPresetReverb.setEnabled(enable);
            }
        }

        public void setReverbPreset(short preset) {
            if (mPresetReverb.getEnabled() && mPresetReverb.getPreset() != preset) {
                mPresetReverb.setPreset(preset);
            }
        }

        public void release() {
            mEqualizer.release();
            mBassBoost.release();
            mVirtualizer.release();
            mPresetReverb.release();
        }
    }

    protected static final String TAG = HeadsetService.class.getSimpleName();
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);


    private void addSession(int sessionId) {
        if (sessionId == 0) {
            return;
        }
        if (DEBUG) Log.i(TAG, String.format("New audio session: %d", sessionId));

        synchronized (mAudioSessionsL) {
            if (mAudioSessionsL.indexOfKey(sessionId) < 0) {
                mAudioSessionsL.put(sessionId, new EffectSet(sessionId));
            }
            updateLocked();
        }
    }

    private void removeSession(int sessionId) {
        if (sessionId == 0) {
            return;
        }
        if (DEBUG) Log.i(TAG, String.format("Audio session removed: %d", sessionId));

        synchronized (mAudioSessionsL) {
            EffectSet gone = mAudioSessionsL.removeReturnOld(sessionId);
            if (gone != null) {
                gone.release();
            }
        }
    }

    public void addSession(AudioSessionInfo info) {
        if (info.getStream() == AudioManager.STREAM_MUSIC &&
                (info.getFlags() < 0 || (info.getFlags() & 0x8) > 0 || (info.getFlags() & 0x10) > 0) &&
                (info.getChannelMask() < 0 || info.getChannelMask() > 1)) {

            // Never auto-attach is someone is recording! We don't want to
            // interfere with any sort of
            // loopback mechanisms.
            final boolean recording = AudioSystem.isSourceActive(0)
                    || AudioSystem.isSourceActive(6);
            if (recording) {
                Log.w(TAG, "Recording in progress, not performing auto-attach!");
                return;
            }
            addSession(info.getSessionId());
        }
    }

    public void removeSession(AudioSessionInfo info) {
        if (info.getStream() == AudioManager.STREAM_MUSIC) {
            removeSession(info.getSessionId());
        }
    }

    public class LocalBinder extends Binder {
        public HeadsetService getService() {
            return HeadsetService.this;
        }
    }

    private final LocalBinder mBinder = new LocalBinder();

    /**
     * Known audio sessions and their associated audioeffect suites.
     */
    private final SparseArray<EffectSet> mAudioSessionsL = new SparseArray<EffectSet>();

    AudioPortListener mAudioPortListener;

    /**
     * Has DSPManager assumed control of equalizer levels?
     */
    private float[] mOverriddenEqualizerLevels;

    /**
     * Update audio parameters when preferences have been updated.
     */
    private final BroadcastReceiver mPreferenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Preferences updated.");
            update();
        }
    };

    private class AudioPortListener implements AudioManager.OnAudioPortUpdateListener {
        private boolean mUseBluetooth;
        private boolean mUseHeadset;
        private boolean mUseUSB;
        private boolean mUseWifiDisplay;
        private boolean mUseSpeaker;
        private boolean mUseLineOut;

        private final Context mContext;

        public AudioPortListener(Context context) {
            mContext = context;
        }

        @Override
        public void onAudioPortListUpdate(AudioPort[] portList) {
            final boolean prevUseHeadset = mUseHeadset;
            final boolean prevUseBluetooth = mUseBluetooth;
            final boolean prevUseUSB = mUseUSB;
            final boolean prevUseWireless = mUseWifiDisplay;
            final boolean prevUseSpeaker = mUseSpeaker;
            final boolean prevUseLineOut = mUseLineOut;

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int device = am.getDevicesForStream(AudioManager.STREAM_MUSIC);
            mUseBluetooth = (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_SCO) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_SCO_CARKIT) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_SCO_HEADSET) != 0;

            mUseHeadset = (device & AudioManager.DEVICE_OUT_WIRED_HEADPHONE) != 0
                    || (device & AudioManager.DEVICE_OUT_WIRED_HEADSET) != 0;

            mUseLineOut = (device & AudioManager.DEVICE_OUT_LINE) != 0;

            mUseUSB = (device & AudioManager.DEVICE_OUT_USB_ACCESSORY) != 0
                    || (device & AudioManager.DEVICE_OUT_USB_DEVICE) != 0;

            mUseWifiDisplay = false; //TODO add support for wireless display..

            mUseSpeaker = (device & AudioManager.DEVICE_OUT_SPEAKER) != 0;

            Log.i(TAG, "Headset=" + mUseHeadset + "; Bluetooth="
                    + mUseBluetooth + " ; USB=" + mUseUSB + "; Speaker=" + mUseSpeaker +
                    "; Line out=" + mUseLineOut);

            if (prevUseHeadset != mUseHeadset
                    || prevUseBluetooth != mUseBluetooth
                    || prevUseUSB != mUseUSB
                    || prevUseWireless != mUseWifiDisplay
                    || prevUseSpeaker != mUseSpeaker
                    || prevUseLineOut != mUseLineOut) {

                update();

                Intent i = new Intent(ACTION_UPDATE_PREFERENCES);
                mContext.sendBroadcast(i);
            }
        }

        @Override
        public void onAudioPatchListUpdate(AudioPatch[] patchList) {

        }

        @Override
        public void onServiceDied() {

        }

        public String getInternalAudioOutputRouting() {
            if (mUseSpeaker) {
                return "speaker";
            }
            if (mUseBluetooth) {
                return "bluetooth";
            }
            if (mUseHeadset) {
                return "headset";
            }
            if (mUseUSB) {
                return "usb";
            }
            if (mUseWifiDisplay) {
                return "wireless";
            }
            if (mUseLineOut) {
                return "lineout";
            }
            return "speaker";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Starting service.");

        registerReceiver(mPreferenceUpdateReceiver,
                new IntentFilter(ACTION_UPDATE_PREFERENCES));

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.registerAudioPortUpdateListener(mAudioPortListener = new AudioPortListener(this));

        saveDefaults();
    }


    /**
     * maps {@link AudioEffect#EXTRA_CONTENT_TYPE} to an AudioManager.STREAM_* item
     */
    private static int mapContentTypeToStream(int contentType) {
        switch (contentType) {
            case AudioEffect.CONTENT_TYPE_VOICE:
                return AudioManager.STREAM_VOICE_CALL;
            case AudioEffect.CONTENT_TYPE_GAME:
                // explicitly don't support game effects right now
                return -1;
            case AudioEffect.CONTENT_TYPE_MOVIE:
            case AudioEffect.CONTENT_TYPE_MUSIC:
            default:
                return AudioManager.STREAM_MUSIC;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (DEBUG) {
                Log.i(TAG, "onStartCommand() called with " + "intent = [" + intent + "], flags = ["
                        + flags + "], startId = [" + startId + "], extras = [" +
                        (intent.getExtras() == null ? "null" : intent.getExtras().toString())
                        + "]");
            }
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            int stream = mapContentTypeToStream(
                    intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE,
                            AudioEffect.CONTENT_TYPE_MUSIC));

            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                if (DEBUG) {
                    Log.i(TAG, String.format("New audio session: %d package: %s contentType=%d",
                            sessionId, pkg, stream));
                }
                addSession(sessionId);

            } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {

                removeSession(sessionId);

            } else if (action.equals(CMAudioManager.ACTION_AUDIO_SESSIONS_CHANGED)) {

                final AudioSessionInfo info = (AudioSessionInfo) intent.getParcelableExtra(
                        CMAudioManager.EXTRA_SESSION_INFO);
                if (info != null && info.getSessionId() > 0) {
                    boolean added = intent.getBooleanExtra(CMAudioManager.EXTRA_SESSION_ADDED,
                            false);
                    if (added) {
                        addSession(info);
                    } else {
                        removeSession(info);
                    }
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service.");

        unregisterReceiver(mPreferenceUpdateReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Gain temporary control over the global equalizer.
     * Used by DSPManager when testing a new equalizer setting.
     *
     * @param levels
     */
    public void setEqualizerLevels(float[] levels) {
        mOverriddenEqualizerLevels = levels;
        update();
    }

    /**
     * There appears to be no way to find out what the current actual audio routing is.
     * For instance, if a wired headset is plugged in, the following objects/classes are involved:
     * </p>
     * <ol>
     * <li>wiredaccessoryobserver</li>
     * <li>audioservice</li>
     * <li>audiosystem</li>
     * <li>audiopolicyservice</li>
     * <li>audiopolicymanager</li>
     * </ol>
     * <p>Once the decision of new routing has been made by the policy manager, it is relayed to
     * audiopolicyservice, which waits for some time to let application buffers drain, and then
     * informs it to hardware. The full chain is:</p>
     * <ol>
     * <li>audiopolicymanager</li>
     * <li>audiopolicyservice</li>
     * <li>audiosystem</li>
     * <li>audioflinger</li>
     * <li>audioeffect (if any)</li>
     * </ol>
     * <p>However, the decision does not appear to be relayed to java layer, so we must
     * make a guess about what the audio output routing is.</p>
     *
     * @return string token that identifies configuration to use
     */
    public String getAudioOutputRouting() {
        if (mAudioPortListener != null) {
            return mAudioPortListener.getInternalAudioOutputRouting();
        }
        return "speaker";
    }

    public EffectSet getEffects(int session) {
        synchronized (mAudioSessionsL) {
            return mAudioSessionsL.get(session);
        }
    }

    private void saveDefaults() {
        EffectSet temp;
        try {
            temp = new EffectSet(0);
        } catch (Exception e) {
            // this is really bad- likely the media stack is broken.
            // disable ourself if we get into this state, as the service
            // will restart itself repeatedly!
            Log.e(TAG, e.getMessage(), e);
            stopSelf();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("global", 0);

        final int numBands = temp.getNumEqualizerBands();
        final int numPresets = temp.getNumEqualizerPresets();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("equalizer.number_of_presets", String.valueOf(numPresets)).apply();
        editor.putString("equalizer.number_of_bands", String.valueOf(numBands)).apply();

        // range
        short[] rangeShortArr = temp.mEqualizer.getBandLevelRange();


        editor.putString("equalizer.band_level_range", rangeShortArr[0] + ";" + rangeShortArr[1]).apply();

        // center freqs
        StringBuilder centerFreqs = new StringBuilder();
        // audiofx.global.centerfreqs
        for (short i = 0; i < numBands; i++) {
            centerFreqs.append(temp.mEqualizer.getCenterFreq(i));
            centerFreqs.append(";");

        }
        centerFreqs.deleteCharAt(centerFreqs.length() - 1);
        editor.putString("equalizer.center_freqs", centerFreqs.toString()).apply();

        // populate preset names
        StringBuilder presetNames = new StringBuilder();
        for (int i = 0; i < numPresets; i++) {
            String presetName = temp.mEqualizer.getPresetName((short) i);
            presetNames.append(presetName);
            presetNames.append("|");

            // populate preset band values
            StringBuilder presetBands = new StringBuilder();
            temp.mEqualizer.usePreset((short) i);

            for (int j = 0; j < numBands; j++) {
                // loop through preset bands
                presetBands.append(temp.mEqualizer.getBandLevel((short) j));
                presetBands.append(";");
            }
            presetBands.deleteCharAt(presetBands.length() - 1);
            editor.putString("equalizer.preset." + i, presetBands.toString()).apply();
        }
        presetNames.deleteCharAt(presetNames.length() - 1);
        editor.putString("equalizer.preset_names", presetNames.toString()).apply();
        temp.release();

        // add ci-extreme
        StringBuilder ciExtremeBuilder = new StringBuilder("0;800;400;100;1000");
        if (numBands > 5) {
            int extraBands = numBands - 5;
            for (int i = 0; i < extraBands; i++) {
                ciExtremeBuilder.insert(0, "0;");
            }
        }
        editor.putString("equalizer.preset." + numPresets, ciExtremeBuilder.toString()).apply();

        // add small-speaker
        StringBuilder ssBuilder = new StringBuilder("-170;270;50;-220;200");
        if (numBands > 5) {
            int extraBands = numBands - 5;
            for (int i = 0; i < extraBands; i++) {
                ssBuilder.insert(0,  "0;");
            }
        }
        editor.putString("equalizer.preset." + (numPresets + 1), ssBuilder.toString()).apply();
        editor.commit();

        // Enable for the speaker by default
        if (!getSharedPrefsFile("speaker").exists()) {
            SharedPreferences spk = getSharedPreferences("speaker", 0);
            spk.edit().putBoolean("audiofx.global.enable", true).apply();
            spk.edit().putString("audiofx.eq.preset", String.valueOf(numPresets + 1)).apply();
        }
    }

    /**
     * Push new configuration to audio stack.
     */
    void update() {
        synchronized (mAudioSessionsL) {
            updateLocked();
        }
    }

    private void updateLocked() {
        final String mode = getAudioOutputRouting();
        SharedPreferences preferences = getSharedPreferences(
                mode, 0);

        if (DEBUG) Log.i(TAG, "Selected configuration: " + mode);

        for (int i = 0; i < mAudioSessionsL.size(); i++) {
            updateDsp(preferences, mAudioSessionsL.valueAt(i));
        }
    }

    private void updateDsp(SharedPreferences prefs, EffectSet session) {
        final boolean globalEnabled = prefs.getBoolean("audiofx.global.enable", false);

        try {
            session.enableBassBoost(globalEnabled && prefs.getBoolean("audiofx.bass.enable", false));
            session.setBassBoostStrength(Short.valueOf(prefs
                    .getString("audiofx.bass.strength", "0")));

        } catch (Exception e) {
            Log.e(TAG, "Error enabling bass boost!", e);
        }

        try {
            short preset = Short.decode(prefs.getString("audiofx.reverb.preset",
                    String.valueOf(PresetReverb.PRESET_NONE)));
            session.enableReverb(globalEnabled && (preset > 0));
            session.setReverbPreset(preset);

        } catch (Exception e) {
            Log.e(TAG, "Error enabling reverb preset", e);
        }

        try {
            session.enableEqualizer(globalEnabled);
            final int customPresetPos = session.getNumEqualizerPresets() + 2;
            final int preset = Integer.valueOf(prefs.getString("audiofx.eq.preset",
                    String.valueOf(customPresetPos)));
            final int bands = session.getNumEqualizerBands();

            /*
             * Equalizer state is in a single string preference with all values
             * separated by ;
             */
            String[] levels = null;
            short[] equalizerLevels = null;

            if (mOverriddenEqualizerLevels != null) {

            } else if (preset == customPresetPos) {
                if (DEBUG) Log.i(TAG, "loading custom band levels");
                levels = prefs.getString("audiofx.eq.bandlevels.custom",
                        getZeroedBandsString(bands)).split(";");
            } else {
                if (DEBUG) Log.i(TAG, "loading preset band levels");
                levels = getSharedPreferences("global", 0).getString("equalizer.preset." + preset,
                        getZeroedBandsString(bands)).split(";");
            }

            if (levels != null) {
                if (DEBUG) Log.i(TAG, "band levels applied: " + Arrays.toString(levels));
                equalizerLevels = new short[levels.length];
                for (int i = 0; i < levels.length; i++) {
                    equalizerLevels[i] = (short) (Float.parseFloat(levels[i]));
                }
            } else if (mOverriddenEqualizerLevels != null) {
                equalizerLevels = new short[mOverriddenEqualizerLevels.length];
                for (int i = 0; i < mOverriddenEqualizerLevels.length; i++) {
                    equalizerLevels[i] = (short) mOverriddenEqualizerLevels[i];
                }
            }
            if (equalizerLevels != null) {
                session.setEqualizerLevels(equalizerLevels);
            }


        } catch (Exception e) {
            Log.e(TAG, "Error enabling equalizer!", e);
        }

        try {
            session.enableVirtualizer(globalEnabled
                    && prefs.getBoolean("audiofx.virtualizer.enable", false));
            session.setVirtualizerStrength(Short.valueOf(prefs.getString(
                    "audiofx.virtualizer.strength", "0")));

        } catch (Exception e) {
            Log.e(TAG, "Error enabling virtualizer!");
        }
    }
}
