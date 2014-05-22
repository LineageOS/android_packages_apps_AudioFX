/*
 * Copyright (C) 2010-2011 The Android Open Source Project
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

package org.cyanogenmod.audiofx;

import android.app.ActionBar;
import android.app.Activity;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AudioEffect.Descriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;
import org.cyanogenmod.audiofx.widget.EqualizerSurface;
import org.cyanogenmod.audiofx.widget.Gallery;
import org.cyanogenmod.audiofx.widget.InterceptableLinearLayout;
import org.cyanogenmod.audiofx.widget.Knob;
import org.cyanogenmod.audiofx.widget.Knob.OnKnobChangeListener;

import java.util.Arrays;
import java.util.UUID;

/**
 *
 */
public class ActivityMusic extends Activity {

    private final static String TAG = "AudioFXActivityMusic";
    private final static boolean DEBUG = true;

    /**
     * Max number of EQ bands supported
     */
    private final static int EQUALIZER_MAX_BANDS = 32;

    /**
     * Indicates if Virtualizer effect is supported.
     */
    private boolean mVirtualizerSupported;
    private boolean mVirtualizerIsHeadphoneOnly;
    /**
     * Indicates if BassBoost effect is supported.
     */
    private boolean mBassBoostSupported;
    /**
     * Indicates if Equalizer effect is supported.
     */
    private boolean mEqualizerSupported;
    /**
     * Indicates if Preset Reverb effect is supported.
     */
    private boolean mPresetReverbSupported;
    private ServiceConnection mServiceConnection;

    // Equalizer fields
    private int mNumberEqualizerBands;
    private int mEQCustomPresetPosition = 1;
    private int mEQPreset;
    private String[] mEQPresetNames;
    private String[] mReverbPresetNames;

    private int mPRPreset;

    private boolean mEQAnimatingToUserPos = false;

    private ViewGroup mContentEffectsViewGroup;
    private EqualizerSurface mEqualizerSurface;
    private Gallery mEqGallery;
    private Gallery mReverbGallery;
    private Knob mVirtualizerKnob;
    private Knob mBassKnob;

    private boolean mKnobsAvailable = false;
    private Switch mToggleSwitch;

//    private VisualizerView mVisualizer;

    private boolean mStandalone = false;
    private boolean mStateChangeUpdate = false;

    HeadsetService mService;

    private String mCurrentDevice = "speaker"; // the sensible default

    private static final int[] mReverbPresetRSids = {
            R.string.none, R.string.smallroom, R.string.mediumroom, R.string.largeroom,
            R.string.mediumhall, R.string.largehall, R.string.plate
    };

    private Context mContext;

    private int mAudioSession = AudioEffect.ERROR_BAD_VALUE;

