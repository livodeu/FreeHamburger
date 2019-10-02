package de.freehamburger.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import de.freehamburger.R;

/**
 *
 */
public class Clock extends View {

    private final Handler handler = new Handler();

    private final Drawable hourHand;
    private final Drawable minuteHand;
    private Drawable dial;

    private int dialWidth;
    private int dialHeight;

    private final GregorianCalendar calendar;
    private SlowTimeSetter slowTimeSetter;

    private boolean attached;

    private float minutes;
    private float hour;
    private boolean changed;

    @ColorInt
    private int tint;

    /**
     * Constructor.
     * @param context Context
     */
    public Clock(Context context) {
        this(context, null);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr defStyleAttr
     */
    public Clock(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr defStyleAttr
     * @param defStyleRes defStyleRes
     */
    private Clock(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final Resources r = context.getResources();

        this.dial = r.getDrawable(R.drawable.clock_dial_b);
        this.hourHand = r.getDrawable(R.drawable.clock_hand_hour_d);
        this.minuteHand = r.getDrawable(R.drawable.clock_hand_minute_d);

        this.calendar = new GregorianCalendar();

        this.dialWidth = this.dial.getIntrinsicWidth();
        this.dialHeight = this.dial.getIntrinsicHeight();
    }

    /** {@inheritDoc} */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.attached = true;
        this.calendar.setTimeInMillis(System.currentTimeMillis());
        onTimeChanged();
    }

    /** {@inheritDoc} */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.attached = false;
        if (this.slowTimeSetter != null) this.slowTimeSetter.abort = true;
    }

    /** {@inheritDoc} */
    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        boolean changed = this.changed;
        if (changed) {
            this.changed = false;
        }

        final int availableWidth = getRight() - getLeft();
        final int availableHeight = getBottom() - getTop();

        int x = availableWidth / 2;
        int y = availableHeight / 2;

        final Drawable dial = this.dial;
        int w = dial.getIntrinsicWidth();
        int h = dial.getIntrinsicHeight();

        boolean scaled = false;

        final boolean hasTint = Color.alpha(this.tint) > 0;

        if (availableWidth < w || availableHeight < h) {
            scaled = true;
            float scale = Math.min((float) availableWidth / (float) w, (float) availableHeight / (float) h);
            canvas.save();
            canvas.scale(scale, scale, x, y);
        }

        if (changed) {
            dial.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
        }
        if (hasTint) {
            dial.setColorFilter(this.tint, PorterDuff.Mode.SRC_ATOP);
        } else {
            dial.setColorFilter(null);
        }
        dial.draw(canvas);

        if (this.isEnabled()) {
            canvas.save();
            canvas.rotate(this.hour * 30f, x, y);    // 30f = 1 / 12f * 360f
            final Drawable hourHand = this.hourHand;
            if (changed) {
                w = hourHand.getIntrinsicWidth();
                h = hourHand.getIntrinsicHeight();
                hourHand.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
            }
            if (hasTint) hourHand.setColorFilter(this.tint, PorterDuff.Mode.SRC_ATOP);
            else hourHand.setColorFilter(null);
            ((BitmapDrawable) hourHand).setAntiAlias(true);
            hourHand.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.rotate(this.minutes * 6f, x, y); // 6f = 1 / 60f * 360f

            final Drawable minuteHand = this.minuteHand;
            if (changed) {
                w = minuteHand.getIntrinsicWidth();
                h = minuteHand.getIntrinsicHeight();
                minuteHand.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
            }
            if (hasTint) minuteHand.setColorFilter(this.tint, PorterDuff.Mode.SRC_ATOP);
            else minuteHand.setColorFilter(null);
            ((BitmapDrawable) minuteHand).setAntiAlias(true);
            minuteHand.draw(canvas);
            canvas.restore();
        }

