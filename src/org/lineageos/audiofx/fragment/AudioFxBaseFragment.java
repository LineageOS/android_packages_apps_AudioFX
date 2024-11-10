/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.fragment;

import android.animation.Animator;
import android.app.Fragment;
import android.os.Bundle;

import org.lineageos.audiofx.activity.MasterConfigControl;

public class AudioFxBaseFragment extends Fragment {

    MasterConfigControl mConfig;

    AudioFxFragment mFrag;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFrag = (AudioFxFragment) getParentFragment();

        mConfig = MasterConfigControl.getInstance(getActivity());
    }

    public int getDisabledColor() {
        return mFrag.getDisabledColor();
    }

    public int getCurrentBackgroundColor() {
        return mFrag.mCurrentBackgroundColor;
    }

    public void animateBackgroundColorTo(Integer colorTo, Animator.AnimatorListener listener,
            AudioFxFragment.ColorUpdateListener updateListener) {
        if (mFrag != null) {
            mFrag.animateBackgroundColorTo(colorTo, listener, updateListener);
        }
    }

    /**
     * Call to change the color and propogate it up to the activity, which will call {@link
     * #updateFragmentBackgroundColors(int)}
     *
     * @param color
     */
    public void setBackgroundColor(int color, boolean cancelAnimated) {
        if (mFrag != null) {
            mFrag.updateBackgroundColors(color, cancelAnimated);
        }
    }

    /**
     * For sub class fragments to override and apply the color
     *
     * @param color the new color to apply to any colored elements
     */
    public void updateFragmentBackgroundColors(int color) {
    }

    /**
     * For sub class fragments to override when they might need to update their enabled states
     */
    public void updateEnabledState() {

    }
}
