package de.freehamburger.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import java.util.HashSet;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.R;

/**
 *
 */
public enum Source {

    HOME(R.string.action_section_homepage, App.URL_PREFIX + "homepage/"),
    NEWS(R.string.action_section_news, App.URL_PREFIX + "news/"),
    INLAND(R.string.action_section_inland, App.URL_PREFIX + "news/?ressort=inland"),
    AUSLAND(R.string.action_section_ausland, App.URL_PREFIX + "news/?ressort=ausland"),
    SPORT(R.string.action_section_sport, App.URL_PREFIX + "news/?ressort=sport"),
    VIDEO(R.string.action_section_video, App.URL_PREFIX + "news/?ressort=video"),
    WIRTSCHAFT(R.string.action_section_wirtschaft, App.URL_PREFIX + "news/?ressort=wirtschaft"),
    CHANNELS(R.string.action_section_channels, App.URL_PREFIX + "channels/"),
    /** params look like "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" */
    REGIONAL(R.string.action_section_regional, App.URL_PREFIX + "news/?regions=", true)
    ;

    @StringRes
    private final int label;
    private final String url;
    private final boolean needsParams;

    /**
     * Returns the parameter char sequence for {@link #REGIONAL}, based on the user's preferences.
     * @param ctx Context
     * @return CharSequence of region ids, separated by commas
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static CharSequence getParamsForRegional(@NonNull Context ctx) {
        final StringBuilder params = new StringBuilder();
        final Set<String> regionIds = PreferenceManager.getDefaultSharedPreferences(ctx).getStringSet(App.PREF_REGIONS, new HashSet<>(0));
        // "0" is not valid (leads to HTTP 400 Bad Request)
        String idUnknown = String.valueOf(Region.UNKNOWN.getId());
        //
        for (String regionId : regionIds) {
            if (idUnknown.equals(regionId)) continue;
            params.append(regionId).append(',');
        }
        int nc = params.length();
        if (nc > 0) {
            params.deleteCharAt(nc - 1);
            return params;
        }
        return "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16";
    }

    Source(@StringRes int label, String url) {
        this(label, url, false);
    }

    Source(@StringRes int label, String url, boolean needsParams) {
        this.label = label;
        this.url = url;
        this.needsParams = needsParams;
    }

    @StringRes
    public int getLabel() {
        return label;
    }

    public String getUrl() {
        return url;
    }

    public boolean needsParams() {
        return needsParams;
    }
}
