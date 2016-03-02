
package com.cyngn.audiofx.service;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static android.media.AudioDeviceInfo.*;

public abstract class AudioOutputChangeListener extends AudioDeviceCallback {

    private static final String TAG = "AudioFx-" + AudioOutputChangeListener.class.getSimpleName();

    private boolean mInitial = true;

    private final Context mContext;
    private final AudioManager mAudioManager;
    private final Handler mHandler;
    private int mLastDevice = -1;

    public AudioOutputChangeListener(Context context, Handler handler) {
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = handler;
    }

    public void register() {
        mAudioManager.registerAudioDeviceCallback(this, mHandler);
        callback();
    }

    public void unregister() {
        mAudioManager.unregisterAudioDeviceCallback(this);
    }

    public abstract void onAudioOutputChanged(boolean firstChange, AudioDeviceInfo outputType);

    private void callback() {
        AudioDeviceInfo device = getCurrentDevice();

        if (device == null) {
            Log.w(TAG,  "Unable to determine audio device!");
            return;
        }

        if (mInitial || device.getId() != mLastDevice) {
            Log.d(TAG, "onAudioOutputChanged id: " + device.getId() +
                    " type: " + device.getType() +
                    " name: " + device.getProductName() +
                    " address: " + device.getAddress() +
                    " [" + device.toString() + "]");
            mLastDevice = device.getId();
            onAudioOutputChanged(mInitial, device);
        }

        if (mInitial) {
            mInitial = false;
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
        for (AudioDeviceInfo ai :  mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (ai.getId() == id) {
                return ai;
            }
        }
        return null;
    }
}
