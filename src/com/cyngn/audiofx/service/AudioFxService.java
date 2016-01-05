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
package com.cyngn.audiofx.service;

import static com.cyngn.audiofx.Constants.AUDIOFX_GLOBAL_FILE;
import static com.cyngn.audiofx.Constants.AUDIOFX_GLOBAL_HAS_BASSBOOST;
import static com.cyngn.audiofx.Constants.AUDIOFX_GLOBAL_HAS_DTS;
import static com.cyngn.audiofx.Constants.AUDIOFX_GLOBAL_HAS_MAXXAUDIO;
import static com.cyngn.audiofx.Constants.AUDIOFX_GLOBAL_HAS_VIRTUALIZER;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_BASS_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_BASS_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_EQ_PRESET;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_REVERB_PRESET;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_DEFAULT_GLOBAL_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_SPEAKER;
import static com.cyngn.audiofx.Constants.EQUALIZER_BAND_LEVEL_RANGE;
import static com.cyngn.audiofx.Constants.EQUALIZER_CENTER_FREQS;
import static com.cyngn.audiofx.Constants.EQUALIZER_NUMBER_OF_BANDS;
import static com.cyngn.audiofx.Constants.EQUALIZER_NUMBER_OF_PRESETS;
import static com.cyngn.audiofx.Constants.EQUALIZER_PRESET;
import static com.cyngn.audiofx.Constants.EQUALIZER_PRESET_NAMES;
import static com.cyngn.audiofx.Constants.SAVED_DEFAULTS;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.PresetReverb;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.util.ArrayMap;
import android.util.Log;

import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.backends.EffectSet;
import com.cyngn.audiofx.backends.EffectsFactory;
import com.cyngn.audiofx.eq.EqUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This service is responsible for applying all requested effects from the AudioFX UI.
 *
 * Since the AudioFX UI allows for different configurations based on the current output device,
 * the service is also responsible for applying the effects properly based on user configuration,
 * and the current device output state.
 */
public class AudioFxService extends Service {

    protected static final String TAG = AudioFxService.class.getSimpleName();

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final boolean ENABLE_REVERB = false;

    public static final String ACTION_DEVICE_OUTPUT_CHANGED
            = "org.cyanogenmod.audiofx.ACTION_DEVICE_OUTPUT_CHANGED";

    public static final String EXTRA_DEVICE = "device";

    private static final int CURRENT_PREFS_INT_VERSION = 1;

    private final Map<Integer, EffectSet> mAudioSessions
            = Collections.synchronizedMap(new ArrayMap<Integer, EffectSet>());

    private final List<Integer> mSessionsToRemove = Collections.synchronizedList(new ArrayList<Integer>());


    int mMostRecentSessionId;
    Handler mHandler;
    Handler mBackgroundHandler;
    AudioOutputChangeListener mDeviceListener;
    BluetoothDevice mLastBluetoothDevice;

    BluetoothAdapter mBluetoothAdapter;
    DtsControl mDts;

    private AudioDeviceInfo mCurrentDevice;
    private AudioDeviceInfo mPreviousDevice;

    // audio priority handler messages
    private static final int MSG_UPDATE_DSP = 100;
    private static final int MSG_ADD_SESSION = 101;
    private static final int MSG_REMOVE_SESSION = 102;
    private static final int MSG_UPDATE_FOR_SESSION = 103;
    private static final int MSG_SELF_DESTRUCT = 104;

    // background priority messages
    private static final int MSG_BG_UPDATE_EQ_OVERRIDE = 200;

    public static class LocalBinder extends Binder {
        WeakReference<AudioFxService> mService;

        public LocalBinder(AudioFxService service) {// added a constructor for Stub here
            mService = new WeakReference<AudioFxService>(service);
        }

        private boolean checkService() {
            if (mService.get() == null) {
                Log.e("AudioFx-LocalBinder", "Service was null!");
            }
            return mService.get() != null;
        }

        public void update() {
            if (checkService()) {
                mService.get().update();
            }
        }

        public void applyDefaults() {
            if (checkService()) {
                mService.get().forceDefaults();
            }
        }

        public void setOverrideLevels(short band, short level) {
            if (checkService()) {
                mService.get().setOverrideLevels(band, level);
            }
        }

        public AudioDeviceInfo getCurrentDevice() {
            if (checkService()) {
                return null;
            }
            return mService.get().mDeviceListener.getCurrentDevice();
        }

