package com.cyngn.audiofx.eq;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.cyngn.audiofx.R;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.service.OutputDevice;

import java.util.ArrayList;
import java.util.List;

public class EqContainerView extends FrameLayout
        implements MasterConfigControl.EqUpdatedCallback, MasterConfigControl.EqControlStateCallback {

    private static final String TAG = EqContainerView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private int mWidth;
    private int mHeight;
    private MasterConfigControl mConfig;
    private List<EqBandInfo> mBandInfo;
    private List<EqBarView> mBarViews;
    private List<Integer> mSelectedBands;

    private CheckBox mLockBox;
    private ImageView mRenameControl;
    private ImageView mRemoveControl;
    private ImageView mSaveControl;
    private ViewGroup mControls;
    private boolean mControlsVisible;

    private int mSelectedBandColor;
    private boolean mFirstLayout = true;

    private Paint mTextPaint;
    private Paint mFreqPaint;
    private Paint mSelectedFreqPaint;
    private Paint mCenterLinePaint;
    private Path mDashPath;

    private Runnable mVibrateRunnable = new Runnable() {
        @Override
        public void run() {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(30);
        }
    };
    private int mPaddingTop;
    private int mPaddingBottom;
    private int mBarWidth;
    private int mBarSeparation;
    private int mBarBottomGrabSpacePadding;

    public static class EqBandInfo {
        public int mIndex;

        public String mFreq;
        public String mDb;
        public EqBarView mBar;
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
        final Resources r = getResources();

        mBarWidth = r.getDimensionPixelSize(R.dimen.eq_bar_width);
        mBarSeparation = r.getDimensionPixelSize(R.dimen.separator_width);

        mBarBottomGrabSpacePadding = r.getDimensionPixelSize(R.dimen.eq_bar_bottom_grab_space);
        int freqTextSize = r.getDimensionPixelSize(R.dimen.eq_label_text_size);
        int selectedBoxTextSize = r.getDimensionPixelSize(R.dimen.eq_selected_box_height);

        int extraTopSpace = r.getDimensionPixelSize(R.dimen.eq_bar_top_padding);

        mPaddingTop = selectedBoxTextSize + extraTopSpace;
        mPaddingBottom = selectedBoxTextSize + mBarBottomGrabSpacePadding;

        mConfig = MasterConfigControl.getInstance(mContext);
        mBarViews = new ArrayList<>();
        mBandInfo = new ArrayList<>();
        mSelectedBands = new ArrayList<>();

        setWillNotDraw(false);

        mSelectedBandColor = r.getColor(R.color.band_bar_color_selected);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setElegantTextHeight(true);
        mTextPaint.setTextSize(selectedBoxTextSize);

        mFreqPaint = new Paint();
        mFreqPaint.setAntiAlias(true);
        mFreqPaint.setColor(Color.WHITE);
        mFreqPaint.setTextAlign(Paint.Align.CENTER);
        mFreqPaint.setTextSize(freqTextSize);

        mSelectedFreqPaint = new Paint(mFreqPaint);
        mSelectedFreqPaint.setAntiAlias(true);
        mSelectedFreqPaint.setTextSize(selectedBoxTextSize);

        mCenterLinePaint = new Paint();
        mCenterLinePaint.setColor(Color.WHITE);
        mCenterLinePaint.setAntiAlias(true);
        mCenterLinePaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));
        mCenterLinePaint.setStyle(Paint.Style.STROKE);
        mCenterLinePaint.setAntiAlias(true);

        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                generateAndAddBars();
            }
        });
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

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

    private void generateAndAddBars() {
        if (mFirstLayout) {
            mFirstLayout = false;
            mBarViews.clear();

            for (int i = 0; i < mConfig.getNumBands(); i++) {
                final EqBandInfo band = new EqBandInfo();
                band.mIndex = i;
                mBandInfo.add(band);

                final EqBarView bar = new EqBarView(mContext);
                band.mBar = bar;
                bar.setTag(band);
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

                addView(bar, getFrameParams(i));
                bar.setParentHeight(mHeight, getTop());

                final float freq = mConfig.getCenterFreq(i);
                String frequencyText = String.format(freq < 1000 ? "%.0f" : "%.0fk",
                        freq < 1000 ? freq : freq / 1000);
                band.mFreq = frequencyText;
                mBarViews.add(bar);
            }
            updateSelectedBands();
        } else {
            for (EqBarView barView : mBarViews) {
                barView.setParentHeight(mHeight, getTop());
            }
        }
    }

    public EqBarView startTouchingBarUnder(MotionEvent event) {
        EqBarView foundBar = findBar(event.getX(), event.getY(), mBarViews);
        if (foundBar != null) {
            foundBar.updateHeight();

            foundBar.startInteraction(event.getRawX(), event.getRawY());
            startBarInteraction(foundBar);
        }
        return foundBar;
    }

    public void startBarInteraction(EqBarView bar) {
        setControlsVisible(false, false);
        EqBandInfo band = (EqBandInfo) bar.getTag();
        mSelectedBands.add(band.mIndex);
        updateSelectedBands();
        AsyncTask.execute(mVibrateRunnable);
    }

    public void stopBarInteraction(EqBarView bar) {
        EqBandInfo band = (EqBandInfo) bar.getTag();
        mSelectedBands.remove((Integer) band.mIndex);
        updateSelectedBands();
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h - mPaddingTop - mPaddingBottom;
        generateAndAddBars();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        //---------------------------------------------------

        if (mFirstLayout)
            return;

        int dashY = bottom - mPaddingBottom - (mHeight / 2);

        final int widthOfBars = (mConfig.getNumBands() * mBarWidth)
                + ((mConfig.getNumBands() - 1) * mBarSeparation);
        final int freeSpace = mWidth - widthOfBars;

        int mCurLeft = (freeSpace / 2);
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child instanceof EqBarView) {
                final int childWidth = child.getMeasuredWidth();
                final int childHeight = child.getMeasuredHeight();

                int l = mCurLeft;
                int r = l + mBarWidth;

                mCurLeft += mBarWidth + mBarSeparation;

                if (((EqBarView) child).isUserInteracting()) {
                    l -= childWidth / 4;
                    r += childWidth / 4;
                }

                final int layoutTop = top + mHeight - childHeight + mPaddingTop;
                final int layoutBottom = layoutTop + childHeight
                        + mPaddingBottom - (mPaddingBottom - mBarBottomGrabSpacePadding);
                child.layout(l, layoutTop, r, layoutBottom);
            }
        }

        if (changed || mDashPath == null) {
            mDashPath = new Path();
            mDashPath.reset();
            mDashPath.moveTo(freeSpace / 2, dashY);
            mDashPath.lineTo(widthOfBars + (freeSpace / 2), dashY);
        }

        mControls.layout(
                right - mControls.getMeasuredWidth() - mControls.getPaddingLeft(),
                top + mControls.getPaddingTop(),
                right - mControls.getPaddingRight(),
                top + mControls.getMeasuredHeight() + mControls.getPaddingTop()
                        + mControls.getPaddingBottom()
        );
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawPath(mDashPath, mCenterLinePaint);

        for (int i = 0; i < mBandInfo.size(); i++) {
            EqBandInfo info = mBandInfo.get(i);

            final float x = info.mBar.getX() + (info.mBar.getWidth() / 2);
            final boolean userInteracting = info.mBar.isUserInteracting();
            if (userInteracting) {
                canvas.drawText(
                        info.mDb,
                        x,
                        info.mBar.getY() - (mTextPaint.getTextSize() / 2),
                        mTextPaint);
            }

            Paint drawPaint = userInteracting ? mSelectedFreqPaint : mFreqPaint;

            canvas.drawText(info.mFreq, x,
                    info.mBar.getBottom() + drawPaint.getTextSize(),
                    drawPaint);
        }
    }

    private void updateSelectedBands() {
        for (int i = 0; i < mConfig.getNumBands(); i++) {
            EqBandInfo tag = mBandInfo.get(i);
            final EqBarView bar = (EqBarView) findViewWithTag(tag);
            if (bar != null) {
                final ViewPropertyAnimator barAnimation = bar.animate().withLayer();
                if (mSelectedBands.isEmpty()) {
                    if (i % 2 == 0) {
                        barAnimation.alpha(0.6f);
                    } else {
                        barAnimation.alpha(0.8f);
                    }
                } else if (mSelectedBands.contains(i)) {
                    barAnimation.alpha(1f);
                    bar.setBackgroundColor(mSelectedBandColor);
                } else {
                    barAnimation.alpha(0.40f);
                }
            }
        }
    }

    private FrameLayout.LayoutParams getFrameParams(int index) {
        int width = getResources().getDimensionPixelSize(R.dimen.eq_bar_width);
        int height = Math.round((1 - mConfig.projectY(mConfig.getLevel(index))) * mHeight);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(width, height);
        ll.gravity = Gravity.TOP;
        return ll;
    }

    @Override
    public void onBandLevelChange(int band, float dB, boolean fromSystem) {
        if (mFirstLayout) return;
        mBandInfo.get(band).mDb = dB != 0 ? String.format("%+1.1f", dB) : "0.0";
        invalidate();
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
            v.setVisibility(View.VISIBLE);
            v.animate()
                    .alpha(1f)
                    .setDuration(350)
                    .withEndAction(null);
        } else {
            v.animate()
                    .alpha(0f)
                    .setDuration(350)
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
