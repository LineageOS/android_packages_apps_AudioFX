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
package org.lineageos.audiofx.fragment;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import org.lineageos.audiofx.Preset;
import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;
import org.lineageos.audiofx.eq.EqContainerView;
import org.lineageos.audiofx.preset.InfinitePagerAdapter;
import org.lineageos.audiofx.preset.InfiniteViewPager;
import org.lineageos.audiofx.preset.PresetPagerAdapter;
import org.lineageos.audiofx.viewpagerindicator.CirclePageIndicator;

public class EqualizerFragment extends AudioFxBaseFragment
        implements StateCallbacks.DeviceChangedCallback, StateCallbacks.EqUpdatedCallback {

    private static final String TAG = EqualizerFragment.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_VIEWPAGER = false;

    private final ArgbEvaluator mArgbEval = new ArgbEvaluator();

    public EqContainerView mEqContainer;
    InfiniteViewPager mPresetPager;
    CirclePageIndicator mPresetPageIndicator;
    PresetPagerAdapter mDataAdapter;
    InfinitePagerAdapter mInfiniteAdapter;
    int mCurrentRealPage;

    private Handler mHandler;

    // whether we are in the middle of animating while switching devices
    boolean mDeviceChanging;

    private ViewPager mFakePager;

    private int mAnimatingToRealPageTarget = -1;

    /*
     * this array can hold on to arrays which store preset levels,
     * so modifying values in here should only be done with extreme care
     */
    private float[] mSelectedPositionBands;

    // current selected index
    public int mSelectedPosition = 0;

    private MasterConfigControl mConfig;
    private EqualizerManager mEqManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mConfig = MasterConfigControl.getInstance(getActivity());
        mEqManager = mConfig.getEqualizerManager();

        mHandler = new Handler();
    }

    @Override
    public void onPause() {
        mEqContainer.stopListening();
        mConfig.getCallbacks().removeDeviceChangedCallback(this);
        mConfig.getCallbacks().removeEqUpdatedCallback(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mEqContainer.startListening();
        mConfig.getCallbacks().addEqUpdatedCallback(this);
        mConfig.getCallbacks().addDeviceChangedCallback(this);
        mPresetPageIndicator.notifyDataSetChanged();
        mDataAdapter.notifyDataSetChanged();
    }

    @Override
    public void updateFragmentBackgroundColors(int color) {
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().getDecorView().setBackgroundColor(color);
        }
    }

    public void jumpToPreset(int index) {
        int diff = index - (mCurrentRealPage % mDataAdapter.getCount());
        // double it, short (e.g. 1 hop) distances sometimes bug out??
        diff += mDataAdapter.getCount();
        int newPage = mCurrentRealPage + diff;
        mPresetPager.setCurrentItemAbsolute(newPage, false);
    }

    private void removeCurrentCustomPreset(boolean showWarning) {
        if (showWarning) {
            Preset p = mEqManager.getCurrentPreset();
            new AlertDialog.Builder(getActivity())
                    .setMessage(String.format(getString(
                            R.string.remove_custom_preset_warning_message), p.getName()))
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    removeCurrentCustomPreset(false);
                                }
                            })
                    .create()
                    .show();
            return;
        }

        final int currentIndexBeforeRemove = mEqManager.getCurrentPresetIndex();
        if (mEqManager.removePreset(currentIndexBeforeRemove)) {
            mInfiniteAdapter.notifyDataSetChanged();
            mDataAdapter.notifyDataSetChanged();
            mPresetPageIndicator.notifyDataSetChanged();

            jumpToPreset(mSelectedPosition - 1);
        }
    }

    private void openRenameDialog() {
        AlertDialog.Builder renameDialog = new AlertDialog.Builder(getActivity());
        renameDialog.setTitle(R.string.rename);
        final EditText newName = new EditText(getActivity());
        newName.setText(mEqManager.getCurrentPreset().getName());
        renameDialog.setView(newName);
        renameDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface d, int which) {
                mEqManager.renameCurrentPreset(newName.getText().toString());
                final TextView viewWithTag = (TextView) mPresetPager
                        .findViewWithTag(mEqManager.getCurrentPreset());
                viewWithTag.setText(newName.getText().toString());
                mDataAdapter.notifyDataSetChanged();
                mPresetPager.invalidate();
            }
        });

        renameDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface d, int which) {
                    }
                });

        // disable ok button if text is empty
        final AlertDialog dialog = renameDialog.create();
        newName.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(s.length() != 0);
            }
        });

        dialog.show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.equalizer, container, false);
        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSelectedPositionBands = mEqManager.getPersistedPresetLevels(
                mEqManager.getCurrentPresetIndex());
        mSelectedPosition = mEqManager.getCurrentPresetIndex();

        mEqContainer = view.findViewById(R.id.eq_container);
        mPresetPager = (InfiniteViewPager) view.findViewById(R.id.pager);
        mPresetPageIndicator = view.findViewById(R.id.indicator);
        mFakePager = view.findViewById(R.id.fake_pager);

        mEqContainer.findViewById(R.id.save).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final int newidx = mEqManager.addPresetFromCustom();
                        mInfiniteAdapter.notifyDataSetChanged();
                        mDataAdapter.notifyDataSetChanged();
                        mPresetPageIndicator.notifyDataSetChanged();

                        jumpToPreset(newidx);
                    }
                }
        );
        mEqContainer.findViewById(R.id.rename).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mEqManager.isUserPreset()) {
                            openRenameDialog();
                        }
                    }
                }
        );
        mEqContainer.findViewById(R.id.remove).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeCurrentCustomPreset(true);
                    }
                }
        );

        mDataAdapter = new PresetPagerAdapter(getActivity());
        mInfiniteAdapter = new InfinitePagerAdapter(mDataAdapter);

        mPresetPager.setAdapter(mInfiniteAdapter);
        mPresetPager.setOnPageChangeListener(mViewPageChangeListener);

        mFakePager.setAdapter(mDataAdapter);
        mCurrentRealPage = mPresetPager.getCurrentItem();

        mPresetPageIndicator.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // eat all events
                return true;
            }
        });
        mPresetPageIndicator.setSnap(true);

        mPresetPageIndicator.setViewPager(mFakePager, 0);
        mPresetPageIndicator.setCurrentItem(mSelectedPosition);

        mFakePager.setCurrentItem(mSelectedPosition);
        mPresetPager.setCurrentItem(mSelectedPosition);
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        // call backs we get when bands are changing, check if the user is physically touching them
        // and set the preset to "custom" and do proper animations.
        if (!fromSystem) { // from user
            if (!mEqManager.isCustomPreset() // not on custom already
                    && !mEqManager.isUserPreset() // or not on a user preset
                    && !mEqManager.isAnimatingToCustom()) { // and animation hasn't started
                if (DEBUG) Log.w(TAG, "met conditions to start an animation to custom trigger");
                // view pager is infinite, so we can't set the item to 0. find NEXT 0
                mEqManager.setAnimatingToCustom(true);

                final int newIndex = mEqManager.copyToCustom();

                mInfiniteAdapter.notifyDataSetChanged();
                mDataAdapter.notifyDataSetChanged();
                mPresetPager.getAdapter().notifyDataSetChanged();
                // do background transition manually as viewpager can't handle this bg change
                final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                        ? getDisabledColor()
                        : mEqManager.getAssociatedPresetColorHex(newIndex);
                final Animator.AnimatorListener listener = new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        int diff = newIndex - (mCurrentRealPage % mDataAdapter.getCount());
                        diff += mDataAdapter.getCount();
                        int newPage = mCurrentRealPage + diff;

                        mAnimatingToRealPageTarget = newPage;
                        mPresetPager.setCurrentItemAbsolute(newPage);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                };
                animateBackgroundColorTo(colorTo, listener, null);

            }
            mSelectedPositionBands[band] = dB;
        }
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {
    }

    @Override
    public void onPresetsChanged() {
        mDataAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceChanged(AudioDeviceInfo device, boolean userChange) {
        int diff = mEqManager.getCurrentPresetIndex() - mSelectedPosition;
        final boolean samePage = diff == 0;
        diff = mDataAdapter.getCount() + diff;
        if (DEBUG) {
            Log.d(TAG, "diff: " + diff);
        }
        mCurrentRealPage = mPresetPager.getCurrentItem();

        if (DEBUG) Log.d(TAG, "mCurrentRealPage Before: " + mCurrentRealPage);
        final int newPage = mCurrentRealPage + diff;
        if (DEBUG) Log.d(TAG, "mCurrentRealPage After: " + newPage);

        mSelectedPositionBands = mEqManager.getPresetLevels(mSelectedPosition);
        final float[] targetBandLevels = mEqManager.getPresetLevels(
                mEqManager.getCurrentPresetIndex());

        // do background transition manually as viewpager can't handle this bg change
        final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                ? getDisabledColor()
                : mEqManager.getAssociatedPresetColorHex(mEqManager.getCurrentPresetIndex());

        final Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mEqManager.setChangingPresets(true);

                mDeviceChanging = true;

                if (!samePage) {
                    mPresetPager.setCurrentItemAbsolute(newPage);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mEqManager.setChangingPresets(false);

                mSelectedPosition = mEqManager.getCurrentPresetIndex();
                mSelectedPositionBands = mEqManager.getPresetLevels(mSelectedPosition);

                mDeviceChanging = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };

        final AudioFxFragment.ColorUpdateListener animatorUpdateListener
                = new AudioFxFragment.ColorUpdateListener(this) {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                super.onAnimationUpdate(animator);

                final int N = mEqManager.getNumBands();
                for (int i = 0; i < N; i++) { // animate bands
                    float delta = targetBandLevels[i] - mSelectedPositionBands[i];
                    float newBandLevel = mSelectedPositionBands[i]
                            + (delta * animator.getAnimatedFraction());
                    //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ", newBandLevel:
                    // " + newBandLevel);
                    mEqManager.setLevel(i, newBandLevel, true);
                }
            }
        };

        animateBackgroundColorTo(colorTo, animatorListener, animatorUpdateListener);
    }

    @Override
    public void onGlobalDeviceToggle(boolean on) {
        if (!on) {
            mFakePager.setCurrentItem(mFakePager.getCurrentItem(), true);
        }
    }


    private final ViewPager.OnPageChangeListener mViewPageChangeListener =
            new ViewPager.OnPageChangeListener() {

                int mState;
                float mLastOffset;
                boolean mJustGotToCustomAndSettling;

                @Override
                public void onPageScrolled(int newPosition, float positionOffset,
                        int positionOffsetPixels) {
                    if (DEBUG_VIEWPAGER) {
                        Log.i(TAG, "onPageScrolled(" + newPosition + ", " + positionOffset + ", "
                                + positionOffsetPixels + ")");
                    }
                    Integer colorFrom;
                    Integer colorTo;

                    if (newPosition == mAnimatingToRealPageTarget
                            && mEqManager.isAnimatingToCustom()) {
                        if (DEBUG_VIEWPAGER) Log.w(TAG, "settling var set to true");
                        mJustGotToCustomAndSettling = true;
                        mAnimatingToRealPageTarget = -1;
                    }

                    newPosition = newPosition % mDataAdapter.getCount();


                    if (mEqManager.isAnimatingToCustom() || mDeviceChanging) {
                        if (DEBUG_VIEWPAGER) {
                            Log.i(TAG,
                                    "ignoring onPageScrolled because animating to custom or "
                                            + "device is changing");
                        }
                        return;
                    }

                    int toPos;
                    if (mLastOffset - positionOffset > 0.8) { // this is needed for flings
                        //Log.e(TAG, "OFFSET DIFF > 0.8! Setting selected position from: " +
                        // mSelectedPosition + " to " + newPosition);
                        mSelectedPosition = newPosition;
                        // mSelectedPositionBands will be reset by setPreset() below calling back
                        // to onPresetChanged()

                        mEqManager.setPreset(mSelectedPosition);
                    }

                    if (newPosition < mSelectedPosition
                            || (newPosition == mDataAdapter.getCount() - 1)
                            && mSelectedPosition == 0) {
                        // scrolling left <<<<<
                        positionOffset = (1 - positionOffset);
                        //Log.v(TAG, "<<<<<< positionOffset: " + positionOffset + " (last offset:
                        // " + mLastOffset + ")");
                        toPos = newPosition;
                        colorTo = mEqManager.getAssociatedPresetColorHex(toPos);
                    } else {
                        // scrolling right >>>>>
                        //Log.v(TAG, ">>>>>>> positionOffset: " + positionOffset + " (last
                        // offset: " + mLastOffset + ")");
                        toPos = newPosition + 1 % mDataAdapter.getCount();
                        if (toPos >= mDataAdapter.getCount()) {
                            toPos = 0;
                        }

                        colorTo = mEqManager.getAssociatedPresetColorHex(toPos);
                    }

                    if (!mDeviceChanging && mConfig.isCurrentDeviceEnabled()) {
                        colorFrom = mEqManager.getAssociatedPresetColorHex(mSelectedPosition);
                        setBackgroundColor(
                                (Integer) mArgbEval.evaluate(positionOffset, colorFrom, colorTo),
                                true);
                    }

                    if (mSelectedPositionBands == null) {
                        mSelectedPositionBands = mEqManager.getPresetLevels(mSelectedPosition);
                    }
                    // get current bands
                    float[] finalPresetLevels = mEqManager.getPresetLevels(toPos);

                    final int N = mEqManager.getNumBands();
                    for (int i = 0; i < N; i++) { // animate bands
                        float delta = finalPresetLevels[i] - mSelectedPositionBands[i];
                        float newBandLevel = mSelectedPositionBands[i] + (delta * positionOffset);
                        //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ",
                        // newBandLevel: " + newBandLevel);
                        mEqManager.setLevel(i, newBandLevel, true);
                    }
                    mLastOffset = positionOffset;

                }

                @Override
                public void onPageSelected(int position) {
                    if (DEBUG_VIEWPAGER) Log.i(TAG, "onPageSelected(" + position + ")");
                    mCurrentRealPage = position;
                    position = position % mDataAdapter.getCount();
                    if (DEBUG_VIEWPAGER) Log.e(TAG, "onPageSelected(" + position + ")");
                    mFakePager.setCurrentItem(position);
                    mSelectedPosition = position;
                    if (!mDeviceChanging) {
                        mSelectedPositionBands = mEqManager.getPresetLevels(mSelectedPosition);
                    }
                }


                @Override
                public void onPageScrollStateChanged(int newState) {
                    mState = newState;
                    if (mDeviceChanging) { // avoid setting unwanted presets during custom
                        // animations
                        return;
                    }
                    if (DEBUG_VIEWPAGER) {
                        Log.w(TAG, "onPageScrollStateChanged(" + stateToString(newState) + ")");
                    }

                    if (mJustGotToCustomAndSettling && mState == ViewPager.SCROLL_STATE_IDLE) {
                        if (DEBUG_VIEWPAGER) {
                            Log.w(TAG, "onPageScrollChanged() setting animating to custom = false");
                        }
                        mJustGotToCustomAndSettling = false;
                        mEqManager.setChangingPresets(false);
                        mEqManager.setAnimatingToCustom(false);
                    } else {
                        if (mState == ViewPager.SCROLL_STATE_IDLE) {
                            animateBackgroundColorTo(!mConfig.isCurrentDeviceEnabled()
                                            ? getDisabledColor()
                                            : mEqManager.getAssociatedPresetColorHex(mSelectedPosition),
                                    null, null);

                            mEqManager.setChangingPresets(false);
                            mEqManager.setPreset(mSelectedPosition);

                        } else {
                            // not idle
                            mEqManager.setChangingPresets(true);
                        }
                    }
                }

                private String stateToString(int state) {
                    switch (state) {
                        case 0:
                            return "STATE_IDLE";
                        case 1:
                            return "STATE_DRAGGING";
                        case 2:
                            return "STATE_SETTLING";
                        default:
                            return "STATE_WUT";
                    }
                }

            };
}
