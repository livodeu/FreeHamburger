package de.freehamburger.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.TestOnly;

import java.util.Calendar;
import java.util.GregorianCalendar;

import de.freehamburger.R;

/**
 *
 */
public class ClockView extends RelativeLayout {

    private Clock clock;
    private TextView textView;

    /**
     * Constructor.
     * @param context Context
     */
    public ClockView(Context context) {
        super(context);
        init(context);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public ClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr defStyleAttr
     */
    public ClockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public CharSequence getTooltipText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return this.clock.getTooltipText();
        }
        return "";
    }

    @TestOnly
    public boolean hasOnTextClickListener() {
        return this.textView.hasOnClickListeners();
    }

    /**
     * Initialisation.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @SuppressLint("ClickableViewAccessibility")
    private void init(@NonNull Context ctx) {
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return;
        inflater.inflate(R.layout.clock_view, this);
        this.clock = findViewById(R.id.clock);
        this.textView = findViewById(R.id.textView);
        setTime(System.currentTimeMillis());
        this.clock.setOnTouchListener((v, event) -> {
            int a = event.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                setBackground(R.drawable.clock_dial_b_flat);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                setBackground(R.drawable.clock_dial_b);
            }
            return false;
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean performClick() {
        super.performClick();
        return this.clock.performClick();
    }

    /** {@inheritDoc} */
    @Override
    public boolean performLongClick() {
        return this.clock.performLongClick();
    }

    private void setBackground(@DrawableRes int id) {
        this.clock.setDial(id);
    }

    /**
     * Passes the enabled state on to the {@link Clock clock}.<br>
     * This ClockView will always remain enabled!
     * @param enabled true / false
     */
    @Override
    public void setEnabled(boolean enabled) {
        this.clock.setEnabled(enabled);
    }

    /** {@inheritDoc} */
    @Override
    public void setOnClickListener(@Nullable View.OnClickListener l) {
        this.clock.setOnClickListener(l);
    }

    /**
     * Adds a {@link View.OnClickListener} to the text area.
     * @param l OnClickListener
     */
    public void setOnTextClickListener(@Nullable View.OnClickListener l) {
        this.textView.setOnClickListener(l);
    }

    public void setText(CharSequence text) {
        this.textView.setText(text);
    }

    /**
     * Sets the time.
     * @param timestamp milliseconds
     */
    public void setTime(long timestamp) {
        if (timestamp == 0L) return;
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(timestamp);
        this.clock.setTimeSlowly(calendar);
    }

    /**
     * Sets the color used to dye the clock face and hands.<br>
     * See {@link Clock#setTint(int)}.
     * @param tint color
     */
    public void setTint(@ColorInt int tint) {
        this.clock.setTint(tint);
    }

    /** {@inheritDoc} */
    @Override
    public void setTooltipText(@Nullable CharSequence tooltipText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.clock.setTooltipText(tooltipText);
        }
    }
}
