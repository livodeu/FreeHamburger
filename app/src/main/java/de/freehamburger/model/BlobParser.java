package de.freehamburger.model;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;
import android.support.annotation.VisibleForTesting;
import android.util.JsonReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * AsyncTask implementation that parses a json file.
 */
public class BlobParser extends AsyncTask<File, Float, Blob> {

    @NonNull private final Reference<Context> refctx;
    @Nullable private BlobParserListener listener;
    @VisibleForTesting
    public Exception thrown;

    /**
     * Constructor.
     * @param ctx Context
     * @param listener BlobParserListener
     */
    public BlobParser(@NonNull Context ctx, @Nullable BlobParserListener listener) {
        super();
        this.refctx = new SoftReference<>(ctx);
        this.listener = listener;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public Blob doInBackground(@Size(1) File[] files) {
        Context ctx = this.refctx.get();
        if (ctx == null) return null;
        Blob blob = null;
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(files[0])), StandardCharsets.UTF_8));
            reader.setLenient(true);
            blob = Blob.parseApi(ctx, reader);
        } catch (Exception e) {
            this.thrown = e;
            if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), e.toString(), e);
        } finally {
            Util.close(reader);
        }
        return blob;
    }

    /** {@inheritDoc} */
    @Override
    @MainThread
    protected void onCancelled() {
        if (this.listener != null) {
            this.listener.blobParsed(null, false, this.thrown);
            this.listener = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    @MainThread
    protected void onPostExecute(@Nullable Blob blob) {
        if (this.listener != null) {
            this.listener.blobParsed(blob, blob != null && this.thrown == null, this.thrown);
            this.listener = null;
        }
    }

    /**
     * Implemented by classes that wish to convert json data into Blobs.
     */
    public interface BlobParserListener {

        /**
         * The json data has been parsed and a {@link Blob} might have been generated.
         * @param blob Blob
         * @param ok {@code true} / {@code false}
         * @param oops this could be an Exception that was thrown while parsing
         */
        void blobParsed(@Nullable Blob blob, boolean ok, @Nullable Throwable oops);
    }
}
