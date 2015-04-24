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
package com.cyngn.audiofx.knobs;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;

public class KnobContainer extends LinearLayout {

    private static final String TAG = KnobContainer.class.getSimpleName();
    private static final int MSG_EXPAND = 0;
    private static final int MSG_CONTRACT = 1;
    private RadialKnob mTrebleKnob;
    private RadialKnob mBassKnob;
    private RadialKnob mVirtualizerKnob;
    private int mRegularHeight = -1;
    private int mExpandedHeight = -1;
    private H mHandler;
    private boolean mDown;
    private int mPreviousHiddenKnobWidth;


    public KnobContainer(Context context) {
        super(context);
        init();
    }

    public KnobContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KnobContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mRegularHeight = getResources().getDimensionPixelSize(R.dimen.knob_container_height_small);
        mExpandedHeight = getResources().getDimensionPixelSize(R.dimen.knob_container_height_expanded);
        mHandler = new H();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        OnTouchListener knobTouchListener = new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Message message;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mDown = true;
                        message = mHandler.obtainMessage(MSG_EXPAND, v.getTag());
                        mHandler.sendMessageDelayed(message, 0);
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mDown = false;
                        mHandler.removeMessages(MSG_EXPAND);
                        message = mHandler.obtainMessage(MSG_CONTRACT, v.getTag());
                        mHandler.sendMessageDelayed(message, 10);
                        return false;
                }

                return false;
            }
        };

        mTrebleKnob = (RadialKnob) findViewById(R.id.treble_knob);
        mBassKnob = (RadialKnob) findViewById(R.id.bass_knob);
        mVirtualizerKnob = (RadialKnob) findViewById(R.id.virtualizer_knob);

        View bassLabel = findViewById(R.id.bass_label);
        View trebleLabel = findViewById(R.id.treble_label);
        View virtLabel = findViewById(R.id.virtualizer_label);

        mTrebleKnob.setTag(new KnobInfo(mTrebleKnob, trebleLabel));
        mBassKnob.setTag(new KnobInfo(mBassKnob, bassLabel));
        mVirtualizerKnob.setTag(new KnobInfo(mVirtualizerKnob, virtLabel));

        mTrebleKnob.setOnTouchListener(knobTouchListener);
        mBassKnob.setOnTouchListener(knobTouchListener);
        mVirtualizerKnob.setOnTouchListener(knobTouchListener);

        mTrebleKnob.setOnKnobChangeListener(
                MasterConfigControl.getInstance(getContext()).getRadialKnobCallback(
                        MasterConfigControl.KNOB_TREBLE
                )
        );
        mBassKnob.setOnKnobChangeListener(
                MasterConfigControl.getInstance(getContext()).getRadialKnobCallback(
                        MasterConfigControl.KNOB_BASS
                )
        );
        mVirtualizerKnob.setOnKnobChangeListener(
                MasterConfigControl.getInstance(getContext()).getRadialKnobCallback(
                        MasterConfigControl.KNOB_VIRTUALIZER
                )
        );

        mTrebleKnob.setMax(100);
        mBassKnob.setMax(100);
        mVirtualizerKnob.setMax(100);
    }

    public void setKnobVisible(int knob, boolean visible) {
        final int newMode = visible ? View.VISIBLE : View.GONE;
        ViewGroup v = null;
        switch (knob) {
            case MasterConfigControl.KNOB_VIRTUALIZER:
                v = (ViewGroup) findViewById(R.id.virtualizer_knob_container);
                break;
            case MasterConfigControl.KNOB_BASS:
                v = (ViewGroup) findViewById(R.id.bass_knob_container);
                break;
            case MasterConfigControl.KNOB_TREBLE:
                v = (ViewGroup) findViewById(R.id.treble_knob_container);
                break;
        }
        if (v == null) {
            throw new UnsupportedOperationException("no knob container");
        }
        v.setVisibility(newMode);

        /* ensure spacing looks ok!
         *
         * it goes like, Space, knob layout, Space, knob layout, Space, etc.....
         * starting with the first knob (skipping the first space), ensure the pairs have the same
         * visibility so there's no extra space at the end.
         */
        for (int i = 1; i < getChildCount() - 1; i += 2) {
            View layout = getChildAt(i);
            View space = getChildAt(i + 1);
            if (space.getVisibility() != layout.getVisibility()) {
                space.setVisibility(layout.getVisibility());
            }
        }
    }

    public void updateKnobHighlights(int color) {
        if (mTrebleKnob != null) {
            mTrebleKnob.setHighlightColor(color);
        }
        if (mBassKnob != null) {
            mBassKnob.setHighlightColor(color);
        }
        if (mVirtualizerKnob != null) {
            mVirtualizerKnob.setHighlightColor(color);
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void resize(RadialKnob knob, View label, boolean makeBig) {
        if (knob.isEnabled()) {
            label.animate()
                    .alpha(makeBig ? 0 : 1)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(100);

            /*
            if (makeBig) {
                ResizeAnimation resizeAnimation = new ResizeAnimation(this);
                resizeAnimation.setHeightParams(getHeight(), mExpandedHeight);
                resizeAnimation.setDuration(100);
                startAnimation(resizeAnimation);
            } else {
                ResizeAnimation resizeAnimation = new ResizeAnimation(this);
                resizeAnimation.setHeightParams(getHeight(), mRegularHeight);
                resizeAnimation.setDuration(100);
                startAnimation(resizeAnimation);
            }
            */
            knob.resize(makeBig);
        }
        invalidate();
    }

    public static class KnobInfo {
        RadialKnob knob;
        View label;

        public KnobInfo(RadialKnob knob, View label) {
            this.knob = knob;
            this.label = label;
        }
    }

    private class H extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_EXPAND:
                case MSG_CONTRACT:
                    RadialKnob knob = ((KnobInfo) msg.obj).knob;
                    View label = ((KnobInfo) msg.obj).label;
                    boolean expand = msg.what == MSG_EXPAND;
                    resize(knob, label, expand);
                    break;
            }
        }
    }
}
