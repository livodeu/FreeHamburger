package de.freehamburger;

import android.Manifest;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import android.text.Html;
import android.util.TypedValue;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import de.freehamburger.util.Downloader;
import de.freehamburger.util.Log;
import de.freehamburger.util.OkHttpDownloader;
import de.freehamburger.util.OkHttpDownloaderForPicasso;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView2;


/**
 */
public class HamburgerService extends Service implements Html.ImageGetter, Picasso.Listener, NewsView2.BitmapGetter, SharedPreferences.OnSharedPreferenceChangeListener {

    static final boolean USE_FORKJOINPOOL = true;
    private static final Drawable EMPTY_DRAWABLE = new PaintDrawable(0);
    private static final String TAG = "HamburgerService";
    /** the delay [ms] after which {@link #cacheFiller} willl be invoked whenever a picture/image address is added or removed to/from {@link #cacheables} */
    private static final long CACHEFILLER_DELAY = 250L;
    private final HamburgerServiceBinder binder = new HamburgerServiceBinder(this);
    private final Handler handler = new Handler();
    /** urls of pictures/images to be loaded into the memory cache */
    private final Set<String> cacheables = new HashSet<>();
    /** loads pictures/images into the memory cache - delegates to Picasso */
    private final CacheFiller cacheFiller = new CacheFiller();
    /** see <a href="https://web.archive.org/web/20220120071931/https://developer.android.com/training/articles/perf-tips#PackageInner">https://developer.android.com/training/articles/perf-tips.html#PackageInner</a> */
    private Picasso picasso;
    private ExecutorService loaderExecutor;
    private com.squareup.picasso.LruCache memoryCache;
    /** to be re-used */
    @Nullable private PaintDrawable latestPaintDrawable;

    /**
     * Builds the Picasso instance.
     */
    private synchronized void buildPicasso() {
        if (BuildConfig.DEBUG) Log.i(TAG, "buildPicasso()");
        if (this.picasso != null) {
            this.picasso.shutdown();
            this.picasso = null;
        }

        // first, cleanup if there are old things around
        if (this.loaderExecutor != null) {
            this.loaderExecutor.shutdown();
        }
        if (this.memoryCache != null) {
            clearMemoryCache();
        }

        if (USE_FORKJOINPOOL) {
            // https://web.archive.org/web/20110819180102/https://vanillajava.blogspot.com/2011/06/java-secrets-using-executorservice-to.html
            // https://web.archive.org/web/20201112020343/https://www.cs.oswego.edu/pipermail/concurrency-interest/2012-January/008987.html
            this.loaderExecutor = new ForkJoinPool(Runtime.getRuntime().availableProcessors() << 1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
        } else {
            this.loaderExecutor = Executors.newCachedThreadPool();
        }

        createMemoryCache();
        // start Picasso
        this.picasso = new Picasso.Builder(this)
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .downloader(new OkHttpDownloaderForPicasso(this))
                .executor(this.loaderExecutor)
                //.indicatorsEnabled(BuildConfig.DEBUG)   // arpanet: red, floppy: blue, ram: green
                .listener(this)
                .loggingEnabled(false)
                .memoryCache(this.memoryCache)
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
    }

    /**
     * Builds the memory cache used by Picasso.
     */
    void createMemoryCache() {
        clearMemoryCache();
        int maxRamCacheSizeInMB = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(App.PREF_MEM_CACHE_MAX_SIZE, App.DEFAULT_MEM_CACHE_MAX_SIZE));
        this.memoryCache = new com.squareup.picasso.LruCache(Math.min((int)App.PREF_MEM_CACHE_MAX_SIZE_MAX, maxRamCacheSizeInMB << 20));
    }

