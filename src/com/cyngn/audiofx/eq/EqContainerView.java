package com.cyngn.audiofx.eq;

import android.content.Context;
import android.graphics.Color;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;

import java.util.ArrayList;
import java.util.List;

public class EqContainerView extends FrameLayout
        implements MasterConfigControl.EqUpdatedCallback, MasterConfigControl.EqControlStateCallback {

    private static final String TAG = EqContainerView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    int mWidth;
    int mHeight;
    MasterConfigControl mConfig;
    List<EqBandInfo> mBandInfo;

    View mDash;
    CheckBox mLockBox;
    ImageView mRenameControl;
    ImageView mRemoveControl;
    ImageView mSaveControl;
    ViewGroup mControls;
    boolean mControlsVisible;

    boolean mFirstLayout = true;
    boolean mInLayout;
    private List<Integer> mSelectedBands = new ArrayList<>();

    public static class EqBandInfo {
        public int index;
        public int id;

        public TextView label;
        public BandDeltaBlockView db;
    }

    public EqContainerView(Context context) {
        super(context);
        init();
    }

    public EqContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqContainerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mConfig = MasterConfigControl.getInstance(mContext);
        mBandInfo = new ArrayList<EqBandInfo>();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDash = findViewById(R.id.dash);
        mControls = (ViewGroup) findViewById(R.id.eq_controls);

        mLockBox = (CheckBox) findViewById(R.id.lock);
        mLockBox.setOnCheckedChangeListener(mConfig.getLockChangeListener());

        mRenameControl = (ImageView) findViewById(R.id.rename);
        mRemoveControl = (ImageView) findViewById(R.id.remove);
        mSaveControl = (ImageView) findViewById(R.id.save);
    }

    @Override
    protected void onAttachedToWindow() {
        if (DEBUG) Log.d(TAG, "onAttachedToWindow()");
        super.onAttachedToWindow();

        mConfig.setEqControlCallback(this);
        mConfig.addEqStateChangeCallback(this);
        onPresetChanged(mConfig.getCurrentPresetIndex()); // update initial state
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DEBUG) Log.d(TAG, "onDetachedFromWindow()");
        mConfig.removeEqStateChangeCallback(this);
        mConfig.setEqControlCallback(null);
        super.onDetachedFromWindow();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    List<EqBarView> mBarViews = new ArrayList<>();

    private void generateAndAddBars() {
        if (mFirstLayout) {
            mFirstLayout = false;
            mBarViews.clear();

            final float barWidth = getResources().getDimension(R.dimen.eq_bar_width);
            final int textBlock = getResources().getDimensionPixelSize(R.dimen.eq_text_height);
            for (int i = 0; i < mConfig.getNumBands(); i++) {
                final EqBandInfo band = new EqBandInfo();
                band.index = i;
                band.id = View.generateViewId();
                mBandInfo.add(band);

                final EqBarView bar = new EqBarView(mContext);
                bar.setTag(band);

                final int finalI = i;
                bar.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (mConfig.isEqualizerLocked()) {
                            return false;
                        }
                        switch (event.getActionMasked()) {

                            case MotionEvent.ACTION_DOWN:
                                startBarInteraction(bar);
                                break;
                            case MotionEvent.ACTION_CANCEL:
                            case MotionEvent.ACTION_UP:
                                stopBarInteraction(bar);
                                break;
                        }

                        return false;
                    }
                });

                // set correct initial alpha
                if (i % 2 == 0) {
                    bar.setAlpha(0.6f);
                } else {
                    bar.setAlpha(0.8f);
                }
                bar.setBackgroundColor(Color.WHITE);
                bar.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                        getResources().getDisplayMetrics()));

                addView(bar, getFrameParams());
                bar.setParentHeight(mHeight, (int) (getTop()), textBlock);

                // add freq label
                band.label = new TextView(mContext, null);
                final float freq = mConfig.getCenterFreq(band.index);
                String frequencyText = String.format(freq < 1000 ? "%.0f" : "%.0fk",
                        freq < 1000 ? freq : freq / 1000);
                band.label.setText(frequencyText);
                band.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                band.label.setGravity(Gravity.CENTER_HORIZONTAL);
                band.label.setTextAlignment(TEXT_ALIGNMENT_CENTER);
                band.label.setTextColor(getResources().getColor(R.color.band_freq_label));

                addView(band.label, getTextLabelParams());

                // add band delta block
                BandDeltaBlockView inflated = (BandDeltaBlockView) LayoutInflater.from(mContext)
                        .inflate(R.layout.eq_band_delta_block, this, false);
                band.db = inflated;
                band.db.setBand(i);

                addView(band.db, getSelectedBoxParams());

                mBarViews.add(bar);
            }
            updateSelectedBands();
        }
    }

    public EqBarView startTouchingBarUnder(MotionEvent event) {
        EqBarView foundBar = findBar(event.getX(), event.getY(), mBarViews);
        if (foundBar != null) {
            foundBar.updateHeight(false);

            foundBar.startInteraction(event.getRawX(), event.getRawY());
            startBarInteraction(foundBar);
        }
        return foundBar;
    }

    public void startBarInteraction(EqBarView bar) {
        Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(30);

        setControlsVisible(false, false);
        synchronized (mSelectedBands) {
            EqBandInfo band = (EqBandInfo) bar.getTag();
            mSelectedBands.add((Integer) band.index);
        }
        updateSelectedBands();
    }

    public void stopBarInteraction(EqBarView bar) {
        synchronized (mSelectedBands) {
            EqBandInfo band = (EqBandInfo) bar.getTag();
            mSelectedBands.remove((Integer) band.index);
            updateSelectedBands();
        }
        setControlsVisible(mControlsVisible, true);
    }

    private EqBarView findBar(float x, float y, List<EqBarView> targets) {
        final int count = targets.size();
        for (int i = 0; i < count; i++) {
            final EqBarView target = targets.get(i);
            if (target.getRight() > x && target.getTop() < y
                    && target.getBottom() > y && target.getLeft() < x) {
                return target;
            }
        }
        return null;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final float selectedBoxHeight = getResources().getDimension(R.dimen.eq_selected_box_height);
        final float barWidth = getResources().getDimension(R.dimen.eq_bar_width);
        final float separatorWidth = getResources().getDimension(R.dimen.separator_width);
        final int textBlock = getResources().getDimensionPixelSize(R.dimen.eq_text_height);

        mWidth = right - left;
        mHeight = (int) (bottom - top);

        generateAndAddBars();

        //---------------------------------------------------
        mInLayout = true;

        int dashY = (int) ((mConfig.projectY(0) * mHeight) + separatorWidth);

        final int widthOfBars = (int) ((mConfig.getNumBands() * barWidth)
                + ((mConfig.getNumBands() - 1) * separatorWidth));
        final int freeSpace = (int) (mWidth - widthOfBars);

        int mCurLeft = (freeSpace / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (child instanceof EqBarView) {
                EqBandInfo info = (EqBandInfo) child.getTag();
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                float freq = mConfig.getCenterFreq(info.index);
                float x = mConfig.projectX(freq) * mWidth;

                int l = mCurLeft;
                int r = (int) (l + barWidth);

                mCurLeft += barWidth + separatorWidth;

                final int layoutBottom = (int) (mHeight - lp.bottomMargin);

                if (((EqBarView) child).isUserInteracting()) {
                    l -= childWidth / 4;
                    r += childWidth / 4;

                }
                // layout selected box
                final int bb = (int) (layoutBottom - childHeight - separatorWidth - (selectedBoxHeight * 2));
                info.db.layout(l, bb, r, (int) (bb + selectedBoxHeight));

                // layout label
                info.label.layout(l,
                        layoutBottom - textBlock,
                        r,
                        layoutBottom);

                child.layout(l,
                        (int) (layoutBottom - childHeight - selectedBoxHeight),
                        r,
                        layoutBottom - textBlock);
            }
        }

        mDash.layout(
                freeSpace / 2,
                dashY - 2,
                (int) (widthOfBars + (freeSpace / 2)),
                dashY + 2);

        mControls.layout(
                right - mControls.getMeasuredWidth() - mControls.getPaddingLeft(),
                top + mControls.getPaddingTop(),
                right - mControls.getPaddingRight(),
                top + mControls.getMeasuredHeight() + mControls.getPaddingTop() + mControls.getPaddingBottom()
        );
        mInLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    private void updateSelectedBands() {
        final float barWidth = getResources().getDimension(R.dimen.eq_bar_width);
        for (int i = 0; i < mConfig.getNumBands(); i++) {
            EqBandInfo tag = mBandInfo.get(i);
            EqBarView bar = (EqBarView) findViewWithTag(tag);
            if (bar != null) {
                if (mSelectedBands.isEmpty()) {
                    if (i % 2 == 0) {
                        bar.animate().withLayer().alpha(0.6f);
                    } else {
                        bar.animate().withLayer().alpha(0.8f);
                    }
                    tag.db.setAlpha(0);

                    tag.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                    tag.label.setWidth((int) barWidth);
                    tag.label.getLayoutParams().width = (int) barWidth;
                    tag.label.requestLayout();

                } else if (mSelectedBands.contains(i)) {
                    tag.db.setAlpha(1);

                    bar.animate().alpha(1f);
                    bar.setBackgroundColor(getResources().getColor(R.color.band_bar_color_selected));

                    tag.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tag.label.setWidth((int) barWidth * 2);
                    tag.label.getLayoutParams().width = (int) barWidth * 2;
                    tag.label.requestLayout();
                } else {
                    tag.db.setAlpha(0);
                    bar.animate().withLayer().alpha(0.40f);
                }
            }
        }
    }

    private FrameLayout.LayoutParams getFrameParams() {
        float width = getResources().getDimension(R.dimen.eq_bar_width);

        final float selectedBoxHeight = getResources().getDimension(R.dimen.eq_selected_box_height);
        final float paddingTop = getResources().getDimension(R.dimen.eq_top_padding);
        final int textBlock = getResources().getDimensionPixelSize(R.dimen.eq_text_height);

        float height = (mHeight - selectedBoxHeight - textBlock - paddingTop) / 2;

        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams((int) width, (int) height);
        ll.gravity = Gravity.BOTTOM;

        return ll;
    }

    private FrameLayout.LayoutParams getTextLabelParams() {
        final float barWidth = getResources().getDimension(R.dimen.eq_bar_width);

        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams((int) barWidth,
                LayoutParams.WRAP_CONTENT);
        return ll;
    }

    private FrameLayout.LayoutParams getSelectedBoxParams() {
        final int w = getResources().getDimensionPixelSize(R.dimen.eq_selected_box_width);
        final int h = getResources().getDimensionPixelSize(R.dimen.eq_selected_box_height);

        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(w, h);
        return ll;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        postInvalidate();
    }

    @Override
    public void onPresetChanged(int newPresetIndex) {
        updateEqState();
        if (mConfig.isUserPreset()) {
            mLockBox.setChecked(mConfig.isEqualizerLocked());
        }
    }

    @Override
    public void updateEqState() {
        boolean controlsVisible = mConfig.isUserPreset() || mConfig.isCustomPreset();
        mControlsVisible = controlsVisible; // persist this.
        if (!mSelectedBands.isEmpty()) {
            // selected bands, force override to hide controls
            controlsVisible = false;
        }
        setControlsVisible(controlsVisible, false);

        MasterConfigControl.EqControlState state = mConfig.getEqControlState();
        animateControl(mLockBox, state.unlockVisible);
        animateControl(mRemoveControl, state.removeVisible);
        animateControl(mRenameControl, state.renameVisible);
        animateControl(mSaveControl, state.saveVisible);
    }

    private void animateControl(final View v, boolean visible) {
        if (visible) {
            v.animate().cancel();
            v.setVisibility(View.VISIBLE);
            v.animate()
                    .alpha(1f)
                    .withLayer()
                    .setDuration(250)
                    .withEndAction(null);
        } else {
            v.animate().cancel();
            v.animate()
                    .alpha(0f)
                    .withLayer()
                    .setDuration(250)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            v.setVisibility(View.INVISIBLE);
                        }
                    });
        }
    }


    @Override
    public void onPresetsChanged() {

    }

    @Override
    public void onDeviceChanged(OutputDevice deviceId, boolean userChange) {

    }

    public void setControlsVisible(boolean visible, boolean keepChange) {
        if (keepChange) {
            mControlsVisible = visible;
        }

        if (mControls != null) {
            animateControl(mControls, visible);
        }
    }

}
