/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package com.cyngn.audiofx.eq;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;

public class BandDeltaBlockView extends FrameLayout implements MasterConfigControl.EqUpdatedCallback {

    MasterConfigControl mConfig;
    TextView mDeltaText;
    int mBand;

    public BandDeltaBlockView(Context context) {
        super(context);
        init();
    }

    public BandDeltaBlockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BandDeltaBlockView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

    public void setBand(int band) {
        mBand = band;
    }

    private void init() {
        mConfig = MasterConfigControl.getInstance(mContext);

        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDeltaText = (TextView) findViewById(R.id.text);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mConfig.addEqStateChangeCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mConfig.removeEqStateChangeCallback(this);
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (mBand == band) {
            if (mDeltaText != null) {
                mDeltaText.setText(String.format("%+1.1f", dB));
            }
        }
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {

    }

    @Override
    public void onPresetsChanged() {

    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {

    }
}
