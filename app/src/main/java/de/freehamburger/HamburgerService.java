package de.freehamburger;

import android.Manifest;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.text.Html;
import android.widget.ImageView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.freehamburger.util.BitmapTarget;
import de.freehamburger.util.Downloader;
import de.freehamburger.util.Log;
import de.freehamburger.util.OkHttpDownloader;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView;


/**
 */
public class HamburgerService extends Service implements Html.ImageGetter, Picasso.Listener, NewsView.BitmapGetter, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "HamburgerService";

    private final HamburgerServiceBinder binder = new HamburgerServiceBinder(this);
    private final Handler handler = new Handler();
    /** key: url, value: Picasso cache key (see {@link com.squareup.picasso.Utils}) */
    private final Map<String, String> cacheKeys = Collections.synchronizedMap(new HashMap<>(32));
    /** see <a href="https://developer.android.com/training/articles/perf-tips.html#PackageInner">https://developer.android.com/training/articles/perf-tips.html#PackageInner</a> */
    private Picasso picasso;
    private ExecutorService loaderExecutor;
    private LruCache memoryCache;

    /**
     * Generates a Picasso cache key.<br>
     * <em>Must be adjusted if the parameters given to Picasso in {@link PictureLoader PictureLoader}
     * (like, for example, {@link RequestCreator#centerInside() centerInside} or {@link RequestCreator#resize(int, int) resize}) change!</em><br>
     * See {@link com.squareup.picasso.Utils#createKey(Request, StringBuilder)}.
     * @param url image url
     * @param width image width
     * @param height image height
     * @return Picasso cache key
     */
    @NonNull
    private static String makeCacheKey(String url, int width, int height) {
        return url + '\n' + "resize:" + width + 'x' + height + '\n' + "centerInside" + '\n';
    }

    /**
     * Adds a bitmap to the Picasso cache.
     * @param uri image url
     * @param bitmap bitmap
     */
    void addToCache(@NonNull String uri, @NonNull Bitmap bitmap) {
        String cacheKey = makeCacheKey(uri, bitmap.getWidth(), bitmap.getHeight());
        this.cacheKeys.put(uri, cacheKey);
        this.memoryCache.set(cacheKey, bitmap);
    }

    /**
     * Builds the Picasso instance.
     */
    void buildPicasso() {
        if (BuildConfig.DEBUG) Log.i(TAG, "Building Picasso.");
        // first, cleanup if there are old things around
        if (this.loaderExecutor != null) {
            this.loaderExecutor.shutdown();
        }
        if (this.memoryCache != null) {
            this.memoryCache.clear();
        }
        //
        this.loaderExecutor = Executors.newCachedThreadPool();
        createMemoryCache();
        // as the docs say that Bitmap.Config.HARDWARE "is optimal for cases, when the only operation with the bitmap is to draw it on a screen", we try to use it here
        Bitmap.Config config = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_USE_HARDWARE_BMPS, true)
                ? Bitmap.Config.HARDWARE : Bitmap.Config.RGB_565;
        // start Picasso
        this.picasso = new Picasso.Builder(this)
                .memoryCache(this.memoryCache)
                .downloader(new OkHttpDownloader(this))
                .defaultBitmapConfig(config)
                .loggingEnabled(BuildConfig.DEBUG)
                .listener(this)
                .executor(this.loaderExecutor)
                .build();
    }

    /**
     * Checks for network connection and Picasso instantiation.
     * @return {@code true} if downloading is NOT possible, {@code false} if it is
     */
    private boolean cannotDownload() {
        return !Util.isNetworkAvailable(this) || this.picasso == null;
    }

    /**
     * Clears the Picasso memory cache.
     */
    private void clearMemoryCache() {
        if (this.memoryCache == null) return;
        this.memoryCache.clear();
        this.cacheKeys.clear();
    }

    /**
     * Builds the memory cache used by Picasso.
     */
    void createMemoryCache() {
        clearMemoryCache();
        int maxRamCacheSizeInMB = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(App.PREF_MEM_CACHE_MAX_SIZE, App.DEFAULT_MEM_CACHE_MAX_SIZE));
        this.memoryCache = new LruCache(maxRamCacheSizeInMB << 20);
    }

    /**
     * Returns a bitmap from the Picasso memory cache.
     * @param url image uri
     * @return Bitmap
     */
    @Nullable
    public Bitmap getCachedBitmap(@Nullable String url) {
        if (url == null || url.length() < 8) return null;
        String key = this.cacheKeys.get(url.charAt(4) == ':' ? "https:" + url.substring(5) : url);  // http -> https
        return key != null ? this.memoryCache.get(key) : null;
    }

    /** {@inheritDoc}
     * <br>This class implements {@link Html.ImageGetter} which is used in {@link Html#fromHtml(String, int, Html.ImageGetter, Html.TagHandler)} */
    @Override
    public Drawable getDrawable(@NonNull String source) {
        Bitmap cached = getCachedBitmap(source);
        if (cached != null) {
            return new BitmapDrawable(getResources(), cached);
        }
        return getResources().getDrawable(R.drawable.placeholder, getTheme());
    }

    /**
     * @return the current size (in bytes) of the Picasso memory cache
     */
    int getMemoryCacheSize() {
        return this.memoryCache != null ? this.memoryCache.size() : 0;
    }

    /**
     * Loads a remote resource.
     * @param url Url to load from
     * @param localFile local file to save to
     * @param listener DownloaderListener (optional)
     * @throws NullPointerException if {@code localFile} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    void loadFile(@NonNull String url, @NonNull File localFile, @Nullable Downloader.DownloaderListener listener) {
        loadFile(url, localFile, 0L, listener);
    }

    /**
     * Loads a remote resource.
     * @param url Url to load from
     * @param localFile local file to save to
     * @param mostRecentUpdate timestamp of point in time when the resource has been loaded most recently; will set the "If-Modified-Since" header
     * @param listener DownloaderListener (optional)
     * @throws NullPointerException if {@code localFile} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    void loadFile(@NonNull String url, @NonNull File localFile, long mostRecentUpdate, @Nullable Downloader.DownloaderListener listener) {
        OkHttpDownloader fd = new OkHttpDownloader(this);
        try {
            fd.executeOnExecutor(this.loaderExecutor, new Downloader.Order(url, localFile.getAbsolutePath(), mostRecentUpdate, false, listener));
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "loadFile(\"" + url + "\", ..., ...) failed: " + e.toString());
            if (listener != null) listener.downloaded(false, null);
        }
    }

    /**
     * Loads a picture via Picasso into a {@link Target target}.
     * @param uri picture URL
     * @param target target
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void loadImage(@NonNull String uri, @NonNull Target target) {
        this.handler.post(new PictureLoader(this, uri, null, target, null));
    }

    /**
     * Loads a picture via Picasso into the given ImageView.
     * @param url picture URL
     * @param dest ImageView
     * @param imageWidth expected width of the image
     * @param imageHeight expected height of the image
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void loadImageIntoImageView(@NonNull String url, @Nullable final ImageView dest, int imageWidth, int imageHeight) {
        if (url.length() < 8) return;
        if (url.charAt(0) == '/' && url.charAt(1) == '/') {
            url = "https:" + url;
        } else if (url.startsWith("http:")) {
            url = "https" + url.substring(4);
        }
        if (imageWidth > 0 && imageHeight > 0) {
            PaintDrawable pd = new PaintDrawable(getResources().getColor(R.color.colorPrimaryTrans));
            pd.setIntrinsicWidth(imageWidth);
            pd.setIntrinsicHeight(imageHeight);
            this.handler.post(new PictureLoader(this, url, dest, null, pd, (float)imageWidth / (float)imageHeight));
        } else {
            this.handler.post(new PictureLoader(this, url, dest, null, null));
        }
    }

    /** {@inheritDoc} */
    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    /**
     * {@inheritDoc}
     * <hr>
     * Attempts to {@link #startService(Intent) start} the service, too.<br>
     * As of Oreo, the OS will prevent that if the App is in the background.
     * See <a href="https://developer.android.com/about/versions/oreo/background">here</a>.<br>
     * Starts up <a href="https://square.github.io/picasso">Picasso</a>, too.<br>
     * For OkHttp, see their <a href="https://square.github.io/okhttp">page</a>.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        buildPicasso();
        // make sure this service is started; otherwise each activity would create its own instance
        try {
            startService(new Intent(this, getClass()));
        } catch (IllegalStateException e) {
            /*
             * This is likely:
             * java.lang.IllegalStateException:
             * Not allowed to start service Intent { cmp=de.freehamburger.debug/de.freehamburger.HamburgerService }: app is in background uid UidRecord{...}
             */
            if (BuildConfig.DEBUG) Log.w(TAG, "onCreate() - startService(): " + e.toString(), e, 4);
        }
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(this);
        if (this.picasso != null) {
            this.picasso.shutdown();
            this.picasso = null;
        }
        if (!this.loaderExecutor.isShutdown()) {
            this.loaderExecutor.shutdown();
        }
        this.loaderExecutor = null;
        this.memoryCache = null;
        this.cacheKeys.clear();
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public void onImageLoadFailed(Picasso picasso, Uri uri, Exception e) {
        if (BuildConfig.DEBUG) {
            String s = e.toString();
            /*
             HTTP 504 Gateway Timeout (https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.5)
             occurs with message "Unsatisfiable Request (only-if-cached)"
             when request headers contained "Cache-Control: max-stale=2147483647, only-if-cached"
             and com.squareup.picasso.NetworkPolicy was 4
             */
            if (s.contains("HTTP 504")) return;
            if (s.contains("NetworkRequestHandler$ResponseException")) Log.e(TAG, "Loading image from '" + uri + "' failed: " + e.getMessage(), e);
            else Log.e(TAG, "Loading image from '" + uri + "' failed: " + e, e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (App.PREF_PROXY_SERVER.equals(key) || App.PREF_PROXY_TYPE.equals(key)) {
            // Picasso needs to be rebuilt; otherwise we'd get a "java.lang.IllegalStateException: cache is closed" because the 'downloader' cannot be changed once Picasso is built
            buildPicasso();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            clearMemoryCache();
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            stopSelf();
        }
    }

    /**
     *
     */
    static final class HamburgerServiceBinder extends Binder {

        @NonNull
        private final Reference<HamburgerService> refService;

        /**
         * Constructor.
         * @param service HamburgerService
         */
        private HamburgerServiceBinder(@NonNull HamburgerService service) {
            super();
            this.refService = new WeakReference<>(service);
        }

        /**
         * @return HamburgerService
         */
        @Nullable
        HamburgerService getHamburgerService() {
            return this.refService.get();
        }
    }

    /**
     * Loads pictures via Picasso.<br>
     * See <a href="https://square.github.io/picasso">Picasso web page</a>.
     */
    private static class PictureLoader implements Runnable, Callback {

        private final Reference<HamburgerService> refService;
        private final String url;
        @Nullable
        private final Reference<Target> refTarget;
        private final float ratio;
        @Nullable
        private final Drawable placeholder;
        @Nullable
        private ImageView dest;
        /** initially false to indicate that loading from network has not been attempted yet */
        private boolean loadingFromWebAttempted;
        private int width, height;

        /**
         * Constructor.
         * @param service HamburgerService
         * @param url picture http url
         * @param dest optional ImageView to set the picture in
         * @param target optional {@link Target} to use instead
         * @param placeholder optional placeholder Drawable
         */
        private PictureLoader(@NonNull HamburgerService service, String url, @Nullable ImageView dest, @Nullable Target target, @Nullable Drawable placeholder) {
            this(service, url, dest, target, placeholder, 0f);
        }

        /**
         * Constructor.
         * @param service HamburgerService
         * @param url picture http url
         * @param dest optional ImageView to set the picture in
         * @param target optional {@link Target} to use instead
         * @param placeholder optional placeholder Drawable
         * @param ratio x / y ratio of the image to be loaded (0 if not known)
         */
        private PictureLoader(@NonNull HamburgerService service, String url, @Nullable ImageView dest, @Nullable Target target, @Nullable Drawable placeholder, float ratio) {
            super();
            this.refService = new WeakReference<>(service);
            this.url = url;
            this.dest = dest;
            this.refTarget = target != null ? new WeakReference<>(target) : null;
            this.placeholder = placeholder;
            this.ratio = ratio;
        }

        /** {@inheritDoc} */
        @Override
        public void onError(Exception e) {
            if (!this.loadingFromWebAttempted) {
                // picture failed to load from memoryCache => load from web
                this.loadingFromWebAttempted = true;
                // dest should be non-null here but Android Studio is not sure...
                if (this.dest == null) return;
                HamburgerService service = this.refService.get();
                if (service == null || service.cannotDownload()) return;
                RequestCreator rc = service.picasso.load(this.url);
                if (this.placeholder != null) rc.placeholder(this.placeholder); //else rc.noPlaceholder();
                rc
                        .networkPolicy(NetworkPolicy.NO_CACHE)
                        .resize(this.width, this.height)
                        .noFade()
                        .centerCrop()
                        .error(R.drawable.ic_warning_red_24dp)
                        .into(this.dest, this);
            } else {
                // picture failed to load from web
                this.dest = null;
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onSuccess() {
            this.dest = null;
        }

        /** {@inheritDoc} */
        @Override
        @MainThread
        public void run() {
            if (this.url == null || !this.url.toLowerCase(Locale.US).startsWith("http")) {
                return;
            }
            HamburgerService service = this.refService.get();
            if (service == null || service.picasso == null) return;
            Target target = this.refTarget != null ? this.refTarget.get() : null;
            if (target != null) {
                if (service.cannotDownload()) return;
                service.picasso
                        .load(this.url)
                        .noPlaceholder()
                        .into(target);
            } else if (this.dest != null) {
                int normalImageWidth = service.getResources().getDimensionPixelSize(R.dimen.image_width_normal);
                // get width and height of the ImageView
                this.width = normalImageWidth;
                if (this.ratio > 0f) {
                     this.height = Math.round(this.width / this.ratio);
                } else {
                    //noinspection SuspiciousNameCombination
                    this.height = normalImageWidth;
                }
                // load via Picasso (https://square.github.io/picasso/)
                if (service.picasso == null) {
                    return;
                }
                // for the Picasso cache key, see com.squareup.picasso.Utils.createKey()
                String cacheKey = makeCacheKey(this.url, this.width, this.height);
                service.cacheKeys.put(this.url, cacheKey);

                // first try the cache (NetworkPolicy.OFFLINE)
                RequestCreator rc = service.picasso.load(this.url);
                if (this.placeholder != null) rc.placeholder(this.placeholder); //else rc.noPlaceholder();
                rc
                        .networkPolicy(NetworkPolicy.OFFLINE)
                        .resize(this.width, this.height)
                        .noFade()   // without noFade(), the picture would be dimmed before display
                        .centerCrop()
                        .into(this.dest, this);
            } else {
                if (BuildConfig.DEBUG) Log.e(TAG, "PictureLoader for " + url + " with both dest and target null!");
                if (service.cannotDownload()) return;
                service.picasso
                        .load(this.url)
                        .noPlaceholder()
                        .error(R.drawable.ic_warning_red_24dp)
                        .into(new BitmapTarget(this.url));
            }
        }
    }

}
