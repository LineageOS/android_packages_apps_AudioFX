package com.cyngn.audiofx;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPort;
import android.util.Log;
import com.cyngn.audiofx.service.OutputDevice;

public abstract class AudioOutputChangeListener implements AudioManager.OnAudioPortUpdateListener {
    private static final boolean DEBUG = false;
    private static final String TAG = AudioOutputChangeListener.class.getSimpleName();

    private boolean mUseBluetooth;
    private boolean mUseHeadset;
    private boolean mUseUSB;
    private boolean mUseWifiDisplay;
    private boolean mUseSpeaker = true;

    private boolean mInitial = true;

    private Context mContext;

    public AudioOutputChangeListener(Context context) {
        this.mContext = context;
    }

    public void register() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.registerAudioPortUpdateListener(this);
    }

    public void unregister() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.unregisterAudioPortUpdateListener(this);
    }

    public abstract void onAudioOutputChanged(boolean firstChange, int outputType);

    @Override
    public void onAudioPortListUpdate(AudioPort[] portList) {
        final boolean prevUseHeadset = mUseHeadset;
        final boolean prevUseBluetooth = mUseBluetooth;
        final boolean prevUseUSB = mUseUSB;
        final boolean prevUseWireless = mUseWifiDisplay;
        final boolean prevUseSpeaker = mUseSpeaker;

        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
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

        if (mInitial
                || prevUseHeadset != mUseHeadset
                || prevUseBluetooth != mUseBluetooth
                || prevUseUSB != mUseUSB
                || prevUseWireless != mUseWifiDisplay
                || prevUseSpeaker != mUseSpeaker) {
            onAudioOutputChanged(mInitial, getInternalAudioOutputRouting());
        }

        if (mInitial) {
            // don't send the first update. we always get an update after registering as a
            // listener. let's use that to establish an initial state.
            mInitial = false;
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

