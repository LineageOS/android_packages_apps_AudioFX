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
 *
 * Copyright (C) 2016 Cyanogen Inc.
 *
 * Proprietary and confidential.
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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
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
import android.util.Log;

import android.util.SparseArray;
import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.ActivityMusic;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.backends.EffectSet;
import com.cyngn.audiofx.backends.EffectsFactory;
import com.cyngn.audiofx.eq.EqUtils;
import com.cyngn.audiofx.receiver.QuickSettingsTileReceiver;
import cyanogenmod.app.CMStatusBarManager;
import cyanogenmod.app.CustomTile;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This service is responsible for applying all requested effects from the AudioFX UI.
 *
 * Since the AudioFX UI allows for different configurations based on the current output device,
 * the service is also responsible for applying the effects properly based on user configuration,
 * and the current device output state.
 */
public class AudioFxService extends Service {

    private static final String TAG = AudioFxService.class.getSimpleName();

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final boolean ENABLE_REVERB = false;

    public static final String ACTION_DEVICE_OUTPUT_CHANGED
            = "org.cyanogenmod.audiofx.ACTION_DEVICE_OUTPUT_CHANGED";

    public static final String ACTION_UPDATE_TILE = "com.cyngn.audiofx.action.UPDATE_TILE";

    public static final String EXTRA_DEVICE = "device";

    // flags for updateService to minimize DSP traffic
    public static final int EQ_CHANGED              = 0x1;
    public static final int BASS_BOOST_CHANGED      = 0x2;
    public static final int VIRTUALIZER_CHANGED     = 0x4;
    public static final int TREBLE_BOOST_CHANGED    = 0x8;
    public static final int VOLUME_BOOST_CHANGED    = 0x10;
    public static final int REVERB_CHANGED          = 0x20;
    public static final int ALL_CHANGED             = 0xFF;

    private static final int TILE_ID = 555;

    private static final int REMOVE_SESSIONS_DELAY = 10000;

    /**
     * All fields ending with L should be locked on {@link #mAudioSessionsL}
     */
    private final SparseArray<EffectSet> mAudioSessionsL = new SparseArray<EffectSet>();

    private Handler mHandler;
    private Handler mBackgroundHandler;
    private AudioOutputChangeListener mDeviceListener;
    private Locale mLastLocale;

    private AudioDeviceInfo mCurrentDevice;
    private AudioDeviceInfo mPreviousDevice;

    private FxSessionCallback mSessionCallback;

    private CustomTile mTile;
    private CustomTile.Builder mTileBuilder;

    // audio priority handler messages
    private static final int MSG_UPDATE_DSP = 100;
    private static final int MSG_ADD_SESSION = 101;
    private static final int MSG_REMOVE_SESSION = 102;
    private static final int MSG_UPDATE_FOR_SESSION = 103;
    private static final int MSG_SELF_DESTRUCT = 104;
    private static final int MSG_UPDATE_DEVICE = 105;

    // background priority messages
    private static final int MSG_BG_UPDATE_EQ_OVERRIDE = 200;

    public static class LocalBinder extends Binder {

        final WeakReference<AudioFxService> mService;

        public LocalBinder(AudioFxService service) {// added a constructor for Stub here
            mService = new WeakReference<AudioFxService>(service);
        }

        private boolean checkService() {
            if (mService.get() == null) {
                Log.e("AudioFx-LocalBinder", "Service was null!");
            }
            return mService.get() != null;
        }

        public void update(int flags) {
            if (checkService()) {
                mService.get().update(flags);
            }
        }

        public void setOverrideLevels(short band, float level) {
            if (checkService()) {
                mService.get().setOverrideLevels(band, level);
            }
        }

        public AudioDeviceInfo getCurrentDevice() {
            if (checkService()) {
                return mService.get().mDeviceListener.getCurrentDevice();
            }
            return null;
        }

        public AudioDeviceInfo getPreviousDevice() {
            if (checkService()) {
                return mService.get().mPreviousDevice;
            }
            return null;
        }

