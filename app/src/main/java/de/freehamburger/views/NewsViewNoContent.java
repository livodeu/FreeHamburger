package de.freehamburger.views;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;

import de.freehamburger.R;

/**
 * A {@link NewsView} variant that does not contain {@link NewsView#textViewFirstSentence}.
 */

public class NewsViewNoContent extends NewsView {

    /**
     * Constructor.
     * @param ha Context
     */
    public NewsViewNoContent(Context ha) {
        super(ha);
    }

    /**
     * @return layout resource id
     */
    @LayoutRes
    @Override
    int getLid() {
        return R.layout.news_view_nocontent;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return getClass().getSimpleName() + " \"" + (textViewTitle != null ? textViewTitle.getText().toString() : "<null>") + "\"";
    }
}
