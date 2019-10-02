package de.freehamburger.model;

import androidx.annotation.Nullable;

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
