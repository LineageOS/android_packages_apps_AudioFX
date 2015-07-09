package com.cyngn.audiofx.fragment;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.ActivityMusic;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.DtsControl;

public class DTSFragment extends Fragment implements ActivityMusic.ActivityStateListener {

    private static final String TAG = DTSFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    private ImageView mLogo;
    private boolean mGlobalToggleEnabled;
    private DtsControl mDts;
    private int mMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.i(TAG, "onCreate() called with "
                + "savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        mDts = new DtsControl(getActivity());
        ((ActivityMusic)getActivity()).addToggleListener(this);

        mMode = ((ActivityMusic) getActivity()).getCurrentMode();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMode = ((ActivityMusic) getActivity()).getCurrentMode();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((ActivityMusic)getActivity()).removeToggleListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean dtsEnabledByUser = mDts.getUserEnabled();
        ((ActivityMusic)getActivity())
                .setGlobalToggleChecked(mGlobalToggleEnabled = dtsEnabledByUser);
        updateLogo();

        final boolean dtsEnabled = mDts.isDtsEnabled();
        if (DEBUG) Log.i(TAG, "onResume() dtsEnabledByUser=" + dtsEnabledByUser
                + ", dtsEnabled=" + dtsEnabled);
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.i(TAG, "onPause() called with " + "");
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dts, container, false);

        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mLogo = (ImageView) view.findViewById(R.id.logo);
        updateLogo();
    }

    private void updateLogo() {
        if (mLogo != null) {
            mLogo.setImageResource(mGlobalToggleEnabled
                    ? R.drawable.logo_dts_fc : R.drawable.logo_dts_1c);
            mLogo.animate().cancel();
            mLogo.animate().alpha(mGlobalToggleEnabled ? 1 : .6f);
        }
    }

    @Override
    public void onGlobalToggleChanged(CompoundButton compoundButton, boolean checked) {
        if (DEBUG) Log.i(TAG, "onGlobalToggleChanged() called with "
                + "checked = [" + checked + "]");
        if (mMode != ActivityMusic.CURRENT_MODE_DTS) {
            Log.w(TAG, "not visible, ignoring toggle change");
            // not interested in this update
            return;
        }

        mGlobalToggleEnabled = checked;
        updateLogo();

        mDts.setUserEnabled(checked);
        AudioFxService.updateService(getActivity());
    }

    @Override
    public void onModeChanged(int mode) {
        mMode = mode;
    }

}