    /** {@inheritDoc}
     * <br>
     * <br>According to that ↑, this method is allowed to return {@code null}; this actual implementation never returns {@code null}, though.
     * <br>
     * <br>This class implements {@link Html.ImageGetter} which is used in {@link Html#fromHtml(String, int, Html.ImageGetter, Html.TagHandler)}
     * */
    @Override
    public Drawable getDrawable(@NonNull String source) {
        // the returned Drawable is invisible
        return EMPTY_DRAWABLE;
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
     * @param listener DownloaderListener
     * @throws NullPointerException if {@code localFile} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    void loadFile(@NonNull String url, @NonNull File localFile, @NonNull Downloader.DownloaderListener listener) {
        loadFile(url, localFile, 0L, listener);
    }

    /**
     * Loads a remote resource.
     * @param url Url to load from
     * @param localFile local file to save to
     * @param mostRecentUpdate timestamp of point in time when the resource has been loaded most recently; will set the "If-Modified-Since" header
     * @param listener DownloaderListener
     * @throws NullPointerException if {@code localFile} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    void loadFile(@NonNull String url, @NonNull File localFile, long mostRecentUpdate, @NonNull Downloader.DownloaderListener listener) {
        OkHttpDownloader downloader = new OkHttpDownloader(this);
        try {
            if (this.loaderExecutor != null) {
                downloader.executeOnExecutor(this.loaderExecutor, new Downloader.Order(url, localFile.getAbsolutePath(), mostRecentUpdate, false, listener));
            } else {
                if (BuildConfig.DEBUG) Log.e(TAG, "loadFile(\"" + url + "\", …, …, …): loaderExecutor is null!");
                downloader.execute(new Downloader.Order(url, localFile.getAbsolutePath(), mostRecentUpdate, false, listener));
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "loadFile(\"" + url + "\", …, …, …) failed: " + e, e);
            listener.downloaded(false, null);
        }
    }

    /**
     * Loads a picture via Picasso into a {@link Target target}.
     * @param uri picture URL
     * @param target target
     */
    @AnyThread
    @RequiresPermission(Manifest.permission.INTERNET)
    public void loadImage(@NonNull String uri, @NonNull Target target) {
        loadImage(uri, target, null);
    }


    /**
     * Loads a picture via Picasso into a {@link Target target}.
     * @param uri picture URL
     * @param target target
     * @param placeholder placeholder
     */
    @AnyThread
    @RequiresPermission(Manifest.permission.INTERNET)
    public void loadImage(@NonNull String uri, @NonNull Target target, @Nullable Drawable placeholder) {
        this.handler.post(new PictureLoader(this, uri, null, target, placeholder));
    }

    /**
     * Attempts to load a picture <em>synchronously</em> from the cache.<br>
     * May not be run on the main thread!
     * @param uri picture URL
     * @return Bitmap or null
     */
    @WorkerThread
    @Nullable
    public Bitmap loadImageFromCache(@NonNull String uri) {
        if (this.picasso == null) return null;
        try {
            return this.picasso.load(uri).networkPolicy(NetworkPolicy.OFFLINE).noFade().get();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While trying to load " + uri + " from the cache: " + e);
        }
        return null;
    }

    /**
     * Loads a picture via Picasso into the given ImageView.<br>
     * If the url points to a resource ("android.resource://…"), Picasso will not be used, though.
     * @param url picture URL
     * @param dest ImageView
     * @param imageWidth expected width of the image
     * @param imageHeight expected height of the image
     * @throws NullPointerException if {@code url} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    @UiThread
    public void loadImageIntoImageView(@NonNull String url, @Nullable final ImageView dest, int imageWidth, int imageHeight) {
        if (url.length() < 8) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Cannot load image from \"" + url + "\"!");
            return;
        }
        if (url.startsWith("android.resource:")) {
            // e.g. "android.resource://de.freehamburger/2131230843"
            if (dest == null) return;
            int slash = url.lastIndexOf('/');
            if (slash < 20) return;
            try {
                int id = Integer.parseInt(url.substring(slash + 1));
                Drawable d = getResources().getDrawableForDensity(id, TypedValue.DENSITY_NONE, null);
                dest.setImageDrawable(d);
                return;
            } catch (Exception ignored) {
            }
        }
        if (url.charAt(0) == '/' && url.charAt(1) == '/') {
            url = "https:" + url;
        } else if (url.startsWith("http:")) {
            url = "https" + url.substring(4);
        }
        loadIntoCacheCancel(url);
        if (imageWidth > 0 && imageHeight > 0) {
            PaintDrawable pd;
            if (this.latestPaintDrawable != null && this.latestPaintDrawable.getIntrinsicWidth() == imageWidth && this.latestPaintDrawable.getIntrinsicHeight() == imageHeight) {
                pd = this.latestPaintDrawable;
            } else {
                pd = new PaintDrawable(android.graphics.Color.TRANSPARENT);
                pd.setIntrinsicWidth(imageWidth);
                pd.setIntrinsicHeight(imageHeight);
                this.latestPaintDrawable = pd;
            }
            this.handler.post(new PictureLoader(this, url, dest, null, pd, (float)imageWidth / (float)imageHeight));
        } else {
            this.handler.post(new PictureLoader(this, url, dest, null, null));
        }
    }

    /**
     * Loads pictures into the memory cache.
     * @param uris addresses of pics to load
     */
    @AnyThread
    @RequiresPermission(Manifest.permission.INTERNET)
    public void loadIntoCache(@NonNull Collection<String> uris) {
        this.handler.removeCallbacks(this.cacheFiller);
        boolean invoke;
        synchronized (this.cacheables) {
            this.cacheables.addAll(uris);
            invoke = !this.cacheables.isEmpty();
        }
        if (invoke) this.handler.postDelayed(this.cacheFiller, CACHEFILLER_DELAY);
    }

    /**
     * Removes the given picture from those about to be loaded into the cache.
     * @param uri address of pic not to load
     */
    @AnyThread
    private void loadIntoCacheCancel(@NonNull String uri) {
        this.handler.removeCallbacks(this.cacheFiller);
        int waiting;
        synchronized (this.cacheables) {
            this.cacheables.remove(uri);
            waiting = this.cacheables.size();
        }
        if (waiting > 0) {
            this.handler.postDelayed(this.cacheFiller, CACHEFILLER_DELAY);
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
        start(1);
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
        if (e instanceof IllegalStateException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "onImageLoadFailed(…, " + uri + ", " + e + ")", e);
            buildPicasso();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (App.PREF_PROXY_SERVER.equals(key) || App.PREF_PROXY_TYPE.equals(key) || App.PREF_MEM_CACHE_MAX_SIZE.equals(key)) {
            // Picasso needs to be rebuilt;
            // If Picasso's downloader were changed, we'd get a "java.lang.IllegalStateException: cache is closed"
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
     * Invokes {@link #startService(Intent)}
     * @param attempt attempt counter
     */
    private void start(int attempt) {
        try {
            startService(new Intent(this, getClass()));
        } catch (IllegalStateException e) {
            /*
             * This is likely:
             * java.lang.IllegalStateException:
             * Not allowed to start service Intent { cmp=de.freehamburger.debug/de.freehamburger.HamburgerService }: app is in background uid UidRecord{...}
             */
            if (attempt > 5) {
                if (BuildConfig.DEBUG) Log.e(TAG, "onCreate() - startService(): " + e);
                System.exit(1);
                return;
            }
            if (BuildConfig.DEBUG) Log.w(TAG, "onCreate() - startService(): " + e, e, 4);
            new Handler().postDelayed(() -> start(attempt + 1), 5_000L);
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
        private final float elevation;
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
            this.elevation = service.getResources().getDimensionPixelSize(R.dimen.news_image_elevation);
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
                if (this.placeholder != null) rc.placeholder(this.placeholder); else rc.noPlaceholder();
                rc
                        .networkPolicy(NetworkPolicy.NO_CACHE)
                        .resize(this.width, this.height)
                        .noFade()
                        .centerCrop()
                        .error(R.drawable.ic_warning_red_24dp)
                        .into(this.dest, this);
            } else {
                // picture failed to load from web
                if (this.dest != null) {
                    this.dest.setElevation(0f);
                    this.dest = null;
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onSuccess() {
            if (this.dest != null) {
                this.dest.setElevation(this.elevation);
                this.dest = null;
            }
        }

        /** {@inheritDoc} */
        @Override
        @MainThread
        public void run() {
            if (this.url == null || !this.url.toLowerCase(Locale.US).startsWith("http")) return;
            final HamburgerService service = this.refService.get();
            if (service == null || service.picasso == null) return;
            Target target = this.refTarget != null ? this.refTarget.get() : null;
            if (target != null) {
                if (service.cannotDownload()) return;
                RequestCreator rc = service.picasso.load(this.url);
                if (this.placeholder != null) rc.placeholder(this.placeholder); else rc.noPlaceholder();
                rc.into(target);
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
                if (service.picasso == null) return;
                // first try the cache (NetworkPolicy.OFFLINE)
                service.picasso.load(this.url)
                        .networkPolicy(NetworkPolicy.OFFLINE)
                        .resize(this.width, this.height)
                        .noFade()   // without noFade(), the picture would be dimmed before display
                        .noPlaceholder()
                        .centerCrop()
                        .into(this.dest, this);

            } else {
                if (service.cannotDownload()) return;
                // Performing Picasso fetch
                service.picasso
                        .load(this.url)
                        .priority(Picasso.Priority.LOW)
                        .noPlaceholder()
                        .noFade()
                        .fetch(null);
            }
        }
    }

    /**
     * Invokes {@link PictureLoader PictureLoaders} for each url in {@link #cacheables}.
     */
    private final class CacheFiller implements Runnable {
        /** {@inheritDoc} */
        @Override public void run() {
            synchronized (HamburgerService.this.cacheables) {
                for (String cacheable : HamburgerService.this.cacheables) {
                    HamburgerService.this.handler.post(new PictureLoader(HamburgerService.this, cacheable, null, null, null));
                }
                HamburgerService.this.cacheables.clear();
            }
        }
    }

}
