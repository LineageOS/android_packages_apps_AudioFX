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
package org.lineageos.audiofx.activity;

import android.media.AudioDeviceInfo;

import java.util.ArrayList;
import java.util.List;

public class StateCallbacks {

    private static final String TAG = "StateCallbacks";

    private final MasterConfigControl mConfig;

    private final List<EqUpdatedCallback> mEqUpdateCallbacks = new ArrayList<EqUpdatedCallback>();

    private final List<DeviceChangedCallback> mDeviceChangedCallbacks =
            new ArrayList<DeviceChangedCallback>();

    private final List<EqControlStateCallback> mEqControlStateCallbacks =
            new ArrayList<EqControlStateCallback>();

    StateCallbacks(MasterConfigControl config) {
        mConfig = config;
    }

    /**
     * Implement this callback to receive any changes called to the MasterConfigControl instance
     */
    public interface EqUpdatedCallback {
        /**
         * A band level has been changed
         *
         * @param band       the band index which changed
         * @param dB         the new decibel value
         * @param fromSystem whether the event was from the system or from the user
         */
        void onBandLevelChange(int band, float dB, boolean fromSystem);

        /**
         * The preset has been set
         *
         * @param newPresetIndex the new preset index.
         */
        void onPresetChanged(int newPresetIndex);

        void onPresetsChanged();
    }

    public void addEqUpdatedCallback(EqUpdatedCallback callback) {
        synchronized (mEqUpdateCallbacks) {
            mEqUpdateCallbacks.add(callback);
        }
    }

    public void removeEqUpdatedCallback(EqUpdatedCallback callback) {
        synchronized (mEqUpdateCallbacks) {
            mEqUpdateCallbacks.remove(callback);
        }
    }

    void notifyPresetsChanged() {
        synchronized (mEqUpdateCallbacks) {
            for (final EqUpdatedCallback callback : mEqUpdateCallbacks) {
                callback.onPresetsChanged();
            }
        }
    }

    void notifyPresetChanged(final int index) {
        synchronized (mEqUpdateCallbacks) {
            for (final EqUpdatedCallback callback : mEqUpdateCallbacks) {
                callback.onPresetChanged(index);
            }
        }
    }

    void notifyBandLevelChangeChanged(final int band, final float dB, final boolean fromSystem) {
        synchronized (mEqUpdateCallbacks) {
            for (final EqUpdatedCallback callback : mEqUpdateCallbacks) {
                callback.onBandLevelChange(band, dB, fromSystem);
            }
        }
    }

    /**
     * Callback for changes to visibility and state of the EQ
     */
    public interface EqControlStateCallback {
        void updateEqState(boolean saveVisible, boolean removeVisible,
                boolean renameVisible, boolean unlockVisible);
    }

    public void addEqControlStateCallback(EqControlStateCallback callback) {
        synchronized (mEqControlStateCallbacks) {
            mEqControlStateCallbacks.add(callback);
        }
    }

    public synchronized void removeEqControlStateCallback(EqControlStateCallback callback) {
        synchronized (mEqControlStateCallbacks) {
            mEqControlStateCallbacks.remove(callback);
        }
    }

    void notifyEqControlStateChanged(boolean saveVisible, boolean removeVisible,
            boolean renameVisible, boolean unlockVisible) {
        synchronized (mEqControlStateCallbacks) {
            for (final EqControlStateCallback callback : mEqControlStateCallbacks) {
                callback.updateEqState(saveVisible, removeVisible, renameVisible, unlockVisible);
            }
        }
    }

    /**
     * Register this callback to receive notification when the output device changes.
     */
    public interface DeviceChangedCallback {
        void onDeviceChanged(AudioDeviceInfo device, boolean userChange);

        void onGlobalDeviceToggle(boolean on);

    }

    public void addDeviceChangedCallback(DeviceChangedCallback callback) {
        synchronized (mDeviceChangedCallbacks) {
            mDeviceChangedCallbacks.add(callback);
            callback.onDeviceChanged(mConfig.getCurrentDevice(), false);
        }
    }

    public synchronized void removeDeviceChangedCallback(DeviceChangedCallback callback) {
        synchronized (mDeviceChangedCallbacks) {
            mDeviceChangedCallbacks.remove(callback);
        }
    }

    void notifyGlobalToggle(boolean on) {
        synchronized (mDeviceChangedCallbacks) {
            for (DeviceChangedCallback callback : mDeviceChangedCallbacks) {
                callback.onGlobalDeviceToggle(on);
            }

        }
    }

    void notifyDeviceChanged(final AudioDeviceInfo newDevice, final boolean fromUser) {
        synchronized (mDeviceChangedCallbacks) {
            for (final DeviceChangedCallback callback : mDeviceChangedCallbacks) {
                callback.onDeviceChanged(newDevice, fromUser);
            }
        }
    }
}
