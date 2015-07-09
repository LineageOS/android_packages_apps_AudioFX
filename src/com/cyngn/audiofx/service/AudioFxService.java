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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPort;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.ArrayMap;
import android.util.Log;

import com.cyngn.audiofx.eq.EqUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.bluetooth.BluetoothAdapter.ERROR;
import static com.cyngn.audiofx.Constants.*;

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
public class AudioFxService extends Service {

    protected static final String TAG = AudioFxService.class.getSimpleName();
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean ENABLE_REVERB = false;

    public static final String ACTION_UPDATE_PREFERENCES = "org.cyanogenmod.audiofx.UPDATE_PREFS";
    public static final String ACTION_BLUETOOTH_DEVICES_UPDATED = "org.cyanogenmod.audiofx.ACTION_BLUETOOTH_DEVICES_UPDATED";
    public static final String ACTION_DEVICE_OUTPUT_CHANGED = "org.cyanogenmod.audiofx.ACTION_DEVICE_OUTPUT_CHANGED";
    public static final String EXTRA_DEVICE = "device";

    private final HashMap<Integer, EffectSet> mAudioSessions = new HashMap<>();
    private int mMostRecentSessionId;

    private AudioServiceHandler mHandler;

    AudioPortListener mAudioPortListener;
    private BluetoothDevice mLastBluetoothDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private final ArrayMap<BluetoothDevice, DeviceInfo> mDeviceInfo = new ArrayMap<>();

    private static final int MSG_UPDATE_DSP = 100;
    private static final int MSG_ADD_SESSION = 101;
    private static final int MSG_REMOVE_SESSION = 102;
    private static final int MSG_UPDATE_FOR_SESSION = 103;

    private List<Integer> mSessionsToRemove = new ArrayList<>();

    private static final ParcelUuid[] BLUETOOTH_AUDIO_UUIDS = {
            BluetoothUuid.AudioSink,
            BluetoothUuid.AdvAudioDist,
            BluetoothUuid.AudioSource
    };

    public class LocalBinder extends Binder {
        WeakReference<AudioFxService> mService;
        public LocalBinder(AudioFxService service) {// added a constructor for Stub here
            mService = new WeakReference<AudioFxService>(service);
        }

        public AudioFxService getService() {
            return mService.get();
        }
    }

    private DtsControl mDts;

