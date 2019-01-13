package de.freehamburger.supp;

import android.content.SearchRecentSuggestionsProvider;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class SearchSuggestionsProvider extends SearchRecentSuggestionsProvider {
    public final static String AUTHORITY = BuildConfig.APPLICATION_ID + ".supp.SearchSuggestionsProvider";
    public final static int MODE = DATABASE_MODE_QUERIES;

    public SearchSuggestionsProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }

}
