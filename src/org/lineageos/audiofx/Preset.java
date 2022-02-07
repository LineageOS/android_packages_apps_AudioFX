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
package org.lineageos.audiofx;

import android.os.Parcel;
import android.os.Parcelable;

import org.lineageos.audiofx.eq.EqUtils;

public class Preset implements Parcelable {

    protected String mName;
    protected final float[] mLevels;

    private Preset(String name, float[] levels) {
        this.mName = name;
        mLevels = new float[levels.length];
        for (int i = 0; i < levels.length; i++) {
            mLevels[i] = levels[i];
        }
    }

    public float[] getLevels() {
        return mLevels;
    }

    public float getBandLevel(int band) {
        return mLevels[band];
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

    @Override
    public boolean equals(Object o) {
        if (o instanceof Preset) {
            Preset other = (Preset) o;

            if (this.mLevels.length != ((Preset) o).mLevels.length) {
                return false;
            }

            for (int i = 0; i < mLevels.length; i++) {
                if (mLevels[i] != other.mLevels[i]) {
                    return false;
                }
            }

            return other.mName.equals(mName);
        }
        return super.equals(o);
    }

    private Preset(Parcel in) {
        if (in.readInt() == 1) {
            mName = in.readString();
        }
        mLevels = new float[in.readInt()];
        in.readFloatArray(mLevels);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mName != null ? 1 : 0);
        if (mName != null) {
            dest.writeString(mName);
        }
        dest.writeInt(mLevels.length);
        dest.writeFloatArray(mLevels);
    }

    public static final Parcelable.Creator<Preset> CREATOR = new Parcelable.Creator<Preset>() {
        @Override
        public Preset createFromParcel(Parcel in) {
            return new Preset(in);
        }

        @Override
        public Preset[] newArray(int size) {
            return new Preset[size];
        }
    };

    public String getName() {
        return mName;
    }

    public static class StaticPreset extends Preset {
        public StaticPreset(String name, float[] levels) {
            super(name, levels);
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
        public boolean equals(Object o) {
            if (o instanceof CustomPreset) {
                return super.equals(o) && mLocked == ((CustomPreset) o).mLocked;
            }
            return false;
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

        public static final Parcelable.Creator<CustomPreset> CREATOR
                = new Parcelable.Creator<CustomPreset>() {
            @Override
            public CustomPreset createFromParcel(Parcel in) {
                return new CustomPreset(in);
            }

            @Override
            public CustomPreset[] newArray(int size) {
                return new CustomPreset[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mLocked ? 1 : 0);
        }

        protected CustomPreset(Parcel in) {
            super(in);
            mLocked = in.readInt() == 1;
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

        protected PermCustomPreset(Parcel in) {
            super(in);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        public static final Creator<PermCustomPreset> CREATOR = new Creator<PermCustomPreset>() {
            @Override
            public PermCustomPreset createFromParcel(Parcel in) {
                return new PermCustomPreset(in);
            }

            @Override
            public PermCustomPreset[] newArray(int size) {
                return new PermCustomPreset[size];
            }
        };
    }
}
