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
package org.cyanogenmod.audiofx.activity;

import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
import static android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO;
import static android.media.AudioDeviceInfo.TYPE_DOCK;
import static android.media.AudioDeviceInfo.TYPE_IP;
import static android.media.AudioDeviceInfo.TYPE_LINE_ANALOG;
import static android.media.AudioDeviceInfo.TYPE_LINE_DIGITAL;
import static android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY;
import static android.media.AudioDeviceInfo.TYPE_USB_DEVICE;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES;
import static android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET;
import static android.media.AudioDeviceInfo.convertDeviceTypeToInternalDevice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.cyanogenmod.audiofx.Constants;
import org.cyanogenmod.audiofx.service.AudioFxService;

import java.util.ArrayList;
import java.util.List;

/**
 * Master configuration class for AudioFX.
 *
 * Contains the main hub where data is stored for the current eq graph (which there should be
 * one of, thus only once instance of this class exists).
 *
 * Anyone can obtain an instance of this class. If one does not exist, a new one is created.
 * Immediately before the new instance creation happens, some defaults are pre-populated
 * with MasterConfigControl.saveDefaults(). That method doesn't ever have to be directly called.
 */
public class MasterConfigControl {

    private static final String TAG = MasterConfigControl.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean SERVICE_DEBUG = false;

    private final Context mContext;

    private AudioFxService.LocalBinder mService;
    private ServiceConnection mServiceConnection;
    private int mServiceRefCount = 0;

    private AudioDeviceInfo mCurrentDevice;
    private AudioDeviceInfo mUserDeviceOverride;

    private final StateCallbacks mCallbacks;
    private final EqualizerManager mEqManager;
    private final AudioManager mAudioManager;

    private static MasterConfigControl sInstance;
    private boolean mShouldBindToService = false;

