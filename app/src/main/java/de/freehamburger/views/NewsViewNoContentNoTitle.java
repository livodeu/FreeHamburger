package de.freehamburger.views;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;

import de.freehamburger.R;

/**
 * A {@link NewsView} variant that does not contain {@link NewsView#textViewFirstSentence}.
 */

public class NewsViewNoContentNoTitle extends NewsView {

    /**
     * Constructor.
     * @param ha Context
     */
    public NewsViewNoContentNoTitle(Context ha) {
        super(ha);
    }

    /**
     * @return layout resource id
     */
    @LayoutRes
    @Override
    int getLid() {
        return R.layout.news_view_nocontent_notitle;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return getClass().getSimpleName();
    }
}
