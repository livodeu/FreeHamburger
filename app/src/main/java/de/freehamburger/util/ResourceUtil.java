package de.freehamburger.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.AnyRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.StringRes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.freehamburger.BuildConfig;

/**
 * Collects {@link Resources}-related utility methods.
 */
public final class ResourceUtil {

    private static final String TAG = "ResourceUtil";

    private ResourceUtil() {
    }

    /**
     * Translates a color resource to a color value. Wraps getters for pre and post-Marshmallow.
     * @param ctx ContextWrapper
     * @param colorRes color resource
     * @return color value
     * @throws NullPointerException if {@code ctx} is {@code null}
     * @throws Resources.NotFoundException if the given color resource does not exist.
     */
    @ColorInt
    public static int getColor(@NonNull Context ctx, @ColorRes int colorRes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ctx.getResources().getColor(colorRes, ctx.getTheme());
        }
        //noinspection deprecation
        return ctx.getResources().getColor(colorRes);
    }

    /**
     * Returns a String from the resources while suppressing RuntimeExceptions, particularly {@link Resources.NotFoundException}s.
     * @param ctx Context
     * @param resid string resource id
     * @param defaultValue default value to return if resid was not found
     * @param formatArgs optional format arguments
     * @return String or null
     */
    @Nullable
    public static String getString(@NonNull Context ctx, @StringRes int resid, @Nullable String defaultValue, Object... formatArgs) {
        String s;
        try {
            if (formatArgs != null) s = ctx.getResources().getString(resid, formatArgs);
            else s = ctx.getResources().getString(resid);
        } catch (RuntimeException e) {
            s = defaultValue;
        }
        return s;
    }

    public static boolean isInvalidResource(@AnyRes int resid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resid == Resources.ID_NULL;
        }
        return resid == 0;
    }

    /**
     * Loads a line-based text file from the resources.<br>
     * Lines that start with # are considered to be a comment.
     * @param ctx Context
     * @param rawId raw resource id
     * @param expextedNumberOfLines expected number of lines to be read
     * @param trim if {@code true}, trim lines
     * @return List of Strings, one for each line
     * @throws IllegalArgumentException if {@code expectedNumberOfLines} is negative
     */
    @NonNull
    public static List<String> loadResourceTextFile(@NonNull Context ctx, @RawRes int rawId, @IntRange(from = 0) int expextedNumberOfLines, final boolean trim) {
        final List<String> lines = new ArrayList<>(expextedNumberOfLines);
        InputStream in = null;
        try {
            in = ctx.getResources().openRawResource(rawId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            for (; ; ) {
                String line = reader.readLine();
                if (line == null) break;
                if (trim) line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "loadResourceTextFile(..., " + rawId + ", ...): " + e);
        } finally {
            Util.close(in);
        }
        return lines;
    }
}
