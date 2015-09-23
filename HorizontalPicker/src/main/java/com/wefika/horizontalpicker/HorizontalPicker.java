/*
 * Copyright 2014 Blaž Šolar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wefika.horizontalpicker;

import android.animation.ArgbEvaluator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.text.TextDirectionHeuristicCompat;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.ExploreByTouchHelper;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by Blaž Šolar on 24/01/14.
 */
public class HorizontalPicker extends View {

    public static final String TAG = "HorizontalTimePicker";

    /**
     * The coefficient by which to adjust (divide) the max fling velocity.
     */
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 4;

    /**
     * The the duration for adjusting the selector wheel.
     */
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;

    /**
     * Determines speed during touch scrolling.
     */
    private VelocityTracker mVelocityTracker;

    /**
     * @see android.view.ViewConfiguration#getScaledMinimumFlingVelocity()
     */
    private int mMinimumFlingVelocity;

    /**
     * @see android.view.ViewConfiguration#getScaledMaximumFlingVelocity()
     */
    private int mMaximumFlingVelocity;

    private final int mOverscrollDistance;

    private int mTouchSlop;

    private CharSequence[] mValues;
    private BoringLayout[] mLayouts;

    private TextPaint mTextPaint;
    private BoringLayout.Metrics mBoringMetrics;
    private TextUtils.TruncateAt mEllipsize;

    private int mItemWidth;
    private RectF mItemClipBounds;
    private RectF mItemClipBoundsOffser;

    private float mLastDownEventX;

    private OverScroller mFlingScrollerX;
    private OverScroller mAdjustScrollerX;

    private int mPreviousScrollerX;

    private boolean mScrollingX;
    private int mPressedItem = -1;

    private ColorStateList mTextColor;

    private OnItemSelected mOnItemSelected;
    private OnItemClicked mOnItemClicked;

    private int mSelectedItem;

    private EdgeEffect mLeftEdgeEffect;
    private EdgeEffect mRightEdgeEffect;

    private Marquee mMarquee;
    private int mMarqueeRepeatLimit = 3;

    private float mDividerSize = 0;

    private int mSideItems = 1;

    private TextDirectionHeuristicCompat mTextDir;

    private final PickerTouchHelper mTouchHelper;

    public HorizontalPicker(Context context) {
        this(context, null);
    }

