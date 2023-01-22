package de.freehamburger.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

/**
 * A {@link NewsView2} variant that contains neither {@link NewsView2#textViewFirstSentence textViewFirstSentence} nor {@link NewsView2#textViewTitle textViewTitle}.
 */

public class NewsViewNoContentNoTitle2 extends NewsView2 {

    /**
     * Constructor.
     * @param ha Context
     */
    public NewsViewNoContentNoTitle2(Context ha) {
        super(ha);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs   AttributeSet
     */
    public NewsViewNoContentNoTitle2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Constructor.
     * @param context      Context
     * @param attrs        AttributeSet
     * @param defStyleAttr int
     */
    public NewsViewNoContentNoTitle2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return getClass().getSimpleName();
    }
}
