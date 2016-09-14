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
package org.cyanogenmod.audiofx.stats;

import android.os.Parcel;
import android.os.Parcelable;
import com.cyanogen.ambient.analytics.Event;
import org.cyanogenmod.audiofx.Preset;
import org.cyanogenmod.audiofx.knobs.KnobCommander;

public class UserSession implements Parcelable {

    private static final String SOURCE_NONE = "none";

    private static UserSession sSession;
    public static final UserSession getInstance() {
        return sSession;
    }

    private String mSource;
    private int mDevicesChanged;
    private int mEnabledDisabledToggles;
    private int mPresetsSelected;
    private int mPresetsCreated;
    private int mPresetsRemoved;
    private int mPresetsRenamed;
    private int mMaxxVolumeToggled;
    private int mTrebleKnobAdjusted;
    private int mBassKnobAdjusted;
    private int mVirtualizerKnobAdjusted;

    public UserSession(String incomingPackageSource) {
        if (incomingPackageSource == null) {
            mSource = SOURCE_NONE;
        } else {
            mSource = incomingPackageSource;
        }
        sSession = this;
    }

    public void deviceChanged() {
        mDevicesChanged++;
    }

    public void deviceEnabledDisabled() {
        mEnabledDisabledToggles++;
    }

    public void presetSelected() {
        mPresetsSelected++;
    }

    public void presetRemoved() {
        mPresetsRemoved++;
    }

    public void presetRenamed() {
        mPresetsRenamed++;
    }

    public void presetCreated() {
        mPresetsCreated++;
    }

    public void maxxVolumeToggled() {
        mMaxxVolumeToggled++;
    }

    public void knobOptionsAdjusted(int knob) {
        switch (knob) {
            case KnobCommander.KNOB_BASS:
                mBassKnobAdjusted++;
                break;
            case KnobCommander.KNOB_TREBLE:
                mTrebleKnobAdjusted++;
                break;
            case KnobCommander.KNOB_VIRTUALIZER:
                mVirtualizerKnobAdjusted++;
                break;
        }
    }

    public void append(Event.Builder builder) {
        builder.addField("session_source", mSource);
        if (mDevicesChanged > 0)
            builder.addField("session_devices_changed_count", mDevicesChanged);
        if (mEnabledDisabledToggles > 0)
            builder.addField("session_devices_enabled_disabled_count", mEnabledDisabledToggles);
        if (mPresetsSelected > 0)
            builder.addField("session_presets_changed_count", mPresetsSelected);
        if (mPresetsCreated > 0)
            builder.addField("session_presets_created_count", mPresetsCreated);
        if (mPresetsRemoved > 0)
            builder.addField("session_presets_removed_count", mPresetsRemoved);
        if (mPresetsRenamed > 0)
            builder.addField("session_presets_renamed_count", mPresetsRenamed);
        if (mMaxxVolumeToggled > 0)
            builder.addField("session_maxx_volume_toggled", mMaxxVolumeToggled);
        if (mBassKnobAdjusted > 0)
            builder.addField("session_knobs_bass_adjusted_count", mBassKnobAdjusted);
        if (mVirtualizerKnobAdjusted > 0)
            builder.addField("session_knobs_virtualizer_adjusted_count", mVirtualizerKnobAdjusted);
        if (mTrebleKnobAdjusted > 0)
            builder.addField("session_knobs_treble_adjusted_count", mTrebleKnobAdjusted);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(getClass().getName() + "[");
        if (mSource != null) {
            s.append("mSource=").append(mSource).append(", ");
        }
        if (mDevicesChanged > 0) {
            s.append("mDevicesChanged=").append(mDevicesChanged).append(", ");
        }
        if (mEnabledDisabledToggles > 0) {
            s.append("mEnabledDisabledToggles=").append(mEnabledDisabledToggles).append(", ");
        }
        if (mPresetsSelected > 0) {
            s.append("mPresetsSelected=").append(mPresetsSelected).append(", ");
        }
        if (mPresetsCreated > 0) {
            s.append("mPresetsCreated=").append(mPresetsCreated).append(", ");
        }
        if (mPresetsRemoved > 0) {
            s.append("mPresetsRemoved=").append(mPresetsRemoved).append(", ");
        }
        if (mPresetsRenamed > 0) {
            s.append("mPresetsRenamed=").append(mPresetsRenamed).append(", ");
        }
        if (mMaxxVolumeToggled > 0) {
            s.append("mMaxxVolumeToggled=").append(mMaxxVolumeToggled).append(", ");
        }
        if (mBassKnobAdjusted > 0) {
            s.append("mBassKnobAdjusted=").append(mBassKnobAdjusted).append(", ");
        }
        if (mVirtualizerKnobAdjusted > 0) {
            s.append("mVirtualizerKnobAdjusted=").append(mVirtualizerKnobAdjusted).append(", ");
        }
        if (mTrebleKnobAdjusted > 0) {
            s.append("mTrebleKnobAdjusted=").append(mTrebleKnobAdjusted).append(", ");
        }
        if (s.charAt(s.length() - 2) == ',') {
            s.delete(s.length() - 2, s.length());
        }
        s.append("]");

        return s.toString();
    }

    public static final Creator<UserSession> CREATOR = new Creator<UserSession>() {
        @Override
        public UserSession createFromParcel(Parcel in) {
            return new UserSession(in);
        }

        @Override
        public UserSession[] newArray(int size) {
            return new UserSession[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    protected UserSession(Parcel in) {
        mSource = in.readString();
        mDevicesChanged = in.readInt();
        mEnabledDisabledToggles = in.readInt();
        mPresetsSelected = in.readInt();
        mPresetsCreated = in.readInt();
        mPresetsRemoved = in.readInt();
        mPresetsRenamed = in.readInt();
        mBassKnobAdjusted = in.readInt();
        mVirtualizerKnobAdjusted = in.readInt();
        mTrebleKnobAdjusted = in.readInt();
        mMaxxVolumeToggled = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSource);
        dest.writeInt(mDevicesChanged);
        dest.writeInt(mEnabledDisabledToggles);
        dest.writeInt(mPresetsSelected);
        dest.writeInt(mPresetsCreated);
        dest.writeInt(mPresetsRemoved);
        dest.writeInt(mPresetsRenamed);
        dest.writeInt(mBassKnobAdjusted);
        dest.writeInt(mVirtualizerKnobAdjusted);
        dest.writeInt(mTrebleKnobAdjusted);
        dest.writeInt(mMaxxVolumeToggled);
    }

    private static class State {
        private String mOutputDevice;
        private Preset mPreset;
        private String mKnobsOpts;
    }
}
