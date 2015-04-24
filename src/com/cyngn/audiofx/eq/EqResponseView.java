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
 * 
 * - Original code by Antti S. Lankila for DSPManager
 * - Modified extensively by cyanogen for multi-band support
 */

package com.cyngn.audiofx.eq;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;
import com.cyngn.audiofx.widget.Biquad;
import com.cyngn.audiofx.widget.Complex;

public class EqResponseView extends SurfaceView implements MasterConfigControl.EqUpdatedCallback {

    private static int SAMPLING_RATE = 44100;

    private int mWidth;
    private int mHeight;

    private final Paint mWhite, mControlBarText, mControlBar;
    private final Paint mFrequencyResponseBg;
    private final Paint mFrequencyResponseHighlight, mFrequencyResponseHighlight2;

    int mBarWidth;
    int mTextSize;
    private ValueAnimator mAnimation;
    private int mPasses = 140;

    MasterConfigControl mConfig;

    public EqResponseView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mConfig = MasterConfigControl.getInstance(context);
        setWillNotDraw(false);

        mWhite = new Paint();
        mWhite.setColor(getResources().getColor(R.color.color_grey));
        mWhite.setStyle(Style.STROKE);
        mWhite.setTextSize(mTextSize = context.getResources().getDimensionPixelSize(R.dimen.eq_label_text_size));
        mWhite.setTypeface(Typeface.DEFAULT_BOLD);
        mWhite.setAntiAlias(true);

        mControlBarText = new Paint();
        mControlBarText.setColor(getResources().getColor(R.color.black));
        mControlBarText.setAntiAlias(true);
        mControlBarText.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.eq_label_text_size));
        mControlBarText.setTypeface(Typeface.DEFAULT_BOLD);
//        mControlBarText.setShadowLayer(2, 0, 0, getResources().getColor(R.color.black));

        mControlBar = new Paint();
        mControlBar.setStyle(Style.FILL);
        mControlBar.setColor(getResources().getColor(R.color.cb));
        mControlBar.setAntiAlias(true);
        mControlBar.setStrokeCap(Cap.SQUARE);
        mControlBar.setShadowLayer(2, 0, 0, getResources().getColor(R.color.black));
        mBarWidth = context.getResources().getDimensionPixelSize(R.dimen.eq_bar_width);
//        mControlBar.setStrokeWidth(mBarWidth);

        mFrequencyResponseBg = new Paint();
        mFrequencyResponseBg.setStyle(Style.FILL);
        mFrequencyResponseBg.setAntiAlias(true);

        mFrequencyResponseHighlight = new Paint();
        mFrequencyResponseHighlight.setStyle(Style.STROKE);
        mFrequencyResponseHighlight.setStrokeWidth(6);
        mFrequencyResponseHighlight.setColor(getResources().getColor(R.color.freq_hl));
        mFrequencyResponseHighlight.setAntiAlias(true);

        mFrequencyResponseHighlight2 = new Paint();
        mFrequencyResponseHighlight2.setStyle(Style.STROKE);
        mFrequencyResponseHighlight2.setStrokeWidth(3);
        mFrequencyResponseHighlight2.setColor(getResources().getColor(R.color.freq_hl2));
        mFrequencyResponseHighlight2.setAntiAlias(true);
    }
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        buildLayer();

        mConfig.addEqStateChangeCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mConfig.removeEqStateChangeCallback(this);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = right - left;
        mHeight = bottom - top;

        Resources res = getResources();
        /**
         * red > +7
         * yellow > +3
         * holo_blue_bright > 0
         * holo_blue < 0
         * holo_blue_dark < 3
         */
        int[] responseColors = new int[] {
                res.getColor(R.color.eq_yellow),
                res.getColor(R.color.eq_green),
                res.getColor(R.color.eq_holo_bright),
                res.getColor(R.color.eq_holo_blue),
                res.getColor(R.color.eq_holo_dark)
        };
        float[] responsePositions = new float[] {
                0, 0.2f, 0.45f, 0.6f, 1f
        };

        mFrequencyResponseBg.setShader(new LinearGradient(0, 0, 0, mHeight - mTextSize,
                responseColors, responsePositions, Shader.TileMode.CLAMP));

        int[] barColors = new int[] {
                res.getColor(R.color.cb_shader),
                res.getColor(R.color.cb_shader_alpha)
        };
        float[] barPositions = new float[] {
                0.95f, 1f
        };

