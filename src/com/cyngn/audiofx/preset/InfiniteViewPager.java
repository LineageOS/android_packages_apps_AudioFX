package com.cyngn.audiofx.preset;


import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.cyngn.audiofx.activity.MasterConfigControl;

/**
 * A {@link ViewPager} that allows pseudo-infinite paging with a wrap-around effect. Should be used with an {@link
 * InfinitePagerAdapter}.
 */
public class InfiniteViewPager extends ViewPager {

    MasterConfigControl mConfig;

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public InfiniteViewPager(Context context) {
        super(context);
        mConfig = MasterConfigControl.getInstance(context);
    }

    public InfiniteViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConfig = MasterConfigControl.getInstance(context);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mConfig.isAnimatingToCustom()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mConfig.isAnimatingToCustom()) {
            return false;
        }
        boolean result;
        try {
            result = super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            /* There's a bug with the support library where it doesn't check
             * the proper pointer index, so when multi touching the container,
             * this can sometimes be thrown. Supposedly there are no downsides to just
             * catching the exception and moving along, so let's do that....
             */
            result = false;
        }
        return result;
    }

    @Override
    public void setAdapter(PagerAdapter adapter) {
        super.setAdapter(adapter);
        // offset first element so that we can scroll to the left
        setCurrentItem(0);
    }

    @Override
    public void setCurrentItem(int item) {
        // offset the current item to ensure there is space to scroll
        item = getOffsetAmount() + (item % getAdapter().getCount());
        super.setCurrentItem(item);
    }

    public void setCurrentItemAbsolute(int item) {
        super.setCurrentItem(item);
    }

    private int getOffsetAmount() {
        if (getAdapter() instanceof InfinitePagerAdapter) {
            InfinitePagerAdapter infAdapter = (InfinitePagerAdapter) getAdapter();
            // allow for 100 back cycles from the beginning
            // should be enough to create an illusion of infinity
            // warning: scrolling to very high values (1,000,000+) results in
            // strange drawing behaviour
            return infAdapter.getRealCount() * 100;
        } else {
            return 0;
        }
    }

    public void setCurrentItemAbsolute(int newPage, boolean b) {
        super.setCurrentItem(newPage, b);
    }
}
