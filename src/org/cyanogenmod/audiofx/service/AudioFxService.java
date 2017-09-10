/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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
package org.cyanogenmod.audiofx.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.cyanogenmod.audiofx.R;
import org.cyanogenmod.audiofx.activity.ActivityMusic;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import org.cyanogenmod.audiofx.backends.EffectSet;
import org.cyanogenmod.audiofx.receiver.QuickSettingsTileReceiver;

import java.lang.ref.WeakReference;
import java.util.Locale;

import cyanogenmod.app.CMStatusBarManager;
import cyanogenmod.app.CustomTile;
import cyanogenmod.media.AudioSessionInfo;
import cyanogenmod.media.CMAudioManager;

/**
 * This service is responsible for applying all requested effects from the AudioFX UI.
 *
 * Since the AudioFX UI allows for different configurations based on the current output device,
 * the service is also responsible for applying the effects properly based on user configuration,
 * and the current device output state.
 */
public class AudioFxService extends Service
        implements AudioOutputChangeListener.AudioOutputChangedCallback {

    static final String TAG = AudioFxService.class.getSimpleName();

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String ACTION_DEVICE_OUTPUT_CHANGED
            = "org.cyanogenmod.audiofx.ACTION_DEVICE_OUTPUT_CHANGED";

    public static final String ACTION_UPDATE_TILE = "org.cyanogenmod.audiofx.action.UPDATE_TILE";

    public static final String EXTRA_DEVICE = "device";

    // flags for updateService to minimize DSP traffic
    public static final int EQ_CHANGED              = 0x1;
    public static final int BASS_BOOST_CHANGED      = 0x2;
    public static final int VIRTUALIZER_CHANGED     = 0x4;
    public static final int TREBLE_BOOST_CHANGED    = 0x8;
    public static final int VOLUME_BOOST_CHANGED    = 0x10;
    public static final int REVERB_CHANGED          = 0x20;
    public static final int ALL_CHANGED             = 0xFF;

    // flags from audio.h, used by session callbacks
    static final int AUDIO_OUTPUT_FLAG_FAST = 0x4;
    static final int AUDIO_OUTPUT_FLAG_DEEP_BUFFER = 0x8;
    static final int AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD = 0x10;

    private static final int TILE_ID = 555;

    private Locale mLastLocale;

    private CustomTile mTile;
    private CustomTile.Builder mTileBuilder;

    private AudioOutputChangeListener mOutputListener;
    private DevicePreferenceManager mDevicePrefs;
    private SessionManager mSessionManager;
    private Handler mHandler;

    private AudioDeviceInfo mCurrentDevice;

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
                mService.get().mSessionManager.setOverrideLevels(band, level);
            }
        }

        public EffectSet getEffect(Integer id) {
            if (checkService()) {
                return mService.get().mSessionManager.getEffectForSession(id);
            }
            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.i(TAG, "Starting service.");

        HandlerThread handlerThread = new HandlerThread("AudioFx-Backend");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mOutputListener = new AudioOutputChangeListener(getApplicationContext(), mHandler);
        mOutputListener.addCallback(this);

        mDevicePrefs = new DevicePreferenceManager(getApplicationContext(), mCurrentDevice);
        if (!mDevicePrefs.initDefaults()) {
            stopSelf();
            return;
        }

        mSessionManager = new SessionManager(getApplicationContext(), mHandler, mDevicePrefs,
                mCurrentDevice);
        mOutputListener.addCallback(mDevicePrefs, mSessionManager);

        final CMAudioManager cma = CMAudioManager.getInstance(getApplicationContext());
        for (AudioSessionInfo asi : cma.listAudioSessions(AudioManager.STREAM_MUSIC)) {
            mSessionManager.addSession(asi);
        }

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
                update(ALL_CHANGED);
            } else {
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
                    AudioSessionInfo info = new AudioSessionInfo(sessionId, stream, -1, -1, -1);
                    mSessionManager.addSession(info);

                } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {

                    AudioSessionInfo info = new AudioSessionInfo(sessionId, stream, -1, -1, -1);
                    mSessionManager.removeSession(info);

                } else if (action.equals(CMAudioManager.ACTION_AUDIO_SESSIONS_CHANGED)) {

                    final AudioSessionInfo info = (AudioSessionInfo) intent.getParcelableExtra(
                            CMAudioManager.EXTRA_SESSION_INFO);
                    if (info != null && info.getSessionId() > 0) {
                        boolean added = intent.getBooleanExtra(CMAudioManager.EXTRA_SESSION_ADDED,
                                false);
                        if (added) {
                            mSessionManager.addSession(info);
                        } else {
                            mSessionManager.removeSession(info);
                        }
                    }

                }
            }
        }
        return START_STICKY;
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
    public synchronized void onAudioOutputChanged(boolean firstChange,
            AudioDeviceInfo outputDevice) {
        if (outputDevice == null) {
            return;
        }

        mCurrentDevice = outputDevice;

        if (DEBUG)
            Log.d(TAG, "Broadcasting device changed event");

        // Update the UI with the change
        Intent intent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
        intent.putExtra("device", outputDevice.getId());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        updateQsTile();
    }

    private void updateQsTile() {
        if (mCurrentDevice == null || mDevicePrefs == null) {
            // too early
            return;
        }
        if (mTileBuilder == null) {
            mTileBuilder = new CustomTile.Builder(this);
        }

        mLastLocale = getResources().getConfiguration().locale;
        final PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                new Intent(QuickSettingsTileReceiver.ACTION_TOGGLE_CURRENT_DEVICE)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        .setClass(this, QuickSettingsTileReceiver.class), 0);

        final PendingIntent longPress = PendingIntent.getActivity(this, 0,
                new Intent(this, ActivityMusic.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

        String label = getString(R.string.qs_tile_label,
                MasterConfigControl.getDeviceDisplayString(this, mCurrentDevice));

        mTileBuilder
                .hasSensitiveData(false)
                .setIcon(mDevicePrefs.isGlobalEnabled() ? R.drawable.ic_qs_visualizer_on
                        : R.drawable.ic_qs_visualizer_off)
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

        mOutputListener.removeCallback(this, mSessionManager, mDevicePrefs);
        if (mSessionManager != null) {
            mSessionManager.onDestroy();
        }

        CMStatusBarManager.getInstance(this).removeTile(TILE_ID);

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
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mSessionManager.hasActiveSessions()) {
                            stopSelf();
                            Log.w(TAG, "self destructing, no sessions active and nothing to do.");
                        }
                    }
                }, 1000);
                break;
        }
    }

    /**
     * Queue up a backend update.
     */
    private void update(int flags) {
        mSessionManager.update(flags);

        if ((flags & ALL_CHANGED) == ALL_CHANGED) {
            updateQsTile();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!newConfig.locale.equals(mLastLocale)) {
            updateQsTile();
        }
    }
}