//        mControlBar.setShader(new LinearGradient(0, 0, 0, mHeight - mTextSize,
//                barColors, barPositions, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        /* clear canvas */
//        canvas.drawRGB(0, 0, 0);

        Biquad[] biquads = new Biquad[mConfig.getNumBands() - 1];
        for (int i = 0; i < (mConfig.getNumBands() - 1); i++) {
            biquads[i] = new Biquad();
        }

        /* The filtering is realized with 2nd order high shelf filters, and each band
         * is realized as a transition relative to the previous band. The center point for
         * each filter is actually between the bands.
         *
         * 1st band has no previous band, so it's just a fixed gain.
         */
        double gain = Math.pow(10, mConfig.getLevel(0) / 20);
        for (int i = 0; i < biquads.length; i++) {
            biquads[i].setHighShelf(mConfig.getCenterFreq(i), SAMPLING_RATE, mConfig.getLevel(i+1) - mConfig.getLevel(i), 1);
        }

        Path freqResponse = new Path();
        Complex[] zn = new Complex[biquads.length];
//        final int passes = 140;
        for (int i = 0; i < mPasses+1; i ++) {
            double freq = mConfig.reverseProjectX(i / (float) mPasses);
            double omega = freq / SAMPLING_RATE * Math.PI * 2;
            Complex z = new Complex(Math.cos(omega), Math.sin(omega));

            /* Evaluate the response at frequency z */
            /* Complex z1 = z.mul(gain); */
            double lin = gain;
            for (int j = 0; j < biquads.length; j++) {
                zn[j] = biquads[j].evaluateTransfer(z);
                lin *= zn[j].rho();
            }

            /* Magnitude response, dB */
            double dB = mConfig.lin2dB(lin);
            float x = mConfig.projectX(freq) * mWidth;
            float y = mConfig.projectY(dB) * (mHeight);

            /* Set starting point at first point */
            if (i == 0) {
                freqResponse.moveTo(x, y);
            } else {
                freqResponse.lineTo(x, y);
            }
        }

        Path freqResponseBg = new Path();
        freqResponseBg.addPath(freqResponse);
        freqResponseBg.offset(0, -4);
        freqResponseBg.lineTo(mWidth, mHeight);
        freqResponseBg.lineTo(0, mHeight);
        freqResponseBg.close();
        canvas.drawPath(freqResponseBg, mFrequencyResponseBg);

//        canvas.drawPath(freqResponse, mFrequencyResponseHighlight);
//        canvas.drawPath(freqResponse, mFrequencyResponseHighlight2);

        /* draw vertical lines */
//        for (float freq = mMinFreq; freq < mMaxFreq;) {
//            float x = projectX(freq) * mWidth;
//            canvas.drawLine(x, 0, x, mHeight - 1, mGridLines);
//            if (freq < 100) {
//                freq += 10;
//            } else if (freq < 1000) {
//                freq += 100;
//            } else if (freq < 10000) {
//                freq += 1000;
//            } else {
//                freq += 10000;
//            }
//        }

//        /* draw horizontal lines */
        for (float dB = mConfig.getMinDB()+ 3; dB <=mConfig.getMaxDB()- 3; dB += 3) {
            float y = mConfig.projectY(dB) * mHeight;
//            canvas.drawLine(0, y, mWidth - 1, y, mGridLines);
            canvas.drawText(String.format("%+d", (int)dB), 1, (y - 1), mControlBarText);
        }

        for (int i = 0; i < mConfig.getNumBands(); i ++) {
            float freq = mConfig.getCenterFreq(i);
            float x = mConfig.projectX(freq) * mWidth;
            float y = mConfig.projectY(mConfig.getLevel(i)) * (mHeight);

            String frequencyText = String.format(freq < 1000 ? "%.0f" : "%.0fk",
                    freq < 1000 ? freq : freq / 1000);

            int targetHeight = (mHeight);
//
//            int halfX = mBarWidth/2;
//            if (y > targetHeight) {
//                int diff = (int) Math.abs(targetHeight - y);
//                canvas.drawRect(x-halfX, y+diff, x+halfX, targetHeight, mControlBar);
//            } else {
//                canvas.drawRect(x-halfX, y, x+halfX, targetHeight, mControlBar);
//            }

//            canvas.drawText(frequencyText, x, mWhite.getTextSize(), mControlBarText);
//            canvas.drawText(String.format("%+1.1f", mLevels[i]), x, y-1, mControlBarText);
        }
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        invalidate();
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

    public void setPasses(int passes) {
        mPasses = passes;
    }
}
