package com.cyngn.audiofx.fragment;

import android.annotation.Nullable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.knobs.KnobCommander;
import com.cyngn.audiofx.knobs.KnobContainer;
import com.cyngn.audiofx.service.OutputDevice;

public class ControlsFragment extends AudioFxBaseFragment {

    private static final String TAG = ControlsFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    KnobCommander mKnobCommander;
    Handler mHandler;
    KnobContainer mKnobContainer;
    CheckBox mMaxxVolumeSwitch;

    private CompoundButton.OnCheckedChangeListener mMaxxVolumeListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mConfig.setMaxxVolumeEnabled(isChecked);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();
        mKnobCommander = KnobCommander.getInstance(getActivity());

    }

    @Override
    public void onPause() {
        MasterConfigControl.getInstance(getActivity()).removeEqStateChangeCallback(mKnobContainer);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        MasterConfigControl.getInstance(getActivity()).addEqStateChangeCallback(mKnobContainer);
    }

    @Override
    public void updateFragmentBackgroundColors(int color) {
        if (mKnobContainer != null) {
            mKnobContainer.updateKnobHighlights(color);
        }
    }


    public void updateEnabledState() {
        final OutputDevice device = mConfig.getCurrentDevice();
        boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();

        if (DEBUG) {
            Log.d(TAG, "updating with current device: " + device);
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
        mMaxxVolumeSwitch = (CheckBox) view.findViewById(R.id.maxx_volume_switch);

        updateFragmentBackgroundColors(getCurrentBackgroundColor());

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setOnCheckedChangeListener(mMaxxVolumeListener);
        }
    }


}
