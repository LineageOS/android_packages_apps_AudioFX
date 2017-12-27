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
package org.cyanogenmod.audiofx.activity;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.CompoundButton;
import android.widget.Switch;
import org.cyanogenmod.audiofx.AudioFxApplication;
import org.cyanogenmod.audiofx.Constants;
import org.cyanogenmod.audiofx.R;
import org.cyanogenmod.audiofx.fragment.AudioFxFragment;
import org.cyanogenmod.audiofx.knobs.KnobCommander;
import org.cyanogenmod.audiofx.service.AudioFxService;
import org.cyanogenmod.audiofx.service.DevicePreferenceManager;

public class ActivityMusic extends Activity {

    private static final String TAG = ActivityMusic.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String TAG_AUDIOFX = "audiofx";
    public static final String EXTRA_CALLING_PACKAGE = "audiofx::extra_calling_package";

    private Switch mCurrentDeviceToggle;
    MasterConfigControl mConfig;
    String mCallingPackage;

    private boolean mWaitingForService = true;
    private SharedPreferences.OnSharedPreferenceChangeListener mServiceReadyObserver;

    private CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                                     final boolean isChecked) {
            mConfig.setCurrentDeviceEnabled(isChecked);
        }
    };

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (DEBUG)
            Log.i(TAG, "onCreate() called with "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCallingPackage = getIntent().getStringExtra(EXTRA_CALLING_PACKAGE);
        Log.i(TAG, "calling package: " + mCallingPackage);

        mConfig = MasterConfigControl.getInstance(this);

        final SharedPreferences globalPrefs = Constants.getGlobalPrefs(this);

        mWaitingForService = !defaultsSetup();
        if (mWaitingForService) {
            Log.w(TAG, "waiting for service.");
            mServiceReadyObserver = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String key) {
                    if (key.equals(Constants.SAVED_DEFAULTS) && defaultsSetup()) {
                        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
                        mConfig.onResetDefaults();
                        init(savedInstanceState);

                        mWaitingForService = false;
                        invalidateOptionsMenu();
                        mServiceReadyObserver = null;
                    }
                }
            };
            globalPrefs.registerOnSharedPreferenceChangeListener(mServiceReadyObserver);
            startService(new Intent(ActivityMusic.this, AudioFxService.class));
            // TODO add loading fragment if service initialization takes too long
        } else {
            init(savedInstanceState);
        }
    }

    private boolean defaultsSetup() {
        final int targetVersion = DevicePreferenceManager.CURRENT_PREFS_INT_VERSION;
        final SharedPreferences prefs = Constants.getGlobalPrefs(this);
        final int currentVersion = prefs.getInt(Constants.AUDIOFX_GLOBAL_PREFS_VERSION_INT, 0);
        final boolean defaultsSaved = prefs.getBoolean(Constants.SAVED_DEFAULTS, false);
        return defaultsSaved && currentVersion >= targetVersion;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // should null it out if one was there, compat redirector with package will go through onCreate
        mCallingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE);
    }

    @Override
    protected void onDestroy() {
        if (mServiceReadyObserver != null) {
            Constants.getGlobalPrefs(this)
                    .unregisterOnSharedPreferenceChangeListener(mServiceReadyObserver);
            mServiceReadyObserver = null;
        }
        super.onDestroy();
    }

    private void init(Bundle savedInstanceState) {
        mConfig = MasterConfigControl.getInstance(this);

        ActionBar ab = getActionBar();
        ab.setTitle(R.string.app_name_cm);
        ab.setDisplayShowTitleEnabled(true);

        final View extraView = LayoutInflater.from(this)
                .inflate(R.layout.action_bar_custom_components, null);
        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        ab.setCustomView(extraView, lp);
        ab.setDisplayShowCustomEnabled(true);

        mCurrentDeviceToggle = (Switch) ab.getCustomView().findViewById(R.id.global_toggle);
        mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);

        if (savedInstanceState == null && findViewById(R.id.main_fragment) != null) {
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.main_fragment, new AudioFxFragment(), TAG_AUDIOFX)
                    .commit();
        }
        applyOemDecor();
    }

    private void applyOemDecor() {
        ActionBar ab = getActionBar();
        if (mConfig.hasMaxxAudio()) {
            ab.setSubtitle(R.string.powered_by_maxx_audio);
        } else if (mConfig.hasDts()) {
            final ViewStub stub = (ViewStub) ab.getCustomView().findViewById(R.id.logo_stub);
            stub.setLayoutResource(R.layout.action_bar_dts_logo);
            stub.inflate();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DEBUG) Log.i(TAG, "onConfigurationChanged() called with "
                + "newConfig = [" + newConfig + "]");
        if (newConfig.orientation != getResources().getConfiguration().orientation) {
            mCurrentDeviceToggle = null;
        }
    }

    public void setGlobalToggleChecked(boolean checked) {
        if (mCurrentDeviceToggle != null) {
            mCurrentDeviceToggle.setOnCheckedChangeListener(null);
            mCurrentDeviceToggle.setChecked(checked);
            mCurrentDeviceToggle.setOnCheckedChangeListener(mGlobalEnableToggleListener);
        }
    }

    public CompoundButton getGlobalSwitch() {
        return mCurrentDeviceToggle;
    }
}
