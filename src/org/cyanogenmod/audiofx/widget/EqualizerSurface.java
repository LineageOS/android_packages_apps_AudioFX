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

package org.cyanogenmod.audiofx.widget;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import org.cyanogenmod.audiofx.R;

import java.util.Arrays;

public class EqualizerSurface extends SurfaceView implements ValueAnimator.AnimatorUpdateListener {

    private static int SAMPLING_RATE = 44100;

    private int mWidth;
    private int mHeight;

    private float mMinFreq = 10;
    private float mMaxFreq = 21000;
    
    private float mMinDB = -15;
    private float mMaxDB = 15;
    
    private int mNumBands = 6;
        
    private float[] mLevels = new float[mNumBands];
    private float[] mTargetLevels = new float[mNumBands];
    private float[] mCenterFreqs = new float[mNumBands];
    private final Paint mWhite, mControlBarText, mControlBar;
    private final Paint mFrequencyResponseBg;
    private final Paint mFrequencyResponseHighlight, mFrequencyResponseHighlight2;

    private BandUpdatedListener mBandUpdatedListener;
    int mBarWidth;
    int mTextSize;
    private ValueAnimator mAnimation;

    public EqualizerSurface(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setWillNotDraw(false);

        mWhite = new Paint();
        mWhite.setColor(getResources().getColor(R.color.white));
        mWhite.setStyle(Style.STROKE);
        mWhite.setTextSize(mTextSize = context.getResources().getDimensionPixelSize(R.dimen.eq_label_text_size));
        mWhite.setTypeface(Typeface.DEFAULT_BOLD);
        mWhite.setAntiAlias(true);

        mControlBarText = new Paint(mWhite);
        mControlBarText.setTextAlign(Paint.Align.CENTER);
        mControlBarText.setShadowLayer(2, 0, 0, getResources().getColor(R.color.cb));

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

    /**
     * Listener for bands being modified via touch events
     *
     * Invoked with the index of the modified band, and the
     * new value in dB. If the widget is read-only, will set
     * changed = false.
     *
     */
    public interface BandUpdatedListener {
        public void onBandUpdated(int band, float dB);
        public void onBandAnimating(int band, float dB);
        public void onBandAnimationCompleted();
    }
    
    public void setBandLevelRange(float minDB, float maxDB) {
        mMinDB = minDB;
        mMaxDB = maxDB;
    }
    
    public void setCenterFreqs(float[] centerFreqsKHz) {
        mNumBands = centerFreqsKHz.length;
        mLevels = new float[mNumBands];
        mCenterFreqs = Arrays.copyOf(centerFreqsKHz, mNumBands);
        System.arraycopy(centerFreqsKHz, 0, mCenterFreqs, 0, mNumBands);
        mMinFreq = mCenterFreqs[0] / 2;
        mMaxFreq = (float) Math.pow(mCenterFreqs[mNumBands - 1], 2) / mCenterFreqs[mNumBands -2] / 2; 
    }

    public float[] softCopyLevels() {
        float[] levels = new float[mNumBands];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = mLevels[i];
        }
        return levels;
    }
    /*
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle b = new Bundle();
        b.putParcelable("super", super.onSaveInstanceState());
        b.putFloatArray("levels", mLevels);
        return b;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable p) {
        Bundle b = (Bundle) p;
        super.onRestoreInstanceState(b.getBundle("super"));
        mLevels = b.getFloatArray("levels");
    }
    */
    
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        buildLayer();
    }

    /**
     * Returns a color that is assumed to be blended against black background,
     * assuming close to sRGB behavior of screen (gamma 2.2 approximation).
     *
     * @param intensity desired physical intensity of color component
     * @param alpha alpha value of color component
     */
    private static int gamma(float intensity, float alpha) {
        /* intensity = (component * alpha)^2.2
         * <=>
         * intensity^(1/2.2) / alpha = component
         */

        double gamma = Math.round(255 * Math.pow(intensity, 1 / 2.2) / alpha);
        return (int) Math.min(255, Math.max(0, gamma));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        final Resources res = getResources();
        mWidth = right - left;
//        mHeight = bottom - top + (int) mWhite.getTextSize();
        mHeight = bottom - top;

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

    int mPasses = 140;
    float[] mStartLevels;
    float[] mDeltas;
    public void setBands(float[] bands) {
        if (mAnimation != null) {
            mAnimation.cancel();
            mAnimation = null;
        }
        mTargetLevels = bands;

        mStartLevels = new float[mLevels.length];
        mDeltas = new float[mLevels.length];
        for (int i = 0; i < mStartLevels.length; i++) {
            mStartLevels[i] = mLevels[i];

            mDeltas[i] = mTargetLevels[i] - mStartLevels[i];
        }

        mAnimation = ValueAnimator.ofFloat(0f,1f);
        mAnimation.addUpdateListener(this);
        mAnimation.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mPasses = 35;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mPasses = 140;
                mLevels = mTargetLevels;
                animation.removeAllListeners();
                mAnimation = null;
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        mAnimation.setDuration(1000);
//        mAnimation.setStartDelay(100);
        mAnimation.setInterpolator(new DecelerateInterpolator());
        mAnimation.start();

    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        final float fraction = (Float) animation.getAnimatedFraction();
//        final float fraction = ((Float) (animation.getAnimatedValue())).floatValue();

        for (int i = 0; i < mNumBands; i++) {
//            float delta = mTargetLevels[i] - mLevels[i];
            float newValue = mDeltas[i] * fraction;
            mLevels[i] = mStartLevels[i] + newValue;
        }
        invalidate();
    }

    public void setBand(int i, float value) {
        mLevels[i] = value;
        postInvalidate();
    }

    public float getBand(int i) {
        return mLevels[i];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        /* clear canvas */
        canvas.drawRGB(0, 0, 0);

        Biquad[] biquads = new Biquad[mNumBands - 1];
        for (int i = 0; i < (mNumBands - 1); i++) {
            biquads[i] = new Biquad();
        }

        /* The filtering is realized with 2nd order high shelf filters, and each band
         * is realized as a transition relative to the previous band. The center point for
         * each filter is actually between the bands.
         *
         * 1st band has no previous band, so it's just a fixed gain.
         */
        double gain = Math.pow(10, mLevels[0] / 20);
        for (int i = 0; i < biquads.length; i++) {
            biquads[i].setHighShelf(mCenterFreqs[i], SAMPLING_RATE, mLevels[i + 1] - mLevels[i], 1);
        }

        Path freqResponse = new Path();
        Complex[] zn = new Complex[biquads.length];
//        final int passes = 140;
        for (int i = 0; i < mPasses+1; i ++) {
            double freq = reverseProjectX(i / (float)mPasses);
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
            double dB = lin2dB(lin);
            float x = projectX(freq) * mWidth;
            float y = projectY(dB) * (mHeight);

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

        /* draw horizontal lines */
        for (float dB = mMinDB + 3; dB <= mMaxDB - 3; dB += 3) {
            float y = projectY(dB) * mHeight;
//            canvas.drawLine(0, y, mWidth - 1, y, mGridLines);
            canvas.drawText(String.format("%+d", (int)dB), 1, (y - 1), mWhite);
        }

        for (int i = 0; i < mNumBands; i ++) {
            float freq = mCenterFreqs[i];
            float x = projectX(freq) * mWidth;
            float y = projectY(mLevels[i]) * (mHeight);
            String frequencyText = String.format(freq < 1000 ? "%.0f" : "%.0fk",
                    freq < 1000 ? freq : freq / 1000);

            int targetHeight = (mHeight);

            int halfX = mBarWidth/2;
            if (y > targetHeight) {
                int diff = (int) Math.abs(targetHeight - y);
                canvas.drawRect(x-halfX, y+diff, x+halfX, targetHeight, mControlBar);
            } else {
                canvas.drawRect(x-halfX, y, x+halfX, targetHeight, mControlBar);
            }

            canvas.drawText(frequencyText, x, mWhite.getTextSize(), mControlBarText);
            canvas.drawText(String.format("%+1.1f", mLevels[i]), x, y-1, mControlBarText);
        }
    }

    public void registerBandUpdatedListener(BandUpdatedListener listener) {
        mBandUpdatedListener = listener;
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isEnabled()) {
           return false;
        }

        float x = event.getX();
        float y = event.getY();

        /* Which band is closest to the position user pressed? */
        int band = findClosest(x);

        int wy = getHeight();
        float level = (y / wy) * (mMinDB - mMaxDB) - mMinDB;
        if (level < mMinDB) {
            level = mMinDB;
        } else if (level > mMaxDB) {
            level = mMaxDB;
        }
        
        setBand(band, level);
        
        if (mBandUpdatedListener != null) {
            mBandUpdatedListener.onBandUpdated(band, level);
        }
        
        return true;
    }

    private float projectX(double freq) {
        double pos = Math.log(freq);
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return (float) ((pos - minPos) / (maxPos - minPos));
    }

    private double reverseProjectX(float pos) {
        double minPos = Math.log(mMinFreq);
        double maxPos = Math.log(mMaxFreq);
        return Math.exp(pos * (maxPos - minPos) + minPos);
    }

    private float projectY(double dB) {
        double pos = (dB - mMinDB) / (mMaxDB - mMinDB);
        return (float) (1 - pos);
    }

    private double lin2dB(double rho) {
        return rho != 0 ? Math.log(rho) / Math.log(10) * 20 : -99.9;
    }

    /**
     * Find the closest control to given horizontal pixel for adjustment
     *
     * @param px
     * @return index of best match
     */
    public int findClosest(float px) {
        int idx = 0;
        float best = 1e9f;
        for (int i = 0; i < mNumBands; i ++) {
            float freq = mCenterFreqs[i];
            float cx = projectX(freq) * mWidth;
            float distance = Math.abs(cx - px);

            if (distance < best) {
                idx = i;
                best = distance;
            }
        }

        return idx;
    }
}
