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
import android.widget.Scroller;

/**
 * Created by Blaž Šolar on 24/01/14.
 */
public class HorizontalPicker extends View {

    public static final String TAG = "HorizontalTimePicker";

    /**
     * The coefficient by which to adjust (divide) the max fling velocity.
     */
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 2;

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

    private int mTouchSlop;

    private CharSequence[] mValues;

    private Paint mSelectorWheelPaint;

    private int mItemWidth;

    private float mLastDownEventX;
    private long mLastDownEventTime;

    private Scroller mFlingScrollerX;
    private Scroller mAdjustScrollerX;

    private int mPreviousScrollerX;

    private boolean mScrollingX;
    private int mPressedItem = -1;

    private ColorStateList mTextColor;

    private OnItemSelected mOnItemSelected;

    private int mSelectedItem;

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

        mFlingScrollerX = new Scroller(context, null, true);
        mAdjustScrollerX = new Scroller(context, new DecelerateInterpolator(2.5f));

        // initialize constants
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;

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
                    scrollBy(deltaMoveX, 0);

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

            case MotionEvent.ACTION_CANCEL:
                mPressedItem = -1;
                invalidate();
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

    private void computeScrollX() {
        Scroller scroller = mFlingScrollerX;
        if(scroller.isFinished()) {
            scroller = mAdjustScrollerX;
            if(scroller.isFinished()) {
                return;
            }
        }

        scroller.computeScrollOffset();
        int currentScrollerX = scroller.getCurrX();
        if(mPreviousScrollerX == Integer.MIN_VALUE) {
            mPreviousScrollerX = scroller.getStartX();
        }

        scrollBy(currentScrollerX - mPreviousScrollerX, 0);
        mPreviousScrollerX = currentScrollerX;
        if(scroller.isFinished()) {
            onScrollerFinishedX(scroller);
        } else {
            invalidate();
        }
    }

    private void flingX(int velocityX) {

        mPreviousScrollerX = Integer.MIN_VALUE;
        mFlingScrollerX.fling(getScrollX(), 0, -velocityX, 0, 0, mItemWidth * (mValues.length - 1), 0, 0);

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

    private void onScrollerFinishedX(Scroller scroller) {
        if(scroller == mFlingScrollerX) {
            adjustToNearestItemX();
            mScrollingX = false;
        }
    }

    private void moveToNext() {

        int deltaMoveX = mItemWidth;
        deltaMoveX = getDeltaInBound(deltaMoveX);

        mFlingScrollerX.startScroll(getScrollX(), 0, deltaMoveX, 0);
        invalidate();
    }

    private int getPositionOnScreen(float x) {
        return (int) (x / mItemWidth);
    }

    private void moveToPrev() {

        int deltaMoveX = mItemWidth * -1;
        deltaMoveX = getDeltaInBound(deltaMoveX);

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

    public void setOnItemSelectedListener(OnItemSelected onItemSelected) {
        mOnItemSelected = onItemSelected;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
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
        x = getInBoundsX(x);
        super.scrollTo(x, y);
    }

    public CharSequence[] getValues() {
        return mValues;
    }

    public void setValues(String[] values) {
        mValues = values;
    }

    private void setTextSize(float size) {
        if(size != mSelectorWheelPaint.getTextSize()) {
            mSelectorWheelPaint.setTextSize(size);

            requestLayout();
            invalidate();
        }
    }

    private int getPositionFromCoordinates(int x) {
        return Math.round(x / (mItemWidth * 1f));
    }

    private void scrollToItem(int indexX) {
        scrollTo(mItemWidth * indexX, 0);
        invalidate();
    }

    /**
     * Calculates delta in bounds. {@link com.wefika.horizontalpicker.HorizontalPicker#getInBoundsX(int)}
     * @param deltaX
     * @return
     */
    private int getDeltaInBound(int deltaX) {
        int scrollX = getScrollX();
        return getInBoundsX(scrollX + deltaX) - scrollX;
    }

    /**
     * If x is to big or to small to be in bounds of scroller it calculates max/min value otherwise retirns x
     * @param x
     * @return
     */
    private int getInBoundsX(int x) {

        if(x < 0) {
            x = 0;
        } else if(x > mItemWidth * (mValues.length - 1)) {
            x = mItemWidth * (mValues.length - 1);
        }
        return x;
    }

    public interface OnItemSelected {

        public void onItemSelected(int index);

    }

}
