package com.cyngn.audiofx.service;

import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_BASS_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_BASS_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_REVERB_PRESET;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_TREBLE_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_ENABLE;
import static com.cyngn.audiofx.Constants.DEVICE_AUDIOFX_VIRTUALIZER_STRENGTH;
import static com.cyngn.audiofx.Constants.DEVICE_DEFAULT_GLOBAL_ENABLE;
import static com.cyngn.audiofx.activity.MasterConfigControl.getDeviceIdentifierString;
import static com.cyngn.audiofx.service.AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.ALL_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.BASS_BOOST_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.ENABLE_REVERB;
import static com.cyngn.audiofx.service.AudioFxService.EQ_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.REVERB_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.TREBLE_BOOST_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.VIRTUALIZER_CHANGED;
import static com.cyngn.audiofx.service.AudioFxService.VOLUME_BOOST_CHANGED;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.audiofx.PresetReverb;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;

import com.cyngn.audiofx.backends.EffectSet;
import com.cyngn.audiofx.backends.EffectsFactory;
import com.cyngn.audiofx.eq.EqUtils;

class SessionManager extends AudioOutputChangeListener implements AudioSystem.EffectSessionCallback {

    private static final String TAG = AudioFxService.TAG;
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;

    /**
     * All fields ending with L should be locked on {@link #mAudioSessionsL}
     */
    private final SparseArray<EffectSet> mAudioSessionsL = new SparseArray<EffectSet>();

    private Handler mHandler;

    private AudioDeviceInfo mCurrentDevice;
    private AudioDeviceInfo mPreviousDevice;

    // audio priority handler messages
    private static final int MSG_UPDATE_DSP = 100;
    private static final int MSG_ADD_SESSION = 101;
    private static final int MSG_REMOVE_SESSION = 102;
    private static final int MSG_UPDATE_FOR_SESSION = 103;
    private static final int MSG_UPDATE_EQ_OVERRIDE = 104;

    public SessionManager(Context context, Handler handler) {
        super (context, handler);
        mContext = context;

        mHandler = new Handler(handler.getLooper(), new AudioServiceHandler());
        register();

        AudioSystem.setEffectSessionCallback(this);
    }

    public void onDestroy() {
        synchronized (mAudioSessionsL) {
            unregister();

            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler.getLooper().quit();
                mHandler = null;
            }

            AudioSystem.setEffectSessionCallback(null);
        }
    }

    public void update(int flags) {
        if (mHandler == null) {
            return;
        }
        mHandler.obtainMessage(MSG_UPDATE_DSP, flags, 0).sendToTarget();
    }

    public void setOverrideLevels(short band, float level) {
        mHandler.obtainMessage(MSG_UPDATE_EQ_OVERRIDE, band, 0, level).sendToTarget();
    }

    public AudioDeviceInfo getPreviousDevice() {
        return mPreviousDevice;
    }

    /**
     * Callback which listens for session updates from AudioPolicyManager. This is a
     * feature added by CM which notifies when sessions are created or
     * destroyed on a particular stream. This is independent of the standard control
     * intents and should not conflict with them. This feature may not be available on
     * all devices.
     *
     * Default logic is to do our best to only attach to music streams. We never attach
     * to low-latency streams automatically, and we don't attach to mono streams by default
     * either since these are usually notifications/ringtones/etc.
     */
    @Override
    public void onSessionAdded(int stream, int sessionId, int flags, int channelMask, int uid) {
        final boolean music = stream == AudioManager.STREAM_MUSIC;
        final boolean offloaded = (flags < 0)
                || (flags & AudioFxService.AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD) > 0
                || (flags & AudioFxService.AUDIO_OUTPUT_FLAG_DEEP_BUFFER) > 0;
        final boolean stereo = channelMask < 0 || channelMask > 1;

        if (music && offloaded && stereo && !mHandler.hasMessages(MSG_ADD_SESSION, sessionId)) {
            if (DEBUG) Log.i(TAG, String.format("New audio session: %d [flags=%d channelMask=%d uid=%d]",
                    sessionId, flags, channelMask, uid));
            mHandler.obtainMessage(MSG_ADD_SESSION, sessionId).sendToTarget();
        }
    }

    @Override
    public void onSessionRemoved(int stream, int sessionId) {
        if (stream == AudioManager.STREAM_MUSIC &&
                !mHandler.hasMessages(MSG_REMOVE_SESSION, sessionId)) {
            if (DEBUG) Log.i(TAG, String.format("Audio session queued for removal: %d", sessionId));
            mHandler.obtainMessage(MSG_REMOVE_SESSION, sessionId).sendToTarget();
        }
    }

    public String getCurrentDeviceIdentifier() {
        return getDeviceIdentifierString(getCurrentDevice());
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

        final SharedPreferences prefs = mContext.getSharedPreferences(getCurrentDeviceIdentifier(), 0);

        if (DEBUG) {
            Log.i(TAG, "+++ updateBackend() called with flags=[" + flags + "], session=[" + session + "]");
        }

        if (session == null) {
            return;
        }

        final boolean globalEnabled = prefs.getBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE,
                DEVICE_DEFAULT_GLOBAL_ENABLE);

        // global bypass toggle
        session.setGlobalEnabled(globalEnabled);

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
            if (ENABLE_REVERB) {
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
            EffectSet session = null;
            Integer sessionId = 0;
            int flags = 0;
            synchronized (mAudioSessionsL) {
                switch (msg.what) {
                    case MSG_ADD_SESSION:
                        /**
                         * msg.obj = sessionId
                         */
                        sessionId = (Integer) msg.obj;
                        if (sessionId == null || sessionId <= 0) {
                            break;
                        }

                        if (mAudioSessionsL.indexOfKey(sessionId) < 0) {
                            try {
                                session = EffectsFactory.createEffectSet(mContext, sessionId, mCurrentDevice);
                            } catch (Exception e) {
                                Log.e(TAG, "couldn't create effects for session id: " + sessionId, e);
                                break;
                            }
                            mAudioSessionsL.put(sessionId, session);
                            if (DEBUG) Log.w(TAG, "added new EffectSet for sessionId=" + sessionId);
                            updateBackendLocked(ALL_CHANGED, session);
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
                        mHandler.removeMessages(MSG_UPDATE_FOR_SESSION, sessionId);

                        if (mAudioSessionsL.indexOfKey(sessionId) > -1) {
                            session = mAudioSessionsL.removeReturnOld(sessionId);
                        }
                        if (session != null) {
                            session.release();
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
            mPreviousDevice = mCurrentDevice;
            mCurrentDevice = outputDevice;

            int sessionId = 0;
            EffectSet session = null;

            // Update all the sessions for this output which are moving
            final int N = mAudioSessionsL.size();
            for (int i = 0; i < N; i++) {
                sessionId = mAudioSessionsL.keyAt(i);
                session = mAudioSessionsL.valueAt(i);
                if (DEBUG) Log.d(TAG, "UPDATE_DEVICE prev=" +
                        (mPreviousDevice == null ? "none" : mPreviousDevice.getType()) +
                        " new=" + (mCurrentDevice == null ? "none" : mCurrentDevice.getType() +
                                " session=" + sessionId + " session-device=" +
                                (session.getDevice() == null ? "none" : session.getDevice().getType())));

                session.setDevice(mCurrentDevice);
                updateBackendLocked(ALL_CHANGED, session);
            }
        }

        if (DEBUG) Log.d(TAG, "Broadcasting device changed event");

        Intent intent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
        intent.putExtra("device", mCurrentDevice.getId());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }

}