    public static MasterConfigControl getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MasterConfigControl(context);
        }
        return sInstance;
    }

    private MasterConfigControl(Context context) {
        mContext = context.getApplicationContext();

        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mCallbacks = new StateCallbacks(this);
        mEqManager = new EqualizerManager(context, this);
    }

    public void onResetDefaults() {
        mEqManager.applyDefaults();
    }

    public synchronized boolean bindService() {
        boolean conn = true;
        if (SERVICE_DEBUG) Log.i(TAG, "bindService() refCount=" + mServiceRefCount);
        if (mServiceConnection == null && mServiceRefCount == 0) {
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    if (SERVICE_DEBUG) Log.i(TAG, "onServiceConnected refCount=" + mServiceRefCount);
                    mService = ((AudioFxService.LocalBinder) binder);
                    LocalBroadcastManager.getInstance(mContext).registerReceiver(
                            mDeviceChangeReceiver,
                            new IntentFilter(AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED));
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    if (SERVICE_DEBUG) Log.w(TAG, "onServiceDisconnected refCount =" + mServiceRefCount);
                    LocalBroadcastManager.getInstance(mContext).unregisterReceiver(
                            mDeviceChangeReceiver);
                    mService = null;
                }
            };

            Intent serviceIntent = new Intent(mContext, AudioFxService.class);
            conn =  mContext.bindService(serviceIntent, mServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }
        if (conn) {
            mServiceRefCount++;
        }
        return mServiceRefCount > 0;
    }

    public synchronized void unbindService() {
        if (SERVICE_DEBUG) Log.i(TAG, "unbindService() called refCount=" + mServiceRefCount);
        if (mServiceRefCount > 0) {
            mServiceRefCount--;
            if (mServiceRefCount == 0) {
                mContext.unbindService(mServiceConnection);
                mService = null;
                mServiceConnection = null;
            }
        }
    }

    public boolean checkService() {
        if (mService == null && mServiceRefCount == 0 && mShouldBindToService) {
            Log.e(TAG,  "Service went away, rebinding");
            bindService();
        }
        return mService != null;
    }

    private final BroadcastReceiver mDeviceChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int device = intent.getIntExtra("device", -1);
            Log.d(TAG, "deviceChanged: " + device);
            if (device > -1) {
                AudioDeviceInfo info = getDeviceById(device);
                if (info != null) {
                    setCurrentDevice(info, false);
                }
            }
        }
    };

    public void updateService(int flags) {
        if (checkService()) {
            mService.update(flags);
        }
    }

    public StateCallbacks getCallbacks() {
        return mCallbacks;
    }

    public EqualizerManager getEqualizerManager() {
        return mEqManager;
    }

    public synchronized void setCurrentDeviceEnabled(boolean isChecked) {
        getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE, isChecked).apply();
        getCallbacks().notifyGlobalToggle(isChecked);
        updateService(AudioFxService.ALL_CHANGED);
    }

    public synchronized boolean isCurrentDeviceEnabled() {
        return getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE, false);
    }

    public synchronized SharedPreferences getGlobalPrefs() {
        return mContext.getSharedPreferences(Constants.AUDIOFX_GLOBAL_FILE, 0);
    }

    /**
     * Update the current device used when querying any device-specific values such as the current
     * preset, or the user's custom eq preset settings.
     *
     * @param audioOutputRouting the new device key
     */
    public synchronized void setCurrentDevice(AudioDeviceInfo device, final boolean userSwitch) {

        final AudioDeviceInfo current = getCurrentDevice();

        Log.d(TAG, "setCurrentDevice name=" + (current == null ? null : current.getProductName()) +
                " fromUser=" + userSwitch +
                " cur=" + (current == null ? null : current.getType()) +
                " new=" + (device == null ? null : device.getType()));

        if (userSwitch) {
            mUserDeviceOverride = device;
        } else {
            if (device != null) {
                mCurrentDevice = device;
            }
            mUserDeviceOverride = null;
        }

        mEqManager.onPreDeviceChanged();

        mCallbacks.notifyDeviceChanged(device, userSwitch);

        mEqManager.onPostDeviceChanged();
    }

    public AudioDeviceInfo getSystemDevice() {
        if (mCurrentDevice == null) {
            final int forMusic = mAudioManager.getDevicesForStream(AudioManager.STREAM_MUSIC);
            for (AudioDeviceInfo ai : getConnectedDevices()) {
                if ((convertDeviceTypeToInternalDevice(ai.getType()) & forMusic) > 0) {
                    return ai;
                }
            }
        }
        return mCurrentDevice;
    }

    public boolean isUserDeviceOverride() {
        return mUserDeviceOverride != null;
    }

    public AudioDeviceInfo getCurrentDevice() {
        if (isUserDeviceOverride()) {
            return mUserDeviceOverride;
        }
        return getSystemDevice();
    }

    public AudioDeviceInfo getDeviceById(int id) {
        for (AudioDeviceInfo ai : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (ai.getId() == id) {
                return ai;
            }
        }
        return null;
    }

    public List<AudioDeviceInfo> getConnectedDevices(int... filter) {
        final List<AudioDeviceInfo> devices = new ArrayList<AudioDeviceInfo>();
        for (AudioDeviceInfo ai : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (filter.length == 0) {
                devices.add(ai);
            } else {
                for (int i = 0; i < filter.length; i++) {
                    if (ai.getType() == filter[i]) {
                        devices.add(ai);
                        continue;
                    }
                }
            }
        }
        return devices;
    }

    public String getCurrentDeviceIdentifier() {
        return getDeviceIdentifierString(getCurrentDevice());
    }

    public SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(getCurrentDeviceIdentifier(), 0);
    }

    public boolean hasDts() {
        return getGlobalPrefs().getBoolean(Constants.AUDIOFX_GLOBAL_HAS_DTS, false);
    }

    public boolean hasMaxxAudio() {
        return getGlobalPrefs().getBoolean(Constants.AUDIOFX_GLOBAL_HAS_MAXXAUDIO, false);
    }

    public boolean hasBassBoost() {
        return getGlobalPrefs().getBoolean(Constants.AUDIOFX_GLOBAL_HAS_BASSBOOST, false);
    }

    public boolean hasReverb() {
        return getGlobalPrefs().getBoolean(Constants.AUDIOFX_GLOBAL_HAS_REVERB, false);
    }

    public boolean hasVirtualizer() {
        return getGlobalPrefs().getBoolean(Constants.AUDIOFX_GLOBAL_HAS_VIRTUALIZER, false);
    }

    public boolean getMaxxVolumeEnabled() {
        return getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false);
    }

    public boolean getReverbEnabled() {
        return getPrefs().getString(Constants.DEVICE_AUDIOFX_REVERB_PRESET, "0").equals("1");
    }

    public void setMaxxVolumeEnabled(boolean enable) {
        getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, enable).apply();
        updateService(AudioFxService.VOLUME_BOOST_CHANGED);
    }

    public void setReverbEnabled(boolean enable) {
        getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_REVERB_PRESET, enable ? "1" : "0").apply();
        updateService(AudioFxService.REVERB_CHANGED);
    }

    void overrideEqLevels(short band, short level) {
        if (checkService()) {
            mService.setOverrideLevels(band, level);
        }
    }

    public static String getDeviceDisplayString(Context context,  AudioDeviceInfo info) {
        int type = info == null ? -1 : info.getType();
        switch (type) {
            case TYPE_WIRED_HEADSET:
            case TYPE_WIRED_HEADPHONES:
                return context.getString(org.cyanogenmod.audiofx.R.string.device_headset);
            case TYPE_LINE_ANALOG:
            case TYPE_LINE_DIGITAL:
                return context.getString(org.cyanogenmod.audiofx.R.string.device_line_out);
            case TYPE_BLUETOOTH_SCO:
            case TYPE_BLUETOOTH_A2DP:
            case TYPE_USB_DEVICE:
            case TYPE_USB_ACCESSORY:
            case TYPE_DOCK:
            case TYPE_IP:
                return info.getProductName().toString();
            default:
                return context.getString(org.cyanogenmod.audiofx.R.string.device_speaker);
        }
    }

    private static String appendProductName(AudioDeviceInfo info, String prefix) {
        StringBuilder nm = new StringBuilder(prefix);
        if (info != null && info.getProductName() != null) {
            nm.append("-").append(info.getProductName().toString().replaceAll("\\W+", ""));
        }
        return nm.toString();
    }

    private static String appendDeviceAddress(AudioDeviceInfo info, String prefix) {
        StringBuilder nm = new StringBuilder(prefix);
        if (info != null && info.getAddress() != null) {
            nm.append("-").append(info.getAddress().replace(":", ""));
        }
        return nm.toString();
    }

    public static String getDeviceIdentifierString(AudioDeviceInfo info) {
        int type = info == null ? -1 : info.getType();
        switch (type) {
            case TYPE_WIRED_HEADSET:
            case TYPE_WIRED_HEADPHONES:
                return Constants.DEVICE_HEADSET;
            case TYPE_LINE_ANALOG:
            case TYPE_LINE_DIGITAL:
                return Constants.DEVICE_LINE_OUT;
            case TYPE_BLUETOOTH_SCO:
            case TYPE_BLUETOOTH_A2DP:
                return appendDeviceAddress(info, Constants.DEVICE_PREFIX_BLUETOOTH);
            case TYPE_USB_DEVICE:
            case TYPE_USB_ACCESSORY:
            case TYPE_DOCK:
                return appendProductName(info, Constants.DEVICE_PREFIX_USB);
            case TYPE_IP:
                return appendProductName(info, Constants.DEVICE_PREFIX_CAST);
            default:
                return Constants.DEVICE_SPEAKER;
        }
    }

    /**
     * Set whether to automatically attempt to bind to the service.
     * @param bindToService
     */
    public void setAutoBindToService(boolean bindToService) {
        mShouldBindToService = bindToService;
    }
}
