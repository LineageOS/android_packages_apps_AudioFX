package com.cyngn.audiofx.eq;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;

public class EqBarView extends FrameLayout implements MasterConfigControl.EqUpdatedCallback,
        ValueAnimator.AnimatorUpdateListener {

    private static final String TAG = EqBarView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    MasterConfigControl mConfig;

    private float mNormalWidth;
    private float mParentHeight;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mPosX;
    private float mPosY = -1;
    private boolean mUserInteracting;
    private int mParentTop;
    private int mTextOffset;

    private int mFloatingTextHeight;
    private float mInitialLevel;

    private ValueAnimator mAnimator;
    private boolean mInitialAnimation = true;

    public EqBarView(Context context) {
        super(context);
        init();
    }

    public EqBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mConfig = MasterConfigControl.getInstance(mContext);
        mNormalWidth = getResources().getDimension(R.dimen.eq_bar_width);

        mFloatingTextHeight = (int) getResources().getDimension(R.dimen.eq_selected_box_height);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

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

    public void setInitialAnimation(boolean forceAnimate) {
        mInitialAnimation = forceAnimate;
    }

    private EqContainerView.EqBandInfo getInfo() {
        return (EqContainerView.EqBandInfo) getTag();
    }

    public void setParentHeight(float h, int top, int textOffset) {

        final float selectedBoxHeight = getResources().getDimension(R.dimen.eq_selected_box_height);
        final float paddingTop = getResources().getDimension(R.dimen.eq_top_padding);

        mParentHeight = h - selectedBoxHeight - textOffset - paddingTop;
        mParentTop = top;
        mTextOffset = textOffset;
    }

    void updateHeight() {
        if (DEBUG) Log.d(TAG, "updateHeight() mInitialAnimation=" + mInitialAnimation);

        if (getInfo() != null) {
            float level = mConfig.getLevel((getInfo()).index);
            float yProjection = 1 - mConfig.projectY(level);
            float height = (yProjection * (mParentHeight));
            mPosY = height;

            if (DEBUG) {
                Log.d(TAG, getInfo().index + "level: " + level + ", yProjection: "
                        + yProjection + ", mPosY: " + mPosY);
            }

            if (mInitialAnimation) {
                if (mAnimator != null) {
                    mAnimator.cancel();
                    mAnimator = null;
                }
                mAnimator = ValueAnimator.ofFloat(getLayoutParams().height, mPosY);
                mAnimator.addUpdateListener(this);
                if (mInitialAnimation) {
                    mAnimator.setDuration(800);
                    mInitialAnimation = false;
                }
                mAnimator.setInterpolator(new LinearInterpolator());
                mAnimator.start();

            } else {
                getLayoutParams().height = (int) mPosY;
                requestLayout();
            }
        } else {
            if (DEBUG) Log.d(TAG, "could not updateHeight()");
        }
    }

    public boolean isUserInteracting() {
        return mUserInteracting;
    }

    /* package */ void startInteraction(float x, float y) {

        mLastTouchX = x;
        mLastTouchY = y;
        mUserInteracting = true;

        if (DEBUG) Log.d(TAG, "initial level: " + mInitialLevel);
        mInitialLevel = (1 - (mPosY / mParentHeight)) * (mConfig.getMinDB() - mConfig.getMaxDB())
                - mConfig.getMinDB();

        getLayoutParams().width = (int) (mNormalWidth * 2);
        requestLayout();
    }

    /* package */ void endInteraction() {
        mUserInteracting = false;

        getLayoutParams().width = (int) (mNormalWidth);
        requestLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mConfig.isEqualizerLocked()) {
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
                float level = (1 - (mPosY / wy)) * (mConfig.getMinDB() - mConfig.getMaxDB())
                        - mConfig.getMinDB();

                if (DEBUG) Log.d(TAG, "new level: " + level);
                if (level < mConfig.getMinDB()) {
                    level = mConfig.getMinDB();
                } else if (level > mConfig.getMaxDB()) {
                    level = mConfig.getMaxDB();
                }

                if (mInitialLevel != level) {
                    mConfig.setLevel(getInfo().index, level, false);
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

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (getInfo().index != band) {
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

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {

    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        int val = (int) ((Float) animation.getAnimatedValue() + 0.5f);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.height = val;
        setLayoutParams(layoutParams);
    }
}