    private static final int MSG_UPDATE_EQ = 1;
    private static final int MSG_UPDATE_SERVICE = 2;
    private static final int MSG_UPDATE_EQ_ANIMATE = 3;
    Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_UPDATE_EQ:
                    equalizerUpdateDisplayInternal(false);
                    break;
                case MSG_UPDATE_SERVICE:
                    if (mService != null) {
                        mService.update();
                    }
                    break;
                case MSG_UPDATE_EQ_ANIMATE:
                    equalizerUpdateDisplayInternal(true);
                    break;
            }
        }
    };

    // Broadcast receiver to handle wired and Bluetooth A2dp headset events
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (action.equals(HeadsetService.ACTION_UPDATE_PREFERENCES)) {
                if (mCurrentDeviceOverride == false) { // the user has selected a device, don't interrupt them.
                    if (mService != null) {
                        mCurrentDevice = mService.getAudioOutputRouting();
                    }

                }

                updateUI(true);
                mStateChangeUpdate = true;
                getActionBar().setSelectedNavigationItem(getCurrentDeviceIndex());
                equalizerSetPreset(mEQPreset);
                equalizerUpdateDisplay(true);
            }
        }
    };
    private ArrayAdapter<String> mNavBarDeviceAdapter;

    private float[] mEQValues;
    private boolean mCurrentDeviceOverride = false;

    /*
     * Declares and initializes all objects and widgets in the layouts
     *
     * (non-Javadoc)
     *
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, HeadsetService.class));

        // Init context to be used in listeners
        mContext = this;
        // Receive intent
        // get calling intent
        final Intent intent = getIntent();
        mAudioSession = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,
                AudioEffect.ERROR_BAD_VALUE);
        Log.v(TAG, "audio session: " + mAudioSession);

        // check for errors
        if (getCallingPackage() == null) {
            mStandalone = true;
        } else {
            mStandalone = false;
        }
        setResult(RESULT_OK);

        // query available effects
        final Descriptor[] effects = AudioEffect.queryEffects();

        // Determine available/supported effects
        if (DEBUG) Log.v(TAG, "Available effects:");
        for (final Descriptor effect : effects) {
            if (DEBUG) Log.v(TAG, effect.name.toString() + ", type: " + effect.type.toString());

            if (effect.type.equals(AudioEffect.EFFECT_TYPE_VIRTUALIZER)) {
                mVirtualizerSupported = true;
                if (effect.uuid.equals(UUID.fromString("1d4033c0-8557-11df-9f2d-0002a5d5c51b"))
                        || effect.uuid.equals(UUID.fromString("e6c98a16-22a3-11e2-b87b-f23c91aec05e"))
                        || effect.uuid.equals(UUID.fromString("d3467faa-acc7-4d34-acaf-0002a5d5c51b"))) {
                    mVirtualizerIsHeadphoneOnly = true;
                }
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_BASS_BOOST)) {
                mBassBoostSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_EQUALIZER)) {
                mEqualizerSupported = true;
            } else if (effect.type.equals(AudioEffect.EFFECT_TYPE_PRESET_REVERB)) {
                mPresetReverbSupported = true;
            }
        }

        setContentView(R.layout.music_main);

        mContentEffectsViewGroup = (ViewGroup) findViewById(R.id.contentSoundEffects);

        // setup actionbar on off switch
        mToggleSwitch = new Switch(this);
        final int padding = getResources().getDimensionPixelSize(
                R.dimen.action_bar_switch_padding);
        mToggleSwitch.setPaddingRelative(0, 0, padding, 0);
        mToggleSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView,
                                         final boolean isChecked) {
                // set parameter and state
                getPrefs().edit().putBoolean("audiofx.global.enable", isChecked).apply();

                updateUI(true);
                setInterception(isChecked);
                updateService();
            }
        });

        // setup action bar
        String[] navigationBarDevices = new String[HeadsetService.DEFAULT_AUDIO_DEVICES.length];
        for (int i = 0; i < navigationBarDevices.length; i++) {
            navigationBarDevices[i] = localizeDevice(HeadsetService.DEFAULT_AUDIO_DEVICES[i]);
        }

        mNavBarDeviceAdapter = new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_dropdown_item,
                navigationBarDevices);
        ActionBar.OnNavigationListener navigationListener = new ActionBar.OnNavigationListener() {
            @Override
            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                if (mStateChangeUpdate) {
                    mStateChangeUpdate = false;
                } else {
                    mCurrentDeviceOverride = true;
                    mCurrentDevice = HeadsetService.DEFAULT_AUDIO_DEVICES[itemPosition];
                }
                updateUI(true);
                equalizerSetPreset(mEQPreset);
                equalizerUpdateDisplay(true);
                refreshKnob(mVirtualizerKnob, "audiofx.virtualizer.strength");
                refreshKnob(mBassKnob, "audiofx.bass.strength");
                return true;
            }
        };

        ActionBar ab = getActionBar();
        final ActionBar.LayoutParams params = new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END);

        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        ab.setListNavigationCallbacks(mNavBarDeviceAdapter, navigationListener);
        ab.setBackgroundDrawable(new ColorDrawable(0xFF2E2E2E));
        mStateChangeUpdate = true;
        ab.setSelectedNavigationItem(getCurrentDeviceIndex());

        ab.setCustomView(mToggleSwitch, params);
        ab.setHomeButtonEnabled(true);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP
                        | ActionBar.DISPLAY_SHOW_CUSTOM
                        | ActionBar.DISPLAY_SHOW_HOME
        );

        // initialize views
        mEqualizerSurface = (EqualizerSurface) findViewById(R.id.frequencyResponse);
        mEqGallery = (Gallery) findViewById(R.id.eqPresets);
        mReverbGallery = (Gallery) findViewById(R.id.reverb_gallery);
        mVirtualizerKnob = (Knob) findViewById(R.id.vIStrengthKnob);
        mBassKnob = (Knob) findViewById(R.id.bBStrengthKnob);

        // setup equalizer presets
        final int numPresets = Integer.parseInt(getSharedPreferences("global", 0).getString("equalizer.number_of_presets", "0"));
        mEQPresetNames = new String[numPresets + 2];

        String[] presetNames = getSharedPreferences("global", 0).getString("equalizer.preset_names", "").split("\\|");
        for (short i = 0; i < numPresets; i++) {
            mEQPresetNames[i] = localizePresetName(presetNames[i]);
        }
        mEQPresetNames[numPresets] = getString(R.string.ci_extreme);
        mEQPresetNames[numPresets + 1] = getString(R.string.user);
        mEQCustomPresetPosition = numPresets + 1;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.equalizer_presets,
                mEQPresetNames);

        mEqGallery.setAdapter(adapter);
        mEqGallery.setSelection(mEQPreset);
        mEqGallery.setOnItemSelectedListener(new Gallery.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                mEQPreset = position;
                if (!mEQAnimatingToUserPos) {
                    equalizerSetPreset(position);
                } else if (mEQAnimatingToUserPos && mEQPreset == mEQCustomPresetPosition) {
                    mEQAnimatingToUserPos = false;
                }
            }
        });

        // setup equalizer
        mNumberEqualizerBands = Integer.parseInt(getSharedPreferences("global", 0).getString("equalizer.number_of_bands", "5"));
        mEQValues = new float[mNumberEqualizerBands];
        final int[] centerFreqs = getCenterFreqs();
        final int[] bandLevelRange = getBandLevelRange();
        float[] centerFreqsKHz = new float[centerFreqs.length];
        for (int i = 0; i < centerFreqs.length; i++) {
            centerFreqsKHz[i] = (float) centerFreqs[i] / 1000.0f;
        }
        mEqualizerSurface.setCenterFreqs(centerFreqsKHz);
        mEqualizerSurface.setBandLevelRange(bandLevelRange[0] / 100, bandLevelRange[1] / 100);
        final EqualizerSurface.BandUpdatedListener listener = new EqualizerSurface.BandUpdatedListener() {
            @Override
            public void onBandUpdated(int band, float dB) {
                if (mEQPreset != mEQCustomPresetPosition && !mEQAnimatingToUserPos) {
                    equalizerCopyToCustom();
                    mEQAnimatingToUserPos = true;
                    mEqGallery.setAnimationDuration(1000);
                    mEqGallery.setSelection(mEQCustomPresetPosition, true);
                } else {
                    equalizerBandUpdate(band, (int) (dB * 100));
                }
            }
        };
        mEqualizerSurface.registerBandUpdatedListener(listener);

        // setup virtualizer knob
        mVirtualizerKnob.setMax(OpenSLESConstants.VIRTUALIZER_MAX_STRENGTH -
                OpenSLESConstants.VIRTUALIZER_MIN_STRENGTH);
        mVirtualizerKnob.setOnKnobChangeListener(new OnKnobChangeListener() {
            // Update the parameters while Knob changes and set the
            // effect parameter.
            @Override
            public void onValueChanged(final Knob knob, final int value,
                                       final boolean fromUser) {
                if (fromUser) {
                    // set parameter and state
                    getPrefs().edit().putString("audiofx.virtualizer.strength", String.valueOf(value)).apply();
                    mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
                }
            }

            @Override
            public boolean onSwitchChanged(final Knob knob, boolean on) {
                if (on && !mKnobsAvailable) {
                    showHeadsetMsg();
                    return false;
                }
                if (on) {
                    refreshKnob(knob, "audiofx.virtualizer.strength");
                }
                updateService();
                return true;
            }

            @Override
            public void onAnimationFinished(float endValue) {
                getPrefs().edit().putBoolean("audiofx.virtualizer.enable", endValue != 0).apply();

            }
        });

        // setup bass knob
        mBassKnob.setMax(OpenSLESConstants.BASSBOOST_MAX_STRENGTH
                - OpenSLESConstants.BASSBOOST_MIN_STRENGTH);
        mBassKnob.setOnKnobChangeListener(new OnKnobChangeListener() {
            // Update the parameters while SeekBar changes and set the
            // effect parameter.
            @Override
            public void onValueChanged(final Knob knob, final int value,
                                       final boolean fromUser) {
                if (fromUser) {
                    // set parameter and state
                    getPrefs().edit().putString("audiofx.bass.strength", String.valueOf(value)).apply();
                    mHandler.sendEmptyMessage(MSG_UPDATE_SERVICE);
                }
            }

            @Override
            public boolean onSwitchChanged(final Knob knob, boolean on) {
                if (on && !mKnobsAvailable) {
                    showHeadsetMsg();
                    return false;
                }
                if (on) {
                    refreshKnob(knob, "audiofx.bass.strength");
                }
                updateService();
                return true;
            }

            @Override
            public void onAnimationFinished(float endValue) {
                getPrefs().edit().putBoolean("audiofx.bass.enable", endValue != 0).apply();
                updateService();
            }
        });


        // setup reverb presets
        mReverbPresetNames = new String[mReverbPresetRSids.length];
        for (short i = 0; i < mReverbPresetRSids.length; ++i) {
            mReverbPresetNames[i] = getString(mReverbPresetRSids[i]);
        }

        ArrayAdapter<String> reverbAdapter = new ArrayAdapter<String>(this,
                R.layout.equalizer_presets, mReverbPresetNames);
        mReverbGallery.setAdapter(reverbAdapter);
        mReverbGallery.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mPRPreset) {
                    presetReverbSetPreset(position);
                }
                mPRPreset = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mReverbGallery.setSelection(mPRPreset);
    }

    private void refreshKnob(Knob knob, String value) {
        int strength = Integer.valueOf(getPrefs().getString(value, "0"));
        knob.setValue(strength, true);
    }


    private SharedPreferences getPrefs() {
        return getSharedPreferences(mCurrentDevice, 0);
    }

    private final String localizePresetName(final String name) {
        final String[] names = {
                "Normal", "Classical", "Dance", "Flat", "Folk",
                "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"
        };
        final int[] ids = {
                R.string.normal, R.string.classical, R.string.dance, R.string.flat, R.string.folk,
                R.string.heavy_metal, R.string.hip_hop, R.string.jazz, R.string.pop, R.string.rock
        };

        for (int i = names.length - 1; i >= 0; --i) {
            if (names[i].equals(name)) {
                return getString(ids[i]);
            }
        }
        return name;
    }

    private final String localizeDevice(String device) {
        return getString(mContext.getResources().getIdentifier("device_" + device, "string", getPackageName()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mServiceConnection == null) {
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    mService = ((HeadsetService.LocalBinder) binder).getService();
                    if (!mCurrentDeviceOverride) {
                        mCurrentDevice = mService.getAudioOutputRouting();
                    }
                    updateUI(true);

                    mStateChangeUpdate = true;
                    getActionBar().setSelectedNavigationItem(getCurrentDeviceIndex());
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mService = null;
                }
            };
        }
        Intent serviceIntent = new Intent(this, HeadsetService.class);
        bindService(serviceIntent, mServiceConnection, 0);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HeadsetService.ACTION_UPDATE_PREFERENCES);
        registerReceiver(mReceiver, intentFilter);

        equalizerUpdateDisplay(true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unbindService(mServiceConnection);

        // Unregister for broadcast intents. (These affect the visible UI,
        // so we only care about them while we're in the foreground.)
        unregisterReceiver(mReceiver);
    }

    private void updateUI(boolean fromNavbar) {
        if (!fromNavbar) {
            mStateChangeUpdate = true;
            getActionBar().setSelectedNavigationItem(getCurrentDeviceIndex());
        }

        final boolean isEnabled = getPrefs().getBoolean("audiofx.global.enable", false);
        mKnobsAvailable = !mCurrentDevice.equals("speaker");

        mToggleSwitch.setChecked(isEnabled);

        if (mVirtualizerSupported) {
            int strength = Integer.valueOf(getPrefs().getString("audiofx.virtualizer.strength", "0"));
            mVirtualizerKnob.setValue(strength);
            mVirtualizerKnob.setEnabled(mKnobsAvailable);
            if (!fromNavbar) {
                mVirtualizerKnob.setOn(getPrefs().getBoolean("audiofx.virtualizer.enable", true));
            }

        } else {
            mVirtualizerKnob.setVisibility(View.GONE);
        }
        if (mBassBoostSupported) {
            mBassKnob.setValue(Integer.valueOf(getPrefs().getString("audiofx.bass.strength", "0")));
            mBassKnob.setEnabled(mKnobsAvailable);
            if (!fromNavbar) {
                mBassKnob.setOn(getPrefs().getBoolean("audiofx.bass.enable", true));
            }
        } else {
            mBassKnob.setVisibility(View.GONE);
        }
        if (mEqualizerSupported) {
            mEQPreset = Integer.valueOf(getPrefs().getString("audiofx.eq.preset", String.valueOf(mEQCustomPresetPosition)));
            mEqGallery.setSelection(mEQPreset);
            mEqGallery.setEnabled(isEnabled);

            if (!fromNavbar) {
                equalizerUpdateDisplay(false);
            }
        }
        if (mPresetReverbSupported) {
            mPRPreset = Integer.valueOf(getPrefs().getString("audiofx.reverb.preset", "0"));
            mReverbGallery.setSelection(mPRPreset, true);
            mReverbGallery.setEnabled(isEnabled);
        }

        setInterception(isEnabled);
    }

    private void updateUI() {
        updateUI(false);
    }

    private int getCurrentDeviceIndex() {
        for (int i = 0; i < HeadsetService.DEFAULT_AUDIO_DEVICES.length; i++) {
            if (HeadsetService.DEFAULT_AUDIO_DEVICES[i].equals(mCurrentDevice)) {
                return i;
            }
        }
        return 0;
    }

    private void setInterception(boolean isEnabled) {
        final InterceptableLinearLayout ill =
                (InterceptableLinearLayout) findViewById(R.id.contentSoundEffects);
        ill.setInterception(!isEnabled);
        if (isEnabled) {
            ill.setOnClickListener(null);
        } else {
            ill.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Toast toast = Toast.makeText(mContext,
                            getString(R.string.power_on_prompt), Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
            });
        }
    }

    private int[] getBandLevelRange() {
        String savedCenterFreqs = getSharedPreferences("global", 0).getString("equalizer.band_level_range", null);
        if (savedCenterFreqs == null || savedCenterFreqs.isEmpty()) {
            return new int[]{-1500, 1500};
        } else {
            String[] split = savedCenterFreqs.split(";");
            int[] freqs = new int[split.length];
            for (int i = 0; i < split.length; i++) {
                freqs[i] = Integer.valueOf(split[i]);
            }
            return freqs;
        }
    }

    private int[] getCenterFreqs() {
        String savedCenterFreqs = getSharedPreferences("global", 0).getString("equalizer.center_freqs", HeadsetService.getZeroedBandsString(mNumberEqualizerBands));
        String[] split = savedCenterFreqs.split(";");
        int[] freqs = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            freqs[i] = Integer.valueOf(split[i]);
        }
        return freqs;
    }

    /**
     * Updates the EQ by getting the parameters.
     */
    private void equalizerUpdateDisplay(boolean animate) {
        mHandler.removeMessages(animate ? MSG_UPDATE_EQ_ANIMATE : MSG_UPDATE_EQ);
        mHandler.sendEmptyMessageDelayed(animate ? MSG_UPDATE_EQ_ANIMATE : MSG_UPDATE_EQ, 100);
    }

    private void equalizerUpdateDisplayInternal(boolean animate) {
        String levelsString = null;
        float[] floats;

        if (mEQPreset == mEQCustomPresetPosition) {
            // load custom preset for current device
            // here mEQValues needs to be pre-populated with the user's preset values.
            if (animate) {
                floats = new float[mEQValues.length];
                for (int band = 0; band < floats.length; band++) {
                    final float level = mEQValues[band];
                    floats[band] = level / 100.0f;
                }
                mEqualizerSurface.setBands(floats);
            } else {
                for (int band = 0; band < mNumberEqualizerBands; band++) {
                    mEqualizerSurface.setBand(band, (float) mEQValues[band] / 100.0f);
                }
            }
        } else {
            // try to load preset
            levelsString = getSharedPreferences("global", 0).getString("equalizer.preset." + mEQPreset, HeadsetService.getZeroedBandsString(mNumberEqualizerBands));
            String[] bandLevels = levelsString.split(";");
            floats = new float[bandLevels.length];
            for (int band = 0; band < bandLevels.length; band++) {
                final float level = Float.parseFloat(bandLevels[band]);
                floats[band] = level / 100.0f;
                if (!animate) {
                    mEqualizerSurface.setBand(band, (float) level / 100.0f);
                }
            }
            if (animate) {
                mEqualizerSurface.setBands(floats);
            }
        }
    }

    private void equalizerCopyToCustom() {
        if (DEBUG) Log.d(TAG, "equalizerCopyToCustom()");
        StringBuilder bandLevels = new StringBuilder();
        for (int band = 0; band < mNumberEqualizerBands; band++) {
            final float level = mEqualizerSurface.getBand(band);
            mEQValues[band] = level * 100;
            bandLevels.append(mEQValues[band]);
            bandLevels.append(";");
        }
        // remove trailing ";"
        bandLevels.deleteCharAt(bandLevels.length() - 1);
        getPrefs().edit().putString("audiofx.eq.bandlevels", bandLevels.toString()).apply();
        getPrefs().edit().putString("audiofx.eq.bandlevels.custom", bandLevels.toString()).apply();
    }

    private void equalizerBandUpdate(final int band, final int level) {
        if (DEBUG) Log.d(TAG, "equalizerBandUpdate(band: " + band + ", level: " + level + ")");

        mEQValues[band] = level;
        if (DEBUG) Log.d(TAG, "new mEQValues: " + Arrays.toString(mEQValues));
        equalizerUpdateDisplay(false);

        // save
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mNumberEqualizerBands; i++) {
            builder.append(mEQValues[i]);
            builder.append(";");
        }
        builder.deleteCharAt(builder.length() - 1);
        getPrefs().edit().putString("audiofx.eq.bandlevels", builder.toString()).apply();
        getPrefs().edit().putString("audiofx.eq.bandlevels.custom", builder.toString()).apply();

        updateService();
    }

    private void updateService() {
        mHandler.removeMessages(MSG_UPDATE_SERVICE);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_SERVICE, 100);
    }

    private void equalizerSetPreset(final int preset) {
        if (DEBUG) Log.d(TAG, "equalizerSetPreset(" + preset + ")");

        mEQPreset = preset;
        getPrefs().edit().putString("audiofx.eq.preset", String.valueOf(preset)).apply();

        String newLevels = null;
        if (preset == mEQCustomPresetPosition) {
            // load custom if possible
            newLevels = getPrefs().getString("audiofx.eq.bandlevels.custom", HeadsetService.getZeroedBandsString(mNumberEqualizerBands));

            String[] loadedStrings = newLevels.split(";");
            for (int band = 0; band < mNumberEqualizerBands; band++) {
                mEQValues[band] = Float.parseFloat(loadedStrings[band]);
            }

            if (DEBUG) Log.d(TAG, "new mEQValues: " + Arrays.toString(mEQValues));
        } else {
            newLevels = getSharedPreferences("global", 0).getString("equalizer.preset." + preset, HeadsetService.getZeroedBandsString(mNumberEqualizerBands));
        }
        getPrefs().edit().putString("audiofx.eq.bandlevels", newLevels).apply();
        equalizerUpdateDisplay(true);

        updateService();
    }


    private void presetReverbSetPreset(final int preset) {
        getPrefs().edit().putString("audiofx.reverb.preset", String.valueOf(preset)).apply();
        updateService();
    }

    private void showHeadsetMsg() {
        final Context context = getApplicationContext();
        final int duration = Toast.LENGTH_SHORT;

        final Toast toast = Toast.makeText(context, getString(R.string.headset_plug), duration);
        toast.setGravity(Gravity.CENTER, toast.getXOffset() / 2, toast.getYOffset() / 2);
        toast.show();
    }

}