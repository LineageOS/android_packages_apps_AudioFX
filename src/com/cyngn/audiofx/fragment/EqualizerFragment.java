package com.cyngn.audiofx.fragment;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.ActivityMusic;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.eq.EqContainerView;
import com.cyngn.audiofx.preset.InfinitePagerAdapter;
import com.cyngn.audiofx.preset.InfiniteViewPager;
import com.cyngn.audiofx.preset.PresetPagerAdapter;
import com.cyngn.audiofx.service.OutputDevice;
import com.viewpagerindicator.CirclePageIndicator;
import com.viewpagerindicator.PageIndicator;

public class EqualizerFragment extends AudioFxBaseFragment
        implements MasterConfigControl.EqUpdatedCallback {
    private static final String TAG = ControlsFragment.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_VIEWPAGER = false;

    private final ArgbEvaluator mArgbEval = new ArgbEvaluator();

    Handler mHandler;
    public EqContainerView mEqContainer;
    ViewGroup mPresetContainer;
    InfiniteViewPager mViewPager;
    PageIndicator mPresetPageIndicator;
    PresetPagerAdapter mDataAdapter;
    InfinitePagerAdapter mInfiniteAdapter;
    int mCurrentRealPage;

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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        mSelectedPositionBands = mConfig.getPersistedPresetLevels(mConfig.getCurrentPresetIndex());

    }

    @Override
    public void onPause() {
        super.onPause();
        mConfig.removeEqStateChangeCallback(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mConfig.addEqStateChangeCallback(this);

        final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                ? getDisabledColor()
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        animateBackgroundColorTo(colorTo, null, null);
    }

    @Override
    public void onFakeDataClear() {
        super.onFakeDataClear();
        mInfiniteAdapter.notifyDataSetChanged();
        mDataAdapter.notifyDataSetChanged();
        mPresetPageIndicator.notifyDataSetChanged();
        mViewPager.invalidate();

        jumpToPreset(mConfig.getCurrentPresetIndex());
    }

    @Override
    public void updateFragmentBackgroundColors(int color) {
        if (mEqContainer != null) {
            mEqContainer.setBackgroundColor(color);
        }
        if (mPresetContainer != null) {
            mPresetContainer.setBackgroundColor(color);
        }
    }


    public void jumpToPreset(int index) {
        int diff = index - (mCurrentRealPage % mDataAdapter.getCount());
        // double it, short (e.g. 1 hop) distances sometimes bug out??
        diff += mDataAdapter.getCount();
        int newPage = mCurrentRealPage + diff;
        mViewPager.setCurrentItemAbsolute(newPage, false);
    }

    private void removeCurrentCustomPreset(boolean showWarning) {
        if (showWarning) {
            MasterConfigControl.Preset p = mConfig.getCurrentPreset();
            new AlertDialog.Builder(getActivity())
                    .setMessage(String.format(getString(
                            R.string.remove_custom_preset_warning_message), p.mName))
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

        final int currentIndexBeforeRemove = mConfig.getCurrentPresetIndex();
        if (mConfig.removePreset(currentIndexBeforeRemove)) {
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
        newName.setText(mConfig.getCurrentPreset().mName);
        renameDialog.setView(newName);
        renameDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface d, int which) {
                mConfig.renameCurrentPreset(newName.getText().toString());
                final TextView viewWithTag = (TextView) mViewPager
                        .findViewWithTag(mConfig.getCurrentPreset());
                viewWithTag.setText(newName.getText().toString());
                mDataAdapter.notifyDataSetChanged();
                mViewPager.invalidate();
            }
        });

        renameDialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface d, int which) {
            }
        });

        renameDialog.show();
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

        mEqContainer = (EqContainerView) view.findViewById(R.id.eq_container);
        mPresetContainer = (ViewGroup) view.findViewById(R.id.preset_container);
        mViewPager = (InfiniteViewPager) view.findViewById(R.id.pager);
        CirclePageIndicator indicator = (CirclePageIndicator) view.findViewById(R.id.indicator);
        mPresetPageIndicator = indicator;

        final PresetPagerAdapter adapter = new PresetPagerAdapter(getActivity());

        mInfiniteAdapter = new InfinitePagerAdapter(adapter);
        mDataAdapter = adapter;

        mViewPager.setAdapter(mInfiniteAdapter);
        mFakePager = (ViewPager) view.findViewById(R.id.fake_pager);

        mViewPager.setOnPageChangeListener(mViewPageChangeListener);

        mViewPager.setCurrentItem(mSelectedPosition = mConfig.getCurrentPresetIndex());

        mFakePager.setAdapter(adapter);
        mEqContainer.findViewById(R.id.save).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final int newidx = mConfig.addPresetFromCustom();
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
                        if (mConfig.isUserPreset()) {
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

        indicator.setViewPager(mFakePager, mConfig.getCurrentPresetIndex());
        indicator.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // eat all events
                return true;
            }
        });
        indicator.setSnap(true);

        updateFragmentBackgroundColors(getCurrentBackgroundColor());

        mCurrentRealPage = mInfiniteAdapter.getRealCount() * 100;

    }


    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        // call backs we get when bands are changing, check if the user is physically touching them
        // and set the preset to "custom" and do proper animations.
        if (!fromSystem) { // from user
            if (!mConfig.isCustomPreset() // not on custom already
                    && !mConfig.isUserPreset() // or not on a user preset
                    && !mConfig.isAnimatingToCustom()) { // and animation hasn't started
                if (DEBUG) Log.w(TAG, "met conditions to start an animation to custom trigger");
                // view pager is infinite, so we can't set the item to 0. find NEXT 0
                mConfig.setAnimatingToCustom(true);

                final int newIndex = mConfig.copyToCustom();

                mInfiniteAdapter.notifyDataSetChanged();
                mDataAdapter.notifyDataSetChanged();
                mViewPager.getAdapter().notifyDataSetChanged();
                // do background transition manually as viewpager can't handle this bg change
                final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                        ? getDisabledColor()
                        : mConfig.getAssociatedPresetColorHex(newIndex);
                final Animator.AnimatorListener listener = new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        int diff = newIndex - (mCurrentRealPage % mDataAdapter.getCount());
                        diff += mDataAdapter.getCount();
                        int newPage = mCurrentRealPage + diff;

                        mAnimatingToRealPageTarget = newPage;
                        mViewPager.setCurrentItemAbsolute(newPage);
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
        mDataAdapter.notifyDataSetChanged();
        if (!mDeviceChanging) {
            mSelectedPositionBands = mConfig.getPresetLevels(newPresetIndex);
        }
    }

    @Override
    public void onPresetsChanged() {
        mDataAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {
        int diff = mConfig.getCurrentPresetIndex() - mSelectedPosition;
        final boolean samePage = diff == 0;
        diff = mDataAdapter.getCount() + diff;
        if (DEBUG) {
            Log.d(TAG, "diff: " + diff);
        }

        if (DEBUG) Log.d(TAG, "mCurrentRealPage Before: " + mCurrentRealPage);
        final int newPage = mCurrentRealPage + diff;
        if (DEBUG) Log.d(TAG, "mCurrentRealPage After: " + newPage);

        mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition);
        final float[] targetBandLevels = mConfig.getPresetLevels(mConfig.getCurrentPresetIndex());

        // do background transition manually as viewpager can't handle this bg change
        final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                ? getDisabledColor()
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());

        final Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mConfig.setChangingPresets(true);

                mDeviceChanging = true;

                if (!samePage) {
                    mViewPager.setCurrentItemAbsolute(newPage);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
//                setBackgroundColor(colorTo);
                mConfig.setChangingPresets(false);

                mSelectedPosition = mConfig.getCurrentPresetIndex();
                mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition);

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

                final int N = mConfig.getNumBands();
                for (int i = 0; i < N; i++) { // animate bands
                    float delta = targetBandLevels[i] - mSelectedPositionBands[i];
                    float newBandLevel = mSelectedPositionBands[i]
                            + (delta * animator.getAnimatedFraction());
                    //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ", newBandLevel: " + newBandLevel);
                    mConfig.setLevel(i, newBandLevel, true);
                }
            }
        };

        animateBackgroundColorTo(colorTo, animatorListener, animatorUpdateListener);
    }


    private ViewPager.OnPageChangeListener mViewPageChangeListener = new ViewPager.OnPageChangeListener() {

        int mState;
        float mLastOffset;
        boolean mJustGotToCustomAndSettling;

        @Override
        public void onPageScrolled(int newPosition, float positionOffset, int positionOffsetPixels) {
            if (DEBUG_VIEWPAGER)
                Log.i(TAG, "onPageScrolled(" + newPosition + ", " + positionOffset + ", "
                        + positionOffsetPixels + ")");
            Integer colorFrom;
            Integer colorTo;

            if (newPosition == mAnimatingToRealPageTarget && mConfig.isAnimatingToCustom()) {
                if (DEBUG_VIEWPAGER) Log.w(TAG, "settling var set to true");
                mJustGotToCustomAndSettling = true;
                mAnimatingToRealPageTarget = -1;
            }

            newPosition = newPosition % mDataAdapter.getCount();


            if (mConfig.isAnimatingToCustom() || mDeviceChanging) {
                if (DEBUG_VIEWPAGER)
                    Log.i(TAG, "ignoring onPageScrolled because animating to custom or device is changing");
                return;
            }

            int toPos;
            if (mLastOffset - positionOffset > 0.8) { // this is needed for flings
                //Log.e(TAG, "OFFSET DIFF > 0.8! Setting selected position from: " + mSelectedPosition + " to " + newPosition);
                mSelectedPosition = newPosition;
                // mSelectedPositionBands will be reset by setPreset() below calling back to onPresetChanged()

                mConfig.setPreset(mSelectedPosition);
            }

            if (newPosition < mSelectedPosition || (newPosition == mDataAdapter.getCount() - 1)
                    && mSelectedPosition == 0) {
                // scrolling left <<<<<
                positionOffset = (1 - positionOffset);
                //Log.v(TAG, "<<<<<< positionOffset: " + positionOffset + " (last offset: " + mLastOffset + ")");
                toPos = newPosition;
                colorTo = mConfig.getAssociatedPresetColorHex(toPos);
            } else {
                // scrolling right >>>>>
                //Log.v(TAG, ">>>>>>> positionOffset: " + positionOffset + " (last offset: " + mLastOffset + ")");
                toPos = newPosition + 1 % mDataAdapter.getCount();
                if (toPos >= mDataAdapter.getCount()) {
                    toPos = 0;
                }

                colorTo = mConfig.getAssociatedPresetColorHex(toPos);
            }

            if (!mDeviceChanging && mConfig.isCurrentDeviceEnabled()) {
                colorFrom = mConfig.getAssociatedPresetColorHex(mSelectedPosition);
                setBackgroundColor((Integer) mArgbEval.evaluate(positionOffset, colorFrom, colorTo),
                        true);
            }

            if (mSelectedPositionBands == null) {
                mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition);
            }
            // get current bands
            float[] finalPresetLevels = mConfig.getPresetLevels(toPos);

            final int N = mConfig.getNumBands();
            for (int i = 0; i < N; i++) { // animate bands
                float delta = finalPresetLevels[i] - mSelectedPositionBands[i];
                float newBandLevel = mSelectedPositionBands[i] + (delta * positionOffset);
                //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ", newBandLevel: " + newBandLevel);
                mConfig.setLevel(i, newBandLevel, true);
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
                mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition);
            }
        }


        @Override
        public void onPageScrollStateChanged(int newState) {
            mState = newState;
            if (mDeviceChanging) { // avoid setting unwanted presets during custom animations
                return;
            }
            if (DEBUG_VIEWPAGER)
                Log.w(TAG, "onPageScrollStateChanged(" + stateToString(newState) + ")");

            if (mJustGotToCustomAndSettling && mState == ViewPager.SCROLL_STATE_IDLE) {
                if (DEBUG_VIEWPAGER)
                    Log.w(TAG, "onPageScrollChanged() setting animating to custom = false");
                mJustGotToCustomAndSettling = false;
                mConfig.setChangingPresets(false);
                mConfig.setAnimatingToCustom(false);
            } else {
                if (mState == ViewPager.SCROLL_STATE_IDLE) {
                    animateBackgroundColorTo(!mConfig.isCurrentDeviceEnabled()
                                    ? getDisabledColor()
                                    : mConfig.getAssociatedPresetColorHex(mSelectedPosition),
                            null, null);

                    mConfig.setChangingPresets(false);
                    mConfig.setPreset(mSelectedPosition);
                } else {
                    // not idle
                    mConfig.setChangingPresets(true);
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
