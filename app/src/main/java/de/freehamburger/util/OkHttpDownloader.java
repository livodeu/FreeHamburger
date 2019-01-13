package de.freehamburger.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.R;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * This class can be used by Picasso as well as by other classes.<br>
 * Picasso uses the methods defined by {@link com.squareup.picasso.Downloader}.<br>
 * Other classes use {@link #load(Order)}.<br>
 * As true for every {@link android.os.AsyncTask}, an instance can only be used once!
 */
public class OkHttpDownloader extends Downloader implements com.squareup.picasso.Downloader {

    private static final String TAG = "OkHttpDownloader";
    /** read buffer size; during tests, the maximum number of bytes read was 7786, even with READ_BUFFER set to 16834 */
    private static final int READ_BUFFER = 8192;

    /**
     * HTTP Date format<br>
     * e.g.: If-Modified-Since: Sat, 29 Oct 1994 19:43:31 GMT<br>
     * See <a href="https://tools.ietf.org/html/rfc2616#section-14.25">here</a>
     */
    @SuppressLint("SimpleDateFormat")
    private static final DateFormat DF = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
    static {
        DF.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
    }

    /** "has not been found" */
    private final String errorUnknownHost;
    private OkHttpClient client;

    /**
     * Constructor.
     * @param ctx Context
     */
    public OkHttpDownloader(@NonNull Context ctx) {
        super();
        this.client = ((App)ctx.getApplicationContext()).getOkHttpClient();
        this.errorUnknownHost = ctx.getString(R.string.error_unknown_host);
    }

    /**
     * {@inheritDoc}
     * The original app V2.3.3 sends these requests:<br>
     * Upon app start:
     * <pre>
     *     GET /api2/homepage/ HTTP/1.1
     *     Cache-Control: max-age=0
     *     User-Agent: Tagesschau/de.tagesschau (2018080901, Android: 6.0.1)
     *     Host: www.tagesschau.de
     *     Connection: Keep-Alive
     *     Accept-Encoding: gzip
     *     If-Modified-Since: Sat, 14 Oct 1066 09:00:00 GMT
     * </pre>
     * or, when the user used swipe refresh:
     * <pre>
     *     GET /api2/homepage/ HTTP/1.1
     *     Cache-Control: no-cache
     *     User-Agent: Tagesschau/de.tagesschau (2018080901, Android: 6.0.1)
     *     Host: www.tagesschau.de
     *     Connection: Keep-Alive
     *     Accept-Encoding: gzip
     * </pre>
     */
    @NonNull
    protected Result load(@NonNull Order order) {
        File f = new File(order.localPath);
        Request.Builder requestBuilder = new Request.Builder()
                .url(order.url)
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("User-Agent", App.USER_AGENT)
                // don't let okio do the decoding transparently; instead add the Accept-Encoding header here and receive a Content-Length response header
                .addHeader("Accept-Encoding", "gzip")
                // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.41
                // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6
        ;
        if (order.preventChunky) {
            requestBuilder.addHeader("Transfer-Encoding", "identity");
        }

        if (f.length() > 0L) {
            // don't use lastModified() for .source files which indicates the newest article in that file; instead use App.getMostRecentUpdate(Source)
            String ifModifiedSince;
            if (order.mostRecentUpdate > 0L) {
                ifModifiedSince = DF.format(new Date(order.mostRecentUpdate));
            } else {
                ifModifiedSince = DF.format(new Date(f.lastModified()));
            }
            requestBuilder.addHeader("If-Modified-Since", ifModifiedSince);
        }
        //
        Request request = requestBuilder.build();
        Response response;
        ResponseBody body = null;
        OutputStream out = null;
        try {
            response = this.client.newCall(request).execute();
            body = response.body();
            if (!response.isSuccessful() || body == null) {
                if (body != null) body.close();
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to load from " + order.url + ": HTTP " + response.code() + " " + response.message());
                return new Result(order.url, response.code(), response.message(), f, null, 0L, order.listener);
            }
            MediaType mediaType = body.contentType();
            long contentLength = body.contentLength();
            if (BuildConfig.DEBUG && contentLength <= 0L) {
                Log.e(TAG, "No Content-Length after request with headers:\n" + request.headers().toString());
            }
            out = new BufferedOutputStream(new FileOutputStream(f));
            final InputStream in;
            String contentEncoding = response.header("Content-Encoding");
            if ("gzip".equals(contentEncoding)) {
                in = new GZIPInputStream(body.byteStream());
            } else {
                in = body.byteStream();
            }

            for (byte[] buffer = new byte[READ_BUFFER]; ; ) {
                int read = in.read(buffer);
                if (read <= 0) break;
                out.write(buffer, 0, read);
            }
            Util.close(out, body);
            return new Result(order.url, response.code(), null, f, mediaType != null ? mediaType.toString() : null, contentLength, order.listener);
        } catch (UnknownHostException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            Util.close(body, out);
            Util.deleteFile(f);
            int doubleSlash = order.url.indexOf("//");
            int singleSlash = order.url.indexOf('/', doubleSlash + 2);
            String msg;
            if (doubleSlash > 0 && singleSlash > doubleSlash) {
                msg = order.url.substring(doubleSlash + 2, singleSlash) + " " + this.errorUnknownHost;
            } else {
                msg = order.url + " " + this.errorUnknownHost;
            }
            return new Result(order.url, HttpURLConnection.HTTP_BAD_REQUEST, msg, f, null, 0L, order.listener);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            Util.close(body, out);
            Util.deleteFile(f);
            String msg = e.getMessage();
            if (TextUtils.isEmpty(msg)) msg = e.toString();
            return new Result(order.url, 500, msg, f, null, 0L, order.listener);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Response load(@NonNull okhttp3.Request request) throws IOException {
        return this.client.newCall(request).execute();
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        this.client = null;
    }

}
