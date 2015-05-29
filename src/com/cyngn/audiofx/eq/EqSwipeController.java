package com.cyngn.audiofx.eq;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.preset.InfiniteViewPager;

public class EqSwipeController extends LinearLayout {

    /*
     * minimum amount of time we wait before allowing user to "attach" to a bar, to allow
     * for swiping
     */
    private static final int SWIPE_THRESH = 100;

    /*
     * maximum amount of time in ms, from downtime, which will allow bars to be grabbed.
     * basically, when the user goes > this threshold swiping, we ignore bar "attach" events
     */
    private static final int BAR_MAX_THRESH = 1000;

    /*
     * x velocity max for deciding whether to try to grab a bar
     */
    private static final int X_VELOCITY_THRESH = 20;

    EqContainerView mEq;
    InfiniteViewPager mPager;
    private VelocityTracker mVelocityTracker = null;
    long mDownTime;
    EqBarView mBar;
    boolean mBarActive;
    private ViewGroup mControls;

    public EqSwipeController(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEq = (EqContainerView) findViewById(R.id.eq_container);
        mPager = (InfiniteViewPager) findViewById(R.id.pager);
        mControls = (ViewGroup) findViewById(R.id.eq_controls);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // don't intercept touches over the EQ controls
        if (mControls.getRight() > x && mControls.getTop() < y
                && mControls.getBottom() > y && mControls.getLeft() < x) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = event.getActionIndex();
        int action = event.getActionMasked();
        int pointerId = event.getPointerId(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                mVelocityTracker.addMovement(event);
                mVelocityTracker.computeCurrentVelocity(1000);
                float xVelocity = mVelocityTracker.getXVelocity(pointerId);
                float yVelocity = mVelocityTracker.getYVelocity(pointerId);

                if (!mBarActive
                        && System.currentTimeMillis() - mDownTime > SWIPE_THRESH
                        && System.currentTimeMillis() - mDownTime < BAR_MAX_THRESH
                        && Math.abs(xVelocity) < X_VELOCITY_THRESH
                        && !MasterConfigControl.getInstance(getContext()).isEqualizerLocked()) {
                    mBarActive = true;
                    mBar = mEq.startTouchingBarUnder(event);
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mVelocityTracker.recycle();
                mVelocityTracker = null;

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
