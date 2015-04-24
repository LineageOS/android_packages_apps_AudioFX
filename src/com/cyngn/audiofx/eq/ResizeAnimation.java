package com.cyngn.audiofx.eq;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class ResizeAnimation extends Animation {

    private static final String TAG = ResizeAnimation.class.getSimpleName();

    private View mView;
    private float mToHeight;
    private float mFromHeight;
    private float mHeightDiff;

    private float mToWidth;
    private float mFromWidth;

    private boolean mAnimateHeight;
    private boolean mAnimateWidth;

    public ResizeAnimation(View v, float fromHeight, float toHeight, float fromWidth, float toWidth) {
        setHeightParams(fromHeight, toHeight);
        setWidthParams(fromWidth, toWidth);

        mView = v;
    }

    public ResizeAnimation(View v) {
        mView = v;
    }

    public void setWidthParams(float fromWidth, float toWidth) {
        mAnimateWidth = true;
        mFromWidth = fromWidth;
        mToWidth = toWidth;
    }

    public void setHeightParams(float fromHeight, float toHeight) {
        mAnimateHeight = true;
        mFromHeight = fromHeight;
        mToHeight = toHeight;

        mHeightDiff = (toHeight - fromHeight);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        ViewGroup.LayoutParams p = mView.getLayoutParams();

        if (mAnimateHeight) {
            float height= mFromHeight + (mHeightDiff * interpolatedTime);

            p.height = (int) height;
        }
        if (mAnimateWidth) {
            float width = (mToWidth- mFromWidth) * interpolatedTime + mFromWidth;
            p.width = (int) width;
        }

        mView.requestLayout();
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }

}