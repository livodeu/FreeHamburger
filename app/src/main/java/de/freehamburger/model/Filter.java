package de.freehamburger.model;

import androidx.annotation.Nullable;

/**
 *
 */
public interface Filter extends Comparable<Filter> {

    /**
     * Checks whether at least one out of a Collection of Filters does not accept a News.
     * @param filters Filters to check
     * @param news News to check
     * @return {@code true} if at least one of the Filters does <em>NOT</em> accept the News, {@code false} if all given Filters accept the News
     */
    static boolean refusedByAny(@Nullable final java.util.Collection<Filter> filters, final News news) {
        if (filters == null) return false;
        for (Filter filter : filters) {
            if (!filter.accept(news)) return true;
        }
        return false;
    }

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
