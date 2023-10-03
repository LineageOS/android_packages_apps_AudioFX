/*
 * SPDX-FileCopyrightText: 2014 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.audiofx.preset;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;

import org.lineageos.audiofx.Preset;
import org.lineageos.audiofx.R;
import org.lineageos.audiofx.activity.EqualizerManager;
import org.lineageos.audiofx.activity.MasterConfigControl;

public class PresetPagerAdapter extends PagerAdapter {

    private final Context mContext;
    private final EqualizerManager mEqManager;

    public PresetPagerAdapter(Context context) {
        super();
        mContext = context;
        mEqManager = MasterConfigControl.getInstance(mContext).getEqualizerManager();
    }

    @Override
    public int getItemPosition(Object object) {
        View v = (View) object;
        int index = mEqManager.indexOf(((Preset) v.getTag()));
        if (index == -1) {
            return POSITION_NONE;
        } else {
            return index;
        }
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.preset_adapter_row, container, false);
        TextView tv = (TextView) view;
        tv.setText(mEqManager.getLocalizedPresetName(position));
        tv.setTag(mEqManager.getPreset(position));
        container.addView(tv);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        if (object instanceof View) {
            container.removeView((View) object);
        }
    }

    @Override
    public int getCount() {
        return mEqManager.getPresetCount();
    }

    @Override
    public boolean isViewFromObject(View view, Object o) {
        return view == o;
    }


}
