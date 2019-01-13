package de.freehamburger.model;

import android.support.annotation.Nullable;

/**
 *
 */
public interface Filter extends Comparable<Filter> {

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean accept(@Nullable final News news);

    CharSequence getText();

    @SuppressWarnings("SameReturnValue")
    boolean isEditable();

    boolean isTemporary();
}
