package com.cyngn.audiofx.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.CompoundButton;

import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.eq.EqUtils;
import com.cyngn.audiofx.knobs.KnobCommander;
import com.cyngn.audiofx.knobs.RadialKnob;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.service.OutputDevice;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Master configuration class for AudioFX.
 *
 * Contains the main hub where data is stored for the current eq graph (which there should be
 * one of, thus only once instance of this class exists).
 *
 * Anyone can obtain an instance of this class. If one does not exist, a new one is created.
 * Immediately before the new instance creation happens, some defaults are pre-populated
 * with MasterConfigControl.saveDefaults(). That method doesn't ever have to be directly called.
 */
public class MasterConfigControl {

    private static final String TAG = MasterConfigControl.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);


    private static final int MSG_SAVE_PRESETS = 1;

    private Context mContext;

    private boolean mHasMaxxAudio;
    private float mMinFreq;
    private float mMaxFreq;

    private float mMinDB;
    private float mMaxDB;
    private int mNumBands;
    private CompoundButton.OnCheckedChangeListener mLockChangeListener;

    EqControlState mEqControlState = new EqControlState();

    private AudioFxService mService;
    private ServiceConnection mServiceConnection;
    private boolean mServiceBinding = false;
    private boolean mServiceUnbinding = false;

    public void bindService() {
        if (mService != null) {
            // service already  bound and we might be resuming - emulate what happens below
            if (!mService.getCurrentDevice().equals(mCurrentDevice)) {
                setCurrentDevice(mService.getCurrentDevice(), false); // update from service
                updateEqControls();
            }
            return;
        }
        if (mServiceBinding) {
            return;
        }
        mServiceBinding = true;
        if (mServiceConnection == null) {
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    mServiceBinding = false;
                    mServiceUnbinding = false;
                    mService = ((AudioFxService.LocalBinder) binder).getService();
                    mService.update();
                    setCurrentDevice(mService.getCurrentDevice(), false); // update from service
                    updateEqControls();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mService = null;
                    mServiceBinding = false;
                    mServiceUnbinding = false;
                }
            };
        }
        Intent serviceIntent = new Intent(mContext, AudioFxService.class);
        mContext.bindService(serviceIntent, mServiceConnection, 0);
        mServiceBinding = true;
    }

    public void unbindService() {
        if (mServiceUnbinding || mService == null) {
            return;
        }
        mServiceUnbinding = true;
        mContext.unbindService(mServiceConnection);
    }

    public boolean isServiceBound() {
        return mService != null;
    }

    public void updateService() {
        if (mService != null) {
            mService.update();
        }
    }

    public boolean isUserPreset() {
        boolean result = mCurrentPreset >= mPredefinedPresets;
        /*if (DEBUG) {
            Log.i(TAG, "isUserPreset(), current preset: " + mCurrentPreset);
            Log.i(TAG, "----> predefined presets: " + mPredefinedPresets);
            Log.d(TAG, "----> RESULT: " + result);
        }*/
        return result;
    }

    public boolean isCustomPreset() {
        return mCurrentPreset == mEQCustomPresetPosition;
    }

    /*
     * presets from the library custom preset.
     */
    private int mPredefinedPresets;
    private float[] mCenterFreqs;
    private float[] mGlobalLevels;

    private AtomicBoolean mAnimatingToCustom = new AtomicBoolean(false);

    // whether we are in between presets, animating them and such
    private boolean mChangingPreset = false;

    private int mCurrentPreset;

    ArrayList<EqUpdatedCallback> mEqUpdateCallbacks;

    private OutputDevice mCurrentDevice =
            new OutputDevice(OutputDevice.DEVICE_SPEAKER); // default!

    private final ArrayList<Preset> mEqPresets = new ArrayList<Preset>();
    private int mEQCustomPresetPosition;

    private String mZeroedBandString;


    public void setCurrentDeviceEnabled(boolean isChecked) {
        getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE, isChecked).apply();
        updateService();
    }

    public boolean isCurrentDeviceEnabled() {
        return getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_GLOBAL_ENABLE, false);
    }

    public CompoundButton.OnCheckedChangeListener getLockChangeListener() {
        if (mLockChangeListener == null) {
            mLockChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isUserPreset()) {
                        ((CustomPreset) mEqPresets.get(mCurrentPreset)).setLocked(isChecked);
                    }
                }
            };
        }
        return mLockChangeListener;
    }

    private static MasterConfigControl sInstance;

    public static MasterConfigControl getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MasterConfigControl(context);
        }
        return sInstance;
    }

    private MasterConfigControl(Context context) {
        mContext = context;
        bindService();
        initialize();
    }

    private synchronized void initialize() {
        // setup eq
        int bands = Integer.parseInt(getGlobalPrefs()
                .getString("equalizer.number_of_bands", "5"));
        final int[] centerFreqs = EqUtils.getCenterFreqs(mContext, bands);
        final int[] bandLevelRange = EqUtils.getBandLevelRange(mContext);

        float[] centerFreqsKHz = new float[centerFreqs.length];
        for (int i = 0; i < centerFreqs.length; i++) {
            centerFreqsKHz[i] = (float) centerFreqs[i] / 1000.0f;
        }

        mMinDB = bandLevelRange[0] / 100;
        mMaxDB = bandLevelRange[1] / 100;

        mNumBands = centerFreqsKHz.length;
        mGlobalLevels = new float[mNumBands];
        for (int i = 0; i < mGlobalLevels.length; i++) {
            mGlobalLevels[i] = 0;
        }

        mZeroedBandString = EqUtils.getZeroedBandsString(getNumBands());

        mCenterFreqs = Arrays.copyOf(centerFreqsKHz, mNumBands);
        System.arraycopy(centerFreqsKHz, 0, mCenterFreqs, 0, mNumBands);
        mMinFreq = mCenterFreqs[0] / 2;
        mMaxFreq = (float) Math.pow(mCenterFreqs[mNumBands - 1], 2) / mCenterFreqs[mNumBands - 2] / 2;

        mEqUpdateCallbacks = new ArrayList<EqUpdatedCallback>();

        // setup equalizer presets
        final int numPresets = Integer.parseInt(getGlobalPrefs()
                .getString("equalizer.number_of_presets", "0"));

        // add library-provided presets
        String[] presetNames = getGlobalPrefs().getString("equalizer.preset_names", "").split("\\|");
        mPredefinedPresets = presetNames.length + 1; // we consider first EQ to be part of predefined
        for (int i = 0; i < numPresets; i++) {
            mEqPresets.add(new StaticPreset(
                    localizePresetName(presetNames[i]),
                    getPersistedPresetLevels(i)));
        }

        // add custom preset
        mEqPresets.add(new PermCustomPreset(mContext.getString(R.string.user),
                getPersistedCustomLevels()));
        mEQCustomPresetPosition = mEqPresets.size() - 1;

        // restore custom prefs
        mEqPresets.addAll(EqUtils.getCustomPresets(mContext, mNumBands));

        // setup default preset for speaker
        mCurrentPreset = Integer.parseInt(getPrefs().getString(Constants.DEVICE_AUDIOFX_EQ_PRESET, "0"));
        if (mCurrentPreset > mEqPresets.size() - 1) {
            mCurrentPreset = 0;
        }
        setPreset(mCurrentPreset);

        mHasMaxxAudio = getGlobalPrefs()
                .getBoolean(Constants.DEVICE_AUDIOFX_GLOBAL_HAS_MAXXAUDIO, false);
    }

    public SharedPreferences getGlobalPrefs() {
        return mContext.getSharedPreferences("global", 0);
    }

    public boolean isChangingPresets() {
        return mChangingPreset;
    }

    public void setChangingPresets(boolean changing) {
        mChangingPreset = changing;
        if (changing) {
            mEqControlState.saveVisible = false;
            mEqControlState.removeVisible = false;
            mEqControlState.renameVisible = false;
            mEqControlState.unlockVisible = false;
            if (mEqCallback != null) {
                mEqCallback.updateEqState();
            }
        }
    }

    public boolean isAnimatingToCustom() {
        return mAnimatingToCustom.get();
    }

    public void setAnimatingToCustom(boolean animating) {
        mAnimatingToCustom.set(animating);
        if (!animating) {
            // finished animation
            updateEqControls();
        }
    }

    /**
     * Resets the state of the config to the default state
     */
    public synchronized void resetState() {
        File prefsdir = new File(mContext.getApplicationInfo().dataDir,"shared_prefs");
        if (prefsdir.exists() && prefsdir.isDirectory()) {
            String[] files = prefsdir.list();
            for (String name : files) {
                File f = new File(prefsdir, name);
                if (f.isFile() && !f.equals("global")) {
                    f.delete();
                }
            }
        }

        if (mService != null) {
            mService.applyDefaults(true);
        } else {
            Log.e(TAG, "resetState() but we have no service to apply defaults to!");
        }

        for (int i = getPresetCount() - 1; i >= 0; i--) {
            Preset p = getPreset(i);
            if (p instanceof CustomPreset) {
                if (p instanceof PermCustomPreset) {
                    ((PermCustomPreset) p).setLevels(EqUtils.stringBandsToFloats(mZeroedBandString));
                } else {
                    removePreset(i);
                }
            }
        }
        setCurrentDeviceEnabled(isCurrentDeviceEnabled());
        final KnobCommander knobs = KnobCommander.getInstance(mContext);
        knobs.setVirtualiserStrength(knobs.getVirtualizerStrength());
        knobs.setVirtualizerEnabled(knobs.isVirtualizerEffectEnabled());
        knobs.setBassStrength(knobs.getBassStrength());
        knobs.setBassEnabled(knobs.isBassEffectEnabled());
        knobs.setTrebleStrength(knobs.getTrebleStrength());
        knobs.setTrebleEnabled(knobs.isTrebleEffectEnabled());
        setMaxxVolumeEnabled(getMaxxVolumeEnabled());
        setPreset(0); // calls updateEqControls()
    }

    private void savePresetsDelayed() {
        mHandler.sendEmptyMessageDelayed(MSG_SAVE_PRESETS, 500);
    }

    public int indexOf(Preset p) {
        return mEqPresets.indexOf(p);
    }

    /**
     * Update the current device used when querying any device-specific values such as the current
     * preset, or the user's custom eq preset settings.
     *
     * @param audioOutputRouting the new device key
     */
    public void setCurrentDevice(OutputDevice audioOutputRouting, boolean userSwitch) {
        mCurrentDevice = audioOutputRouting;
        // need to update the current preset based on the device here.
        int newPreset = Integer.parseInt(getPrefs().getString(Constants.DEVICE_AUDIOFX_EQ_PRESET, "0"));
        if (newPreset > mEqPresets.size() - 1) {
            newPreset = 0;
        }
        setPreset(newPreset);

//        for (int i = 0; i < getNumBands(); i++) {
//            setLevel(i, getLevel(i), true);
//        }

        for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
            callback.onDeviceChanged(audioOutputRouting, userSwitch);
        }
    }

    public Preset getCurrentPreset() {
        return mEqPresets.get(mCurrentPreset);
    }

    /**
     * Copy current config levels from the current preset into custom values since the user has
     * initiated some change. Then update the current preset to 'custom'.
     */
    public int copyToCustom() {
        mGlobalLevels = getPersistedPresetLevels(mCurrentPreset);
        if (DEBUG) {
            Log.w(TAG, "using levels from preset: " + mCurrentPreset + ": " + Arrays.toString(mGlobalLevels));
        }

        String levels = EqUtils.floatLevelsToString(
                EqUtils.convertDecibelsToMillibels(
                        mEqPresets.get(mCurrentPreset).mLevels));
        getGlobalPrefs()
                .edit()
                .putString("custom", levels).apply();

        ((PermCustomPreset) mEqPresets.get(mEQCustomPresetPosition)).setLevels(mGlobalLevels);
        if (DEBUG)
            Log.i(TAG, "copyToCustom() wrote current preset levels to index: " + mEQCustomPresetPosition);
        setPreset(mEQCustomPresetPosition);
        savePresetsDelayed();
        return mEQCustomPresetPosition;
    }

    public int addPresetFromCustom() {
        mGlobalLevels = getPresetLevels(mEQCustomPresetPosition);
        if (DEBUG) {
            Log.w(TAG, "using levels from preset: " + mCurrentPreset + ": " + Arrays.toString(mGlobalLevels));
        }

        int writtenToIndex = addPreset(mGlobalLevels);
        if (DEBUG)
            Log.i(TAG, "addPresetFromCustom() wrote current preset levels to index: " + writtenToIndex);
        setPreset(writtenToIndex);
        savePresetsDelayed();
        return writtenToIndex;
    }

    /**
     * Loops through all presets. And finds the first preset that can be written to.
     * If one is not found, then one is inserted, and that new index is returned.
     * @return the index that the levels were copied to
     */
    private int addPreset(float[] levels) {
        final int customPresets = EqUtils.getCustomPresets(mContext, mNumBands).size();
        // format the name so it's like "Custom <N>", start with "Custom 2"
        final String name = String.format(mContext.getString(R.string.user_n), customPresets + 2);

        CustomPreset customPreset = new CustomPreset(name, levels, false);
        mEqPresets.add(customPreset);

        for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
            callback.onPresetsChanged();
        }

        return mEqPresets.size() -1;
    }


    /**
     * Implement this callback to receive any changes called to the MasterConfigControl instance
     */
    public interface EqUpdatedCallback {
        /**
         * A band level has been changed
         *
         * @param band       the band index which changed
         * @param dB         the new decibel value
         * @param fromSystem whether the event was from the system or from the user
         */
        public void onBandLevelChange(int band, float dB, boolean fromSystem);

        /**
         * The preset has been set
         *
         * @param newPresetIndex the new preset index.
         */
        public void onPresetChanged(int newPresetIndex);

        public void onPresetsChanged();

        public void onDeviceChanged(OutputDevice device, boolean userChange);
    }

    /**
     * Add a call back to be notified.
     *
     * @param callback
     */
    public synchronized void addEqStateChangeCallback(EqUpdatedCallback callback) {
        mEqUpdateCallbacks.add(callback);
    }

    /**
     * remove a callback that has been added before.
     *
     * @param callback
     */
    public synchronized void removeEqStateChangeCallback(EqUpdatedCallback callback) {
        mEqUpdateCallbacks.remove(callback);
    }

    public SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(
                getCurrentDevice().getDevicePreferenceName(mContext), 0);
    }

    /**
     * @return returns the current device key that's in use
     */
    public OutputDevice getCurrentDevice() {
        return mCurrentDevice;
    }

    /**
     * Set a new level!
     * <p/>
     * This call will be propogated to all listeners registered with addEqStateChangeCallback().
     *
     * @param band         the band index the band index which changed
     * @param dB           the new decibel value
     * @param systemChange is this change generated by the system?
     */
    public void setLevel(int band, float dB, boolean systemChange) {
        //if (DEBUG) Log.i(TAG, "setLevel(" + band + ", " + dB + ", " + systemChange + ")");

        mGlobalLevels[band] = dB;
        for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
            callback.onBandLevelChange(band, dB, systemChange);
        }
        if (!systemChange) { // user is touching
            // persist

            if (mEqPresets.get(mCurrentPreset) instanceof CustomPreset) {
                if (mAnimatingToCustom.get()) {
                    if (DEBUG) {
                        Log.d(TAG, "setLevel() not persisting new custom band becuase animating.");
                    }
                } else {
                    ((CustomPreset) mEqPresets.get(mCurrentPreset)).setLevel(band, dB);
                    if (mEqPresets.get(mCurrentPreset) instanceof PermCustomPreset) {
                        // store these as millibels
                        String levels = EqUtils.floatLevelsToString(
                                EqUtils.convertDecibelsToMillibels(
                                        mEqPresets.get(mCurrentPreset).mLevels));
                        getGlobalPrefs()
                                .edit()
                                .putString("custom", levels).apply();
                    }
                }
                // needs to be updated immediately here for the service.
                final String levels = EqUtils.floatLevelsToString(
                        mEqPresets.get(mCurrentPreset).mLevels);
                getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS,
                        levels).apply();
            }
            mService.update();
            savePresetsDelayed();
        }
    }

    /**
     * Set a new preset index.
     * <p/>
     * This call will be propogated to all listeners registered with addEqStateChangeCallback().
     *
     * @param newPresetIndex the new preset index.
     */
    public void setPreset(int newPresetIndex) {
        mCurrentPreset = newPresetIndex;
        updateEqControls(); // do this before callback is propogated
        for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
            callback.onPresetChanged(newPresetIndex);
        }
        // persist
        getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_EQ_PRESET, String.valueOf(newPresetIndex)).apply();

        // update mGlobalLevels
        float[] newlevels = getPresetLevels(newPresetIndex);
        for (int i = 0; i < newlevels.length; i++) {
            setLevel(i, newlevels[i], true);
        }

        getPrefs().edit().putString(Constants.DEVICE_AUDIOFX_EQ_PRESET_LEVELS,
                EqUtils.floatLevelsToString(newlevels)).apply();

        updateService();
    }

    private void updateEqControls() {
        //boolean removeVisible, boolean renameVisible, boolean exportVisible
        mEqControlState.saveVisible = mEQCustomPresetPosition == mCurrentPreset;
        mEqControlState.removeVisible = isUserPreset();
        mEqControlState.renameVisible = isUserPreset();
        mEqControlState.unlockVisible = isUserPreset();
        if (mEqCallback != null) {
            mEqCallback.updateEqState();
        }
    }

    /**
     * @return Get the current preset index
     */
    public int getCurrentPresetIndex() {
        return mCurrentPreset;
    }

    /*===============
     * eq methods
     *===============*/

    public float projectX(double freq) {
        double pos = Math.log(freq);
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return (float) ((pos - minPos) / (maxPos - minPos));
    }

    public double reverseProjectX(float pos) {
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return Math.exp(pos * (maxPos - minPos) + minPos);
    }

    public float projectY(double dB) {
        double pos = (dB - mMinDB) / (mMaxDB - mMinDB);
        return (float) (1 - pos);
    }

    public static double lin2dB(double rho) {
        return rho != 0 ? Math.log(rho) / Math.log(10) * 20 : -99.9;
    }

    public float getMinFreq() {
        return mMinFreq;
    }

    public float getMaxFreq() {
        return mMaxFreq;
    }

    public float getMinDB() {
        return mMinDB;
    }

    public float getMaxDB() {
        return mMaxDB;
    }

    public int getNumBands() {
        return mNumBands;
    }

    public float getCenterFreq(int band) {
        return mCenterFreqs[band];
    }

    public float[] getCenterFreqs() {
        return mCenterFreqs;
    }

    public float[] getLevels() {
        return mGlobalLevels;
    }

    public float getLevel(int band) {
        return mGlobalLevels[band];
    }

    /*===============
     * preset methods
     *===============*/

    public float[] getPersistedPresetLevels(int presetIndex) {
        String newLevels = null;

        if (mEqPresets.size() > presetIndex
                && mEqPresets.get(presetIndex) instanceof PermCustomPreset) {
            return getPersistedCustomLevels();
        } else {
            newLevels = getGlobalPrefs().getString("equalizer.preset." +
                            presetIndex,
                    mZeroedBandString);
        }
        // stored as millibels, convert to decibels
        float[] levels = EqUtils.stringBandsToFloats(newLevels);
        return EqUtils.convertMillibelsToDecibels(levels);
    }

    private float[] getPersistedCustomLevels() {
        String newLevels = getGlobalPrefs().getString("custom",
                mZeroedBandString);
        // stored as millibels, convert to decibels
        float[] levels = EqUtils.stringBandsToFloats(newLevels);
        return EqUtils.convertMillibelsToDecibels(levels);
    }

    /**
     * Get preset levels in decibels for a given index
     *
     * @param presetIndex index which to fetch preset levels for
     * @return an array of floats[] with the given index's preset levels
     */
    public float[] getPresetLevels(int presetIndex) {
        return mEqPresets.get(presetIndex).mLevels;
    }

    /**
     * Helper method which maps a preset index to a color value.
     *
     * @param index the preset index which to fetch a color for
     * @return a color which is associated with this preset.
     */
    public int getAssociatedPresetColorHex(int index) {
        int r = -1;
        index = index % mEqPresets.size();
        if (mEqPresets.get(index) instanceof CustomPreset) {
            r = R.color.preset_custom;
        } else {
            switch (index) {
                case 0:
                    r = R.color.preset_normal;
                    break;
                case 1:
                    r = R.color.preset_classical;
                    break;
                case 2:
                    r = R.color.preset_dance;
                    break;
                case 3:
                    r = R.color.preset_flat;
                    break;
                case 4:
                    r = R.color.preset_folk;
                    break;
                case 5:
                    r = R.color.preset_metal;
                    break;
                case 6:
                    r = R.color.preset_hiphop;
                    break;
                case 7:
                    r = R.color.preset_jazz;
                    break;
                case 8:
                    r = R.color.preset_pop;
                    break;
                case 9:
                    r = R.color.preset_rock;
                    break;
                case 10:
                    r = R.color.preset_electronic;
                    break;
                case 11:
                    r = R.color.preset_small_speakers;
                    break;
                default:
                    return r;
            }
        }
        return mContext.getResources().getColor(r);
    }

    /**
     * Get total number of presets
     *
     * @return int value with total number of presets
     */
    public int getPresetCount() {
        return mEqPresets.size();
    }

    public Preset getPreset(int index) {
        return mEqPresets.get(index);
    }

    public String getLocalizedPresetName(int index) {
        // already localized
        return mEqPresets.get(index).mName;
    }

    private final String localizePresetName(final String name) {
        // missing electronic, multimedia, small speakers, custom
        final String[] names = {
                "Normal", "Classical", "Dance", "Flat", "Folk",
                "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock",
                "Electronic", "Small speakers", "Multimedia",
                "Custom"
        };
        final int[] ids = {
                R.string.normal, R.string.classical, R.string.dance, R.string.flat, R.string.folk,
                R.string.heavy_metal, R.string.hip_hop, R.string.jazz, R.string.pop, R.string.rock,
                R.string.ci_extreme, R.string.small_speakers, R.string.multimedia,
                R.string.user
        };

        for (int i = names.length - 1; i >= 0; --i) {
            if (names[i].equalsIgnoreCase(name)) {
                return mContext.getString(ids[i]);
            }
        }
        return name;
    }

    public boolean isEqualizerLocked() {
        return getCurrentPreset() instanceof CustomPreset
                && !(getCurrentPreset() instanceof PermCustomPreset)
                && ((CustomPreset) getCurrentPreset()).isLocked();
    }

    public void renameCurrentPreset(String s) {
        if (isUserPreset()) {
            ((CustomPreset)getCurrentPreset()).setName(s);
        }

        // notify change
        for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
            callback.onPresetsChanged();
        }

        savePresetsDelayed();
    }

    public boolean removePreset(int index) {
        if (index > mEQCustomPresetPosition) {
            mEqPresets.remove(index);
            for (EqUpdatedCallback callback : mEqUpdateCallbacks) {
                callback.onPresetsChanged();
            }
            if (mCurrentPreset == index) {
                if (DEBUG) {
                    Log.w(TAG, "removePreset() called on current preset, changing preset");
                }
                setPreset(mCurrentPreset - 1);
            }
            savePresetsDelayed();
            return true;
        }
        return false;
    }

    public boolean hasMaxxAudio() {
        return mHasMaxxAudio;
    }

    public boolean getMaxxVolumeEnabled() {
        return getPrefs().getBoolean(Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, false);
    }

    public void setMaxxVolumeEnabled(boolean enable) {
        getPrefs().edit().putBoolean(Constants.DEVICE_AUDIOFX_MAXXVOLUME_ENABLE, enable).apply();
        updateService();
    }


    public EqControlState getEqControlState() {
        return mEqControlState;
    }

    public static class Preset {
        public String mName;
        protected final float[] mLevels;

        private Preset(String name, float[] levels) {
            this.mName = name;
            mLevels = new float[levels.length];
            for (int i = 0; i < levels.length; i++) {
                mLevels[i] = levels[i];
            }
        }

        @Override
        public String toString() {
            return mName + "|" + EqUtils.floatLevelsToString(mLevels);
        }

        private static Preset fromString(String input) {
            final String[] split = input.split("\\|");
            if (split == null || split.length != 2) {
                return null;
            }
            float[] levels = EqUtils.stringBandsToFloats(split[1]);
            return new Preset(split[0], levels);
        }
    }

    public static class StaticPreset extends Preset {
        public StaticPreset(String name, float[] levels) {
            super(name, levels);
        }
    }

    public static class PermCustomPreset extends CustomPreset {

        public PermCustomPreset(String name, float[] levels) {
            super(name, levels, false);
        }

        @Override
        public String toString() {
            return mName + "|" + EqUtils.floatLevelsToString(mLevels);
        }

        public static PermCustomPreset fromString(String input) {
            final String[] split = input.split("\\|");
            if (split == null || split.length != 2) {
                return null;
            }
            float[] levels = EqUtils.stringBandsToFloats(split[1]);
            return new PermCustomPreset(split[0], levels);
        }
    }

    public static class CustomPreset extends Preset {

        private boolean mLocked;

        public CustomPreset(String name, float[] levels, boolean locked) {
            super(name, levels);
            mLocked = locked;
        }

        public boolean isLocked() {
            return mLocked;
        }

        public void setLocked(boolean locked) {
            mLocked = locked;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setLevel(int band, float level) {
            mLevels[band] = level;
        }

        public void setLevels(float[] levels) {
            for (int i = 0; i < levels.length; i++) {
                mLevels[i] = levels[i];
            }
        }

        public float getLevel(int band) {
            return mLevels[band];
        }

        @Override
        public String toString() {
            return super.toString() + "|" + mLocked;
        }

        public static CustomPreset fromString(String input) {
            final String[] split = input.split("\\|");
            if (split == null || split.length != 3) {
                return null;
            }
            float[] levels = EqUtils.stringBandsToFloats(split[1]);
            return new CustomPreset(split[0], levels, Boolean.valueOf(split[2]));
        }
    }

    public List<OutputDevice> getBluetoothDevices() {
        if (mService != null) {
            return mService.getBluetoothDevices();
        }
        return null;
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_SAVE_PRESETS:
                    EqUtils.saveCustomPresets(mContext, mEqPresets);
                    break;
            }
        }
    };

    public static class EqControlState {
        public boolean removeVisible;
        public boolean renameVisible;
        public boolean unlockVisible;
        public boolean saveVisible;
    }

    public void setEqControlCallback(EqControlStateCallback cb) {
        mEqCallback = cb;
    }

    private EqControlStateCallback mEqCallback;
    public interface EqControlStateCallback {
        public void updateEqState();
    }
}
