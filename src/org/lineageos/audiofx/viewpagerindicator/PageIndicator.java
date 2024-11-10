/*
 * SPDX-FileCopyrightText: 2011 Patrik Akerfeldt
 * SPDX-FileCopyrightText: 2011 Jake Wharton
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.viewpagerindicator;

import androidx.viewpager.widget.ViewPager;

/**
 * A PageIndicator is responsible to show an visual indicator on the total views number and the
 * current visible view.
 */
public interface PageIndicator extends ViewPager.OnPageChangeListener {
    /**
     * Bind the indicator to a ViewPager.
     *
     * @param view
     */
    void setViewPager(ViewPager view);

    /**
     * Bind the indicator to a ViewPager.
     *
     * @param view
     * @param initialPosition
     */
    void setViewPager(ViewPager view, int initialPosition);

    /**
     * <p>Set the current page of both the ViewPager and indicator.</p>
     *
     * <p>This <strong>must</strong> be used if you need to set the page before
     * the views are drawn on screen (e.g., default start page).</p>
     *
     * @param item
     */
    void setCurrentItem(int item);

    /**
     * Set a page change listener which will receive forwarded events.
     *
     * @param listener
     */
    void setOnPageChangeListener(ViewPager.OnPageChangeListener listener);

    /**
     * Notify the indicator that the fragment list has changed.
     */
    void notifyDataSetChanged();
}
