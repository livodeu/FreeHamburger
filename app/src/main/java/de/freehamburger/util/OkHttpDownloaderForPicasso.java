package de.freehamburger.util;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;

import androidx.annotation.NonNull;
import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Simple synchronuous Downloader to be used by Picasso.
 */
public class OkHttpDownloaderForPicasso implements com.squareup.picasso.Downloader {

    private OkHttpClient client;

    /**
     * Constructor.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public OkHttpDownloaderForPicasso(@NonNull Context ctx) {
        super();
        this.client = ((App)ctx.getApplicationContext()).getOkHttpClient();
    }


    /** {@inheritDoc} */
    @NonNull
    @Override
    public Response load(@NonNull Request request) throws IOException {
        try {
            // on an emulator instance (API 31) with exoplayer 2.18.2 there once was
            // E/HamburgerService: Loading image from 'https://www.tagesschau.de/…/….jpg' failed: java.lang.IllegalStateException: cache is closed
            // but this could not be reproduced
            return this.client.newCall(request).execute();
        } catch (IllegalStateException ise) {
            if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), ise.toString(), ise);
            return new Response.Builder().code(HttpURLConnection.HTTP_INTERNAL_ERROR).message(ise.toString()).request(request).build();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        this.client = null;
    }
}
