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
package org.cyanogenmod.audiofx.service;

import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_BASS_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_BASS_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_REVERB_PRESET;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE;
import static org.cyanogenmod.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH;
import static org.cyanogenmod.audiofx.Constants.DEVICE_DEFAULT_GLOBAL_ENABLE;
import static org.cyanogenmod.audiofx.activity.MasterConfigControl.getDeviceIdentifierString;
import static org.cyanogenmod.audiofx.service.AudioFxService.ALL_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.BASS_BOOST_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.EQ_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.REVERB_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.TREBLE_BOOST_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.VIRTUALIZER_CHANGED;
import static org.cyanogenmod.audiofx.service.AudioFxService.VOLUME_BOOST_CHANGED;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.audiofx.PresetReverb;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import org.cyanogenmod.audiofx.backends.EffectSet;
import org.cyanogenmod.audiofx.backends.EffectsFactory;
import org.cyanogenmod.audiofx.eq.EqUtils;

import cyanogenmod.media.AudioSessionInfo;
import cyanogenmod.media.CMAudioManager;

class SessionManager implements AudioOutputChangeListener.AudioOutputChangedCallback {

    private static final String TAG = AudioFxService.TAG;
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final Handler mHandler;
    private final DevicePreferenceManager mDevicePrefs;
    private final CMAudioManager mCMAudio;

    /**
     * All fields ending with L should be locked on {@link #mAudioSessionsL}
     */
    private final SparseArray<EffectSet> mAudioSessionsL = new SparseArray<EffectSet>();


    private AudioDeviceInfo mCurrentDevice = null;

    // audio priority handler messages
    private static final int MSG_UPDATE_DSP = 100;
    private static final int MSG_ADD_SESSION = 101;
    private static final int MSG_REMOVE_SESSION = 102;
    private static final int MSG_UPDATE_FOR_SESSION = 103;
    private static final int MSG_UPDATE_EQ_OVERRIDE = 104;

    public SessionManager(Context context, Handler handler, DevicePreferenceManager devicePrefs,
            AudioDeviceInfo outputDevice) {
        mContext = context;
        mCMAudio = CMAudioManager.getInstance(context);
        mDevicePrefs = devicePrefs;
        mCurrentDevice = outputDevice;
        mHandler = new Handler(handler.getLooper(), new AudioServiceHandler());
    }

