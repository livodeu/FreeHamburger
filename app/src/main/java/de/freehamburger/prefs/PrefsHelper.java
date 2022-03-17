package de.freehamburger.prefs;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;

public final class PrefsHelper {

    /**
     * Returns a preference that is stored as a String but represents an int as such.
     * @param prefs SharedPreferences
     * @param key preference key
     * @param defaultValue default int value
     * @return int value
     */
    public static int getStringAsInt(@NonNull SharedPreferences prefs, @NonNull final String key, final int defaultValue) {
        try {
            String stringValue = prefs.getString(key, String.valueOf(defaultValue));
            return stringValue != null ? Integer.parseInt(stringValue) : defaultValue;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(PrefsHelper.class.getSimpleName(), "While getting " + key + ": " + e);
        }
        return defaultValue;
    }

    private PrefsHelper() {
    }
}
