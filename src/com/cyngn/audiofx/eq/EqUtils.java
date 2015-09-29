package com.cyngn.audiofx.eq;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cyngn.audiofx.Preset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EqUtils {

    private static final String TAG = EqUtils.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

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
