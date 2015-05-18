package com.cyngn.audiofx.activity;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.text.InputFilter;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.OutputDevice;
import com.viewpagerindicator.CirclePageIndicator;
import com.viewpagerindicator.PageIndicator;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.eq.EqContainerView;
import com.cyngn.audiofx.knobs.KnobContainer;
import com.cyngn.audiofx.knobs.RadialKnob;
import com.cyngn.audiofx.preset.InfinitePagerAdapter;
import com.cyngn.audiofx.preset.InfiniteViewPager;
import com.cyngn.audiofx.preset.PresetPagerAdapter;
import com.cyngn.audiofx.widget.InterceptableLinearLayout;

import java.util.List;
import java.util.Map;

public class ActivityMusic extends Activity implements MasterConfigControl.EqUpdatedCallback {

    private static final String TAG = ActivityMusic.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean DEBUG_VIEWPAGER = false;
    private final ArgbEvaluator mArgbEval = new ArgbEvaluator();
    MasterConfigControl mConfig;
    Handler mHandler;
    RadialKnob mTrebleKnob;
    RadialKnob mBassKnob;
    RadialKnob mVirtualizerKnob;
    KnobContainer mKnobContainer;
    EqContainerView mEqContainer;
    ViewGroup mPresetContainer;
    InfiniteViewPager mViewPager;
    PageIndicator mPresetPageIndicator;
    PresetPagerAdapter mDataAdapter;
    InfinitePagerAdapter mInfiniteAdapter;
    InterceptableLinearLayout mInterceptLayout;
    InterceptableLinearLayout mTopInterceptLayout;
    CheckBox mMaxxVolumeSwitch;
    int mCurrentBackgroundColor;
    int mCurrentRealPage;
    int mCurrentPage;
    boolean mCurrentDeviceOverride;
    boolean mDeviceChanging;
    boolean mAutomatedColorChange;

    private float[] mOverrideFromBands, mOverrideToBands;
    private MenuItem mMenuDevices;
    private ViewPager mFakePager;
    private CheckBox mCurrentDeviceToggle;

    private int mAnimatingToRealPageTarget = -1;

    private float[] mSelectedPositionBands;
    private int mInitialOffset;
    public int mSelectedPosition = 0;
    private Map<Integer, OutputDevice> mBluetoothMap
            = new ArrayMap<Integer, OutputDevice>();
    private OutputDevice mOutputDevice;

