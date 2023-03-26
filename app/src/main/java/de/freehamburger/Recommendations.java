package de.freehamburger;

import android.Manifest;
import android.content.Context;
import android.util.JsonReader;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.freehamburger.model.News;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Recommendations implements okhttp3.Callback {

    private static final String HOST = "storage.googleapis.com";
    private static final String PREFIX = "https://" + HOST + "/tagesschauatifosrecommendation-prod/recommendations/";
    private static final String SUFFIX = ".json";
    private static final String TAG = "Recommendations";

    private static Boolean hostAllowed = null;

    @NonNull private final News news;
    private RecommendationsCallback callback;
    @Nullable private List<News> result;
    private int resultCode = 0;
    private boolean justChecking;
    private Boolean available;

    /**
     * Constructor.
     * @param news News
     */
    public Recommendations(@NonNull News news) {
        super();
        this.news = news;
        // initialise with data from the News
        this.result = this.news.getRecommendations();
        if (this.result != null) {
            this.resultCode = 200;
        }
    }

    /**
     * Determines whether recommendations are enabled.
     * @param ctx Context
     * @return true / false
     */
    public static boolean isEnabled(@NonNull Context ctx) {
        if (hostAllowed == null) {
            hostAllowed = App.isHostAllowed(HOST);
        }
        boolean is = hostAllowed && PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_RECOMMENDATIONS_ENABLED, App.PREF_RECOMMENDATIONS_ENABLED_DEFAULT);
        if (BuildConfig.DEBUG) Log.i(TAG, "isEnabled() returns "  + is);
        return is;
    }

    @NonNull
    @VisibleForTesting
    public static List<News> parse(InputStream in) {
        final List<News> result = new ArrayList<>(3);
        JsonReader reader = null;
        try {
            reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            reader.beginArray();
            for (; reader.hasNext(); ) {
                result.add(News.parseNews(reader, false, 0));
            }
            reader.endArray();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While parsing: " + e);
        } finally {
            Util.close(reader);
        }
        return result;
    }

    /**
     * Attempts to retrieve recommendations for {@link #news}.
     * @param ctx Context
     * @param callback RecommendationsCallback
     * @throws NullPointerException if any parameter is null
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void call(@NonNull Context ctx, @NonNull RecommendationsCallback callback) {
        if (this.news.getSophoraId() == null) {
            this.resultCode = 500;
            callback.onFailure(this.resultCode, null);
            return;
        }
        if (this.result != null) {
            callback.onSuccess(this.result);
            return;
        }
        if (this.resultCode > 299) {
            callback.onFailure(this.resultCode, null);
            return;
        }
        this.callback = callback;
        String url = PREFIX + this.news.getSophoraId() + SUFFIX;
        Call c = ((App)ctx.getApplicationContext()).getOkHttpClient().newCall(new Request.Builder().url(url).build());
        this.justChecking = false;
        c.enqueue(this);
    }

    /**
     * Issues a HEAD request (if not done yet).
     * @param ctx Context
     */
    public void check(@NonNull Context ctx) {
        if (hasBeenChecked()) return;
        if (this.news.getSophoraId() == null) {
            this.available = Boolean.FALSE;
            return;
        }
        String url = PREFIX + this.news.getSophoraId() + SUFFIX;
        Call c = ((App)ctx.getApplicationContext()).getOkHttpClient().newCall(new Request.Builder().url(url).head().build());
        this.justChecking = true;
        c.enqueue(this);
    }

    public void cleanup() {
        this.callback = null;
        this.result = null;
        this.resultCode = 0;
        this.available = null;
    }

    @IntRange(from = 0)
    public int getResultCode() {
        return this.resultCode;
    }

    public boolean hasBeenChecked() {
        return this.available != null;
    }

    /**
     * Returns true if the GET or HEAD request was successful.
     * @return true / false
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(this.available);
    }

    /** {@inheritDoc} */
    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
        if (this.justChecking) return;
        this.resultCode = 500;
        if (this.callback != null) this.callback.onFailure(this.resultCode, e);
    }

    /** {@inheritDoc} */
    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        this.available = response.isSuccessful();
        final ResponseBody body = response.body();
        if (this.justChecking) {
            if (body != null) body.close();
            return;
        }
        this.resultCode = response.code();
        if (!response.isSuccessful() || body == null) {
            if (body != null) body.close();
            if (this.callback != null) this.callback.onFailure(this.resultCode, null);
            return;
        }

        this.result = parse(body.byteStream());
        body.close();
        this.news.setRecommendations(new ArrayList<>(this.result));
        if (this.callback != null) this.callback.onSuccess(this.result);
    }

    public interface RecommendationsCallback {
        /**
         * Retrieval of recommendations has failed.
         * @param code HTTP(-like) status code - not meaningful if {@code e} is set
         * @param e IOException (not always set)
         */
        void onFailure(@IntRange(from = 0) int code, @Nullable IOException e);

        /**
         * Retrieval of recommendations has been successful.<br>
         * <em>Note: The result has not been processed (i.e. sorted, filtered etc.)</em>
         * @param result List of News objects that have been recommended (possibly empty)
         */
        void onSuccess(@NonNull List<News> result);
    }
}