        public AudioDeviceInfo getPreviousDevice() {
            if (checkService()) {
                return null;
            }
            return mService.get().mPreviousDevice;
        }

        public List<AudioDeviceInfo> getConnectedOutputs() {
            if (checkService()) {
                return new ArrayList<AudioDeviceInfo>();
            }
            return mService.get().mDeviceListener.getConnectedOutputs();
        }

        public AudioDeviceInfo getDeviceById(int id) {
            if (checkService()) {
                return null;
            }
            return mService.get().mDeviceListener.getDeviceById(id);
        }
    }

    private void setOverrideLevels(short band, short level) {
        mBackgroundHandler.obtainMessage(MSG_BG_UPDATE_EQ_OVERRIDE, band, level).sendToTarget();
    }

    private String getCurrentDeviceIdentifier() {
        return MasterConfigControl.getDeviceIdentifierString(mDeviceListener.getCurrentDevice());
    }

    private class AudioServiceHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ADD_SESSION:
                    if (!mAudioSessions.containsKey(msg.arg1)) {
                        if (DEBUG) Log.d(TAG, "added new EffectSet for sessionId=" + msg.arg1);
                        mAudioSessions.put(msg.arg1, EffectsFactory.createEffectSet(msg.arg1));
                    }
                    mMostRecentSessionId = msg.arg1;
                    if (DEBUG) Log.d(TAG, "new most recent sesssionId=" + msg.arg1);

                    update();
                    break;

                case MSG_REMOVE_SESSION:
                    for (Integer id : mSessionsToRemove) {
                        EffectSet gone = mAudioSessions.remove(id);
                        if (gone != null) {
                            if (DEBUG) Log.d(TAG, "removed EffectSet for sessionId=" + id);
                            gone.release();
                        }
                        if (mMostRecentSessionId == id) {
                            if (DEBUG) Log.d(TAG, "resetting most recent session ID");
                            mMostRecentSessionId = -1;
                        }
                    }
                    mSessionsToRemove.clear();

                    update();
                    break;

                case MSG_UPDATE_DSP:
                    if (mDts.hasDts()) {
                        if (mDts.shouldUseDts()) {
                            if (DEBUG) Log.d(TAG, "forcing DTS effects");
                            disableAllEffects();

                            mDts.setEnabled(mDts.isUserEnabled());
                            break;
                        } else {
                            if (DEBUG) Log.d(TAG, "not using DTS");
                            mDts.setEnabled(false);
                        }
                    }

                    final String mode = getCurrentDeviceIdentifier();
                    if (DEBUG) Log.i(TAG, "Updating to configuration: " + mode);
                    // immediately update most recent session
                    if (mMostRecentSessionId > 0) {
                        if (DEBUG) Log.d(TAG, "updating DSP for most recent session id ("
                                + mMostRecentSessionId + ")!");
                        updateDsp(getSharedPreferences(mode, 0),
                                mAudioSessions.get(mMostRecentSessionId)
                        );

                        // cancel updates for other effects, let them go through on the last call
                        mHandler.removeMessages(MSG_UPDATE_FOR_SESSION);
                        int delay = 500;
                        for (Integer integer : mAudioSessions.keySet()) {
                            if (integer == mMostRecentSessionId) {
                                continue;
                            }
                            mHandler.sendMessageDelayed(Message.obtain(mHandler,
                                    MSG_UPDATE_FOR_SESSION, integer, 0), delay);
                            delay += 500;
                        }
                    }
                    break;

                case MSG_UPDATE_FOR_SESSION:
                    String device = getCurrentDeviceIdentifier();
                    if (DEBUG)
                        Log.i(TAG, "updating DSP for sessionId=" + msg.arg1 + ", device=" + device);
                    updateDsp(getSharedPreferences(device, 0), mAudioSessions.get(msg.arg1));
                    break;