    public void onDestroy() {
        synchronized (mAudioSessionsL) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.getLooper().quit();
        }
    }

    public void update(int flags) {
        if (mHandler == null) {
            return;
        }
        synchronized (mAudioSessionsL) {
            mHandler.obtainMessage(MSG_UPDATE_DSP, flags, 0).sendToTarget();
        }
    }

    public void setOverrideLevels(short band, float level) {
        synchronized (mAudioSessionsL) {
            mHandler.obtainMessage(MSG_UPDATE_EQ_OVERRIDE, band, 0, level).sendToTarget();
        }
    }

    /**
     * Callback which listens for session updates from AudioPolicyManager. This is a
     * feature added by CM which notifies when sessions are created or
     * destroyed on a particular stream. This is independent of the standard control
     * intents and should not conflict with them. This feature may not be available on
     * all devices.
     *
     * Default logic is to do our best to only attach to music streams. We don't attach
     * to mono streams by default since these are usually notifications/ringtones/etc.
     */
    public boolean shouldHandleSession(AudioSessionInfo info) {
        final boolean music = info.getStream() == AudioManager.STREAM_MUSIC;
        final boolean stereo = info.getChannelMask() < 0 || info.getChannelMask() > 1;

        return music && stereo && info.getSessionId() > 0;
    }

    public void addSession(AudioSessionInfo info) {
        synchronized (mAudioSessionsL) {
            // Never auto-attach is someone is recording! We don't want to interfere
            // with any sort of loopback mechanisms.
            final boolean recording = AudioSystem.isSourceActive(0) || AudioSystem.isSourceActive(6);
            if (recording) {
                Log.w(TAG, "Recording in progress, not performing auto-attach!");
                return;
            }
            if (shouldHandleSession(info) &&
                    !mHandler.hasMessages(MSG_ADD_SESSION, info.getSessionId())) {
                mHandler.removeMessages(MSG_REMOVE_SESSION, info.getSessionId());
                mHandler.obtainMessage(MSG_ADD_SESSION, info.getSessionId()).sendToTarget();
                if (DEBUG) Log.i(TAG, "New audio session: " + info.toString());
            }
        }
    }

    public void removeSession(AudioSessionInfo info) {
        synchronized (mAudioSessionsL) {
            if (shouldHandleSession(info) &&
                    !mHandler.hasMessages(MSG_REMOVE_SESSION, info.getSessionId())) {
                int sid = info.getSessionId();
                final EffectSet effects = mAudioSessionsL.get(sid);
                if (effects != null) {
                    effects.setMarkedForDeath(true);
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MSG_REMOVE_SESSION, sid),
                            effects.getReleaseDelay());
                    if (DEBUG) Log.i(TAG, "Audio session queued for removal: " + info.toString());
                }
            }
        }
    }

    public String getCurrentDeviceIdentifier() {
        return getDeviceIdentifierString(mCurrentDevice);
    }

    public boolean hasActiveSessions() {
        synchronized (mAudioSessionsL) {
            return mAudioSessionsL.size() > 0;
        }
    }

    EffectSet getEffectForSession(int sessionId) {
        synchronized (mAudioSessionsL) {
            return mAudioSessionsL.get(sessionId);
        }
    }

    /**
     * Update the backend with our changed preferences.
     *
     * This must only be called from the HandlerThread!
     */
    private void updateBackendLocked(int flags, EffectSet session) {
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new IllegalStateException("updateBackend must not be called on the UI thread!");
        }

        final SharedPreferences prefs = mDevicePrefs.getCurrentDevicePrefs();

        if (DEBUG) {
            Log.i(TAG, "+++ updateBackend() called with flags=[" + flags + "], session=[" + session + "]");
        }

        if (session == null) {
            return;
        }

        final boolean globalEnabled = prefs.getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE,
                DEVICE_DEFAULT_GLOBAL_ENABLE);

        if ((flags & ALL_CHANGED) > 0) {
            // global bypass toggle
            session.setGlobalEnabled(globalEnabled);
        }

        if (globalEnabled) {
            // tell the backend it's time to party
            if (!session.beginUpdate()) {
                Log.e(TAG, "session " + session + " failed to beginUpdate()");
                return;
            }

            // equalizer
            try {
                if ((flags & EQ_CHANGED) > 0) {
                    // equalizer is always on unless bypassed
                    session.enableEqualizer(true);
                    String savedPreset = prefs.getString(DEVICE_AUDIOFX_EQ_PRESET_LEVELS, null);
                    if (savedPreset != null) {
                        session.setEqualizerLevelsDecibels(EqUtils.stringBandsToFloats(savedPreset));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling equalizer!", e);
            }

            // bass
            try {
                if ((flags & BASS_BOOST_CHANGED) > 0 && session.hasBassBoost()) {
                    boolean enable = prefs.getBoolean(DEVICE_AUDIOFX_BASS_ENABLE, false);
                    session.enableBassBoost(enable);
                    session.setBassBoostStrength(Short.valueOf(prefs
                            .getString(DEVICE_AUDIOFX_BASS_STRENGTH, "0")));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling bass boost!", e);
            }

            // reverb
            try {
                if ((flags & REVERB_CHANGED) > 0 && session.hasReverb()) {
                    short preset = Short.decode(prefs.getString(DEVICE_AUDIOFX_REVERB_PRESET,
                            String.valueOf(PresetReverb.PRESET_NONE)));
                    session.enableReverb(preset > 0);
                    session.setReverbPreset(preset);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling reverb preset", e);
            }

            // virtualizer
            try {
                if ((flags & VIRTUALIZER_CHANGED) > 0 && session.hasVirtualizer()) {
                    boolean enable = prefs.getBoolean(DEVICE_AUDIOFX_VIRTUALIZER_ENABLE, false);
                    session.enableVirtualizer(enable);
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
                    boolean enable = prefs.getBoolean(DEVICE_AUDIOFX_TREBLE_ENABLE, false);
                    session.enableTrebleBoost(enable);
                    session.setTrebleBoostStrength(Short.valueOf(
                            prefs.getString(DEVICE_AUDIOFX_TREBLE_STRENGTH, "0")));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling treble boost!", e);
            }

            try {
                if ((flags & VOLUME_BOOST_CHANGED) > 0 && session.hasVolumeBoost()) {
                    // maxx volume
                    session.enableVolumeBoost(prefs.getBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enabling volume boost!", e);
            }

            // mic drop
            if (!session.commitUpdate()) {
                Log.e(TAG, "session " + session + " failed to commitUpdate()");
            }
        }
        if (DEBUG) {
            Log.i(TAG, "--- updateBackend() called with flags=[" + flags + "], session=[" + session + "]");
        }
    }

    private class AudioServiceHandler implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            synchronized (mAudioSessionsL) {
                EffectSet session = null;
                Integer sessionId = 0;
                int flags = 0;

                switch (msg.what) {
                    case MSG_ADD_SESSION:
                        /**
                         * msg.obj = sessionId
                         */
                        sessionId = (Integer) msg.obj;
                        if (sessionId == null || sessionId <= 0) {
                            break;
                        }

                        session = mAudioSessionsL.get(sessionId);
                        if (session == null) {
                            try {
                                session = new EffectsFactory()
                                        .createEffectSet(mContext, sessionId, mCurrentDevice);
                            } catch (Exception e) {
                                Log.e(TAG, "couldn't create effects for session id: " + sessionId, e);
                                break;
                            }
                            mAudioSessionsL.put(sessionId, session);
                            if (DEBUG) Log.w(TAG, "added new EffectSet for sessionId=" + sessionId);
                            updateBackendLocked(ALL_CHANGED, session);
                        } else {
                            session.setMarkedForDeath(false);
                        }
                        break;

                    case MSG_REMOVE_SESSION:
                        /**
                         * msg.obj = sessionId
                         */
                        sessionId = (Integer) msg.obj;
                        if (sessionId == null || sessionId <= 0) {
                            break;
                        }

                        session = mAudioSessionsL.get(sessionId);
                        if (session != null && session.isMarkedForDeath()) {
                            mHandler.removeMessages(MSG_UPDATE_FOR_SESSION, sessionId);
                            session.release();
                            mAudioSessionsL.remove(sessionId);
                            if (DEBUG) Log.w(TAG, "removed and released sessionId=" + sessionId);
                        }

                        break;

                    case MSG_UPDATE_DSP:
                        /**
                         * msg.arg1 = update what flags
                         */
                        flags = msg.arg1;

                        final String mode = getCurrentDeviceIdentifier();
                        if (DEBUG) Log.i(TAG, "Updating to configuration: " + mode);

                        final int N = mAudioSessionsL.size();
                        for (int i = 0; i < N; i++) {
                            sessionId = mAudioSessionsL.keyAt(i);
                            mHandler.obtainMessage(MSG_UPDATE_FOR_SESSION, flags, 0, sessionId).sendToTarget();
                        }
                        break;

                    case MSG_UPDATE_FOR_SESSION:
                        /**
                         * msg.arg1 = update what flags
                         * msg.arg2 = unused
                         * msg.obj = session id integer (for consistency)
                         */
                        sessionId = (Integer) msg.obj;
                        flags = msg.arg1;

                        if (sessionId == null || sessionId <= 0) {
                            break;
                        }

                        String device = getCurrentDeviceIdentifier();
                        if (DEBUG) Log.i(TAG, "updating DSP for sessionId=" + sessionId +
                                ", device=" + device + " flags=" + flags);

                        session = mAudioSessionsL.get(sessionId);
                        if (session != null) {
                            updateBackendLocked(flags, session);
                        }
                        break;

                    case MSG_UPDATE_EQ_OVERRIDE:
                        for (int i = 0; i < mAudioSessionsL.size(); i++) {
                            sessionId = mAudioSessionsL.keyAt(i);
                            session = mAudioSessionsL.get(sessionId);
                            if (session != null) {
                                session.setEqualizerBandLevel((short) msg.arg1, (float) msg.obj);
                            }
                        }
                        break;
                }
                return true;
            }
        }
    }

    /**
     * Updates the backend and notifies the frontend when the output device has changed
     */
    @Override
    public void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputDevice) {
        synchronized (mAudioSessionsL) {
            if (mCurrentDevice == null ||
                    (outputDevice != null && mCurrentDevice.getId() != outputDevice.getId())) {
                mCurrentDevice = outputDevice;
            }

            EffectSet session = null;

            // Update all the sessions for this output which are moving
            final int N = mAudioSessionsL.size();
            for (int i = 0; i < N; i++) {
                session = mAudioSessionsL.valueAt(i);

                session.setDevice(mCurrentDevice);
                updateBackendLocked(ALL_CHANGED, session);
            }
        }
    }
}
