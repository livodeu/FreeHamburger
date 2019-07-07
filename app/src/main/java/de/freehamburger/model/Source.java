package de.freehamburger.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
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

    HOME(R.string.action_section_homepage, App.URL_PREFIX + "homepage/", R.drawable.ic_home_black_24dp),
    NEWS(R.string.action_section_news, App.URL_PREFIX + "news/", R.drawable.ic_local_library_black_24dp),
    INLAND(R.string.action_section_inland, App.URL_PREFIX + "news/?ressort=inland", R.drawable.ic_germany),
    AUSLAND(R.string.action_section_ausland, App.URL_PREFIX + "news/?ressort=ausland", R.drawable.ic_language_black_24dp),
    SPORT(R.string.action_section_sport, App.URL_PREFIX + "news/?ressort=sport", R.drawable.ic_directions_run_black_24dp),
    VIDEO(R.string.action_section_video, App.URL_PREFIX + "news/?ressort=video", R.drawable.ic_videocam_black_24dp),
    WIRTSCHAFT(R.string.action_section_wirtschaft, App.URL_PREFIX + "news/?ressort=wirtschaft", R.drawable.ic_build_black_24dp),
    CHANNELS(R.string.action_section_channels, App.URL_PREFIX + "channels/", R.drawable.ic_tv_black_24dp),
    /** params look like "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" */
    REGIONAL(R.string.action_section_regional, App.URL_PREFIX + "news/?regions=", R.drawable.ic_place_black_24dp, true)
    ;

    @StringRes
    private final int label;
    private final String url;
    private final boolean needsParams;
    @DrawableRes
    private final int icon;

    /**
     * Returns the parameter char sequence for {@link #REGIONAL}, based on the user's preferences.
     * @param ctx Context
     * @return CharSequence of region ids, separated by commas
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static CharSequence getParamsForRegional(@NonNull Context ctx) {
        final Set<String> regionIds = PreferenceManager.getDefaultSharedPreferences(ctx).getStringSet(App.PREF_REGIONS, new HashSet<>(0));
        final StringBuilder params = new StringBuilder(regionIds.size() * 3);
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

    /**
     * Constructor.
     * @param label string resource id
     * @param url url
     */
    Source(@StringRes int label, String url, @DrawableRes int icon) {
        this(label, url, icon, false);
    }

    /**
     * Constructor.
     * @param label string resource id
     * @param url url
     * @param needsParams {@code true} if this Source needs additional parameters
     */
    Source(@StringRes int label, String url, @DrawableRes int icon, boolean needsParams) {
        this.label = label;
        this.url = url;
        this.icon = icon;
        this.needsParams = needsParams;
        if (icon == 0) throw new java.lang.AssertionError("No source icon");
    }

    /**
     * @return icon for this Source
     */
    @DrawableRes
    public int getIcon() {
        return icon;
    }

    /**
     * @return string resource id of the label
     */
    @StringRes
    public int getLabel() {
        return label;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    public boolean needsParams() {
        return needsParams;
    }
}
