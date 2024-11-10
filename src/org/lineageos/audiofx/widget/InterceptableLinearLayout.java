/*
 * SPDX-FileCopyrightText: 2013 The Linux Foundation
 * SPDX-FileCopyrightText: 2014-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.lineageos.audiofx.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class InterceptableLinearLayout extends LinearLayout {
    private boolean mIntercept = true;

    public InterceptableLinearLayout(Context context) {
        super(context);
    }

    public InterceptableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public InterceptableLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mIntercept;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setInterception(boolean intercept) {
        mIntercept = intercept;
    }
}