        public List<AudioDeviceInfo> getConnectedOutputs() {
            if (checkService()) {
                return mService.get().mDeviceListener.getConnectedOutputs();
            }
            return new ArrayList<AudioDeviceInfo>();
        }

        public AudioDeviceInfo getDeviceById(int id) {
            if (checkService()) {
                return mService.get().mDeviceListener.getDeviceById(id);
            }
            return null;
        }

        public EffectSet getEffect(Integer id) {
            if (checkService()) {
                synchronized (mService.get().mAudioSessionsL) {
                    final SparseArray<EffectSet> sessions = mService.get().mAudioSessionsL;
                    return sessions.get(id);
                }
            }
            return null;
        }
    }

    private void setOverrideLevels(short band, float level) {
        mBackgroundHandler.obtainMessage(MSG_BG_UPDATE_EQ_OVERRIDE, band, 0, level).sendToTarget();
    }

    private String getCurrentDeviceIdentifier() {
        return MasterConfigControl.getDeviceIdentifierString(mCurrentDevice);
    }

    private class FxSessionCallback implements AudioSystem.EffectSessionCallback {

        @Override
        public void onSessionAdded(int stream, int sessionId) {
            if (stream == AudioManager.STREAM_MUSIC) {
                if (DEBUG) {
                    Log.i(TAG, String.format("New audio session: %d", sessionId));
                }

                mHandler.sendMessageAtFrontOfQueue(Message.obtain(mHandler, MSG_ADD_SESSION,
                        sessionId));
            }
        }

