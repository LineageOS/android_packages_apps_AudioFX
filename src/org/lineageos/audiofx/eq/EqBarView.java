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
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;
import org.lineageos.audiofx.activity.StateCallbacks;

public class EqBarView extends FrameLayout implements StateCallbacks.EqUpdatedCallback {

    private static final String TAG = EqBarView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private EqualizerManager mEqManager;

    private float mNormalWidth;
    private float mParentHeight = -1;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX;
    private float mPosY = -1;
    private boolean mUserInteracting;
    private int mParentTop;
    private Integer mIndex;
    private float mInitialLevel;
    private Context mContext;

    public EqBarView(Context context) {
        super(context);
        mContext = context;
        init();
    }

    public EqBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    public EqBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init();
    }

    private void init() {
        mEqManager = MasterConfigControl.getInstance(mContext).getEqualizerManager();
        mNormalWidth = getResources().getDimension(R.dimen.eq_bar_width);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private EqContainerView.EqBandInfo getInfo() {
        return (EqContainerView.EqBandInfo) getTag();
    }

    public void setParentHeight(float h, int top) {
        mParentHeight = h;
        mParentTop = top;
        updateHeight();
    }

    void updateHeight() {
        if (DEBUG) Log.d(TAG, "updateHeight()");

        if (getInfo() != null) {
            float level = mEqManager.getLevel(getIndex());
            float yProjection = 1 - mEqManager.projectY(level);
            float height = (yProjection * (mParentHeight));
            mPosY = height;

            if (DEBUG) {
                Log.d(TAG, getIndex() + "level: " + level + ", yProjection: "
                        + yProjection + ", mPosY: " + mPosY);
            }
            updateHeight((int) mPosY);
        } else {
            if (DEBUG) Log.d(TAG, "could not updateHeight()");
        }
    }

    public int getIndex() {
        if (mIndex == null) {
            mIndex = (getInfo()).mIndex;
        }
        return mIndex;
    }

    public boolean isUserInteracting() {
        return mUserInteracting;
    }

    /* package */ void startInteraction(float x, float y) {

        mLastTouchX = x;
        mLastTouchY = y;
        mUserInteracting = true;

        if (DEBUG) Log.d(TAG, "initial level: " + mInitialLevel);
        mInitialLevel =
                (1 - (mPosY / mParentHeight)) * (mEqManager.getMinDB() - mEqManager.getMaxDB())
                        - mEqManager.getMinDB();

        updateWidth((int) (mNormalWidth * 2));
    }

    /* package */ void endInteraction() {
        mUserInteracting = false;

        updateWidth((int) mNormalWidth);
    }

    private void updateHeight(int h) {
        if (!isInLayout()) {
            final ViewGroup.LayoutParams params = getLayoutParams();
            params.height = h;
            setLayoutParams(params);
        }
    }

    private void updateWidth(int w) {
        final ViewGroup.LayoutParams params = getLayoutParams();
        params.width = w;
        setLayoutParams(params);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mEqManager.isEqualizerLocked()) {
            return false;
        }

        final float x = event.getRawX();
        final float y = event.getRawY() - mParentTop;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startInteraction(x, y);
                break;

            case MotionEvent.ACTION_MOVE:
                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                mPosX += dx;
                mPosY -= dy;

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;

                int wy = (int) mParentHeight;
                float level = (1 - (mPosY / wy)) * (mEqManager.getMinDB() - mEqManager.getMaxDB())
                        - mEqManager.getMinDB();

                if (DEBUG) Log.d(TAG, "new level: " + level);
                if (level < mEqManager.getMinDB()) {
                    level = mEqManager.getMinDB();
                } else if (level > mEqManager.getMaxDB()) {
                    level = mEqManager.getMaxDB();
                }

                if (mInitialLevel != level) {
                    mEqManager.setLevel(getInfo().mIndex, level, false);
                } else {
                    updateHeight();
                }

                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                endInteraction();
                break;

        }

        return true;
    }

    public float getPosY() {
        return mPosY;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (getInfo().mIndex != band) {
            return;
        }

        updateHeight();
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {

    }

    @Override
    public void onPresetsChanged() {

    }
}
