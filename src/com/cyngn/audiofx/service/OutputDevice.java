package com.cyngn.audiofx.service;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Created by roman on 4/13/15.
 */
public class OutputDevice implements Parcelable {
    public static final int DEVICE_HEADSET = 0;
    public static final int DEVICE_SPEAKER = 1;
    public static final int DEVICE_USB = 2;
    public static final int DEVICE_BLUETOOTH = 3;
    public static final int DEVICE_WIRELESS = 4;

    int mDeviceType;
    String mDeviceDisplayName;
    String mUniqueDeviceIdentifier;

    public OutputDevice(int deviceType) {
        this.mDeviceType = deviceType;
        this.mUniqueDeviceIdentifier = null;
    }

    public OutputDevice(int deviceType, String deviceId, String deviceName) {
        mDeviceType = deviceType;
        mUniqueDeviceIdentifier = deviceId;
        mDeviceDisplayName = deviceName;
    }

    public int getDeviceType() {
        return mDeviceType;
    }

    public String getDisplayName() {
        return mDeviceDisplayName;
    }

    public static String getDeviceTypeKey(int type) {
        switch (type) {
            case DEVICE_BLUETOOTH:
                return "bluetooth";
            case DEVICE_HEADSET:
                return "headset";
            case DEVICE_SPEAKER:
                return "speaker";
            case DEVICE_USB:
                return "usb";
            case DEVICE_WIRELESS:
                return "wireless";
            default:
                return null;
        }
    }

    public String getDevicePreferenceName(Context context) {
        if (mDeviceType == DEVICE_BLUETOOTH
                && mUniqueDeviceIdentifier != null) {
            String trimmedId = mUniqueDeviceIdentifier.replace(":", "");
            return getDeviceTypeKey(mDeviceType) + "_" + trimmedId;
        } else {
            return getDeviceTypeKey(mDeviceType);
        }
    }

    @Override
    public String toString() {
        return "deviceType=" + getDeviceTypeKey(mDeviceType) + ", displayName=" + mDeviceDisplayName + ", uniqueId=" + mUniqueDeviceIdentifier;
    }

    protected OutputDevice(Parcel in) {
        mDeviceType = in.readInt();
        if (in.readInt() == 1) {
            mDeviceDisplayName = in.readString();
        }
        if (in.readInt() == 1) {
            mUniqueDeviceIdentifier = in.readString();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDeviceType);
        if (mDeviceDisplayName != null) {
            dest.writeInt(1);
            dest.writeString(mDeviceDisplayName);
        } else {
            dest.writeInt(0);
        }
        if (mUniqueDeviceIdentifier != null) {
            dest.writeInt(1);
            dest.writeString(mUniqueDeviceIdentifier);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof OutputDevice)) {
            return false;
        }
        OutputDevice other = (OutputDevice) o;
        return getDeviceType() == other.getDeviceType()
                && Objects.equals(mUniqueDeviceIdentifier, other.mUniqueDeviceIdentifier);
    }

    public static final Parcelable.Creator<OutputDevice> CREATOR
            = new Parcelable.Creator<OutputDevice>() {
        @Override
        public OutputDevice createFromParcel(Parcel in) {
            return new OutputDevice(in);
        }

        @Override
        public OutputDevice[] newArray(int size) {
            return new OutputDevice[size];
        }
    };
}