        if (scaled) {
            canvas.restore();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        float hScale = 1.0f;
        float vScale = 1.0f;

        if (widthMode != MeasureSpec.UNSPECIFIED && widthSize < this.dialWidth) {
            hScale = (float) widthSize / (float) this.dialWidth;
        }

        if (heightMode != MeasureSpec.UNSPECIFIED && heightSize < this.dialHeight) {
            vScale = (float) heightSize / (float) this.dialHeight;
        }

        float scale = Math.min(hScale, vScale);

        setMeasuredDimension(resolveSizeAndState((int) (this.dialWidth * scale), widthMeasureSpec, 0), resolveSizeAndState((int) (this.dialHeight * scale), heightMeasureSpec, 0));
    }

    /** {@inheritDoc} */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.changed = true;
    }

    private void onTimeChanged() {
        int hour = this.calendar.get(Calendar.HOUR_OF_DAY);
        int minute = this.calendar.get(Calendar.MINUTE);
        int second = this.calendar.get(Calendar.SECOND);

        this.minutes = minute + second / 60.0f;
        this.hour = hour + this.minutes / 60.0f;
        this.changed = true;

        updateContentDescription();
    }

    public void setDial(@DrawableRes int id) {
        this.dial = getContext().getDrawable(id);
        if (this.dial == null) return;
        this.dialWidth = this.dial.getIntrinsicWidth();
        this.dialHeight = this.dial.getIntrinsicHeight();
        this.changed = true;
        invalidate();
    }

    private void setTime(Calendar target) {
        if (target == null) return;
        if (this.slowTimeSetter != null) {
            this.slowTimeSetter.abort = true;
            this.slowTimeSetter = null;
        }
        setTimeInternal(target);
    }

    private void setTimeInternal(@NonNull Calendar target) {
        this.calendar.setTimeInMillis(target.getTimeInMillis());
        onTimeChanged();
        invalidate();
    }

    @MainThread
    public void setTimeSlowly(Calendar target) {
        if (target == null) return;
        Calendar now = new GregorianCalendar();
        int year = this.calendar.get(Calendar.YEAR);
        if (year < now.get(Calendar.YEAR)) {
            setTime(target);
        } else {
            if (this.slowTimeSetter != null) {
                this.slowTimeSetter.abort = true;
                this.handler.removeCallbacks(this.slowTimeSetter);
            }
            this.slowTimeSetter = new SlowTimeSetter(target);
            this.handler.post(this.slowTimeSetter);
        }
    }

    /**
     * Sets the color used to dye the clock face and hands.
     * @param tint color
     */
    public void setTint(@ColorInt int tint) {
        boolean modified = tint != this.tint;
        this.tint = tint;
        if (modified) invalidate();
    }

    private void updateContentDescription() {
        String contentDescription = DateFormat.getDateInstance(DateFormat.SHORT).format(new java.util.Date(this.calendar.getTimeInMillis()));
        setContentDescription(contentDescription);
    }

    /**
     *
     */
    private class SlowTimeSetter implements Runnable {

        private final boolean down;
        private final Calendar target;
        private final long stepsize;
        private boolean abort;

        /**
         * Constructor.
         * @param target target time
         */
        private SlowTimeSetter(Calendar target) {
            super();
            this.target = new GregorianCalendar();
            this.target.set(Calendar.HOUR_OF_DAY, target.get(Calendar.HOUR_OF_DAY));
            this.target.set(Calendar.MINUTE, target.get(Calendar.MINUTE));
            this.target.set(Calendar.SECOND, target.get(Calendar.SECOND));
            long diffMs = (target.getTimeInMillis() - Clock.this.calendar.getTimeInMillis());
            this.down = diffMs < 0L;
            this.stepsize = this.down ? Math.min(-300_000L, diffMs / 30L) : Math.max(300_000L, diffMs / 30L);
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            if (this.abort || !Clock.this.attached) return;
            Calendar step = new GregorianCalendar();
            step.setTimeInMillis(Clock.this.calendar.getTimeInMillis() + this.stepsize);
            if (this.down) {
                if (step.before(this.target)) {
                    step = this.target;
                }
            } else {
                if (step.after(this.target)) {
                    step = this.target;
                }
            }
            setTimeInternal(step);
            boolean moreNeeded = this.down ? step.after(this.target) : step.before(this.target);
            if (moreNeeded && !this.abort) {
                Clock.this.handler.postDelayed(this, 200L);
            }
        }
    }

}
