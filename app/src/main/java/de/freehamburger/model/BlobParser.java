package de.freehamburger.model;

import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.AnyThread;
import androidx.annotation.FloatRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import android.util.JsonReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
    @VisibleForTesting
    public Exception thrown;
    @Nullable private BlobParserListener listener;

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
        if (files == null) return null;
        Context ctx = this.refctx.get();
        if (ctx == null) return null;
        Source src = Source.getSourceFromFile(files[0]);
        if (src == null) return null;
        if (src.isLocked()) {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) {
                return null;
            }
            if (src.isLocked()) {
                if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), "Cannot parse " + files[0] + " because " + src + " is locked!");
                return null;
            }
        }
        src.setLocked(true);
        Blob blob = null;
        JsonReader reader = null;
        ProgressReporter reporter = null;
        try {
            CountingFileInputStream in = new CountingFileInputStream(files[0]);
            reader = new JsonReader(new InputStreamReader(new BufferedInputStream(in), StandardCharsets.UTF_8));
            reader.setLenient(true);
            reporter = new ProgressReporter(files[0].length(), in);
            reporter.start();
            blob = Blob.parseApi(ctx, reader);
            reporter.stop = true;
        } catch (Exception e) {
            if (reporter != null) reporter.stop = true;
            this.thrown = e;
            if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), e.toString(), e);
        } finally {
            Util.close(reader);
        }
        src.setLocked(false);
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

    /** {@inheritDoc} */
    @Override
    protected void onProgressUpdate(Float... values) {
        if (this.listener == null) return;
        this.listener.parsingProgressed(values[0]);
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

        /**
         * The parsing progress has changed.
         * @param progress [0..1]
         */
        void parsingProgressed(@FloatRange(from = 0, to = 1) float progress);
    }

    /**
     * A FileInputStream that keeps track of the number of bytes read.
     */
    private static class CountingFileInputStream extends FileInputStream {

        private final Object sync = new Object();
        private long read;

        private CountingFileInputStream(@NonNull File file) throws FileNotFoundException {
            super(file);
        }

        @AnyThread
        private long getRead() {
            long r;
            synchronized (this.sync) {
                r = this.read;
            }
            return r;
        }

        @Override
        public int read(@NonNull byte[] b) throws IOException {
            int r = super.read(b);
            if (r != -1) {
                synchronized (this.sync) {
                    this.read += r;
                }
            }
            return r;
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) throws IOException {
            int r = super.read(b, off, len);
            if (r != -1) {
                synchronized (this.sync) {
                    this.read += r;
                }
            }
            return r;
        }

        @Override
        public int read() throws IOException {
            int r = super.read();
            if (r != -1) {
                synchronized (this.sync) {
                    this.read++;
                }
            }
            return r;
        }
    }

    /**
     * Calls {@link AsyncTask#publishProgress(Object[])} periodically.
     */
    private class ProgressReporter extends Thread {

        private final long length;
        @NonNull private final CountingFileInputStream in;
        private volatile boolean stop;

        /**
         * Constructor.
         * @param length file size
         * @param in input stream
         */
        private ProgressReporter(long length, @NonNull CountingFileInputStream in) {
            super();
            this.length = length;
            this.in = in;
            setPriority(Thread.NORM_PRIORITY - 1);
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            if (this.length > 0L) {
                try {
                    for (long latestRead = 0L; !this.stop; ) {
                        long read = this.in.getRead();
                        if (read >= this.length) break;
                        if (read > latestRead) {
                            float progress = (float) read / (float) this.length;
                            latestRead = read;
                            publishProgress(progress);
                        }
                        Thread.sleep(100L);
                    }
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), e.toString());
                }
            }
            publishProgress(1f);
        }
    }
}
