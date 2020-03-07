package de.freehamburger.util;

import android.content.Context;

import java.io.IOException;

import androidx.annotation.NonNull;
import de.freehamburger.App;
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
        return this.client.newCall(request).execute();
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        this.client = null;
    }
}