                case MSG_SELF_DESTRUCT:
                    mHandler.removeMessages(MSG_SELF_DESTRUCT);
                    if (mAudioSessions.isEmpty()) {
                        stopSelf();
                        Log.w(TAG, "self destructing, no sessions active and nothing to do.");
                    } else {
                        if (DEBUG) {
                            Log.w(TAG, "failed to self destruct, mAudioSession size: "
                                    + mAudioSessions.size() + ", mSessionsToRemove size: "
                                    + mSessionsToRemove.size() + ", mMostRecentSession: "
                                    + mMostRecentSessionId);
                        }
                    }
                    break;
            }
            return true;
        }
    }


    private class AudioBackgroundHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BG_UPDATE_EQ_OVERRIDE:
                    if (mMostRecentSessionId != -1) {
                        updateEqBand((short) msg.arg1, (short) msg.arg2,
                                mAudioSessions.get(mMostRecentSessionId));
                    }
                    break;
            }
            return true;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.i(TAG, "Starting service.");

        HandlerThread handlerThread = new HandlerThread(TAG + "-AUDIO",
                Process.THREAD_PRIORITY_LESS_FAVORABLE);
        handlerThread.start();

        HandlerThread backgroundThread = new HandlerThread(TAG + "-BG_WORK",
                Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();

        final Looper audioLooper = handlerThread.getLooper();
        final Looper backgroundLooper = backgroundThread.getLooper();

        mHandler = new Handler(audioLooper, new AudioServiceHandler());
        mBackgroundHandler = new Handler(backgroundLooper, new AudioBackgroundHandler());

        mDts = new DtsControl(this);

        try {
            saveAndApplyDefaults(false);
        } catch (Exception e) {
            SharedPreferences prefs = getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);
            prefs.edit().clear().commit();
            Log.e(TAG, "Error initializing effects!", e);
            stopSelf();
        }

        mDeviceListener = new AudioOutputChangeListener(this, mHandler) {
            @Override
            public void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputDevice) {
                mPreviousDevice = mCurrentDevice;
                mCurrentDevice = outputDevice;
                update();

                Intent intent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
                intent.putExtra("device", mCurrentDevice.getId());
                LocalBroadcastManager.getInstance(AudioFxService.this).sendBroadcast(intent);
            }
        };

        mDeviceListener.register();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);

            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                if (DEBUG) Log.i(TAG, String.format("New audio session: %d, package: %s",
                        sessionId, pkg));

                mSessionsToRemove.remove((Integer) sessionId);
                mHandler.sendMessage(Message.obtain(mHandler, MSG_ADD_SESSION, sessionId, 0));

            } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
                if (DEBUG) Log.i(TAG, String.format("Audio session removed: %d, package: %s",
                        sessionId, pkg));

                mSessionsToRemove.add(sessionId);
                mHandler.sendMessage(Message.obtain(mHandler, MSG_REMOVE_SESSION, sessionId, 0));

            }
        }
        if (DEBUG)
            Log.i(TAG, "onStartCommand() called with " + "intent = [" + intent + "], flags = ["
                    + flags + "], startId = [" + startId + "]");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "Stopping service.");

        mDeviceListener.unregister();
        mDeviceListener = null;

        mHandler.removeCallbacksAndMessages(null);
        mBackgroundHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        mHandler.getLooper().quit();
        mBackgroundHandler.getLooper().quit();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(this);
    }

    @Override
    public void onTrimMemory(int level) {
        if (DEBUG) Log.d(TAG, "onTrimMemory: level=" + level);
        switch (level) {
            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_RUNNING_MODERATE:
            case TRIM_MEMORY_COMPLETE:
                if (DEBUG) Log.d(TAG, "killing service if no effects active.");
                mHandler.sendEmptyMessageDelayed(MSG_SELF_DESTRUCT, 1000);
                break;
        }
    }

    // ======== DSP UPDATE METHODS BELOW ============= //

    /**
     * Temporarily override a band level. {@link #updateDsp(SharedPreferences, EffectSet)} will take
     * care of overriding the preset value when a preset is selected
     */
    private synchronized void updateEqBand(short band, short level, EffectSet effectSet) {
        if (effectSet != null) {
            effectSet.setEqualizerBandLevel(band, level);
        }
    }

    /**
     * Push new configuration to audio stack.
     */
    public void update() {
        if (!mHandler.hasMessages(MSG_UPDATE_DSP)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_DSP);
        }
    }

    private void updateDsp(SharedPreferences prefs, EffectSet session) {
        if (DEBUG) {
            Log.i(TAG, "updateDsp() called with " + "prefs = [" + prefs
                    + "], session = [" + session + "]");
        }
        if (session == null) {
            return;
        }

        final boolean globalEnabled = prefs.getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE,
                DEVICE_DEFAULT_GLOBAL_ENABLE);

        // bass
        try {
            session.enableBassBoost(globalEnabled
                    && prefs.getBoolean(DEVICE_AUDIOFX_BASS_ENABLE, false));
            session.setBassBoostStrength(Short.valueOf(prefs
                    .getString(DEVICE_AUDIOFX_BASS_STRENGTH, "0")));

        } catch (Exception e) {
            Log.e(TAG, "Error enabling bass boost!", e);
        }

        if (ENABLE_REVERB) {
            try {
                short preset = Short.decode(prefs.getString(DEVICE_AUDIOFX_REVERB_PRESET,
                        String.valueOf(PresetReverb.PRESET_NONE)));
                session.enableReverb(globalEnabled && (preset > 0));
                session.setReverbPreset(preset);

            } catch (Exception e) {
                Log.e(TAG, "Error enabling reverb preset", e);
            }
        }

        try {
            session.enableEqualizer(globalEnabled);

            String savedPreset = prefs.getString(DEVICE_AUDIOFX_EQ_PRESET_LEVELS, null);
            if (savedPreset != null) {
                short[] equalizerLevels = EqUtils.convertDecibelsToMillibelsInShorts(
                        EqUtils.stringBandsToFloats(savedPreset));

                if (equalizerLevels != null && globalEnabled) {
                    session.setEqualizerLevels(equalizerLevels);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling equalizer!", e);
        }

        try {
            session.enableVirtualizer(globalEnabled
                    && prefs.getBoolean(DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, false));
            session.setVirtualizerStrength(Short.valueOf(prefs.getString(
                    DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH, "0")));

        } catch (Exception e) {
            Log.e(TAG, "Error enabling virtualizer!");
        }

        // extended audio effects
        try {
            if (session.hasTrebleBoost()) {
                // treble
                session.enableTrebleBoost(
                        globalEnabled && prefs.getBoolean(DEVICE_AUDIOFX_TREBLE_ENABLE, false));
                session.setTrebleBoostStrength(Short.valueOf(
                        prefs.getString(DEVICE_AUDIOFX_TREBLE_STRENGTH, "0")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling treble boost!", e);
        }

        try {
            if (session.hasVolumeBoost()) {
                // maxx volume
                session.enableVolumeBoost(
                        globalEnabled && prefs.getBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling volume boost!", e);
        }
    }

    private void disableAllEffects() {
        final Collection<EffectSet> values = mAudioSessions.values();
        for (EffectSet effectSet : values) {
            effectSet.disableAll();
        }
    }

    private void forceDefaults() {
        SharedPreferences prefs = getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);
        prefs.edit().putBoolean(SAVED_DEFAULTS, false).apply();
        saveAndApplyDefaults(true);
    }

    /**
     * This method sets some sane defaults for presets, device defaults, etc
     * <p/>
     * First we read presets from the system, then adjusts some setting values
     * for some better defaults!
     */
    private synchronized void saveAndApplyDefaults(boolean overridePrevious) {
        SharedPreferences prefs = getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);

        final int currentPrefVer = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0);
        boolean needsPrefsUpdate = currentPrefVer
                < CURRENT_PREFS_INT_VERSION;

        if (needsPrefsUpdate) {
            Log.d(TAG, "rebuilding presets due to preference upgrade from " + currentPrefVer
                    + " to " + CURRENT_PREFS_INT_VERSION);
        }

        if (prefs.getBoolean(SAVED_DEFAULTS, false) && !needsPrefsUpdate) {
            return;
        }
        EffectSet temp = EffectsFactory.createEffectSet(0);

        final int numBands = temp.getNumEqualizerBands();
        final int numPresets = temp.getNumEqualizerPresets();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(EQUALIZER_NUMBER_OF_PRESETS, String.valueOf(numPresets));
        editor.putString(EQUALIZER_NUMBER_OF_BANDS, String.valueOf(numBands));

        // range
        short[] rangeShortArr = temp.getEqualizerBandLevelRange();
        editor.putString(EQUALIZER_BAND_LEVEL_RANGE, rangeShortArr[0]
                + ";" + rangeShortArr[1]);

        // center freqs
        StringBuilder centerFreqs = new StringBuilder();
        // audiofx.global.centerfreqs
        for (short i = 0; i < numBands; i++) {
            centerFreqs.append(temp.getCenterFrequency(i));
            centerFreqs.append(";");

        }
        centerFreqs.deleteCharAt(centerFreqs.length() - 1);
        editor.putString(EQUALIZER_CENTER_FREQS, centerFreqs.toString());

        // populate preset names
        StringBuilder presetNames = new StringBuilder();
        for (int i = 0; i < numPresets; i++) {
            String presetName = temp.getEqualizerPresetName((short) i);
            presetNames.append(presetName);
            presetNames.append("|");

            // populate preset band values
            StringBuilder presetBands = new StringBuilder();
            temp.useEqualizerPreset((short) i);

            for (int j = 0; j < numBands; j++) {
                // loop through preset bands
                presetBands.append(temp.getEqualizerBandLevel((short) j));
                presetBands.append(";");
            }
            presetBands.deleteCharAt(presetBands.length() - 1);
            editor.putString(EQUALIZER_PRESET + i, presetBands.toString());
        }
        presetNames.deleteCharAt(presetNames.length() - 1);
        editor.putString(EQUALIZER_PRESET_NAMES, presetNames.toString());


        editor.putBoolean(AUDIOFX_GLOBAL_HAS_VIRTUALIZER, temp.hasVirtualizer());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_BASSBOOST, temp.hasBassBoost());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, temp.getBrand() == EffectsFactory.MAXXAUDIO);
        temp.release();
        editor.commit();

        applyDefaults(overridePrevious || needsPrefsUpdate);

        prefs
                .edit()
                .putInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, CURRENT_PREFS_INT_VERSION)
                .putBoolean(Constants.SAVED_DEFAULTS, true)
                .commit();
    }


    /**
     * This method sets up some *persisted* defaults.
     * Prereq: saveDefaults() must have been run before this can apply its defaults properly.
     */
    private void applyDefaults(boolean overridePrevious) {
        final SharedPreferences globalPrefs = getSharedPreferences(AUDIOFX_GLOBAL_FILE, 0);
        if (globalPrefs.getBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, false)) {
            // Maxx Audio defaults:
            // enable speaker by default, enable maxx volume, set preset to the first index,
            // which should be flat
            if (!getSharedPrefsFile(DEVICE_SPEAKER).exists() || overridePrevious) {
                getSharedPreferences(DEVICE_SPEAKER, 0)
                        .edit()
                        .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                        .putString(DEVICE_AUDIOFX_EQ_PRESET, "0")
                        .putBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, true)
                        .commit();
            }
        } else if (globalPrefs.getBoolean(AUDIOFX_GLOBAL_HAS_DTS, false)) {
            // do nothing for DTS
        } else {
            // apply defaults for all others
            if (Integer.parseInt(globalPrefs.getString(EQUALIZER_NUMBER_OF_BANDS, "0")) == 5) {

                // for 5 band configs, let's add a `Small Speaker` configuration if one
                // doesn't exist ( from oss AudioFX: -170;270;50;-220;200 )
                int currentPresets = Integer.parseInt(
                        globalPrefs.getString(EQUALIZER_NUMBER_OF_PRESETS, "0"));

                final String currentPresetNames = globalPrefs.getString(EQUALIZER_PRESET_NAMES, "");

                // we use the name as keys - get the english string so its consistent with the
                // others even if user has changed locale
                final String smallSpeakerPresetName
                        = getNonLocalizedString(R.string.small_speakers);

                // sanity check
                if (currentPresetNames.toLowerCase().contains(smallSpeakerPresetName)) {
                    // nothing to do!
                    return;
                }

                // append new preset identifier
                String newPresetNames = currentPresetNames
                        + (currentPresets > 0 ? "|" : "")
                        + smallSpeakerPresetName;

                // set this new preset as the default and enable it for speaker
                if (!getSharedPrefsFile(DEVICE_SPEAKER).exists() || overridePrevious) {
                    getSharedPreferences(DEVICE_SPEAKER, 0)
                            .edit()
                            .putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true)
                            .putString(DEVICE_AUDIOFX_EQ_PRESET, Integer.toString(currentPresets))
                            .commit();
                }

                // currentPresets is incremented below
                if (!getSharedPrefsFile(AUDIOFX_GLOBAL_FILE).exists() || overridePrevious) {
                    globalPrefs
                            .edit()
                            .putString(EQUALIZER_PRESET + currentPresets, "-170;270;50;-220;200")
                            .putString(EQUALIZER_PRESET_NAMES, newPresetNames)
                            .putString(EQUALIZER_NUMBER_OF_PRESETS,
                                    Integer.toString(++currentPresets))
                            .commit();
                }
            }
        }
    }

    private String getNonLocalizedString(int res) {
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(Locale.ROOT);
        return createConfigurationContext(config).getString(res);
    }
}
