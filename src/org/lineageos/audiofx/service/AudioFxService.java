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
package org.lineageos.audiofx.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.lineageos.audiofx.backends.EffectSet;

import java.lang.ref.WeakReference;

/**
 * This service is responsible for applying all requested effects from the AudioFX UI.
 * <p>
 * Since the AudioFX UI allows for different configurations based on the current output device, the
 * service is also responsible for applying the effects properly based on user configuration, and
 * the current device output state.
 */
public class AudioFxService extends Service
        implements AudioOutputChangeListener.AudioOutputChangedCallback {

    static final String TAG = AudioFxService.class.getSimpleName();

    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String ACTION_DEVICE_OUTPUT_CHANGED
            = "org.lineageos.audiofx.ACTION_DEVICE_OUTPUT_CHANGED";

    public static final String EXTRA_DEVICE = "device";

    // flags for updateService to minimize DSP traffic
    public static final int EQ_CHANGED = 0x1;
    public static final int BASS_BOOST_CHANGED = 0x2;
    public static final int VIRTUALIZER_CHANGED = 0x4;
    public static final int TREBLE_BOOST_CHANGED = 0x8;
    public static final int VOLUME_BOOST_CHANGED = 0x10;
    public static final int REVERB_CHANGED = 0x20;
    public static final int ALL_CHANGED = 0xFF;

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

        mCurrentDevice = mOutputListener.getCurrentDevice();

        mDevicePrefs = new DevicePreferenceManager(getApplicationContext(), mCurrentDevice);
        if (!mDevicePrefs.initDefaults()) {
            stopSelf();
            return;
        }

        mSessionManager = new SessionManager(getApplicationContext(), mHandler, mDevicePrefs,
                mCurrentDevice);
        mOutputListener.addCallback(mDevicePrefs, mSessionManager);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.i(TAG, "onStartCommand() called with " + "intent = [" + intent + "], flags = ["
                    + flags + "], startId = [" + startId + "]");
        }
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                    AudioEffect.ERROR_BAD_VALUE);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);

            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                mSessionManager.addSession(sessionId);
            } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
                mSessionManager.removeSession(sessionId);
            }
        }
        return START_STICKY;
    }

    @Override
    public synchronized void onAudioOutputChanged(boolean firstChange,
            AudioDeviceInfo outputDevice) {
        if (outputDevice == null) {
            return;
        }

        mCurrentDevice = outputDevice;

        if (DEBUG) {
            Log.d(TAG, "Broadcasting device changed event");
        }

        // Update the UI with the change
        Intent intent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
        intent.putExtra("device", outputDevice.getId());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.i(TAG, "Stopping service.");

        mOutputListener.removeCallback(this, mSessionManager, mDevicePrefs);
        if (mSessionManager != null) {
            mSessionManager.onDestroy();
        }

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
    }
}
