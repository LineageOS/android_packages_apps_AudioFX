package com.cyngn.audiofx.activity;

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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import com.cyanogen.ambient.analytics.Event;
import com.cyngn.audiofx.AudioFxApplication;
import com.cyngn.audiofx.Constants;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.fragment.AudioFxFragment;
import com.cyngn.audiofx.knobs.KnobCommander;
import com.cyngn.audiofx.service.AudioFxService;
import com.cyngn.audiofx.stats.AppState;
import com.cyngn.audiofx.stats.UserSession;

public class ActivityMusic extends Activity {

    private static final String TAG = ActivityMusic.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final String TAG_AUDIOFX = "audiofx";
    public static final String EXTRA_CALLING_PACKAGE = "audiofx::extra_calling_package";

    private CheckBox mCurrentDeviceToggle;
    MasterConfigControl mConfig;
    String mCallingPackage;

    private boolean mWaitingForService = true;
    private SharedPreferences.OnSharedPreferenceChangeListener mServiceReadyObserver;

    private CompoundButton.OnCheckedChangeListener mGlobalEnableToggleListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(final CompoundButton buttonView,
                                     final boolean isChecked) {
            if (UserSession.getInstance() != null) {
                UserSession.getInstance().deviceEnabledDisabled();
            }
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
        final int targetVersion = Constants.CURRENT_PREFS_INT_VERSION;
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
        ab.setTitle(R.string.app_title);
        ab.setDisplayShowTitleEnabled(true);

        final View extraView = LayoutInflater.from(this)
                .inflate(R.layout.action_bar_custom_components, null);
        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        ab.setCustomView(extraView, lp);
        ab.setDisplayShowCustomEnabled(true);

        mCurrentDeviceToggle = (CheckBox) ab.getCustomView().findViewById(R.id.global_toggle);
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
    protected void onResume() {
        if (DEBUG) Log.i(TAG, "onResume() called with " + "");
        super.onResume();

        // initiate a new session
        new UserSession(mCallingPackage);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (DEBUG) Log.d(TAG, "Session: " + UserSession.getInstance());

        final Event.Builder builder = new Event.Builder("session", "ended");
        UserSession.getInstance().append(builder);
        AppState.appendState(mConfig, KnobCommander.getInstance(this), builder);
        ((AudioFxApplication) getApplicationContext()).sendEvent(builder.build());
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
