package de.freehamburger.util;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.freehamburger.BuildConfig;

/**
 * Empty class for release versions.
 */
public class Log {

    public static final Object FILE_LOCK = new Object();

    public static boolean deleteFile() {
        return false;
    }

    public static void e(@NonNull String tag, String msg) {
    }

    public static void e(@NonNull String tag, String msg, Throwable t) {
    }

    /**
     * @return the log file
     */
    @Nullable
    public static File getFile() {
        return null;
    }

    public static void i(@NonNull String tag, String msg) {
    }


    /**
     * Writes pending log messages to the log file.
     */
    public static void sync() {
    }

    private static void truncate() {
    }

    public static void w(@NonNull String tag, String msg) {
    }

    public static void w(@NonNull String tag, String msg, Throwable t) {
    }

    public static void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable t, int depth) {
    }


    public static void wtf(@NonNull String tag, String msg, Throwable t) {
    }

}
