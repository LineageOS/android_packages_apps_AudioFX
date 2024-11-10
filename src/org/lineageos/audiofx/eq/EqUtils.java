/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.eq;

import android.util.Log;

import java.util.Arrays;

public class EqUtils {

    private static final String TAG = EqUtils.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String DEFAULT_DELIMITER = ";";

    public static String getZeroedBandsString(int length) {
        return getZeroedBandsString(length, DEFAULT_DELIMITER);
    }

    public static float[] stringBandsToFloats(String input) {
        return stringBandsToFloats(input, DEFAULT_DELIMITER);
    }

    public static String floatLevelsToString(float[] levels) {
        return floatLevelsToString(levels, DEFAULT_DELIMITER);
    }

    public static String getZeroedBandsString(int length, final String delimiter) {
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < length; i++) {
            buff.append("0").append(delimiter);
        }
        buff.deleteCharAt(buff.length() - 1);
        return buff.toString();
    }

    public static String floatLevelsToString(float[] levels, final String delimiter) {
        // save
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            builder.append(levels[i]);
            builder.append(delimiter);
        }
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    public static float[] stringBandsToFloats(String input, final String delimiter) {
        String[] levels = input.split(delimiter);

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
