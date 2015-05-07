package com.cyngn.audiofx.eq;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cyngn.audiofx.activity.MasterConfigControl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EqUtils {

    private static final String TAG = EqUtils.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static List<MasterConfigControl.Preset> getCustomPresets(Context ctx, int bands) {
        ArrayList<MasterConfigControl.Preset> presets = new ArrayList<MasterConfigControl.Preset>();
        final SharedPreferences presetPrefs = ctx.getSharedPreferences("custom_presets", 0);
        String[] presetNames = presetPrefs.getString("preset_names", "").split("\\|");

        for (int i = 0; i < presetNames.length; i++) {
            String storedPresetString = presetPrefs.getString(presetNames[i], null);
            if (storedPresetString == null) {
                continue;
            }
            MasterConfigControl.CustomPreset p = MasterConfigControl.CustomPreset.fromString(storedPresetString);
            presets.add(p);
        }

        return presets;
    }

    public static void saveCustomPresets(Context ctx, List<MasterConfigControl.Preset> presets) {
        final SharedPreferences.Editor presetPrefs = ctx.getSharedPreferences("custom_presets", 0).edit();
        presetPrefs.clear();

        StringBuffer presetNames = new StringBuffer();
        for (int i = 0; i < presets.size(); i++) {
            final MasterConfigControl.Preset preset = presets.get(i);
            if (preset instanceof MasterConfigControl.CustomPreset
                    && !(preset instanceof MasterConfigControl.PermCustomPreset)) {
                MasterConfigControl.CustomPreset p = (MasterConfigControl.CustomPreset) preset;
                presetNames.append(p.mName);
                presetNames.append("|");

                presetPrefs.putString(p.mName, p.toString());
            }
        }
        if (presetNames.length() > 0) {
            presetNames.deleteCharAt(presetNames.length() - 1);
        }

        presetPrefs.putString("preset_names", presetNames.toString());
        presetPrefs.commit();
    }

    public static int[] getBandLevelRange(Context context) {
        String savedCenterFreqs = context.getSharedPreferences("global", 0).getString("equalizer.band_level_range", null);
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

    public static int[] getCenterFreqs(Context context, int eqBands) {
        String savedCenterFreqs = context.getSharedPreferences("global", 0).getString("equalizer.center_freqs",
                getZeroedBandsString(eqBands));
        String[] split = savedCenterFreqs.split(";");
        int[] freqs = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            freqs[i] = Integer.valueOf(split[i]);
        }
        return freqs;
    }

    public static String getZeroedBandsString(int length) {
        StringBuffer buff = new StringBuffer();
        for (int i = 0; i < length; i++) {
            buff.append("0;");
        }
        buff.deleteCharAt(buff.length() - 1);
        return buff.toString();
    }

    public static String floatLevelsToString(float[] levels) {
        // save
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            builder.append(levels[i]);
            builder.append(";");
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public static short[] stringBandsToShorts(String input) {
        String[] levels = input.split(";");

        short[] equalizerLevels = new short[levels.length];
        for (int i = 0; i < levels.length; i++) {
            equalizerLevels[i] = (short) (Float.parseFloat(levels[i]));
        }
        return equalizerLevels;
    }

    public static float[] stringBandsToFloats(String input) {
        String[] levels = input.split(";");

        float[] equalizerLevels = new float[levels.length];
        for (int i = 0; i < levels.length; i++) {
            equalizerLevels[i] = (Float.parseFloat(levels[i]));
        }
        return equalizerLevels;
    }

    public static float[] convertDecibelsToMillibels(float[] decibels) {
        if (DEBUG) Log.i(TAG, "++ convertDecibelsToMillibels(" + Arrays.toString(decibels) + ")");

        float[] newvals = new float[decibels.length];
        for (int i = 0; i < decibels.length; i++) {
            newvals[i] = decibels[i] * 100;
        }


        if (DEBUG) Log.i(TAG, "-- convertDecibelsToMillibels(" + Arrays.toString(newvals) + ")");
        return newvals;
    }

    public static short[] convertDecibelsToMillibelsInShorts(float[] decibels) {
        if (DEBUG) Log.i(TAG, "++ convertDecibelsToMillibels(" + Arrays.toString(decibels) + ")");

        short[] newvals = new short[decibels.length];
        for (int i = 0; i < decibels.length; i++) {
            newvals[i] = (short) (decibels[i] * 100);
        }


        if (DEBUG) Log.i(TAG, "-- convertDecibelsToMillibels(" + Arrays.toString(newvals) + ")");
        return newvals;
    }

    public static float[] convertMillibelsToDecibels(float[] millibels) {
        if (DEBUG) Log.i(TAG, "++ convertMillibelsToDecibels(" + Arrays.toString(millibels) + ")");
        float[] newvals = new float[millibels.length];
        for (int i = 0; i < millibels.length; i++) {
            newvals[i] = millibels[i] / 100;
        }
        if (DEBUG) Log.i(TAG, "-- convertMillibelsToDecibels(" + Arrays.toString(newvals) + ")");
        return newvals;
    }
}
