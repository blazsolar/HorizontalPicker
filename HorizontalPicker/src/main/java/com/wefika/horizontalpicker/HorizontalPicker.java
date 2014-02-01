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
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

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

    private Paint mSelectorWheelPaint;

    private int mItemWidth;

    private float mLastDownEventX;
    private long mLastDownEventTime;

    private OverScroller mFlingScrollerX;
    private OverScroller mAdjustScrollerX;

    private int mPreviousScrollerX;

    private boolean mScrollingX;
    private int mPressedItem = -1;

    private ColorStateList mTextColor;

    private OnItemSelected mOnItemSelected;

    private int mSelectedItem;

    private EdgeEffect mLeftEdgeEffect;
    private EdgeEffect mRightEdgeEffect;

    public HorizontalPicker(Context context) {
        this(context, null);
    }

    public HorizontalPicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.horizontalPickerStyle);
    }

    public HorizontalPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // create the selector wheel paint
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        mSelectorWheelPaint = paint;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.HorizontalPicker,
                defStyle, 0
        );

        try {
            mTextColor = a.getColorStateList(R.styleable.HorizontalPicker_android_textColor);
            mValues = a.getTextArray(R.styleable.HorizontalPicker_values);

            float textSize = a.getDimension(R.styleable.HorizontalPicker_android_textSize, -1);
            if(textSize > -1) {
                setTextSize(textSize);
            }
        } finally {
            a.recycle();
        }

        if(mValues == null) {
            mValues = new String[0];
        }

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

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

