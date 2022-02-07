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

    private final ArrayList<AudioOutputChangedCallback> mCallbacks =
            new ArrayList<AudioOutputChangedCallback>();

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
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mCallbacks) {
                            for (AudioOutputChangedCallback callback : mCallbacks) {
                                callback.onAudioOutputChanged(mInitial, device);
                            }
                        }
                    }
                });

                if (mInitial) {
                    mInitial = false;
                }
            }
        }
    }

    public void refresh() {
        callback();
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
        final List<AudioDeviceInfo> outputs = new ArrayList<AudioDeviceInfo>();
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

    public AudioDeviceInfo getDeviceById(int id) {
        for (AudioDeviceInfo ai : mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (ai.getId() == id) {
                return ai;
            }
        }
        return null;
    }
}
