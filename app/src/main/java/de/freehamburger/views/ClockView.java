package de.freehamburger.views;

import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

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

    /**
     * Initialisation.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    private void init(@NonNull Context ctx) {
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return;
        inflater.inflate(R.layout.clock_view, this);
        this.clock = findViewById(R.id.clock);
        this.textView = findViewById(R.id.textView);
        setTime(System.currentTimeMillis());
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
}