    /**
     * Receive new broadcast intents for adding DSP to session
     */
    private final BroadcastReceiver mAudioSessionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            int contentType = intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE,
                    AudioEffect.CONTENT_TYPE_MUSIC);

            if (action.equals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)) {
                if (DEBUG) Log.i(TAG, String.format("New audio session: %d, package: %s",
                        sessionId, pkg));

                mSessionsToRemove.remove((Integer) sessionId);
                mHandler.sendMessage(Message.obtain(mHandler, MSG_ADD_SESSION, sessionId, 0));
            } else if (action.equals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
                if (DEBUG) Log.i(TAG, String.format("Audio session removed: %d, package: %s",
                        sessionId, pkg));

                mSessionsToRemove.add(sessionId);
                mHandler.sendMessageDelayed(
                        Message.obtain(mHandler, MSG_REMOVE_SESSION, sessionId, 0),
                        10000);
            }
        }
    };

    /**
     * Update audio parameters when preferences have been updated.
     */
    private final BroadcastReceiver mPreferenceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.i(TAG, "Preferences updated.");
            if (!mHandler.hasMessages(MSG_UPDATE_DSP)) {
                update();
            }
        }
    };

    private class AudioServiceHandler extends Handler {
        public AudioServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ADD_SESSION:

                    synchronized (mAudioSessions) {
                        if (!mAudioSessions.containsKey(msg.arg1)) {
                            if (DEBUG) Log.d(TAG, "added new EffectSet for sessionId=" + msg.arg1);
                            mAudioSessions.put(msg.arg1, new EffectSet(msg.arg1));
                        }
                    }
                    mMostRecentSessionId = msg.arg1;
                    if (DEBUG) Log.d(TAG, "new most recent sesssionId=" + msg.arg1);

                    update();
                    break;

                case MSG_REMOVE_SESSION:

                    for (Integer id : mSessionsToRemove) {
                        synchronized (mAudioSessions) {
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
                    }

                    update();
                    break;

                case MSG_UPDATE_DSP:

                    if (mDts.hasDts()) {
                        if (mDts.shouldUseDts()) {
                            if (DEBUG) Log.d(TAG, "forcing DTS effects");
                            disableAllEffects();

                            mDts.setEnabled(mDts.getUserEnabled());
                            return;
                        } else {
                            if (DEBUG) Log.d(TAG, "not using DTS");
                            mDts.setEnabled(false);
                        }
                    }

                    final String mode = getCurrentDevicePreferenceName();
                    if (DEBUG) Log.i(TAG, "Updating to configuration: " + mode);
                    synchronized (mAudioSessions) {
                        // immediately update most recent session
                        if (mMostRecentSessionId > 0) {
                            if (DEBUG) Log.d(TAG, "updating DSP for most recent session id ("
                                    + mMostRecentSessionId + ")!");
                            updateDsp(getSharedPreferences(mode, 0),
                                    mAudioSessions.get(mMostRecentSessionId)
                            );
                        }

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
                    String device = getCurrentDevicePreferenceName();
                    if (DEBUG) Log.i(TAG, "updating DSP for sessionId=" + msg.arg1 + ", device=" + device);
                    updateDsp(getSharedPreferences(device, 0), mAudioSessions.get(msg.arg1)
                    );
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.i(TAG, "Starting service.");

        HandlerThread handlerThread = new HandlerThread(TAG, android.os.Process.THREAD_PRIORITY_AUDIO);
        handlerThread.start();
        mHandler = new AudioServiceHandler(handlerThread.getLooper());

        mDts = new DtsControl(this);
        try {
            saveDefaults();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing effects!", e);
            stopSelf();
        }

        IntentFilter audioFilter = new IntentFilter();
        audioFilter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        audioFilter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mAudioSessionReceiver, audioFilter);

        registerReceiver(mPreferenceUpdateReceiver,
                new IntentFilter(ACTION_UPDATE_PREFERENCES));

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ALIAS_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);

        BluetoothManager btMan = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = btMan.getAdapter();
        updateBondedBluetoothDevices();

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.registerAudioPortUpdateListener(mAudioPortListener = new AudioPortListener());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.unregisterAudioPortUpdateListener(mAudioPortListener);

        super.onDestroy();
        if (DEBUG) Log.i(TAG, "Stopping service.");

        unregisterReceiver(mAudioSessionReceiver);
        unregisterReceiver(mPreferenceUpdateReceiver);

        unregisterReceiver(mBluetoothReceiver);

        mHandler.getLooper().quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(this);
    }

    // ======== Effects =============== //

    /**
     * Helper class representing the full complement of effects attached to one
     * audio session.
     *
     * @author alankila
     */
    public static class EffectSet {
        /**
         * Session-specific equalizer
         */
        public final Equalizer mEqualizer;
        /**
         * Session-specific bassboost
         */
        public final BassBoost mBassBoost;
        /**
         * Session-specific virtualizer
         */
        public final Virtualizer mVirtualizer;

        public final PresetReverb mPresetReverb;

        public MaxxAudioEffects mMaxxAudioEffects;

        private short mEqNumPresets = -1;
        private short mEqNumBands = -1;

        private final int mSessionId;

        public EffectSet(int sessionId) {
            mSessionId = sessionId;

            try {
                mMaxxAudioEffects = new MaxxAudioEffects(1000, sessionId);
            } catch (Exception e) {
                mMaxxAudioEffects = null;
                if (DEBUG) Log.w(TAG, "Unable to initialize MaxxAudio library!");
            }

            mEqualizer = new Equalizer(1000, sessionId);
            mBassBoost = new BassBoost(1000, sessionId);
            mVirtualizer = new Virtualizer(1000, sessionId);
            if (ENABLE_REVERB) {
                mPresetReverb = new PresetReverb(1000, sessionId);
            } else {
                mPresetReverb = null;
            }
        }

        public boolean hasMaxxAudio() {
            return mMaxxAudioEffects != null;
        }

        public boolean hasVirtualizer() {
            return mVirtualizer.getStrengthSupported();
        }

        public boolean hasBassBoost() {
            return mBassBoost.getStrengthSupported();
        }

        /*
         * Take lots of care to not poke values that don't need
         * to be poked- this can cause audible pops.
         */

        public void enableEqualizer(boolean enable) {
            mEqualizer.setEnabled(enable);
        }

        public void setEqualizerLevels(short[] levels) {
            if (mEqualizer.getEnabled()) {
                for (short i = 0; i < levels.length; i++) {
                    mEqualizer.setBandLevel(i, levels[i]);
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
            mBassBoost.setEnabled(enable);
        }

        public void setBassBoostStrength(short strength) {
            mBassBoost.setStrength(strength);
        }

        public void enableVirtualizer(boolean enable) {
            mVirtualizer.setEnabled(enable);
        }

        public void setVirtualizerStrength(short strength) {
            mVirtualizer.setStrength(strength);
        }

        public void enableReverb(boolean enable) {
            if (mPresetReverb != null) {
                mPresetReverb.setEnabled(enable);
            }
        }

        public void setReverbPreset(short preset) {
            if (mPresetReverb != null) {
                mPresetReverb.setPreset(preset);
            }
        }

        public void release() {
            mEqualizer.release();
            mBassBoost.release();
            mVirtualizer.release();
            if (mPresetReverb != null) {
                mPresetReverb.release();
            }
            if (mMaxxAudioEffects != null) {
                mMaxxAudioEffects.release();
            }
        }
    }

    // ======== output routing ============= //

    public List<OutputDevice> getBluetoothDevices() {
        ArrayList<OutputDevice> devices = new ArrayList<OutputDevice>();
        synchronized (mDeviceInfo) {
            Set<Map.Entry<BluetoothDevice, DeviceInfo>> entries = mDeviceInfo.entrySet();
            for (Map.Entry<BluetoothDevice, DeviceInfo> entry : entries) {
                if (entry.getValue().bonded) {
                    devices.add(new OutputDevice(OutputDevice.DEVICE_BLUETOOTH, entry.getKey().toString(),
                            entry.getKey().getAliasName()));
                }
            }
        }
        if (DEBUG) Log.d(TAG, "bluetooth devices: " + Arrays.toString(devices.toArray()));
        return devices;
    }

    private String getCurrentDevicePreferenceName() {
        return getCurrentDevice().getDevicePreferenceName(AudioFxService.this);
    }

    public OutputDevice getCurrentDevice() {
        final int audioOutputRouting = getAudioOutputRouting();
        Log.d(TAG, "getCurrentDevice, audioOutputRouting=" + audioOutputRouting);
        switch (audioOutputRouting) {
            case OutputDevice.DEVICE_BLUETOOTH:
                if (mLastBluetoothDevice == null) {
                    final Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
                    if (bondedDevices != null) {
                        mLastBluetoothDevice = bondedDevices.iterator().next();
                    }
                }
                if (mLastBluetoothDevice == null) {
                    return new OutputDevice(audioOutputRouting);
                } else {
                    return new OutputDevice(audioOutputRouting, mLastBluetoothDevice.toString()
                            , getLastDeviceName());
                }

            default:
            case OutputDevice.DEVICE_SPEAKER:
            case OutputDevice.DEVICE_USB:
            case OutputDevice.DEVICE_HEADSET:
            case OutputDevice.DEVICE_WIRELESS:
                return new OutputDevice(audioOutputRouting);
        }
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
    public int getAudioOutputRouting() {
        if (mAudioPortListener != null) {
            return mAudioPortListener.getInternalAudioOutputRouting();
        }
        return OutputDevice.DEVICE_SPEAKER;
    }

    public String getLastDeviceName() {
        return mLastBluetoothDevice != null ? mLastBluetoothDevice.getAliasName() : null;
    }

    private boolean isAudioBluetoothDevice(BluetoothDevice device) {
        if (device != null) {
            final ParcelUuid[] uuids = device.getUuids();
            if (uuids != null) {
                if (BluetoothUuid.containsAnyUuid(uuids, BLUETOOTH_AUDIO_UUIDS)) {
                    return true;
                }
            }
            final BluetoothClass bluetoothClass = device.getBluetoothClass();
            if (bluetoothClass != null) {
                if (bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_A2DP) ||
                        bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_A2DP_SINK) ||
                        bluetoothClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateBondedBluetoothDevices() {
        if (mBluetoothAdapter == null) return;
        final Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        synchronized (mDeviceInfo) {
            for (DeviceInfo info : mDeviceInfo.values()) {
                info.bonded = false;
            }
        }
        int bondedCount = 0;
        BluetoothDevice lastBonded = null;
        if (bondedDevices != null) {
            for (BluetoothDevice bondedDevice : bondedDevices) {
                if (isAudioBluetoothDevice(bondedDevice)) {
                    final boolean bonded = bondedDevice.getBondState() != BluetoothDevice.BOND_NONE;
                    updateInfo(bondedDevice).bonded = bonded;
                    if (bonded) {
                        bondedCount++;
                        lastBonded = bondedDevice;
                    }
                }
            }
        }
        if (mLastBluetoothDevice == null && bondedCount == 1) {
            mLastBluetoothDevice = lastBonded;
        }
        sendBroadcast(new Intent(ACTION_BLUETOOTH_DEVICES_UPDATED)); // let UI know to refresh
    }

    private DeviceInfo updateInfo(BluetoothDevice device) {
        synchronized (mDeviceInfo) {
            DeviceInfo info = mDeviceInfo.get(device);
            info = info != null ? info : new DeviceInfo();
            mDeviceInfo.put(device, info);
            return info;
        }
    }

    private static class DeviceInfo {
        int connectionState = BluetoothAdapter.STATE_DISCONNECTED;
        boolean bonded;  // per getBondedDevices
    }

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "bluetooth receiver, action: " + intent.getAction());
            final String action = intent.getAction();
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                final DeviceInfo info = updateInfo(device);
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        ERROR);
                if (state != ERROR) {
                    info.connectionState = state;
                }
                mLastBluetoothDevice = device;
            } else if (action.equals(BluetoothDevice.ACTION_ALIAS_CHANGED)) {
                updateInfo(device);
                mLastBluetoothDevice = device;
            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                if (DEBUG) Log.d(TAG, "ACTION_BOND_STATE_CHANGED " + device);
                // we'll update all bonded devices below
            }
            updateBondedBluetoothDevices();
        }
    };


    private class AudioPortListener implements AudioManager.OnAudioPortUpdateListener {
        private boolean mUseBluetooth;
        private boolean mUseHeadset;
        private boolean mUseUSB;
        private boolean mUseWifiDisplay;
        private boolean mUseSpeaker;

        @Override
        public void onAudioPortListUpdate(AudioPort[] portList) {
            final boolean prevUseHeadset = mUseHeadset;
            final boolean prevUseBluetooth = mUseBluetooth;
            final boolean prevUseUSB = mUseUSB;
            final boolean prevUseWireless = mUseWifiDisplay;
            final boolean prevUseSpeaker = mUseSpeaker;

            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int device = am.getDevicesForStream(AudioManager.STREAM_MUSIC);
            mUseBluetooth = (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES) != 0
                    || (device & AudioManager.DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER) != 0;

            mUseHeadset = (device & AudioManager.DEVICE_OUT_WIRED_HEADPHONE) != 0
                    || (device & AudioManager.DEVICE_OUT_WIRED_HEADSET) != 0;

            mUseUSB = (device & AudioManager.DEVICE_OUT_USB_ACCESSORY) != 0
                    || (device & AudioManager.DEVICE_OUT_USB_DEVICE) != 0;

            mUseWifiDisplay = false; //TODO add support for wireless display..

            mUseSpeaker = (device & AudioManager.DEVICE_OUT_SPEAKER) != 0;

            if (DEBUG) Log.i(TAG, "Headset=" + mUseHeadset + "; Bluetooth="
                    + mUseBluetooth + " ; USB=" + mUseUSB + "; Speaker=" + mUseSpeaker);
            if (prevUseHeadset != mUseHeadset
                    || prevUseBluetooth != mUseBluetooth
                    || prevUseUSB != mUseUSB
                    || prevUseWireless != mUseWifiDisplay
                    || prevUseSpeaker != mUseSpeaker) {
                Intent deviceChangedIntent = new Intent(ACTION_DEVICE_OUTPUT_CHANGED);
                deviceChangedIntent.setPackage(getPackageName());
                deviceChangedIntent.putExtra(EXTRA_DEVICE, getCurrentDevice());
                sendBroadcast(deviceChangedIntent);

                update();
            }
        }

        @Override
        public void onAudioPatchListUpdate(AudioPatch[] patchList) {

        }

        @Override
        public void onServiceDied() {

        }

        public int getInternalAudioOutputRouting() {
            if (mUseSpeaker) {
                return OutputDevice.DEVICE_SPEAKER;
            }
            if (mUseBluetooth) {
                return OutputDevice.DEVICE_BLUETOOTH;
            }
            if (mUseHeadset) {
                return OutputDevice.DEVICE_HEADSET;
            }
            if (mUseUSB) {
                return OutputDevice.DEVICE_USB;
            }
            if (mUseWifiDisplay) {
                return OutputDevice.DEVICE_WIRELESS;
            }
            return OutputDevice.DEVICE_SPEAKER;
        }
    }

    // ======== DSP UPDATE METHODS BELOW ============= //

    /**
     * Push new configuration to audio stack.
     */
    public void update() {
        mHandler.removeMessages(MSG_UPDATE_DSP);
        mHandler.sendEmptyMessage(MSG_UPDATE_DSP);
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

        // maxx audio effects
        try {
            if (session.hasMaxxAudio()) {
                // treble
                session.mMaxxAudioEffects.setMaxxTrebleEnabled(
                        globalEnabled && prefs.getBoolean(DEVICE_AUDIOFX_TREBLE_ENABLE, false));
                session.mMaxxAudioEffects.setMaxxTrebleStrength(Short.valueOf(
                        prefs.getString(DEVICE_AUDIOFX_TREBLE_STRENGTH, "0")));

                // maxx volume
                session.mMaxxAudioEffects.setMaxxVolumeEnabled(
                        globalEnabled && prefs.getBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling maxx audio effects!", e);
        }
    }

    private void disableAllEffects() {
        final Collection<EffectSet> values = mAudioSessions.values();
        for (EffectSet effectSet : values) {
            effectSet.enableBassBoost(false);
            effectSet.enableEqualizer(false);
            effectSet.enableVirtualizer(false);
            effectSet.enableReverb(false);
            if (effectSet.hasMaxxAudio()) {
                effectSet.mMaxxAudioEffects.setMaxxTrebleEnabled(false);
                effectSet.mMaxxAudioEffects.setMaxxVolumeEnabled(false);
            }
        }
    }

    /**
     * This method sets some sane defaults for presets, device defaults, etc
     * <p/>
     * First we read presets from the system, then adjusts some setting values
     * for some better defaults!
     */
    private void saveDefaults() {
        EffectSet temp = new EffectSet(0);
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


        editor.putBoolean(AUDIOFX_GLOBAL_HAS_VIRTUALIZER, temp.hasVirtualizer()).apply();
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_BASSBOOST, temp.hasBassBoost()).apply();
        editor.putBoolean(AUDIOFX_GLOBAL_HAS_MAXXAUDIO, temp.hasMaxxAudio()).apply();
        temp.release();

        applyDefaults(false);
    }


    /**
     * This method sets up some *persisted* defaults.
     */
    public void applyDefaults(boolean overridePrevious) {
        // Enable for the speaker by default
        if (!getSharedPrefsFile("speaker").exists() || overridePrevious) {
            SharedPreferences spk = getSharedPreferences("speaker", 0);
            spk.edit().putBoolean(DEVICE_AUDIOFX_GLOBAL_ENABLE, true).apply();
            spk.edit().putString(DEVICE_AUDIOFX_EQ_PRESET, "0").apply();
            spk.edit().putBoolean(DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, true).apply();
        }
    }

    public static void updateService(Context context) {
        final Intent updateServiceIntent = new Intent(AudioFxService.ACTION_UPDATE_PREFERENCES);
//        updateServiceIntent.setClass(context, AudioFxService.class);
        context.sendBroadcast(updateServiceIntent);
    }
}
