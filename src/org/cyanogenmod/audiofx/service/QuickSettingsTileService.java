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

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.cyanogenmod.audiofx.R;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import org.cyanogenmod.audiofx.service.AudioFxService;

public class QuickSettingsTileService extends TileService
        implements AudioOutputChangeListener.AudioOutputChangedCallback {
    
    private AudioOutputChangeListener mOutputListener;
    private AudioDeviceInfo mCurrentDevice;
    private DevicePreferenceManager mDevicePrefs;
    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        
        mDevicePrefs = new DevicePreferenceManager(getApplicationContext(), mCurrentDevice);
        if (!mDevicePrefs.initDefaults()) {
            stopSelf();
            return;
        }

        HandlerThread handlerThread = new HandlerThread("AudioFx-QsTileService");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mOutputListener = new AudioOutputChangeListener(getApplicationContext(), mHandler);
        mOutputListener.addCallback(this, mDevicePrefs);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mOutputListener.removeCallback(this, mDevicePrefs);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        setTileState(mDevicePrefs.isGlobalEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
    }

    private void setTileState(int state) {
        if (mCurrentDevice == null || mDevicePrefs == null) return;

        Tile tile = getQsTile();
        if (tile == null) return;

        String label = getString(R.string.qs_tile_label,
                MasterConfigControl.getDeviceDisplayString(this, mCurrentDevice));
        String description = getString(R.string.qs_tile_content_description);
        Icon icon = Icon.createWithResource(this, state == Tile.STATE_ACTIVE ?
                R.drawable.ic_qs_visualizer_on : R.drawable.ic_qs_visualizer_off);
        tile.setLabel(label);
        tile.setContentDescription(description);
        tile.setIcon(icon);
        tile.setState(state);

        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        Tile tile = getQsTile();
        setTileState(tile.getState() == Tile.STATE_INACTIVE ?
                Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);

        MasterConfigControl config = MasterConfigControl.getInstance(this);
        config.setCurrentDeviceEnabled(!config.isCurrentDeviceEnabled());
    }

    @Override
    public synchronized void onAudioOutputChanged(boolean firstChange,
            AudioDeviceInfo outputDevice) {
        if (outputDevice == null) return;
        mCurrentDevice = outputDevice;
    }
}
