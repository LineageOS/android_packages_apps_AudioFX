/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.service;

import static android.media.AudioDeviceInfo.convertDeviceTypeToInternalDevice;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioOutputChangeListener extends AudioDeviceCallback {

    private static final String TAG = "AudioFx-" + AudioOutputChangeListener.class.getSimpleName();

    private boolean mInitial = true;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final Handler mHandler;
    private int mLastDevice = -1;

    private final ArrayList<AudioOutputChangedCallback> mCallbacks = new ArrayList<>();

    public interface AudioOutputChangedCallback {
        void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputDevice);
    }

    public AudioOutputChangeListener(Context context, Handler handler) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = handler;
    }

    public void addCallback(AudioOutputChangedCallback... callbacks) {
        synchronized (mCallbacks) {
            boolean initial = mCallbacks.size() == 0;
            mCallbacks.addAll(Arrays.asList(callbacks));
            if (initial) {
                mAudioManager.registerAudioDeviceCallback(this, mHandler);
            }
        }
    }

    public void removeCallback(AudioOutputChangedCallback... callbacks) {
        synchronized (mCallbacks) {
            mCallbacks.removeAll(Arrays.asList(callbacks));
            if (mCallbacks.size() == 0) {
                mAudioManager.unregisterAudioDeviceCallback(this);
            }
        }
    }

    private void callback() {
        synchronized (mCallbacks) {
            final AudioDeviceInfo device = getCurrentDevice();

            if (device == null) {
                Log.w(TAG, "Unable to determine audio device!");
                return;
            }

            if (mInitial || device.getId() != mLastDevice) {
                Log.d(TAG, "onAudioOutputChanged id: " + device.getId() +
                        " type: " + device.getType() +
                        " name: " + device.getProductName() +
                        " address: " + device.getAddress() +
                        " [" + device + "]");
                mLastDevice = device.getId();
                mHandler.post(() -> {
                    synchronized (mCallbacks) {
                        for (AudioOutputChangedCallback callback : mCallbacks) {
                            callback.onAudioOutputChanged(mInitial, device);
                        }
                    }
                });

                if (mInitial) {
                    mInitial = false;
                }
            }
        }
    }

    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
        callback();
    }

    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        callback();
    }

    public List<AudioDeviceInfo> getConnectedOutputs() {
        final List<AudioDeviceInfo> outputs = new ArrayList<>();
        final int forMusic = mAudioManager.getDevicesForStream(AudioManager.STREAM_MUSIC);
        for (AudioDeviceInfo ai : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if ((convertDeviceTypeToInternalDevice(ai.getType()) & forMusic) > 0) {
                outputs.add(ai);
            }
        }
        return outputs;
    }

    public AudioDeviceInfo getCurrentDevice() {
        final List<AudioDeviceInfo> devices = getConnectedOutputs();
        return devices.size() > 0 ? devices.get(0) : null;
    }
}