    private BroadcastReceiver mDevicesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioFxService.ACTION_BLUETOOTH_DEVICES_UPDATED.equals(intent.getAction())) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        invalidateOptionsMenu();
                    }
                });
            } else if (AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED.equals(intent.getAction())) {
                OutputDevice device = intent.getParcelableExtra(AudioFxService.EXTRA_DEVICE);
                mConfig.setCurrentDevice(device, false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        invalidateOptionsMenu();
                    }
                });
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() savedInstanceState=" + savedInstanceState);
        mHandler = new Handler();
        mConfig = MasterConfigControl.getInstance(this);

        if (mConfig.hasMaxxAudio()) {
            setContentView(R.layout.activity_main_maxx_audio);
        } else {
            setContentView(R.layout.activity_main_generic);
        }

        mInterceptLayout = (InterceptableLinearLayout) findViewById(R.id.interceptable_layout);
        mTopInterceptLayout = (InterceptableLinearLayout) findViewById(R.id.top_content);
        mTopInterceptLayout.setInterception(false);

        mKnobContainer = (KnobContainer) findViewById(R.id.knob_container);
        mTrebleKnob = (RadialKnob) findViewById(R.id.treble_knob);
        mBassKnob = (RadialKnob) findViewById(R.id.bass_knob);
        mVirtualizerKnob = (RadialKnob) findViewById(R.id.virtualizer_knob);
        mMaxxVolumeSwitch = (CheckBox) findViewById(R.id.maxx_volume_switch);
        mEqContainer = (EqContainerView) findViewById(R.id.eq_container);
        mPresetContainer = (ViewGroup) findViewById(R.id.preset_container);
        mViewPager = (InfiniteViewPager) findViewById(R.id.pager);
        CirclePageIndicator indicator = (CirclePageIndicator) findViewById(R.id.indicator);
        mPresetPageIndicator = indicator;

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setOnCheckedChangeListener(mMaxxVolumeListener);
        }

        final PresetPagerAdapter adapter = new PresetPagerAdapter(this);
        InfinitePagerAdapter infinitePagerAdapter = new InfinitePagerAdapter(adapter);

        mInfiniteAdapter = infinitePagerAdapter;
        mDataAdapter = adapter;

        mViewPager.setAdapter(mInfiniteAdapter);
        mFakePager = (ViewPager) findViewById(R.id.fake_pager);

        mViewPager.setOnPageChangeListener(mViewPageChangeListener);

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
                        final int currentIndexBeforeRemove = mConfig.getCurrentPresetIndex();
                        if (mConfig.removePreset(currentIndexBeforeRemove)) {
                            mInfiniteAdapter.notifyDataSetChanged();
                            mDataAdapter.notifyDataSetChanged();
                            mPresetPageIndicator.notifyDataSetChanged();

                            jumpToPreset(mCurrentPage - 1);
                        }
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

        mCurrentRealPage = mInfiniteAdapter.getRealCount() * 100;

        mViewPager.setCurrentItem(mConfig.getCurrentPresetIndex());
        mCurrentBackgroundColor = !mConfig.isCurrentDeviceEnabled()
                ? getResources().getColor(R.color.disabled_eq)
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        updateBackgroundColors();

        // setup actionbar on off switch
        mCurrentDeviceToggle = new CheckBox(this);
        final int padding = getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        mCurrentDeviceToggle.setPaddingRelative(0, 0, padding, 0);
        mCurrentDeviceToggle.setButtonDrawable(R.drawable.toggle_check);
        mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);

        final ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END);

        ActionBar ab = getActionBar();
        ab.setTitle(R.string.app_title);
        if (mConfig.hasMaxxAudio()) {
            ab.setSubtitle(R.string.app_subtitle);
        }
        ab.setCustomView(mCurrentDeviceToggle, params);
        ab.setHomeButtonEnabled(true);
        ab.setDisplayShowTitleEnabled(true);
        ab.setDisplayShowCustomEnabled(true);

        updateDeviceState();
    }

    private void openRenameDialog() {
        AlertDialog.Builder renameDialog = new AlertDialog.Builder(this);
        renameDialog.setTitle("Rename");
        final EditText newName = new EditText(this);
        newName.setText(mConfig.getCurrentPreset().mName);
        renameDialog.setView(newName);
        renameDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface d, int which) {
                mConfig.renameCurrentPreset(newName.getText().toString());
                final TextView viewWithTag = (TextView) mViewPager.findViewWithTag(mConfig.getCurrentPreset());
                viewWithTag.setText(newName.getText().toString());
                mDataAdapter.notifyDataSetChanged();
                mViewPager.invalidate();
            }
        });

        renameDialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface d, int which) {
            }
        });

        renameDialog.show();
    }

    private void updateDeviceState() {
        if (DEBUG) Log.d(TAG, "updateDeviceState()");
        final OutputDevice device = mConfig.getCurrentDevice();
        if (mOutputDevice != null && mOutputDevice.equals(device)) {
            return;
        }
        if (mMenuDevices != null) {
            if (DEBUG) {
                Log.d(TAG, "updating with current device: " + device);
            }
            int icon = 0;
            if (device.getDeviceType() == OutputDevice.DEVICE_HEADSET) {
                //headset
                icon = R.drawable.ic_action_dsp_icons_headphones;
            } else if (device.getDeviceType() == OutputDevice.DEVICE_SPEAKER) {
                //speaker
                icon = R.drawable.ic_action_dsp_icons_speaker;
            } else if (device.getDeviceType() == OutputDevice.DEVICE_USB) {
                // usb
                icon = R.drawable.ic_action_dsp_icons_usb;
            } else if (device.getDeviceType() == OutputDevice.DEVICE_BLUETOOTH) {
                //bluetooth
                icon = R.drawable.ic_action_dsp_icons_bluetoof;
            } else if (device.getDeviceType() == OutputDevice.DEVICE_WIRELESS) {
                // wireless
            }
            mMenuDevices.setIcon(icon);
        }
        final boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();
        if (mCurrentDeviceToggle != null) {
            mCurrentDeviceToggle.setChecked(currentDeviceEnabled);
        }
        if (mInterceptLayout != null) {
            mInterceptLayout.setInterception(!currentDeviceEnabled);
        }
        if (mTrebleKnob != null) {
            mTrebleKnob.setValue(mConfig.getTrebleStrength());
            mTrebleKnob.setOn(mConfig.isTrebleEffectEnabled(), false);
        }
        if (mBassKnob != null) {
            mBassKnob.setValue(mConfig.getBassStrength());
            mBassKnob.setOn(mConfig.isBassEffectEnabled(), false);
        }
        if (mVirtualizerKnob != null) {
            mVirtualizerKnob.setValue(mConfig.getVirtualizerStrength());
            mVirtualizerKnob.setOn(mConfig.isVirtualizerEffectEnabled(), false);
        }
        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setChecked(mConfig.getMaxxVolumeEnabled());
            mMaxxVolumeSwitch.setEnabled(currentDeviceEnabled);
        }

        // apply special factors
        if (device.getDeviceType() == OutputDevice.DEVICE_SPEAKER) {
            // speaker? disable virtual
            mVirtualizerKnob.setEnabled(false);
            mKnobContainer.setKnobVisible(MasterConfigControl.KNOB_VIRTUALIZER, false);
        } else {
            mVirtualizerKnob.setEnabled(true);
            mKnobContainer.setKnobVisible(MasterConfigControl.KNOB_VIRTUALIZER, true);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.devices, menu);
        mCurrentDeviceOverride = false;
        mMenuDevices = menu.findItem(R.id.devices);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        mMenuDevices.getSubMenu().removeGroup(R.id.bluetooth_devices);
        mBluetoothMap.clear();

        // add bluetooth devices
        List<OutputDevice> bluetoothDevices =
                mConfig.getBluetoothDevices();
        if (bluetoothDevices != null) {
            for (int i = 0; i < bluetoothDevices.size(); i++) {
                int viewId = View.generateViewId();
                mBluetoothMap.put(viewId, bluetoothDevices.get(i));
                MenuItem item = mMenuDevices.getSubMenu().add(R.id.bluetooth_devices, viewId, i,
                        bluetoothDevices.get(i).getDisplayName());
                item.setIcon(R.drawable.ic_action_dsp_icons_bluetoof);

            }
            updateDeviceState();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        OutputDevice newDevice = null;
        switch (item.getItemId()) {
            case R.id.device_headset:
                newDevice = new OutputDevice(OutputDevice.DEVICE_HEADSET);
                break;

            case R.id.device_usb:
                newDevice = new OutputDevice(OutputDevice.DEVICE_USB);
                break;

            case R.id.device_speaker:
                newDevice = new OutputDevice(OutputDevice.DEVICE_SPEAKER);
                break;

            default:
                newDevice = mBluetoothMap.get(item.getItemId());
                break;

        }
        if (newDevice != null) {
            mCurrentDeviceOverride = true;
            mDeviceChanging = true;
            mConfig.setCurrentDevice(newDevice, true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateBackgroundColors() {
        mEqContainer.setBackgroundColor(mCurrentBackgroundColor);
        mPresetContainer.setBackgroundColor(mCurrentBackgroundColor);
        mKnobContainer.updateKnobHighlights(mCurrentBackgroundColor);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mConfig.bindService();

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioFxService.ACTION_BLUETOOTH_DEVICES_UPDATED);
        filter.addAction(AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED);
        registerReceiver(mDevicesChangedReceiver, filter);
        mConfig.addEqStateChangeCallback(ActivityMusic.this);

        /**
         * Since the app is 'persisted', the user cannot clear the app's data because the app will
         * still be in memory after they try and force close it. Let's mimic clearing the data if we
         * detect this.
         */
        if (!getSharedPrefsFile("global").exists()) {
            Log.w(TAG, "missing global configuration file, resetting state");
            mConfig.removeEqStateChangeCallback(ActivityMusic.this);
            mConfig.resetState();
            mConfig.addEqStateChangeCallback(ActivityMusic.this);

            getSharedPreferences("global", 0).edit().commit();

            mInfiniteAdapter.notifyDataSetChanged();
            mDataAdapter.notifyDataSetChanged();
            mPresetPageIndicator.notifyDataSetChanged();
            mViewPager.invalidate();

            jumpToPreset(mConfig.getCurrentPresetIndex());
            updateDeviceState();
        } else {
            invalidateOptionsMenu();
        }
    }

    private void jumpToPreset(int index) {
//        mViewPager.setOnPageChangeListener(null);

        mCurrentBackgroundColor = mConfig.getAssociatedPresetColorHex(index);
        updateBackgroundColors();

        int diff = index -(mCurrentRealPage % mDataAdapter.getCount());
        // double it, short (e.g. 1 hop) distances sometimes bug out??
        diff += mDataAdapter.getCount();
        int newPage = mCurrentRealPage + diff;
        mViewPager.setCurrentItemAbsolute(newPage, false);

//        mViewPager.setOnPageChangeListener(mViewPageChangeListener);
    }

    @Override
    protected void onPause() {
        mConfig.unbindService();
        unregisterReceiver(mDevicesChangedReceiver);
        mConfig.removeEqStateChangeCallback(this);
        super.onPause();
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (!fromSystem) { // from user
            if (!mConfig.isCustomPreset() // not on custom already
                    && !mConfig.isUserPreset() // or not on a user preset
                    && !mConfig.isAnimatingToCustom()) { // and animation hasn't started
                if (DEBUG) Log.w(TAG, "met conditions to start an animation to custom trigger");
                // view pager is infinite, so we can't set the item to 0. find NEXT 0
                mConfig.setAnimatingToCustom(true);

                final int lengthBefore = mDataAdapter.getCount();
                final int newIndex = mConfig.copyToCustom();
                final int lengthAfter = mDataAdapter.getCount();

                mInfiniteAdapter.notifyDataSetChanged();
                mDataAdapter.notifyDataSetChanged();
                mViewPager.getAdapter().notifyDataSetChanged();
                // do background transition manually as viewpager can't handle this bg change
                final Integer colorFrom = mCurrentBackgroundColor;
                final Integer colorTo = mConfig.getAssociatedPresetColorHex(newIndex);
                ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
                colorAnimation.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        int diff = newIndex - (mCurrentRealPage % mDataAdapter.getCount());
                        // double it, short (e.g. 1 hop) distances sometimes bug out??
                        diff += mDataAdapter.getCount();
                        int newPage = mCurrentRealPage + diff;

                        mAnimatingToRealPageTarget = newPage;
                            /*if (DEBUG) {
                                Log.i(TAG, "mCurrentPage: " + mCurrentPage);
                                Log.i(TAG, "mCurrentRealPage: " + mCurrentRealPage);
                                Log.i(TAG, "diff: " + diff);
                                Log.i(TAG, "newPage: " + newPage);
                                Log.i(TAG, "animating to index: " + mAnimatingToRealPageTarget);
                                Log.i(TAG, "--------------------------------");
                            }*/
                        mViewPager.setCurrentItemAbsolute(newPage);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                    @Override
                    public void onAnimationUpdate(ValueAnimator animator) {
                        mCurrentBackgroundColor = (Integer) animator.getAnimatedValue();
                        updateBackgroundColors();
                    }
                });
                colorAnimation.start();

            }
            mSelectedPositionBands[band] = dB;
        }
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {
        // do nothing
    }

    @Override
    public void onPresetsChanged() {
        mDataAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {
        updateDeviceState();
        if (DEBUG) {
            Log.d(TAG, "deviceID: " + deviceId);
            Log.d(TAG, "current preset idx for the device id: " + mConfig.getCurrentPresetIndex());
            Log.d(TAG, "mCurrentRealPage: " + mCurrentRealPage);
            Log.d(TAG, "mCurrentPage: " + mCurrentPage);
        }
        int diff = mConfig.getCurrentPresetIndex() - mCurrentPage;
        boolean samePage = diff == 0;
        diff = mDataAdapter.getCount() + diff;
        if (DEBUG) {
            Log.d(TAG, "diff: " + diff);
        }

        if (DEBUG) Log.d(TAG, "mCurrentRealPage Before: " + mCurrentRealPage);
        final int newPage = mCurrentRealPage + diff;
        if (DEBUG) Log.d(TAG, "mCurrentRealPage After: " + newPage);

        // TODO enable these and fix the animation, currently doesn't
        // calculate deltas in onPageScrolled() properly.
        //mOverrideFromBands = mConfig.getPresetLevels(mCurrentRealPage);
        //mOverrideToBands = mConfig.getPresetLevels(mConfig.getCurrentPresetIndex());

        // do background transition manually as viewpager can't handle this bg change
        final Integer colorFrom = mCurrentBackgroundColor;
        final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                ? getResources().getColor(R.color.disabled_eq)
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.setDuration(500);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                mCurrentBackgroundColor = (Integer) animator.getAnimatedValue();
                updateBackgroundColors();
                if (mCurrentBackgroundColor == colorTo) {
                    mAutomatedColorChange = false;
                    mDeviceChanging = false;
                    mOverrideToBands = null;
                    mOverrideFromBands = null;
                }
            }
        });
        mAutomatedColorChange = true;
        if (!samePage) {
            mViewPager.setCurrentItemAbsolute(newPage);
        }
        colorAnimation.start();
    }

    private ViewPager.OnPageChangeListener mViewPageChangeListener = new ViewPager.OnPageChangeListener() {

        int mState;
        float mLastOffset;
        boolean mJustGotToCustomAndSettling;

        @Override
        public void onPageScrolled(int newPosition, float positionOffset, int positionOffsetPixels) {
            if (DEBUG_VIEWPAGER)
                Log.i(TAG, "onPageScrolled(" + newPosition + ", " + positionOffset + ", " + positionOffsetPixels + ")");
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
            if (mLastOffset - positionOffset > 0.8) {
                //Log.e(TAG, "OFFSET DIFF > 0.8! Setting selected position from: " + mSelectedPosition + " to " + newPosition);
                mSelectedPosition = newPosition;
                mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition, false);
                mConfig.setPreset(mSelectedPosition);
            }

            if (newPosition < mSelectedPosition || (newPosition == mDataAdapter.getCount() - 1) && mSelectedPosition == 0) {
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

            if (mConfig.isCurrentDeviceEnabled() && !mAutomatedColorChange) {
                colorFrom = mConfig.getAssociatedPresetColorHex(mSelectedPosition);
                mCurrentBackgroundColor = (Integer) mArgbEval.evaluate(positionOffset, colorFrom, colorTo);
                updateBackgroundColors();
            }

            if (mSelectedPositionBands == null) {
                mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition, false);
            }
            // get current bands
            float[] currentPositionLevels = mOverrideFromBands != null
                    ? mOverrideFromBands
                    : mSelectedPositionBands;
            float[] finalPresetLevels = mOverrideToBands != null
                    ? mOverrideToBands
                    : mConfig.getPresetLevels(toPos, false);

            for (int i = 0; i < mConfig.getNumBands(); i++) {
                float delta = finalPresetLevels[i] - currentPositionLevels[i];
                float newBandLevel = currentPositionLevels[i] + (delta * positionOffset);
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
            mCurrentPage = mSelectedPosition = position;
            mSelectedPositionBands = mConfig.getPresetLevels(mSelectedPosition, false);
        }


        @Override
        public void onPageScrollStateChanged(int newState) {
            if (DEBUG_VIEWPAGER) Log.w(TAG, "onPageScrollStateChanged(" + stateToString(newState) + ")");
            mState = newState;

            if (mJustGotToCustomAndSettling && mState == ViewPager.SCROLL_STATE_IDLE) {
                if (DEBUG_VIEWPAGER) Log.w(TAG, "onPageScrollChanged() setting animating to custom = false");
                mJustGotToCustomAndSettling = false;
                mConfig.setChangingPresets(false);
                mConfig.setAnimatingToCustom(false);
            } else {
                if (mState == ViewPager.SCROLL_STATE_IDLE) {
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

    private CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                                     final boolean isChecked) {
            final Integer colorFrom = mCurrentBackgroundColor;
            final Integer colorTo = isChecked
                    ? mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex())
                    : getResources().getColor(R.color.disabled_eq);
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    mCurrentBackgroundColor = (Integer) animator.getAnimatedValue();
                    updateBackgroundColors();
                    if (mCurrentBackgroundColor == colorTo) {
                        // set parameter and state
                        mConfig.setCurrentDeviceEnabled(isChecked);
                        updateDeviceState();
                        buttonView.setEnabled(true);
                    }
                }
            });
            colorAnimation.start();
            buttonView.setEnabled(false);
        }
    };

    private CompoundButton.OnCheckedChangeListener mMaxxVolumeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mConfig.setMaxxVolumeEnabled(isChecked);
        }
    };
}
