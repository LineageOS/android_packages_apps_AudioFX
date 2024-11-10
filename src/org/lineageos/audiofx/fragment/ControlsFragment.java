/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.annotation.Nullable;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.knobs.KnobCommander;
import org.lineageos.audiofx.knobs.KnobContainer;

public class ControlsFragment extends AudioFxBaseFragment {

    private static final String TAG = ControlsFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    KnobCommander mKnobCommander;
    KnobContainer mKnobContainer;
    MaterialSwitch mMaxxVolumeSwitch;
    MaterialSwitch mReverbSwitch;

    private final CompoundButton.OnCheckedChangeListener mMaxxVolumeListener
            = (buttonView, isChecked) -> mConfig.setMaxxVolumeEnabled(isChecked);

    private final CompoundButton.OnCheckedChangeListener mReverbListener
            = (buttonView, isChecked) -> mConfig.setReverbEnabled(isChecked);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mKnobCommander = KnobCommander.getInstance(getActivity());
    }

    @Override
    public void onPause() {
        MasterConfigControl.getInstance(getActivity()).getCallbacks().removeDeviceChangedCallback(
                mKnobContainer);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        MasterConfigControl.getInstance(getActivity()).getCallbacks().addDeviceChangedCallback(
                mKnobContainer);
    }

    @Override
    public void updateFragmentBackgroundColors(int color) {
        if (mKnobContainer != null) {
            mKnobContainer.updateKnobHighlights(color);
        }
        if (mMaxxVolumeSwitch != null) {
            updateSwitchColor(mMaxxVolumeSwitch, color);
        }
        if (mReverbSwitch != null) {
            updateSwitchColor(mReverbSwitch, color);
        }
    }


    public void updateEnabledState() {
        final AudioDeviceInfo device = mConfig.getCurrentDevice();
        boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();

        if (DEBUG) {
            Log.d(TAG, "updating with current device: " + device.getType());
        }

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setChecked(mConfig.getMaxxVolumeEnabled());
            mMaxxVolumeSwitch.setEnabled(currentDeviceEnabled);
        }

        if (mReverbSwitch != null) {
            mReverbSwitch.setChecked(mConfig.getReverbEnabled());
            mReverbSwitch.setEnabled(currentDeviceEnabled);
        }
    }

    private void updateSwitchColor(MaterialSwitch view, int color) {
        ColorStateList trackStates = new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        color,
                        color,
                        Color.GRAY
                }
        );

        view.setTrackTintList(trackStates);
        view.invalidate();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(mConfig.hasMaxxAudio() ? R.layout.controls_maxx_audio
                : R.layout.controls_generic, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mKnobContainer = view.findViewById(R.id.knob_container);
        mMaxxVolumeSwitch = view.findViewById(R.id.maxx_volume_switch);
        mReverbSwitch = view.findViewById(R.id.reverb_switch);

        updateFragmentBackgroundColors(getCurrentBackgroundColor());

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setOnCheckedChangeListener(mMaxxVolumeListener);
        }
        if (mReverbSwitch != null) {
            mReverbSwitch.setOnCheckedChangeListener(mReverbListener);
        }
    }


}
