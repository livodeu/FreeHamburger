package de.freehamburger.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.R;

/**
 *
 */
public enum Source {

    HOME(R.string.action_section_homepage, App.URL_PREFIX + "homepage/", R.drawable.ic_home_black_24dp, false),
    NEWS(R.string.action_section_news, App.URL_PREFIX + "news/", R.drawable.ic_local_library_black_24dp, false, "action_section_news"),
    INLAND(R.string.action_section_inland, App.URL_PREFIX + "news/?ressort=inland", R.drawable.ic_germany, false, "action_section_inland"),
    AUSLAND(R.string.action_section_ausland, App.URL_PREFIX + "news/?ressort=ausland", R.drawable.ic_language_black_24dp, false, "action_section_ausland"),
    SPORT(R.string.action_section_sport, App.URL_PREFIX + "news/?ressort=sport", R.drawable.ic_directions_run_black_24dp, false, "action_section_sport"),
    VIDEO(R.string.action_section_video, App.URL_PREFIX + "news/?ressort=video", R.drawable.ic_videocam_black_24dp, false, "action_section_video"),
    WIRTSCHAFT(R.string.action_section_wirtschaft, App.URL_PREFIX + "news/?ressort=wirtschaft", R.drawable.ic_build_black_24dp, false, "action_section_wirtschaft"),
    CHANNELS(R.string.action_section_channels, App.URL_PREFIX + "channels/", R.drawable.ic_tv_black_24dp, false, "action_section_sendungen"),
    /** params look like "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16" */
    REGIONAL(R.string.action_section_regional, App.URL_PREFIX + "news/?regions=", R.drawable.ic_place_black_24dp, true, "action_section_regional")
    ;

    public static final String FILE_SUFFIX = ".source";

    private final Object lock = new Object();
    @StringRes
    private final int label;
    private final String url;
    private final boolean needsParams;
    @DrawableRes
    private final int icon;
    @Nullable private final String action;
    /** synchronises access from different threads */
    private volatile Reference<Thread> lockHolder;

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
     * Attempts to find the Source that the given action String belongs to.
     * @param action action
     * @return Source
     */
    @Nullable
    public static Source getSourceFromAction(final String action) {
        if (action == null) return null;
        for (Source source : values()) {
            if (action.equals(source.getAction())) return source;
        }
        return null;
    }

    /**
     * Returns the Source that a given local file belongs to.<br>
     * The File object has been created by {@link App#getLocalFile(Source)}.
     * @param file File
     * @return Source
     */
    @Nullable
    public static Source getSourceFromFile(File file) {
        if (file == null) return null;
        String name = file.getName();
        if (!name.endsWith(FILE_SUFFIX)) return null;
        try {
            return valueOf(name.substring(0, name.length() - FILE_SUFFIX.length()));
        } catch (Exception e) {
            if (BuildConfig.DEBUG) de.freehamburger.util.Log.e(Source.class.getSimpleName(), e.toString());
        }
        return null;
    }

    /**
     * Constructor.
     * @param label string resource id
     * @param url url
     * @param icon icon resource
     */
    Source(@StringRes int label, String url, @DrawableRes int icon) {
        this(label, url, icon, false);
    }

    /**
     * Constructor.
     * @param label string resource id
     * @param url url
     * @param icon icon resource
     * @param needsParams {@code true} if this Source needs additional parameters
     */
    Source(@StringRes int label, String url, @DrawableRes int icon, boolean needsParams) {
        this(label, url, icon, needsParams, null);
    }

    /**
     * Constructor.
     * @param label string resource id
     * @param url url
     * @param icon icon resource
     * @param needsParams {@code true} if this Source needs additional parameters
     * @param action action string for an Intent to display this Source
     */
    Source(@StringRes int label, String url, @DrawableRes int icon, boolean needsParams, @Nullable String action) {
        this.label = label;
        this.url = url;
        this.icon = icon;
        this.needsParams = needsParams;
        this.action = action;
        if (icon == 0) throw new java.lang.AssertionError("No source icon");
    }

    @Nullable
    public String getAction() {
        return action;
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

    /**
     * Returns the locked state of this Source.
     * @return {@code true} / {@code false}
     */
    public boolean isLocked() {
        boolean locked;
        synchronized (lock) {
            locked = lockHolder != null && lockHolder.get() != null;
        }
        return locked;
    }

    public boolean needsParams() {
        return needsParams;
    }

    /**
     * Locks or unlocks this Source.<br>
     * Used to synchronise access as parsing is (and should be) done on a different thread.<br>
     * See invocation of {@link android.os.AsyncTask#executeOnExecutor(Executor, Object[]) BlobParser}.
     * @param locked {@code true} / {@code false}
     */
    public void setLocked(boolean locked) {
        synchronized (lock) {
            this.lockHolder = locked ? new WeakReference<>(Thread.currentThread()) : null;
        }
    }
}