//        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = widthSize;

        int height;
        if(heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            Paint.FontMetrics fontMetrics = mSelectorWheelPaint.getFontMetrics();
            int heightText = (int) (Math.abs(fontMetrics.ascent) + Math.abs(fontMetrics.descent));
            heightText += getPaddingTop() + getPaddingBottom();

            height = Math.min(heightSize, heightText);
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int scrollX = getScrollX();

        int y = (int) ((canvas.getHeight() / 2) - ((mSelectorWheelPaint.descent() + mSelectorWheelPaint.ascent()) / 2)) ;

        int saveCount = canvas.getSaveCount();
        canvas.save();

        canvas.translate(mItemWidth / 2, 0);

        for (int i = 0; i < mValues.length; i++) {

            canvas.translate(mItemWidth, 0);

            // set color of text
            int color = mTextColor.getDefaultColor();
            if (scrollX > mItemWidth * i - mItemWidth / 2 &&
                    scrollX < mItemWidth * (i + 1) - mItemWidth / 2) {
                int position = scrollX - mItemWidth / 2;
                color = getColor(position, i);
            } else if(i == mPressedItem) {
                color = mTextColor.getColorForState(new int[] { android.R.attr.state_pressed }, color);
            }
            mSelectorWheelPaint.setColor(color);

            canvas.drawText(mValues[i].toString(), 0, y, mSelectorWheelPaint);
        }

        canvas.restoreToCount(saveCount);

        if(mLeftEdgeEffect != null) {
            if(!mLeftEdgeEffect.isFinished()) {
                final int restoreCount = canvas.getSaveCount();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), Math.max(0, getScrollX()));
                mLeftEdgeEffect.setSize(height, width);
                if(mLeftEdgeEffect.draw(canvas)) {
                    postInvalidate(); // TODO we should probably use postInvalidateOnAnimation(); for API 16+
                }

                canvas.restoreToCount(restoreCount);
            }
            if(!mRightEdgeEffect.isFinished()) {
                final int restoreCount = canvas.getSaveCount();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(),
                        -(Math.max(getScrollRange(), scrollX) + width));
                mRightEdgeEffect.setSize(height, width);
                if(mRightEdgeEffect.draw(canvas)) {
                    postInvalidate(); // TODO we should probably use postInvalidateOnAnimation(); for API 16+
                }

                canvas.restoreToCount(restoreCount);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if(w != oldw) {
            mItemWidth = w / 3;
        }

        scrollToItem(mSelectedItem);
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

                if(mScrollingX || (Math.abs(deltaMoveX) > mTouchSlop)) {

                    if(!mScrollingX) {
                        deltaMoveX = 0;
                        mPressedItem = -1;
                        mScrollingX = true;

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
                mLastDownEventTime = event.getEventTime();

                if(!mScrollingX) {
                    mPressedItem = getPositionFromCoordinates((int) (getScrollX() - mItemWidth * 1.5f + event.getX()));
                }
                invalidate();

                break;
            case MotionEvent.ACTION_UP:

                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocityX = (int) velocityTracker.getXVelocity();

                if(mScrollingX && Math.abs(initialVelocityX) > mMinimumFlingVelocity) {
                    flingX(initialVelocityX);
                } else {
                    float positionX = event.getX();
                    long deltaTime = event.getEventTime() - mLastDownEventTime;
                    if(!mScrollingX && deltaTime < ViewConfiguration.getTapTimeout()) {
                        int itemPos = getPositionOnScreen(positionX);
                        if(itemPos == 0) {
                            moveToPrev();
                        } else if(itemPos == 1) {

                            if(mOnItemSelected != null) {
                                mOnItemSelected.onItemSelected(getSelectedItem());
                            }

                            adjustToNearestItemX();
                        } else {
                            moveToNext();
                        }
                    } else if(mScrollingX) {
                        flingX(initialVelocityX);
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

    public int getSelectedItem() {
        int x = getScrollX();
        return getPositionFromCoordinates(x);
    }

    public void setSelectedItem(int index) {
        mSelectedItem = index;
        scrollToItem(index);
    }

    @Override
    public void scrollBy(int x, int y) {
        super.scrollBy(x, 0);
    }

    @Override
    public void scrollTo(int x, int y) {
//        x = getInBoundsX(x);
        super.scrollTo(x, y);
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
    public void setValues(String[] values) {
        mValues = values;

        if(mValues != null && values != null && mValues.length == values.length ||
                mValues != null ^ values != null) {
            requestLayout();
            invalidate();
        }
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
        mFlingScrollerX.fling(getScrollX(), getScrollY(), -velocityX, 0,     0,
                mItemWidth * (mValues.length - 1), 0, 0, getWidth()/2, 0);

        invalidate();
    }

    private void adjustToNearestItemX() {

        int x = getScrollX();
        int item = Math.round(x / (mItemWidth * 1f));

        if(item < 0) {
            item = 0;
        } else if(item > mValues.length) {
            item = mValues.length;
        }

        mSelectedItem = item;

        int itemX = mItemWidth * item;

        int deltaX = itemX - x;

        mAdjustScrollerX.startScroll(x, 0, deltaX, 0, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
        invalidate();
    }

    private void onScrollerFinishedX(OverScroller scroller) {
        if(scroller == mFlingScrollerX) {
            adjustToNearestItemX();
            mScrollingX = false;
        }
    }

    private void moveToNext() {

        int deltaMoveX = mItemWidth;
        deltaMoveX = getRelativeInBound(deltaMoveX);

        mFlingScrollerX.startScroll(getScrollX(), 0, deltaMoveX, 0);
        invalidate();
    }

    private int getPositionOnScreen(float x) {
        return (int) (x / mItemWidth);
    }

    private void moveToPrev() {

        int deltaMoveX = mItemWidth * -1;
        deltaMoveX = getRelativeInBound(deltaMoveX);

        mFlingScrollerX.startScroll(getScrollX(), 0, deltaMoveX, 0);
        invalidate();
    }

    /**
     * Calculates color for specific position on time picker
     * @param scrollX
     * @return
     */
    private int getColor(int scrollX, int position) {
        float proportion = Math.abs(((1f * scrollX % mItemWidth) / 2) / (mItemWidth / 2f));
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
        if(size != mSelectorWheelPaint.getTextSize()) {
            mSelectorWheelPaint.setTextSize(size);

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
        return Math.round(x / (mItemWidth * 1f));
    }

    /**
     * Scrolls to specified item.
     * @param index Index of an item to scroll to
     */
    private void scrollToItem(int index) {
        scrollTo(mItemWidth * index, 0);
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
        } else if(x > mItemWidth * (mValues.length - 1)) {
            x = mItemWidth * (mValues.length - 1);
        }
        return x;
    }

    private int getScrollRange() {
        int scrollRange = 0;
        if(mValues != null && mValues.length != 0) {
            scrollRange = Math.max(0, (mValues.length - 1) * mItemWidth);
        }
        return scrollRange;
    }

    public interface OnItemSelected {

        public void onItemSelected(int index);

    }
}