        @Override
        public void onSessionRemoved(int stream, int sessionId) {
            if (stream == AudioManager.STREAM_MUSIC) {
                if (DEBUG)  {
                    Log.i(TAG, String.format("Audio session queued for removal: %d", sessionId));
                }

                mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_REMOVE_SESSION, sessionId),
                        REMOVE_SESSIONS_DELAY);
            }
        }
    }

    private class AudioServiceHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            final EffectSet session;
            switch (msg.what) {
                case MSG_ADD_SESSION:
                    /**
                     * msg.obj = sessionId
                     */
                    synchronized (mAudioSessionsL) {
                        final int sessionId = (Integer) msg.obj;
                        mHandler.removeMessages(MSG_REMOVE_SESSION, sessionId);
                        mHandler.removeMessages(MSG_UPDATE_FOR_SESSION, sessionId);
                        if (mAudioSessionsL.indexOfKey(sessionId) < 0) {
                            mAudioSessionsL.put(sessionId, EffectsFactory.createEffectSet(
                                    getApplicationContext(), sessionId));
                            if (DEBUG) Log.w(TAG, "added new EffectSet for sessionId=" + sessionId);

                            Message.obtain(mHandler, MSG_UPDATE_FOR_SESSION, ALL_CHANGED, 0, sessionId)
                                    .sendToTarget();
                        }
                    }
                    break;

                case MSG_REMOVE_SESSION:
                    /**
                     * msg.obj = sessionId
                     */
                    synchronized (mAudioSessionsL) {
                        final Integer sessionId = (Integer) msg.obj;
                        mHandler.removeMessages(MSG_ADD_SESSION, sessionId);
                        mHandler.removeMessages(MSG_UPDATE_FOR_SESSION, sessionId);
                        if (mAudioSessionsL.indexOfKey(sessionId) > -1) {
                            final EffectSet effectSet = mAudioSessionsL.removeReturnOld(sessionId);
                            if (effectSet != null) {
                                effectSet.release();
                                if (DEBUG) Log.w(TAG, "removed and released sessionId=" + sessionId);
                            }
                        }
                    }
                    break;

                case MSG_UPDATE_DSP:
                    /**
                     * msg.arg1 = update what flags
                     */
                    final String mode = getCurrentDeviceIdentifier();
                    if (DEBUG) Log.i(TAG, "Updating to configuration: " + mode);

                    // cancel updates for other effects, let them go through on the last call
                    mHandler.removeMessages(MSG_UPDATE_FOR_SESSION);
                    synchronized (mAudioSessionsL) {
                        final int N = mAudioSessionsL.size();
                        for (int i = 0; i < N; i++) {
                            final int sessionIdKey = mAudioSessionsL.keyAt(i);
                            if (!mHandler.hasMessages(MSG_REMOVE_SESSION, sessionIdKey)) {
                                Message.obtain(mHandler, MSG_UPDATE_FOR_SESSION, msg.arg1, 0, sessionIdKey)
                                        .sendToTarget();
                            }
                        }
                    }
                    break;

                case MSG_UPDATE_FOR_SESSION:
                    /**
                     * msg.arg1 = update what flags
                     * msg.arg2 = unused
                     * msg.obj = session id integer (for consistency)
                     */
                    String device = getCurrentDeviceIdentifier();
                    final Integer sessionId = (Integer) msg.obj;
                    if (DEBUG) {
                        Log.i(TAG, "updating DSP for sessionId=" + sessionId + ", device=" + device);
                    }
                    synchronized (mAudioSessionsL) {
                        session = mAudioSessionsL.get(sessionId);

                        if (!mHandler.hasMessages(MSG_REMOVE_SESSION, sessionId)) {
                            updateDsp(msg.arg1, getSharedPreferences(device, 0), session);
                        }
                    }
                    break;

                case MSG_SELF_DESTRUCT:
                    mHandler.removeMessages(MSG_SELF_DESTRUCT);
                    synchronized (mAudioSessionsL) {
                        if (mAudioSessionsL.size() == 0) {
                            stopSelf();
                            Log.w(TAG, "self destructing, no sessions active and nothing to do.");
                        } else {
                            if (DEBUG) {
                                Log.w(TAG, "failed to self destruct, mAudioSession size: "
                                        + mAudioSessionsL.size());
                            }
                        }
                    }
                    break;

                case MSG_UPDATE_DEVICE:
                    /**
                     * msg.obj = the device we expect
                     */
                    AudioDeviceInfo outputDevice = (AudioDeviceInfo) msg.obj;
                    mPreviousDevice = mCurrentDevice;
                    mCurrentDevice = outputDevice;

                    update(ALL_CHANGED);
                    Intent intent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
                    intent.putExtra("device", mCurrentDevice.getId());
                    LocalBroadcastManager.getInstance(AudioFxService.this).sendBroadcast(intent);
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
                    synchronized (mAudioSessionsL) {
                        final int N = mAudioSessionsL.size();
                        for (int i = 0; i < N; i++) {
                            final int sessionIdKey = mAudioSessionsL.keyAt(i);
                            final EffectSet effectSet = mAudioSessionsL.get(sessionIdKey);
                            if (effectSet != null && !mHandler.hasMessages(MSG_REMOVE_SESSION,
                                    sessionIdKey)) {
                                updateEqBand((short) msg.arg1, (float) msg.obj, effectSet);
                            }
                        }
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
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        handlerThread.start();

        HandlerThread backgroundThread = new HandlerThread(TAG + "-BG_WORK",
                Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();

        final Looper audioLooper = handlerThread.getLooper();
        final Looper backgroundLooper = backgroundThread.getLooper();

        mHandler = new Handler(audioLooper, new AudioServiceHandler());
        mBackgroundHandler = new Handler(backgroundLooper, new AudioBackgroundHandler());

        mDeviceListener = new AudioOutputChangeListener(this, mHandler) {
            @Override
            public void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputDevice) {
                mHandler.obtainMessage(MSG_UPDATE_DEVICE, outputDevice).sendToTarget();
            }
        };

        mDeviceListener.register();

        try {
            saveAndApplyDefaults(false);
        } catch (Exception e) {
            SharedPreferences prefs = getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);
            prefs.edit().clear().commit();
            Log.e(TAG, "Error initializing effects!", e);
            stopSelf();
        }

        mSessionCallback = new FxSessionCallback();
        AudioSystem.setEffectSessionCallback(mSessionCallback);

        updateQsTile();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.i(TAG, "onStartCommand() called with " + "intent = [" + intent + "], flags = ["
                    + flags + "], startId = [" + startId + "]");
        }
        if (intent != null && intent.getAction() != null) {
            if (ACTION_UPDATE_TILE.equals(intent.getAction())) {
                updateQsTile();
            } else {
                String action = intent.getAction();
                int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
                String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
                String contentType = intent.getStringExtra(AudioEffect.EXTRA_CONTENT_TYPE);

                if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                    if (DEBUG)  {
                        Log.i(TAG, String.format("New audio session: %d package: %s contentType=%s",
                                sessionId, pkg, contentType));
                    }
                    mSessionCallback.onSessionAdded(AudioManager.STREAM_MUSIC, sessionId);

                } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {

                    mSessionCallback.onSessionRemoved(AudioManager.STREAM_MUSIC, sessionId);

                }
            }
        }
        return START_STICKY;
    }

    private void updateQsTile() {
        if (mTileBuilder == null) {
            mTileBuilder = new CustomTile.Builder(this);
        }

        mLastLocale = getResources().getConfiguration().locale;
        final PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                new Intent(QuickSettingsTileReceiver.ACTION_TOGGLE_CURRENT_DEVICE)
                        .addFlags(Intent.FLAG_FROM_BACKGROUND)
                        .setClass(this, QuickSettingsTileReceiver.class), 0);

        final PendingIntent longPress = PendingIntent.getActivity(this, 0,
                new Intent(this, ActivityMusic.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

        final SharedPreferences prefs = getSharedPreferences(getCurrentDeviceIdentifier(), 0);
        final boolean enabled = prefs.getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE,
                DEVICE_DEFAULT_GLOBAL_ENABLE);
        String label = getString(R.string.qs_tile_label,
                MasterConfigControl.getDeviceDisplayString(this, mDeviceListener.getCurrentDevice()));

        mTileBuilder
                .hasSensitiveData(false)
                .setIcon(enabled ? R.drawable.ic_qs_visualizer_on : R.drawable.ic_qs_visualizer_off)
                .setLabel(label)
                .setContentDescription(R.string.qs_tile_content_description)
                .shouldCollapsePanel(false)
                .setOnClickIntent(pi)
                .setOnLongClickIntent(longPress);

        mTile = mTileBuilder.build();

        CMStatusBarManager.getInstance(this).publishTile(TILE_ID, mTile);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "Stopping service.");

        CMStatusBarManager.getInstance(this).removeTile(TILE_ID);

        if (mDeviceListener != null) {
            mDeviceListener.unregister();
            mDeviceListener = null;
        }

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.getLooper().quit();
            mHandler = null;
        }
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
            mBackgroundHandler.getLooper().quit();
            mBackgroundHandler = null;
        }

        AudioSystem.setEffectSessionCallback(null);

        super.onDestroy();
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
     * Temporarily override a band level. {@link #updateDsp(int flags, SharedPreferences, EffectSet)} will take
     * care of overriding the preset value when a preset is selected
     */
    private void updateEqBand(short band, float level, EffectSet effectSet) {
        if (effectSet != null) {
            effectSet.setEqualizerBandLevel(band, level);
        }
    }

    /**
     * Push new configuration to audio stack.
     */
    public void update(int flags) {
        if (mHandler == null) {
            return;
        }
        if (!mHandler.hasMessages(MSG_UPDATE_DSP)) {
            mHandler.sendMessage(Message.obtain(mHandler, MSG_UPDATE_DSP, flags, 0));

        }
        if ((flags & ALL_CHANGED) == ALL_CHANGED) {
            updateQsTile();
        }
    }

    private void updateDsp(int flags, SharedPreferences prefs, EffectSet session) {
        if (DEBUG) {
            Log.i(TAG, "updateDsp() called with " + "prefs = [" + prefs
                    + "], session = [" + session + "]");
        }
        if (session == null) {
            return;
        }

        final boolean globalEnabled = prefs.getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE,
                DEVICE_DEFAULT_GLOBAL_ENABLE);
        session.setGlobalEnabled(globalEnabled);

        // bass
        try {
            if ((flags & BASS_BOOST_CHANGED) > 0) {
                session.enableBassBoost(globalEnabled
                        && prefs.getBoolean(DEVICE_AUDIOFX_BASS_ENABLE, false));
                session.setBassBoostStrength(Short.valueOf(prefs
                        .getString(DEVICE_AUDIOFX_BASS_STRENGTH, "0")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling bass boost!", e);
        }

        if (ENABLE_REVERB) {
            try {
                if ((flags & REVERB_CHANGED) > 0) {
                    short preset = Short.decode(prefs.getString(DEVICE_AUDIOFX_REVERB_PRESET,
                            String.valueOf(PresetReverb.PRESET_NONE)));
                    session.enableReverb(globalEnabled && (preset > 0));
                    session.setReverbPreset(preset);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling reverb preset", e);
            }
        }

        try {
            if ((flags & EQ_CHANGED) > 0) {
                session.enableEqualizer(globalEnabled);

                String savedPreset = prefs.getString(DEVICE_AUDIOFX_EQ_PRESET_LEVELS, null);
                if (savedPreset != null) {
                    session.setEqualizerLevelsDecibels(EqUtils.stringBandsToFloats(savedPreset));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling equalizer!", e);
        }

        try {
            if ((flags & VIRTUALIZER_CHANGED) > 0) {
                session.enableVirtualizer(globalEnabled
                        && prefs.getBoolean(DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, false));
                session.setVirtualizerStrength(Short.valueOf(prefs.getString(
                        DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH, "0")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling virtualizer!");
        }

        // extended audio effects
        try {
            if ((flags & TREBLE_BOOST_CHANGED) > 0 && session.hasTrebleBoost()) {
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
            if ((flags & VOLUME_BOOST_CHANGED) > 0 && session.hasVolumeBoost()) {
                // maxx volume
                session.enableVolumeBoost(
                        globalEnabled && prefs.getBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling volume boost!", e);
        }
    }

    /**
     * This method sets some sane defaults for presets, device defaults, etc
     * <p/>
     * First we read presets from the system, then adjusts some setting values
     * for some better defaults!
     */
    private void saveAndApplyDefaults(boolean overridePrevious) {
        if (DEBUG) {
            Log.d(TAG, "saveAndApplyDefaults() called with overridePrevious = " +
                    "[" + overridePrevious + "]");
        }
        SharedPreferences prefs = Constants.getGlobalPrefs(this);

        final int currentPrefVer = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0);
        boolean needsPrefsUpdate = currentPrefVer < Constants.CURRENT_PREFS_INT_VERSION
                || overridePrevious;

        if (needsPrefsUpdate) {
            Log.d(TAG, "rebuilding presets due to preference upgrade from " + currentPrefVer
                    + " to " + Constants.CURRENT_PREFS_INT_VERSION);
        }

        if (prefs.getBoolean(SAVED_DEFAULTS, false) && !needsPrefsUpdate) {
            if (DEBUG) {
                Log.e(TAG, "we've already saved defaults and don't need a pref update. aborting.");
            }
            return;
        }
        EffectSet temp = EffectsFactory.createEffectSet(getApplicationContext(), 0);

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
        if (presetNames.length() > 0) {
            presetNames.deleteCharAt(presetNames.length() - 1);
        }
        editor.putString(EQUALIZER_PRESET_NAMES, presetNames.toString());


        editor.putBoolean(AUDIOFX_GLOBAL_HAS_VIRTUALIZER, temp.hasVirtualizer());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_BASSBOOST, temp.hasBassBoost());
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, temp.getBrand() == EffectsFactory.MAXXAUDIO);
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_DTS, temp.getBrand() == EffectsFactory.DTS);
        editor.commit();
        temp.release();

        applyDefaults(needsPrefsUpdate);

        prefs
                .edit()
                .putInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT,
                            Constants.CURRENT_PREFS_INT_VERSION)
                .putBoolean(Constants.SAVED_DEFAULTS, true)
                .commit();
    }


    /**
     * This method sets up some *persisted* defaults.
     * Prereq: saveDefaults() must have been run before this can apply its defaults properly.
     */
    private void applyDefaults(boolean overridePrevious) {
        if (DEBUG) {
            Log.d(TAG, "applyDefaults() called with overridePrevious = [" + overridePrevious + "]");
        }
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mLastLocale.equals(newConfig.locale)) {
            updateQsTile();
        }
    }
}
