package de.freehamburger.model;

import androidx.annotation.Nullable;

/**
 *
 */
public interface Filter extends Comparable<Filter> {

    /**
     * Returns {@code true} if the News object should be included, {@code false} if it should be omitted.
     * @param news News
     * @return true / false
     */
    boolean accept(@Nullable final News news);

    CharSequence getText();

    @SuppressWarnings("SameReturnValue")
    boolean isEditable();

    boolean isTemporary();
}
