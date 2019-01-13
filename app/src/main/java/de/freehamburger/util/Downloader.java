package de.freehamburger.util;

import android.Manifest;
import android.os.AsyncTask;
import android.support.annotation.IntRange;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.RestrictTo;
import android.support.annotation.Size;

import java.io.File;

import de.freehamburger.BuildConfig;

/**
 * General-purpose downloader.<br>
 * Loads byte data from one given url.<br>
 * No progress updates.
 */
public abstract class Downloader extends AsyncTask<Downloader.Order, Float, Downloader.Result> {

    /**
     * Constructor.
     */
    Downloader() {
        super();
    }

    /** {@inheritDoc} */
    @Override
    @RequiresPermission(Manifest.permission.INTERNET)
    @NonNull
    protected final Result doInBackground(@Size(1) Downloader.Order... params) {
        if (params == null || params.length == 0) {
            if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), "No resource given!");
            return new Result(null, 500, null, null);
        }
        return load(params[0]);
    }

    @RequiresPermission(Manifest.permission.INTERNET)
    @NonNull
    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
    abstract protected Result load(@NonNull Downloader.Order param);

    /** {@inheritDoc} */
    @MainThread
    @Override
    protected void onCancelled(Result result) {
        if (result.listener != null) {
            result.listener.downloaded(false, result);
            result.listener = null;
        }
    }

    /** {@inheritDoc} */
    @MainThread
    @Override
    protected void onPostExecute(Result result) {
        if (result.listener != null) {
            result.listener.downloaded(true, result);
            result.listener = null;
        }
    }

    /** {@inheritDoc} */
    @MainThread
    @Override
    protected void onProgressUpdate(Float... values) {
        /* no-op */
    }

    /**
     *
     */
    public interface DownloaderListener {

        /**
         * Finally, the download process has come to an end. Look at {@code result} to see what you got.
         * @param completed {@code true} if the process has <u>not</u> been cancelled ({@code true} does not indicate a successful download)
         * @param result    Result (or {@code null} in case of an error)
         */
        @MainThread
        void downloaded(boolean completed, @Nullable Result result);
    }

    /**
     * The result of a download.
     */
    public static class Result {
        /** the uri that supplied the data (not necessarily the uri that the user had requested; there might have been a redirect) */
        final String sourceUri;
        /** the HTTP return code */
        public final int rc;
        /** the HTTP return message */
        @Nullable
        public final String msg;
        @Nullable
        public final File file;
        final String contentType;
        @Nullable
        private DownloaderListener listener;

        public final long contentLength;

        /**
         * Constructor.
         * @param sourceUri the uri that supplied the data (not necessarily the uri that the user had requested; there might have been a redirect)
         * @param rc HTTP status code
         * @param msg HTTP status message
         * @param listener optional DownloaderListener
         */
        Result(String sourceUri, int rc, @Nullable String msg, @Nullable DownloaderListener listener) {
            super();
            this.sourceUri = sourceUri;
            this.rc = rc;
            this.msg = msg;
            this.listener = listener;
            this.file = null;
            this.contentType = null;
            this.contentLength = 0L;
        }

        /**
         * Constructor.
         * @param sourceUri the uri that supplied the data (not necessarily the uri that the user had requested; there might have been a redirect)
         * @param rc HTTP status code
         * @param msg HTTP status message
         * @param file the file
         * @param contentType the content type
         * @param contentLength content length
         * @param listener optional DownloaderListener
         */
        Result(String sourceUri, int rc, @Nullable String msg, @Nullable File file, @Nullable String contentType, long contentLength, @Nullable DownloaderListener listener) {
            super();
            this.sourceUri = sourceUri;
            this.rc = rc;
            this.msg = msg;
            this.file = file;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.listener = listener;
        }

        /** {@inheritDoc} */
        @Override
        @NonNull
        public String toString() {
            return "HTTP " + rc + (msg != null ? (" " + msg) : " for source \"" + sourceUri + "\" stored in \"" + file + "\", contentType = " + contentType);
        }
    }

    /**
     * Wrapper around<ul>
     * <li>URL</li>
     * <li>local path</li>
     * <li>a listener</li>
     * </ul>
     * for a download.
     */
    public static final class Order {
        /** URL to download from (non-null) */
        @NonNull
        final String url;
        /** local path for the data to be stored in (non-null) */
        @NonNull
        final String localPath;
        /** the listener to receive a notification upon completion */
        final DownloaderListener listener;
        /** the timestamp when the data had been updated most recently */
        final long mostRecentUpdate;
        /** if this is {@code true} then a "Transfer-Encoding: identity" request header will be added */
        final boolean preventChunky;

        /**
         * Constructor.
         * @param url       URL to download from (non-null)
         * @param localPath local path for the data to be stored in (non-null)
         * @param mostRecentUpdate timestamp of most recent update of the data (0 if not known)
         * @param preventChunky {@code true} to add a "Transfer-Encoding: identity" request header
         * @param listener  the listener to receive a notification upon completion
         */
        public Order(@NonNull String url, @NonNull String localPath, @IntRange(from = 0) long mostRecentUpdate, boolean preventChunky, DownloaderListener listener) {
            super();
            this.url = url;
            this.localPath = localPath;
            this.mostRecentUpdate = mostRecentUpdate;
            this.preventChunky = preventChunky;
            this.listener = listener;
        }
    }
}