    public HorizontalPicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.horizontalPickerStyle);
    }

    public HorizontalPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // create the selector wheel paint
        TextPaint paint = new TextPaint();
        paint.setAntiAlias(true);
        mTextPaint = paint;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.HorizontalPicker,
                defStyle, 0
        );

        CharSequence[] values;
        int ellipsize = 3; // END default value
        int sideItems = mSideItems;

        try {
            mTextColor = a.getColorStateList(R.styleable.HorizontalPicker_android_textColor);
            values = a.getTextArray(R.styleable.HorizontalPicker_values);
            ellipsize = a.getInt(R.styleable.HorizontalPicker_android_ellipsize, ellipsize);
            mMarqueeRepeatLimit = a.getInt(R.styleable.HorizontalPicker_android_marqueeRepeatLimit, mMarqueeRepeatLimit);
            mDividerSize = a.getDimension(R.styleable.HorizontalPicker_dividerSize, mDividerSize);
            sideItems = a.getInt(R.styleable.HorizontalPicker_sideItems, sideItems);

            float textSize = a.getDimension(R.styleable.HorizontalPicker_android_textSize, -1);
            if(textSize > -1) {
                setTextSize(textSize);
            }
        } finally {
            a.recycle();
        }

        switch (ellipsize) {
            case 1:
                setEllipsize(TextUtils.TruncateAt.START);
                break;
            case 2:
                setEllipsize(TextUtils.TruncateAt.MIDDLE);
                break;
            case 3:
                setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 4:
                setEllipsize(TextUtils.TruncateAt.MARQUEE);
                break;
        }

        Paint.FontMetricsInt fontMetricsInt = mTextPaint.getFontMetricsInt();
        mBoringMetrics = new BoringLayout.Metrics();
        mBoringMetrics.ascent = fontMetricsInt.ascent;
        mBoringMetrics.bottom = fontMetricsInt.bottom;
        mBoringMetrics.descent = fontMetricsInt.descent;
        mBoringMetrics.leading = fontMetricsInt.leading;
        mBoringMetrics.top = fontMetricsInt.top;
        mBoringMetrics.width = mItemWidth;

        setWillNotDraw(false);

        mFlingScrollerX = new OverScroller(context);
        mAdjustScrollerX = new OverScroller(context, new DecelerateInterpolator(2.5f));

        // initialize constants
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
        mOverscrollDistance = configuration.getScaledOverscrollDistance();

        mPreviousScrollerX = Integer.MIN_VALUE;

        setValues(values);
        setSideItems(sideItems);

        mTouchHelper = new PickerTouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, mTouchHelper);

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int height;
        if(heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
            int heightText = (int) (Math.abs(fontMetrics.ascent) + Math.abs(fontMetrics.descent));
            heightText += getPaddingTop() + getPaddingBottom();

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(heightSize, heightText);
            } else {
                height = heightText;
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int saveCount = canvas.getSaveCount();
        canvas.save();

        int selectedItem = mSelectedItem;

        float itemWithPadding = mItemWidth + mDividerSize;

        // translate horizontal to center
        canvas.translate(itemWithPadding * mSideItems, 0);

        if (mValues != null) {
            for (int i = 0; i < mValues.length; i++) {

                // set text color for item
                mTextPaint.setColor(getTextColor(i));

                // get text layout
                BoringLayout layout = mLayouts[i];

                int saveCountHeight = canvas.getSaveCount();
                canvas.save();

                float x = 0;

                float lineWidth = layout.getLineWidth(0);
                if (lineWidth > mItemWidth) {
                    if (isRtl(mValues[i])) {
                        x += (lineWidth - mItemWidth) / 2;
                    } else {
                        x -= (lineWidth - mItemWidth) / 2;
                    }
                }

                if (mMarquee != null && i == selectedItem) {
                    x += mMarquee.getScroll();
                }

                // translate vertically to center
                canvas.translate(-x, (canvas.getHeight() - layout.getHeight()) / 2);

                RectF clipBounds;
                if (x == 0) {
                    clipBounds = mItemClipBounds;
                } else {
                    clipBounds = mItemClipBoundsOffser;
                    clipBounds.set(mItemClipBounds);
                    clipBounds.offset(x, 0);
                }

                canvas.clipRect(clipBounds);
                layout.draw(canvas);

                if (mMarquee != null && i == selectedItem && mMarquee.shouldDrawGhost()) {
                    canvas.translate(mMarquee.getGhostOffset(), 0);
                    layout.draw(canvas);
                }

                // restore vertical translation
                canvas.restoreToCount(saveCountHeight);

                // translate horizontal for 1 item
                canvas.translate(itemWithPadding, 0);
            }
        }

        // restore horizontal translation
        canvas.restoreToCount(saveCount);

        drawEdgeEffect(canvas, mLeftEdgeEffect, 270);
        drawEdgeEffect(canvas, mRightEdgeEffect, 90);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        mTextDir = getTextDirectionHeuristic();
    }

    /**
     * TODO cache values
     * @param text
     * @return
     */
    private boolean isRtl(CharSequence text) {
        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        return mTextDir.isRtl(text, 0, text.length());
    }

    private TextDirectionHeuristicCompat getTextDirectionHeuristic() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {

            return TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR;

        } else {

            // Always need to resolve layout direction first
            final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

            switch (getTextDirection()) {
                default:
                case TEXT_DIRECTION_FIRST_STRONG:
                    return (defaultIsRtl ? TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL :
                            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR);
                case TEXT_DIRECTION_ANY_RTL:
                    return TextDirectionHeuristicsCompat.ANYRTL_LTR;
                case TEXT_DIRECTION_LTR:
                    return TextDirectionHeuristicsCompat.LTR;
                case TEXT_DIRECTION_RTL:
                    return TextDirectionHeuristicsCompat.RTL;
                case TEXT_DIRECTION_LOCALE:
                    return TextDirectionHeuristicsCompat.LOCALE;
            }
        }
    }

    private void remakeLayout() {

        if (mLayouts != null && mLayouts.length > 0 && getWidth() > 0)  {
            for (int i = 0; i < mLayouts.length; i++) {
                mLayouts[i].replaceOrMake(mValues[i], mTextPaint, mItemWidth,
                        Layout.Alignment.ALIGN_CENTER, 1f, 1f, mBoringMetrics, false, mEllipsize,
                        mItemWidth);
            }
        }

    }

    private void drawEdgeEffect(Canvas canvas, EdgeEffect edgeEffect, int degrees) {

        if (canvas == null || edgeEffect == null || (degrees != 90 && degrees != 270)) {
            return;
        }

        if(!edgeEffect.isFinished()) {
            final int restoreCount = canvas.getSaveCount();
            final int width = getWidth();
            final int height = getHeight() - getPaddingTop() - getPaddingBottom();

            canvas.rotate(degrees);

            if (degrees == 270) {
                canvas.translate(-height + getPaddingTop(), Math.max(0, getScrollX()));
            } else { // 90
                canvas.translate(-getPaddingTop(), -(Math.max(getScrollRange(), getScaleX()) + width));
            }

            edgeEffect.setSize(height, width);
            if(edgeEffect.draw(canvas)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    postInvalidateOnAnimation();
                } else {
                    postInvalidate();
                }
            }

            canvas.restoreToCount(restoreCount);
        }

    }

    /**
     * Calculates text color for specified item based on its position and state.
     *
     * @param item Index of item to get text color for
     * @return Item text color
     */
    private int getTextColor(int item) {

        int scrollX = getScrollX();

        // set color of text
        int color = mTextColor.getDefaultColor();
        int itemWithPadding = (int) (mItemWidth + mDividerSize);
        if (scrollX > itemWithPadding * item - itemWithPadding / 2 &&
                scrollX < itemWithPadding * (item + 1) - itemWithPadding / 2) {
            int position = scrollX - itemWithPadding / 2;
            color = getColor(position, item);
        } else if(item == mPressedItem) {
            color = mTextColor.getColorForState(new int[] { android.R.attr.state_pressed }, color);
        }

        return color;

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        calculateItemSize(w, h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled()) {
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:

                float currentMoveX = event.getX();

                int deltaMoveX = (int) (mLastDownEventX - currentMoveX);

                if(mScrollingX ||
                        (Math.abs(deltaMoveX) > mTouchSlop) && mValues != null && mValues.length > 0) {

                    if(!mScrollingX) {
                        deltaMoveX = 0;
                        mPressedItem = -1;
                        mScrollingX = true;
                        stopMarqueeIfNeeded();
                    }

                    final int range = getScrollRange();

                    if(overScrollBy(deltaMoveX, 0, getScrollX(), 0, range, 0,
                            mOverscrollDistance, 0, true)) {
                        mVelocityTracker.clear();
                    }

                    final float pulledToX = getScrollX() + deltaMoveX;
                    if(pulledToX < 0) {
                        mLeftEdgeEffect.onPull((float) deltaMoveX / getWidth());
                        if(!mRightEdgeEffect.isFinished()) {
                            mRightEdgeEffect.onRelease();
                        }
                    } else if(pulledToX > range) {
                        mRightEdgeEffect.onPull((float) deltaMoveX / getWidth());
                        if(!mLeftEdgeEffect.isFinished()) {
                            mLeftEdgeEffect.onRelease();
                        }
                    }

                    mLastDownEventX = currentMoveX;
                    invalidate();

                }

                break;
            case MotionEvent.ACTION_DOWN:

                if(!mAdjustScrollerX.isFinished()) {
                    mAdjustScrollerX.forceFinished(true);
                } else if(!mFlingScrollerX.isFinished()) {
                    mFlingScrollerX.forceFinished(true);
                } else {
                    mScrollingX = false;
                }

                mLastDownEventX = event.getX();

                if(!mScrollingX) {
                    mPressedItem = getPositionFromTouch(event.getX());
                }
                invalidate();

                break;
            case MotionEvent.ACTION_UP:

                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocityX = (int) velocityTracker.getXVelocity();

                if(mScrollingX && Math.abs(initialVelocityX) > mMinimumFlingVelocity) {
                    flingX(initialVelocityX);
                } else if (mValues != null) {
                    float positionX = event.getX();
                    if(!mScrollingX) {

                        int itemPos = getPositionOnScreen(positionX);
                        int relativePos = itemPos - mSideItems;

                        if (relativePos == 0) {
                            selectItem();
                        } else {
                            smoothScrollBy(relativePos);
                        }

                    } else if(mScrollingX) {
                        finishScrolling();
                    }
                }

                mVelocityTracker.recycle();
                mVelocityTracker = null;

                if(mLeftEdgeEffect != null) {
                    mLeftEdgeEffect.onRelease();
                    mRightEdgeEffect.onRelease();
                }

            case MotionEvent.ACTION_CANCEL:
                mPressedItem = -1;
                invalidate();

                if(mLeftEdgeEffect != null) {
                    mLeftEdgeEffect.onRelease();
                    mRightEdgeEffect.onRelease();
                }

                break;
        }

        return true;
    }

    private void selectItem() {

        if (mOnItemClicked != null) {
            mOnItemClicked.onItemClicked(getSelectedItem());
        }

        adjustToNearestItemX();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (!isEnabled()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                selectItem();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                smoothScrollBy(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                smoothScrollBy(1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }

    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {

        if (mTouchHelper.dispatchHoverEvent(event)) {
            return true;
        }

        return super.dispatchHoverEvent(event);
    }

    @Override
    public void computeScroll() {
        computeScrollX();
    }

    @Override
    public void getFocusedRect(Rect r) {
        super.getFocusedRect(r); // TODO this should only be current item
    }

    public void setOnItemSelectedListener(OnItemSelected onItemSelected) {
        mOnItemSelected = onItemSelected;
    }

    public void setOnItemClickedListener(OnItemClicked onItemClicked) {
        mOnItemClicked = onItemClicked;
    }

    public int getSelectedItem() {
        int x = getScrollX();
        return getPositionFromCoordinates(x);
    }

    public void setSelectedItem(int index) {
        mSelectedItem = index;
        scrollToItem(index);
    }

    public int getMarqueeRepeatLimit() {
        return mMarqueeRepeatLimit;
    }

    public void setMarqueeRepeatLimit(int marqueeRepeatLimit) {
        mMarqueeRepeatLimit = marqueeRepeatLimit;
    }

    /**
     * @return Number of items on each side of current item.
     */
    public int getSideItems() {
        return mSideItems;
    }

    public void setSideItems(int sideItems) {
        if (mSideItems < 0) {
            throw new IllegalArgumentException("Number of items on each side must be grater or equal to 0.");
        } else if (mSideItems != sideItems) {
            mSideItems = sideItems;
            calculateItemSize(getWidth(), getHeight());
        }
    }

    /**
     * @return
     */
    public CharSequence[] getValues() {
        return mValues;
    }

    /**
     * Sets values to choose from
     * @param values New values to choose from
     */
    public void setValues(CharSequence[] values) {

        if (mValues != values) {
            mValues = values;

            if (mValues != null) {
                mLayouts = new BoringLayout[mValues.length];
                for (int i = 0; i < mLayouts.length; i++) {
                    mLayouts[i] = new BoringLayout(mValues[i], mTextPaint, mItemWidth, Layout.Alignment.ALIGN_CENTER,
                            1f, 1f, mBoringMetrics, false, mEllipsize, mItemWidth);
                }
            } else {
                mLayouts = new BoringLayout[0];
            }

            // start marque only if has already been measured
            if (getWidth() > 0) {
                startMarqueeIfNeeded();
            }

            requestLayout();
            invalidate();
        }

    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {

        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        setSelectedItem(ss.mSelItem);


    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState savedState = new SavedState(superState);
        savedState.mSelItem = mSelectedItem;

        return savedState;

    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if(overScrollMode != OVER_SCROLL_NEVER) {
            Context context = getContext();
            mLeftEdgeEffect = new EdgeEffect(context);
            mRightEdgeEffect = new EdgeEffect(context);
        } else {
            mLeftEdgeEffect = null;
            mRightEdgeEffect = null;
        }

        super.setOverScrollMode(overScrollMode);
    }

    public TextUtils.TruncateAt getEllipsize() {
        return mEllipsize;
    }

    public void setEllipsize(TextUtils.TruncateAt ellipsize) {
        if (mEllipsize != ellipsize) {
            mEllipsize = ellipsize;

            remakeLayout();
            invalidate();
        }
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.scrollTo(scrollX, scrollY);

        if(!mFlingScrollerX.isFinished() && clampedX) {
            mFlingScrollerX.springBack(scrollX, scrollY, 0, getScrollRange(), 0, 0);
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged(); //TODO
    }

    private int getPositionFromTouch(float x) {
        return getPositionFromCoordinates((int) (getScrollX() - (mItemWidth + mDividerSize) * (mSideItems + .5f) + x));
    }

    private void computeScrollX() {
        OverScroller scroller = mFlingScrollerX;
        if(scroller.isFinished()) {
            scroller = mAdjustScrollerX;
            if(scroller.isFinished()) {
                return;
            }
        }

        if(scroller.computeScrollOffset()) {

            int currentScrollerX = scroller.getCurrX();
            if(mPreviousScrollerX == Integer.MIN_VALUE) {
                mPreviousScrollerX = scroller.getStartX();
            }

            int range = getScrollRange();
            if(mPreviousScrollerX >= 0 && currentScrollerX < 0) {
                mLeftEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
            } else if(mPreviousScrollerX <= range && currentScrollerX > range) {
                mRightEdgeEffect.onAbsorb((int) scroller.getCurrVelocity());
            }

            overScrollBy(currentScrollerX - mPreviousScrollerX, 0, mPreviousScrollerX, getScrollY(),
                    getScrollRange(), 0, mOverscrollDistance, 0, false);
            mPreviousScrollerX = currentScrollerX;

            if(scroller.isFinished()) {
                onScrollerFinishedX(scroller);
            }

            postInvalidate();
//            postInvalidateOnAnimation(); // TODO
        }
    }

    private void flingX(int velocityX) {

        mPreviousScrollerX = Integer.MIN_VALUE;
        mFlingScrollerX.fling(getScrollX(), getScrollY(), -velocityX, 0, 0,
                (int) (mItemWidth + mDividerSize) * (mValues.length - 1), 0, 0, getWidth() / 2, 0);

        invalidate();
    }

    private void adjustToNearestItemX() {

        int x = getScrollX();
        int item = Math.round(x / (mItemWidth + mDividerSize * 1f));

        if(item < 0) {
            item = 0;
        } else if(item > mValues.length) {
            item = mValues.length;
        }

        mSelectedItem = item;

        int itemX = (mItemWidth + (int) mDividerSize) * item;

        int deltaX = itemX - x;

        mAdjustScrollerX.startScroll(x, 0, deltaX, 0, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
        invalidate();
    }

    private void calculateItemSize(int w, int h) {

        int items = mSideItems * 2 + 1;
        int totalPadding = ((int) mDividerSize * (items - 1));
        mItemWidth = (w - totalPadding) / items;

        mItemClipBounds = new RectF(0, 0, mItemWidth, h);
        mItemClipBoundsOffser = new RectF(mItemClipBounds);

        scrollToItem(mSelectedItem);

        remakeLayout();
        startMarqueeIfNeeded();

    }

    private void onScrollerFinishedX(OverScroller scroller) {
        if(scroller == mFlingScrollerX) {
            finishScrolling();
        }
    }

    private void finishScrolling() {

        adjustToNearestItemX();
        mScrollingX = false;

        if (mOnItemSelected != null) {
            mOnItemSelected.onItemSelected(getPositionFromCoordinates(getScrollX()));
        }

        startMarqueeIfNeeded();
    }

    private void startMarqueeIfNeeded() {

        stopMarqueeIfNeeded();

        int item = getSelectedItem();

        if (mLayouts != null && mLayouts.length > item) {
            Layout layout = mLayouts[item];
            if (mEllipsize == TextUtils.TruncateAt.MARQUEE
                    && mItemWidth < layout.getLineWidth(0)) {
                mMarquee = new Marquee(this, layout, isRtl(mValues[item]));
                mMarquee.start(mMarqueeRepeatLimit);
            }
        }

    }

    private void stopMarqueeIfNeeded() {

        if (mMarquee != null) {
            mMarquee.stop();
            mMarquee = null;
        }

    }

    private int getPositionOnScreen(float x) {
        return (int) (x / (mItemWidth + mDividerSize));
    }

    private void smoothScrollBy(int i) {
        int deltaMoveX = (mItemWidth + (int) mDividerSize) * i;
        deltaMoveX = getRelativeInBound(deltaMoveX);

        mFlingScrollerX.startScroll(getScrollX(), 0, deltaMoveX, 0);
        stopMarqueeIfNeeded();
        invalidate();
    }

    /**
     * Calculates color for specific position on time picker
     * @param scrollX
     * @return
     */
    private int getColor(int scrollX, int position) {
        int itemWithPadding = (int) (mItemWidth + mDividerSize);
        float proportion = Math.abs(((1f * scrollX % itemWithPadding) / 2) / (itemWithPadding / 2f));
        if(proportion > .5) {
            proportion = (proportion - .5f);
        } else {
            proportion = .5f - proportion;
        }
        proportion *= 2;

        int defaultColor;
        int selectedColor;

        if(mPressedItem == position) {
            defaultColor = mTextColor.getColorForState(new int[] { android.R.attr.state_pressed }, mTextColor.getDefaultColor());
            selectedColor = mTextColor.getColorForState(new int[] { android.R.attr.state_pressed, android.R.attr.state_selected }, defaultColor);
        } else {
            defaultColor = mTextColor.getDefaultColor();
            selectedColor = mTextColor.getColorForState(new int[] { android.R.attr.state_selected }, defaultColor);
        }
        return (Integer) new ArgbEvaluator().evaluate(proportion, selectedColor, defaultColor);
    }

    /**
     * Sets text size for items
     * @param size New item text size in px.
     */
    private void setTextSize(float size) {
        if(size != mTextPaint.getTextSize()) {
            mTextPaint.setTextSize(size);

            requestLayout();
            invalidate();
        }
    }

    /**
     * Calculates item from x coordinate position.
     * @param x Scroll position to calculate.
     * @return Selected item from scrolling position in {param x}
     */
    private int getPositionFromCoordinates(int x) {
        return Math.round(x / (mItemWidth + mDividerSize));
    }

    /**
     * Scrolls to specified item.
     * @param index Index of an item to scroll to
     */
    private void scrollToItem(int index) {
        scrollTo((mItemWidth + (int) mDividerSize) * index, 0);
        invalidate();
    }

    /**
     * Calculates relative horizontal scroll position to be within our scroll bounds.
     * {@link com.wefika.horizontalpicker.HorizontalPicker#getInBoundsX(int)}
     * @param x Relative scroll position to calculate
     * @return Current scroll position + {param x} if is within our scroll bounds, otherwise it
     * will return min/max scroll position.
     */
    private int getRelativeInBound(int x) {
        int scrollX = getScrollX();
        return getInBoundsX(scrollX + x) - scrollX;
    }

    /**
     * Calculates x scroll position that is still in range of view scroller
     * @param x Scroll position to calculate.
     * @return {param x} if is within bounds of over scroller, otherwise it will return min/max
     * value of scoll position.
     */
    private int getInBoundsX(int x) {

        if(x < 0) {
            x = 0;
        } else if(x > ((mItemWidth + (int) mDividerSize) * (mValues.length - 1))) {
            x = ((mItemWidth + (int) mDividerSize) * (mValues.length - 1));
        }
        return x;
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if(mValues != null && mValues.length != 0) {
            scrollRange = Math.max(0, ((mItemWidth + (int) mDividerSize) * (mValues.length - 1)));
        }
        return scrollRange;
    }

    public interface OnItemSelected {

        public void onItemSelected(int index);

    }

    public interface OnItemClicked {

        public void onItemClicked(int index);

    }

    private static final class Marquee extends Handler {
        // TODO: Add an option to configure this
        private static final float MARQUEE_DELTA_MAX = 0.07f;
        private static final int MARQUEE_DELAY = 1200;
        private static final int MARQUEE_RESTART_DELAY = 1200;
        private static final int MARQUEE_RESOLUTION = 1000 / 30;
        private static final int MARQUEE_PIXELS_PER_SECOND = 30;

        private static final byte MARQUEE_STOPPED = 0x0;
        private static final byte MARQUEE_STARTING = 0x1;
        private static final byte MARQUEE_RUNNING = 0x2;

        private static final int MESSAGE_START = 0x1;
        private static final int MESSAGE_TICK = 0x2;
        private static final int MESSAGE_RESTART = 0x3;

        private final WeakReference<HorizontalPicker> mView;
        private final WeakReference<Layout> mLayout;

        private byte mStatus = MARQUEE_STOPPED;
        private final float mScrollUnit;
        private float mMaxScroll;
        private float mMaxFadeScroll;
        private float mGhostStart;
        private float mGhostOffset;
        private float mFadeStop;
        private int mRepeatLimit;

        private float mScroll;

        private boolean mRtl;

        Marquee(HorizontalPicker v, Layout l, boolean rtl) {
            final float density = v.getContext().getResources().getDisplayMetrics().density;
            float scrollUnit = (MARQUEE_PIXELS_PER_SECOND * density) / MARQUEE_RESOLUTION;
            if (rtl) {
                mScrollUnit = -scrollUnit;
            } else {
                mScrollUnit = scrollUnit;
            }

            mView = new WeakReference<HorizontalPicker>(v);
            mLayout = new WeakReference<Layout>(l);
            mRtl = rtl;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_START:
                    mStatus = MARQUEE_RUNNING;
                    tick();
                    break;
                case MESSAGE_TICK:
                    tick();
                    break;
                case MESSAGE_RESTART:
                    if (mStatus == MARQUEE_RUNNING) {
                        if (mRepeatLimit >= 0) {
                            mRepeatLimit--;
                        }
                        start(mRepeatLimit);
                    }
                    break;
            }
        }

        void tick() {
            if (mStatus != MARQUEE_RUNNING) {
                return;
            }

            removeMessages(MESSAGE_TICK);

            final HorizontalPicker view = mView.get();
            final Layout layout = mLayout.get();
            if (view != null && layout != null && (view.isFocused() || view.isSelected())) {
                mScroll += mScrollUnit;
                if (Math.abs(mScroll) > mMaxScroll) {
                    mScroll = mMaxScroll;
                    if (mRtl) {
                        mScroll *= -1;
                    }
                    sendEmptyMessageDelayed(MESSAGE_RESTART, MARQUEE_RESTART_DELAY);
                } else {
                    sendEmptyMessageDelayed(MESSAGE_TICK, MARQUEE_RESOLUTION);
                }
                view.invalidate();
            }
        }

        void stop() {
            mStatus = MARQUEE_STOPPED;
            removeMessages(MESSAGE_START);
            removeMessages(MESSAGE_RESTART);
            removeMessages(MESSAGE_TICK);
            resetScroll();
        }

        private void resetScroll() {
            mScroll = 0.0f;
            final HorizontalPicker view = mView.get();
            if (view != null) view.invalidate();
        }

        void start(int repeatLimit) {
            if (repeatLimit == 0) {
                stop();
                return;
            }
            mRepeatLimit = repeatLimit;
            final HorizontalPicker view = mView.get();
            final Layout layout = mLayout.get();
            if (view != null && layout != null) {
                mStatus = MARQUEE_STARTING;
                mScroll = 0.0f;
                final int textWidth = view.mItemWidth;
                final float lineWidth = layout.getLineWidth(0);
                final float gap = textWidth / 3.0f;
                mGhostStart = lineWidth - textWidth + gap;
                mMaxScroll = mGhostStart + textWidth;
                mGhostOffset = lineWidth + gap;
                mFadeStop = lineWidth + textWidth / 6.0f;
                mMaxFadeScroll = mGhostStart + lineWidth + lineWidth;

                if (mRtl) {
                    mGhostOffset *= -1;
                }

                view.invalidate();
                sendEmptyMessageDelayed(MESSAGE_START, MARQUEE_DELAY);
            }
        }

        float getGhostOffset() {
            return mGhostOffset;
        }

        float getScroll() {
            return mScroll;
        }

        float getMaxFadeScroll() {
            return mMaxFadeScroll;
        }

        boolean shouldDrawLeftFade() {
            return mScroll <= mFadeStop;
        }

        boolean shouldDrawGhost() {
            return mStatus == MARQUEE_RUNNING && Math.abs(mScroll) > mGhostStart;
        }

        boolean isRunning() {
            return mStatus == MARQUEE_RUNNING;
        }

        boolean isStopped() {
            return mStatus == MARQUEE_STOPPED;
        }
    }

    public static class SavedState extends BaseSavedState {

        private int mSelItem;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeInt(mSelItem);
        }

        @Override
        public String toString() {
            return  "HorizontalPicker.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " selItem=" + mSelItem
                    + "}";
        }
    }

    private static class PickerTouchHelper extends ExploreByTouchHelper {

        private HorizontalPicker mPicker;

        public PickerTouchHelper(HorizontalPicker picker) {
            super(picker);
            mPicker = picker;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {

            float itemWidth = mPicker.mItemWidth + mPicker.mDividerSize;
            float position = mPicker.getScrollX() + x - itemWidth * mPicker.mSideItems;

            float item = position / itemWidth;

            if (item < 0 || item > mPicker.mValues.length) {
                return INVALID_ID;
            }

            return (int) item;

        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {

            float itemWidth = mPicker.mItemWidth + mPicker.mDividerSize;
            float position = mPicker.getScrollX() - itemWidth * mPicker.mSideItems;

            int first = (int) (position / itemWidth);

            int items = mPicker.mSideItems * 2 + 1;

            if (position % itemWidth != 0) { // if start next item is starting to appear on screen
                items++;
            }

            if (first < 0) {
                items += first;
                first = 0;
            } else if (first + items > mPicker.mValues.length) {
                items = mPicker.mValues.length - first;
            }

            for (int i = 0; i < items; i++) {
                virtualViewIds.add(first + i);
            }

        }

        @Override
        protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
            event.setContentDescription(mPicker.mValues[virtualViewId]);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {

            float itemWidth = mPicker.mItemWidth + mPicker.mDividerSize;
            float scrollOffset = mPicker.getScrollX() - itemWidth * mPicker.mSideItems;

            int left = (int) (virtualViewId * itemWidth - scrollOffset);
            int right = left + mPicker.mItemWidth;

            node.setContentDescription(mPicker.mValues[virtualViewId]);
            node.setBoundsInParent(new Rect(left, 0, right, mPicker.getHeight()));
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);

        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
            return false;
        }

    }

}
