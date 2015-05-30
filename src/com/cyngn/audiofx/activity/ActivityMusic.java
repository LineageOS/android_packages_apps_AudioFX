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
import com.cyngn.audiofx.knobs.KnobCommander;
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
    KnobCommander mKnobCommander;
    Handler mHandler;
    KnobContainer mKnobContainer;
    EqContainerView mEqContainer;
    ViewGroup mPresetContainer;
    InfiniteViewPager mViewPager;
    PageIndicator mPresetPageIndicator;
    PresetPagerAdapter mDataAdapter;
    InfinitePagerAdapter mInfiniteAdapter;
    InterceptableLinearLayout mInterceptLayout;
    CheckBox mMaxxVolumeSwitch;
    int mCurrentBackgroundColor;
    int mCurrentRealPage;

    // whether we are in the middle of animating while switching devices
    boolean mDeviceChanging;

    private MenuItem mMenuDevices;
    private ViewPager mFakePager;
    private CheckBox mCurrentDeviceToggle;

    private ValueAnimator mDeviceChangeAnimation;
    private int mAnimatingToRealPageTarget = -1;

    /*
     * this array can hold on to arrays which store preset levels,
     * so modifying values in here should only be done with extreme care
     */
    private float[] mSelectedPositionBands;

    // current selected index
    public int mSelectedPosition = 0;
    private Map<Integer, OutputDevice> mBluetoothMap
            = new ArrayMap<Integer, OutputDevice>();

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
                final OutputDevice device = intent.getParcelableExtra(AudioFxService.EXTRA_DEVICE);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mConfig.setCurrentDevice(device, false);
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
        mKnobCommander = KnobCommander.getInstance(this);

        mSelectedPositionBands = mConfig.getPersistedPresetLevels(mConfig.getCurrentPresetIndex());
        if (mConfig.hasMaxxAudio()) {
            setContentView(R.layout.activity_main_maxx_audio);
        } else {
            setContentView(R.layout.activity_main_generic);
        }

        mInterceptLayout = (InterceptableLinearLayout) findViewById(R.id.interceptable_layout);

        mKnobContainer = (KnobContainer) findViewById(R.id.knob_container);
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
                        final int currentIndexBeforeRemove = mConfig.getCurrentPresetIndex();
                        if (mConfig.removePreset(currentIndexBeforeRemove)) {
                            mInfiniteAdapter.notifyDataSetChanged();
                            mDataAdapter.notifyDataSetChanged();
                            mPresetPageIndicator.notifyDataSetChanged();

                            jumpToPreset(mSelectedPosition - 1);
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

        mCurrentBackgroundColor = !mConfig.isCurrentDeviceEnabled()
                ? getResources().getColor(R.color.disabled_eq)
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        updateBackgroundColors(mCurrentBackgroundColor);
        updateActionBarDeviceIcon();
    }

    private void openRenameDialog() {
        AlertDialog.Builder renameDialog = new AlertDialog.Builder(this);
        renameDialog.setTitle(R.string.rename);
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
        boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();
        updateActionBarDeviceIcon();

        if (DEBUG) {
            Log.d(TAG, "updating with current device: " + device);
        }

        if (mCurrentDeviceToggle != null) {
            mCurrentDeviceToggle.setChecked(currentDeviceEnabled);
        }
        if (mInterceptLayout != null) {
            mInterceptLayout.setInterception(!currentDeviceEnabled);
        }

        if (mMaxxVolumeSwitch != null) {
            mMaxxVolumeSwitch.setChecked(mConfig.getMaxxVolumeEnabled());
            mMaxxVolumeSwitch.setEnabled(currentDeviceEnabled);
        }
    }

    private void updateActionBarDeviceIcon() {
        if (mMenuDevices != null) {
            int icon = 0;
            switch (mConfig.getCurrentDevice().getDeviceType()) {
                case OutputDevice.DEVICE_HEADSET:
                    icon = R.drawable.ic_action_dsp_icons_headphones;
                    break;

                case OutputDevice.DEVICE_SPEAKER:
                    icon = R.drawable.ic_action_dsp_icons_speaker;
                    break;

                case OutputDevice.DEVICE_USB:
                    icon = R.drawable.ic_action_dsp_icons_usb;
                    break;

                case OutputDevice.DEVICE_BLUETOOTH:
                    icon = R.drawable.ic_action_dsp_icons_bluetoof;
                    break;

                case OutputDevice.DEVICE_WIRELESS:
                    // TODO add wireless back
                    break;

            }
            mMenuDevices.setIcon(icon);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.devices, menu);
        mMenuDevices = menu.findItem(R.id.devices);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // remove previous bluetooth entries
        for (Integer id : mBluetoothMap.keySet()) {
            mMenuDevices.getSubMenu().removeItem(id);
        }
        mBluetoothMap.clear();

        MenuItem selectedItem = null;

        // add bluetooth devices
        List<OutputDevice> bluetoothDevices =
                mConfig.getBluetoothDevices();
        if (bluetoothDevices != null) {
            for (int i = 0; i < bluetoothDevices.size(); i++) {
                int viewId = View.generateViewId();
                mBluetoothMap.put(viewId, bluetoothDevices.get(i));
                MenuItem item = mMenuDevices.getSubMenu().add(R.id.device_group, viewId, i,
                        bluetoothDevices.get(i).getDisplayName());
                if (bluetoothDevices.get(i).equals(mConfig.getCurrentDevice())) {
                    selectedItem = item;
                }
                item.setIcon(R.drawable.ic_action_dsp_icons_bluetoof);

            }
        }
        mMenuDevices.getSubMenu().setGroupCheckable(R.id.device_group, true, true);

        updateActionBarDeviceIcon();

        // select proper device
        if (mConfig.isServiceBound()) {
            switch (mConfig.getCurrentDevice().getDeviceType()) {
                case OutputDevice.DEVICE_SPEAKER:
                    selectedItem = mMenuDevices.getSubMenu().findItem(R.id.device_speaker);
                    break;
                case OutputDevice.DEVICE_USB:
                    selectedItem = mMenuDevices.getSubMenu().findItem(R.id.device_usb);
                    break;
                case OutputDevice.DEVICE_HEADSET:
                    selectedItem = mMenuDevices.getSubMenu().findItem(R.id.device_headset);
                    break;
            }
            if (selectedItem != null) {
                selectedItem.setChecked(true);
            }
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
            mDeviceChanging = true;
            if (item.isCheckable()) {
                item.setChecked(!item.isChecked());
            }
            mConfig.setCurrentDevice(newDevice, true);
            updateActionBarDeviceIcon();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateBackgroundColors(int color) {
        mEqContainer.setBackgroundColor(color);
        mPresetContainer.setBackgroundColor(color);
        mKnobContainer.updateKnobHighlights(color);
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

            updateDeviceState();
        } else {
            invalidateOptionsMenu();

            // notify eq we should force the bars to animate to their positions
            mEqContainer.resume();
            updateDeviceState();
        }
        jumpToPreset(mConfig.getCurrentPresetIndex());
    }

    private void jumpToPreset(int index) {
        // force instant color jump to preset index
        updateBackgroundColors(mConfig.getAssociatedPresetColorHex(index));

        int diff = index - (mCurrentRealPage % mDataAdapter.getCount());
        // double it, short (e.g. 1 hop) distances sometimes bug out??
        diff += mDataAdapter.getCount();
        int newPage = mCurrentRealPage + diff;
        mViewPager.setCurrentItemAbsolute(newPage, false);
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
                final Integer colorFrom = mCurrentBackgroundColor;
                final Integer colorTo = mConfig.getAssociatedPresetColorHex(newIndex);
                ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
                colorAnimation.setDuration(500);
                colorAnimation.addListener(new Animator.AnimatorListener() {
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
                        mCurrentBackgroundColor = colorTo;
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
                        updateBackgroundColors((Integer) animator.getAnimatedValue());
                    }
                });
                colorAnimation.start();

            }
            mSelectedPositionBands[band] = dB;
        }
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {
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
        if (!mConfig.isServiceBound()) {
            // it's possible we could receive a device change broadcast before the service is bound,
            // from AudioFxService, once service is bound it will call onDeviceChanged.
            return;
        }
        updateDeviceState();
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

        if (mDeviceChangeAnimation != null) {
            mDeviceChangeAnimation.cancel();
        }

        // do background transition manually as viewpager can't handle this bg change
        final Integer colorFrom = mCurrentBackgroundColor;
        final Integer colorTo = !mConfig.isCurrentDeviceEnabled()
                ? getResources().getColor(R.color.disabled_eq)
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        mDeviceChangeAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        mDeviceChangeAnimation.setDuration(500);
        mDeviceChangeAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                updateBackgroundColors((Integer) animator.getAnimatedValue());

                final int N = mConfig.getNumBands();
                for (int i = 0; i < N; i++) { // animate bands
                    float delta = targetBandLevels[i] - mSelectedPositionBands[i];
                    float newBandLevel = mSelectedPositionBands[i] + (delta * animator.getAnimatedFraction());
                    //if (DEBUG_VIEWPAGER) Log.d(TAG, i + ", delta: " + delta + ", newBandLevel: " + newBandLevel);
                    mConfig.setLevel(i, newBandLevel, true);
                }
            }
        });
        mDeviceChangeAnimation.addListener(new Animator.AnimatorListener() {
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
                mCurrentBackgroundColor = colorTo;
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
        });
        mDeviceChangeAnimation.start();
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
            if (mLastOffset - positionOffset > 0.8) { // this is needed for flings
                //Log.e(TAG, "OFFSET DIFF > 0.8! Setting selected position from: " + mSelectedPosition + " to " + newPosition);
                mSelectedPosition = newPosition;
                // mSelectedPositionBands will be reset by setPreset() below calling back to onPresetChanged()

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

            if (mConfig.isCurrentDeviceEnabled()) {
                colorFrom = mConfig.getAssociatedPresetColorHex(mSelectedPosition);
                updateBackgroundColors((Integer) mArgbEval.evaluate(positionOffset, colorFrom, colorTo));
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
                mCurrentBackgroundColor = mConfig.getAssociatedPresetColorHex(mSelectedPosition);
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

    private CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener
            = new CompoundButton.OnCheckedChangeListener() {
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
                    updateBackgroundColors((Integer) animator.getAnimatedValue());
                }
            });
            colorAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    buttonView.setEnabled(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mCurrentBackgroundColor = colorTo;
                    mConfig.setCurrentDeviceEnabled(isChecked);
                    updateDeviceState();
                    buttonView.setEnabled(true);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            colorAnimation.start();
        }
    };

    private CompoundButton.OnCheckedChangeListener mMaxxVolumeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mConfig.setMaxxVolumeEnabled(isChecked);
        }
    };
}
