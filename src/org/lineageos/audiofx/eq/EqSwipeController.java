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
package org.lineageos.audiofx.eq;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.preset.InfiniteViewPager;

public class EqSwipeController extends LinearLayout {

    /*
     * x velocity max for deciding whether to try to grab a bar
     */
    private static final int X_VELOCITY_THRESH = 20;

    private static final int MINIMUM_TIME_HOLD_TIME = 100;

    EqContainerView mEq;
    InfiniteViewPager mPager;
    private VelocityTracker mVelocityTracker = null;
    long mDownTime;
    EqBarView mBar;
    boolean mBarActive;
    private ViewGroup mControls;

    private final EqualizerManager mEqManager;
    private float mDownPositionX;
    private float mDownPositionY;

    public EqSwipeController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEqManager = MasterConfigControl.getInstance(context).getEqualizerManager();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEq = findViewById(R.id.eq_container);
        mPager = (InfiniteViewPager) findViewById(R.id.pager);
        mControls = findViewById(R.id.eq_controls);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // don't intercept touches over the EQ controls
        return !(mControls.getRight() > x) || !(mControls.getTop() < y)
                || !(mControls.getBottom() > y) || !(mControls.getLeft() < x);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = event.getActionIndex();
        int action = event.getActionMasked();
        int pointerId = event.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownPositionX = event.getRawX();
                mDownPositionY = event.getRawY();
                mDownTime = System.currentTimeMillis();
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float xVelocity = mVelocityTracker.getXVelocity(pointerId);
                    float yVelocity = mVelocityTracker.getYVelocity(pointerId);

                    final float deltaX = mDownPositionX - event.getRawX();
                    final float deltaY = mDownPositionY - event.getRawY();
                    final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                    final ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
                    final int touchSlop = viewConfiguration.getScaledTouchSlop();

                    if (!mBarActive && !mEqManager.isChangingPresets()
                            && !mEqManager.isEqualizerLocked()
                            && Math.abs(xVelocity) < X_VELOCITY_THRESH
                            && System.currentTimeMillis() - mDownTime > MINIMUM_TIME_HOLD_TIME) {
                        if (distanceSquared < touchSlop * touchSlop) {
                            mBarActive = true;
                            mBar = mEq.startTouchingBarUnder(event);
                        }
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }

                if (mBarActive) {
                    // reset state?
                    if (mBar != null) {
                        mEq.stopBarInteraction(mBar);
                        mBar.endInteraction();
                    }
                }
                mBar = null;
                mBarActive = false;
                break;
        }
        if (mBarActive && mBar != null) {
            return mBar.onTouchEvent(event);
        } else {
            return mPager.onTouchEvent(event);
        }
    }
}
