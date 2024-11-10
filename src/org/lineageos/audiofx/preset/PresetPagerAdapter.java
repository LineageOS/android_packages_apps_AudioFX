/*
 * SPDX-FileCopyrightText: 2014-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.audiofx.preset;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.preset_adapter_row, container, false);
        TextView tv = (TextView) view;
        tv.setText(mEqManager.getLocalizedPresetName(position));
        tv.setTag(mEqManager.getPreset(position));
        container.addView(tv);
        return view;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        if (object instanceof View) {
            container.removeView((View) object);
        }
    }

    @Override
    public int getCount() {
        return mEqManager.getPresetCount();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
        return view == o;
    }


}
