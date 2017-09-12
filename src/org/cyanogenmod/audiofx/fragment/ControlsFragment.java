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
package org.cyanogenmod.audiofx.fragment;

import android.annotation.Nullable;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.cyanogenmod.audiofx.R;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import org.cyanogenmod.audiofx.knobs.KnobCommander;
import org.cyanogenmod.audiofx.knobs.KnobContainer;

public class ControlsFragment extends AudioFxBaseFragment {

    private static final String TAG = ControlsFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    KnobCommander mKnobCommander;
    KnobContainer mKnobContainer;
    Switch mMaxxVolumeSwitch;

    private CompoundButton.OnCheckedChangeListener mMaxxVolumeListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mConfig.getMaxxVolumeEnabled() != isChecked) {
            }
            mConfig.setMaxxVolumeEnabled(isChecked);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mKnobCommander = KnobCommander.getInstance(getActivity());
    }

    @Override
    public void onPause() {
        MasterConfigControl.getInstance(getActivity()).getCallbacks().removeDeviceChangedCallback(mKnobContainer);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        MasterConfigControl.getInstance(getActivity()).getCallbacks().addDeviceChangedCallback(mKnobContainer);
    }

    @Override
    public void updateFragmentBackgroundColors(int color) {
        if (mKnobContainer != null) {
            mKnobContainer.updateKnobHighlights(color);
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(mConfig.hasMaxxAudio() ? R.layout.controls_maxx_audio
                : R.layout.controls_generic, container, false);
        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mKnobContainer = (KnobContainer) view.findViewById(R.id.knob_container);
        mMaxxVolumeSwitch = (Switch) view.findViewById(R.id.maxx_volume_switch);

        updateFragmentBackgroundColors(getCurrentBackgroundColor());

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setOnCheckedChangeListener(mMaxxVolumeListener);
        }
    }


}
