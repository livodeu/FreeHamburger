package de.freehamburger.model;

import android.support.annotation.Nullable;

/**
 *
 */
public interface Filter extends Comparable<Filter> {

    boolean accept(@Nullable final News news);

    CharSequence getText();

    boolean isEditable();

    boolean isTemporary();
}
