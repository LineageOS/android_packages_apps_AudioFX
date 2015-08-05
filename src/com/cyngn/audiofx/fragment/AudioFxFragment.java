package com.cyngn.audiofx.fragment;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.ActivityMusic;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.OutputDevice;
import com.cyngn.audiofx.widget.InterceptableLinearLayout;

import java.util.List;
import java.util.Map;

public class AudioFxFragment extends Fragment implements MasterConfigControl.EqUpdatedCallback,
        ActivityMusic.ActivityStateListener {

    private static final String TAG = AudioFxFragment.class.getSimpleName();

    public static final String TAG_EQUALIZER = "equalizer";
    public static final String TAG_CONTROLS = "controls";

    Handler mHandler;
    int mCurrentBackgroundColor;

    // whether we are in the middle of animating while switching devices
    boolean mDeviceChanging;

    private MenuItem mMenuDevices;

    // current selected index
    public int mSelectedPosition = 0;
    private Map<Integer, OutputDevice> mBluetoothMap
            = new ArrayMap<Integer, OutputDevice>();
    List<OutputDevice> mBluetoothDevices = null;
    private boolean mResumeDeviceChanged;
    private boolean mUsbDeviceConnected;

    EqualizerFragment mEqFragment;
    ControlsFragment mControlFragment;

    InterceptableLinearLayout mInterceptLayout;
    private ValueAnimator mColorChangeAnimator;

    private int mDisabledColor;

    MasterConfigControl mConfig;

    private int mCurrentMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConfig = MasterConfigControl.getInstance(getActivity());
        mHandler = new Handler();
        mDisabledColor = getResources().getColor(R.color.disabled_eq);

        setHasOptionsMenu(true);
        ((ActivityMusic) getActivity()).addToggleListener(this);

        mCurrentMode = ((ActivityMusic) getActivity()).getCurrentMode();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((ActivityMusic) getActivity()).removeToggleListener(this);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCurrentMode = ((ActivityMusic) getActivity()).getCurrentMode();
    }

    private boolean showFragments() {
        boolean createNewFrags = true;
        final FragmentTransaction fragmentTransaction = getChildFragmentManager()
                .beginTransaction();
        if (mEqFragment == null) {
            mEqFragment = (EqualizerFragment) getChildFragmentManager()
                    .findFragmentByTag(TAG_EQUALIZER);

            if (mEqFragment != null) {
                fragmentTransaction.show(mEqFragment);
            }
        }
        if (mControlFragment == null) {
            mControlFragment = (ControlsFragment) getChildFragmentManager()
                    .findFragmentByTag(TAG_CONTROLS);
            if (mControlFragment != null) {
                fragmentTransaction.show(mControlFragment);
            }
        }

        if (mEqFragment != null && mControlFragment != null) {
            createNewFrags = false;
        }

        fragmentTransaction.commit();

        return createNewFrags;
    }

    @Override
    public void onResume() {
        super.onResume();
        mResumeDeviceChanged = true;
        mConfig.bindService();

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioFxService.ACTION_BLUETOOTH_DEVICES_UPDATED);
        filter.addAction(AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED);
        filter.addAction(AudioManager.ACTION_DIGITAL_AUDIO_DOCK_PLUG);
        filter.addAction(AudioManager.ACTION_ANALOG_AUDIO_DOCK_PLUG);
        filter.addAction(AudioManager.ACTION_USB_AUDIO_DEVICE_PLUG);
        getActivity().registerReceiver(mDevicesChangedReceiver, filter);
        mConfig.addEqStateChangeCallback(this);

        updateEnabledState();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mDevicesChangedReceiver);
        mConfig.removeEqStateChangeCallback(this);
        mConfig.unbindService();
    }

    public void onFakeDataClear() {
        int colorTo = !mConfig.isCurrentDeviceEnabled()
                ? mDisabledColor
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        animateBackgroundColorTo(colorTo, null, null);

        updateEnabledState();

        if (mEqFragment != null) {
            mEqFragment.onFakeDataClear();
        }
        if (mControlFragment != null) {
            mControlFragment.onFakeDataClear();
        }
    }

    public void updateBackgroundColors(Integer color, boolean cancelAnimated) {
        if (cancelAnimated && mColorChangeAnimator != null) {
            mColorChangeAnimator.cancel();
        }
        mCurrentBackgroundColor = color;
        if (mEqFragment != null) {
            mEqFragment.updateFragmentBackgroundColors(color);
        }
        if (mControlFragment != null) {
            mControlFragment.updateFragmentBackgroundColors(color);
        }
    }

    public void updateEnabledState() {
        boolean currentDeviceEnabled = mConfig.isCurrentDeviceEnabled();
        if (mEqFragment != null) {
            mEqFragment.updateEnabledState();
        }
        if (mControlFragment != null) {
            mControlFragment.updateEnabledState();
        }

        ((ActivityMusic) getActivity()).setGlobalToggleChecked(currentDeviceEnabled);

        if (mInterceptLayout != null) {
            mInterceptLayout.setInterception(!currentDeviceEnabled);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.devices, menu);
        mMenuDevices = menu.findItem(R.id.devices);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final OutputDevice currentDevice = mConfig.getCurrentDevice();
        updateActionBarDeviceIcon();

        final MenuItem usb = menu.findItem(R.id.device_usb);
        usb.setVisible(mUsbDeviceConnected);

        // select proper device
        if (mConfig.isServiceBound()) {
            MenuItem selectedItem = null;

            if (mBluetoothDevices != null) {
                // remove previous bluetooth entries
                for (Integer id : mBluetoothMap.keySet()) {
                    mMenuDevices.getSubMenu().removeItem(id);
                }
                mBluetoothMap.clear();

                for (int i = 0; i < mBluetoothDevices.size(); i++) {
                    int viewId = View.generateViewId();
                    mBluetoothMap.put(viewId, mBluetoothDevices.get(i));
                    MenuItem item = mMenuDevices.getSubMenu().add(R.id.device_group, viewId, i,
                            mBluetoothDevices.get(i).getDisplayName());
                    if (mBluetoothDevices.get(i).equals(currentDevice)) {
                        selectedItem = item;
                    }
                    item.setIcon(R.drawable.ic_action_dsp_icons_bluetoof);
                }
            }
            mMenuDevices.getSubMenu().setGroupCheckable(R.id.device_group, true, true);

            switch (currentDevice.getDeviceType()) {
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
            final OutputDevice finalNewDevice = newDevice;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mConfig.setCurrentDevice(finalNewDevice, true);
                }
            }, 100);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            Log.w(TAG, "container is null.");
            // no longer displaying this fragment
            return null;
        }

        View root = inflater.inflate(mConfig.hasMaxxAudio()
                ? R.layout.fragment_audiofx_maxxaudio
                : R.layout.fragment_audiofx, container, false);

        final FragmentTransaction fragmentTransaction = getChildFragmentManager()
                .beginTransaction();

        boolean createNewFrags = true;

        if (savedInstanceState != null) {
            createNewFrags = showFragments();
        }

        if (createNewFrags) {
            fragmentTransaction.add(R.id.equalizer, mEqFragment = new EqualizerFragment(), TAG_EQUALIZER);
            fragmentTransaction.add(R.id.controls, mControlFragment = new ControlsFragment(), TAG_CONTROLS);
        }

        fragmentTransaction.commit();


        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // view was destroyed
        final FragmentTransaction fragmentTransaction = getChildFragmentManager()
                .beginTransaction();

        if (mEqFragment != null) {
            fragmentTransaction.remove(mEqFragment);
            mEqFragment = null;
        }
        if (mControlFragment != null) {
            fragmentTransaction.remove(mControlFragment);
            mControlFragment = null;
        }

        fragmentTransaction.commitAllowingStateLoss();

    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mInterceptLayout = (InterceptableLinearLayout) view.findViewById(R.id.interceptable_layout);

        mCurrentBackgroundColor = !mConfig.isCurrentDeviceEnabled()
                ? mDisabledColor
                : mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex());
        updateBackgroundColors(mCurrentBackgroundColor, false);
    }

    public void animateBackgroundColorTo(int colorTo, Animator.AnimatorListener listener,
                                         ColorUpdateListener updateListener) {
        if (mColorChangeAnimator != null) {
            mColorChangeAnimator.cancel();
            mColorChangeAnimator = null;
        }
        mColorChangeAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                mCurrentBackgroundColor, colorTo);
        mColorChangeAnimator.setDuration(500);
        mColorChangeAnimator.addUpdateListener(updateListener != null ? updateListener
                : mColorUpdateListener);
        if (listener != null) {
            mColorChangeAnimator.addListener(listener);
        }
        mColorChangeAnimator.start();
    }

    private BroadcastReceiver mDevicesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioFxService.ACTION_BLUETOOTH_DEVICES_UPDATED.equals(intent.getAction())) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothDevices = mConfig.getBluetoothDevices();
                        getActivity().invalidateOptionsMenu();
                    }
                });
            } else if (AudioFxService.ACTION_DEVICE_OUTPUT_CHANGED.equals(intent.getAction())) {
                getActivity().invalidateOptionsMenu();
            } else if (AudioManager.ACTION_DIGITAL_AUDIO_DOCK_PLUG.equals(intent.getAction())
                    || AudioManager.ACTION_ANALOG_AUDIO_DOCK_PLUG.equals(intent.getAction())
                    || AudioManager.ACTION_USB_AUDIO_DEVICE_PLUG.equals(intent.getAction())) {
                boolean connected = intent.getIntExtra("state", 0) == 1;
                mUsbDeviceConnected = connected;
                getActivity().invalidateOptionsMenu();
            }
        }
    };

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {

    }

    @Override
    public void onPresetChanged(int newPresetIndex) {

    }

    @Override
    public void onPresetsChanged() {

    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {
        getActivity().invalidateOptionsMenu();
        updateEnabledState();

        if (mResumeDeviceChanged) {
            mBluetoothDevices = mConfig.getBluetoothDevices();
            if (mEqFragment != null) {
                mEqFragment.mEqContainer.animateBars();
                mEqFragment.jumpToPreset(mConfig.getCurrentPresetIndex());
            }
            mResumeDeviceChanged = false;
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

    private ValueAnimator.AnimatorUpdateListener mColorUpdateListener
            = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            updateBackgroundColors((Integer) animation.getAnimatedValue(), false);
        }
    };

    @Override
    public void onGlobalToggleChanged(final CompoundButton buttonView, final boolean checked) {
        if (mCurrentMode != ActivityMusic.CURRENT_MODE_AUDIOFX) {
            return;
        }
        final Animator.AnimatorListener animatorListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                buttonView.setEnabled(false);
                mConfig.setCurrentDeviceEnabled(checked);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                updateEnabledState();
                buttonView.setEnabled(true);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                buttonView.setEnabled(true);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };
        final Integer colorTo = checked
                ? mConfig.getAssociatedPresetColorHex(mConfig.getCurrentPresetIndex())
                : mDisabledColor;
        animateBackgroundColorTo(colorTo,
                animatorListener, null);
    }

    @Override
    public void onModeChanged(int mode) {
        mCurrentMode = mode;
    }

    public static class ColorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        final AudioFxBaseFragment mFrag;

        public ColorUpdateListener(AudioFxBaseFragment frag) {
            this.mFrag = frag;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mFrag.setBackgroundColor((Integer) animation.getAnimatedValue(), false);
        }
    }

    public int getDisabledColor() {
        return mDisabledColor;
    }
}
