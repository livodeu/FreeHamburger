package de.freehamburger;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.picasso.Request;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.security.Provider;
import java.security.Security;
import java.text.DateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import de.freehamburger.exo.ExoFactory;
import de.freehamburger.exo.ExoSupply;
import de.freehamburger.exo.Mp34ExtractorsFactory;
import de.freehamburger.model.Source;
import de.freehamburger.prefs.ButtonPreference;
import de.freehamburger.prefs.PrefsHelper;
import de.freehamburger.supp.SearchHelper;
import de.freehamburger.util.FileDeleter;
import de.freehamburger.util.Log;
import de.freehamburger.util.ResourceUtil;
import de.freehamburger.util.Util;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

/**
 *
 */
public class App extends Application implements Application.ActivityLifecycleCallbacks, ExoSupply, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int BACKGROUND_AUTO = 0;
    public static final int BACKGROUND_DAY = 2;
    public static final int BACKGROUND_NIGHT = 1;
    /** String: default maximum 'disk' cache size in MB */
    public static final String DEFAULT_CACHE_MAX_SIZE = "20";
    /** default maximum 'disk' cache size in MB */
    public static final long DEFAULT_CACHE_MAX_SIZE_MB = Long.parseLong(DEFAULT_CACHE_MAX_SIZE);
    public static final boolean DEFAULT_LOAD_OVER_MOBILE = true;
    public static final boolean DEFAULT_LOAD_VIDEOS_OVER_MOBILE = false;
    /** String: default maximum memory cache size in MB (must be &gt; 0) */
    public static final String DEFAULT_MEM_CACHE_MAX_SIZE = "20";
    public static final int DEFAULT_MEM_CACHE_MAX_SIZE_MB = Integer.parseInt(DEFAULT_MEM_CACHE_MAX_SIZE);
    /** components to be excluded when sharing content */
    @TargetApi(Build.VERSION_CODES.N)
    public static final ComponentName[] EXCLUDED_SEND_TARGETS = new ComponentName[] {
            new ComponentName("com.google.android.gms","com.google.android.gms.nearby.sharing.ShareSheetActivity"),
            new ComponentName("com.google.android.gms",".nearby.sharing.ShareSheetActivity"),
            new ComponentName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"),
            // setting images from this app as profile photos would be weird
            new ComponentName("com.samsung.android.app.contacts","com.samsung.android.contacts.editor.SetProfilePhotoActivity")
    };
    /** the directory that shared files are copied to (in the {@link #getCacheDir() cache dir}) */
    public static final String EXPORTS_DIR = "exports";
    /** the name of the file that imported fonts will be stored in (in the {@link #getFilesDir() files dir}) */
    public static final String FONT_FILE = "font.ttf";
    /** the data will be re-loaded if the data in the local file is older than this */
    public static final long LOCAL_FILE_MAXAGE = 15 * 60_000L;
    /** if this is true, filters will be applied to the categories being displayed - the user can hide categories this way */
    public static final String PREF_APPLY_FILTERS_TO_CATEGORIES = "pref_apply_filters_to_cats";
    public static final boolean PREF_APPLY_FILTERS_TO_CATEGORIES_DEFAULT = false;
    /** boolean: ask before leaving the app */
    public static final String PREF_ASK_BEFORE_FINISH = "pref_ask_before_finish";
    public static final boolean PREF_ASK_BEFORE_FINISH_DEFAULT = true;
    /** boolean: ask user for domain association with www.tagesschau.de and www.ard-text.de */
    public static final String PREF_ASK_FOR_DOMAIN_ASSOCIATION = "pref_ask_for_domain_association";
    /** long: timestamp when the user has been asked for domain association most recently, defaults to 0 */
    public static final String PREF_ASK_FOR_DOMAIN_ASSOCIATION_ASKED = "pref_ask_for_domain_association_asked";
    public static final boolean PREF_ASK_FOR_DOMAIN_ASSOCIATION_DEFAULT = true;
    /** int: 0 automatic; 1 dark; 2 light; see {@link BackgroundSelection} */
    public static final String PREF_BACKGROUND = "pref_background";
    /** int: the selected index out of {@link R.array#entries_background_variant} */
    public static final String PREF_BACKGROUND_VARIANT_INDEX = "pref_background_variant_index";
    public static final int PREF_BACKGROUND_VARIANT_INDEX_DEFAULT = 0;
    /** String: maximum 'disk' cache size in MB */
    public static final String PREF_CACHE_MAX_SIZE = "pref_cache_max_size";
    /** the minimum valid value for the 'disk' max cache size in bytes */
    public static final long PREF_CACHE_MAX_SIZE_MIN = 4_194_304L;
    public static final String PREF_CLICK_FOR_CATS = "pref_click_for_cats";
    public static final boolean PREF_CLICK_FOR_CATS_DEFAULT = true;
    /** ColorSpace to use when decoding bitmaps (apparently not [yet] supported by Picasso, see {@link com.squareup.picasso.RequestHandler#createBitmapOptions(Request)}) */
    public static final String PREF_COLORSPACE = "pref_colorspace";
    /** int: number of columns in landscape orientation - set to 0 to apply defaults */
    public static final String PREF_COLS_LANDSCAPE = "pref_cols_landscape";
    /** int: number of columns in portrait orientation - set to 0 to apply defaults */
    public static final String PREF_COLS_PORTRAIT = "pref_cols_portrait";
    /** boolean -  See <a href="https://en.wikipedia.org/wiki/Quotation_mark#German">here</a> */
    public static final String PREF_CORRECT_WRONG_QUOTATION_MARKS = "pref_correct_quotation_marks";
    public static final boolean PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT = false;
    /** String set */
    public static final String PREF_FILTERS = "pref_filters";
    /** boolean */
    public static final String PREF_FILTERS_APPLY = "pref_filters_apply";
    public static final boolean PREF_FILTERS_APPLY_DEFAULT = true;
    /** int: percentage value (range between @integer/min_magnification_text and @integer/max_magnification_text) */
    public static final String PREF_FONT_ZOOM = "pref_font_zoom";
    public static final int PREF_FONT_ZOOM_DEFAULT = 100;
    public static final String PREF_LOAD_OVER_MOBILE = "pref_load_over_mobile";
    public static final String PREF_LOAD_VIDEOS_OVER_MOBILE = "pref_load_videos_over_mobile";
    /** String: maximum memory cache size in MB */
    public static final String PREF_MEM_CACHE_MAX_SIZE = "pref_mem_cache_max_size";
    /** the maximum valid value for the memory max cache size in bytes */
    public static final long PREF_MEM_CACHE_MAX_SIZE_MAX = 100L << 20;
    /** the minimum valid value for the memory max cache size in bytes */
    public static final long PREF_MEM_CACHE_MAX_SIZE_MIN = 1_048_576L;
    public static final String PREF_NFC_USE = "pref_nfc_use";
    public static final boolean PREF_NFC_USE_DEFAULT = false;
    /** boolean: open web links internally instead of posting an {@link Intent#ACTION_VIEW} intent */
    public static final String PREF_OPEN_LINKS_INTERNALLY = "pref_open_links_internally";
    public static final boolean PREF_OPEN_LINKS_INTERNALLY_DEFAULT = true;
    /** boolean */
    public static final String PREF_PLAY_INTRO = "pref_play_intro";
    public static final boolean PREF_PLAY_INTRO_DEFAULT = true;
    /** boolean: remove stupid leading and trailing "++" */
    public static final String PREF_PLUS_IS_NEGATIVE = "pref_plus_is_negative";
    public static final boolean PREF_PLUS_IS_NEGATIVE_DEFAULT = false;
    /** boolean */
    public static final String PREF_POLL = "pref_poll";
    /** boolean */
    public static final String PREF_POLL_ASK_SWITCHOFF = "pref_poll_ask_switchoff";
    public static final boolean PREF_POLL_ASK_SWITCHOFF_DEFAULT = true;
    /** boolean */
    public static final String PREF_POLL_BREAKING_ONLY = "pref_poll_breaking_only";
    public static final boolean PREF_POLL_BREAKING_ONLY_DEFAULT = true;
    public static final boolean PREF_POLL_DEFAULT = false;
    /** long: set to the current timestamp if scheduling the background job had failed */
    public static final String PREF_POLL_FAILED = "pref_poll_failed";
    /** <b>String</b>: polling interval in minutes */
    public static final String PREF_POLL_INTERVAL = "pref_poll_interval";
    public static final int PREF_POLL_INTERVAL_DEFAULT = 15;
    /** <b>String</b>: polling interval during the night in minutes */
    public static final String PREF_POLL_INTERVAL_NIGHT = "pref_poll_interval_night";
    /** float */
    public static final String PREF_POLL_NIGHT_END = "pref_poll_night_end";
    public static final float PREF_POLL_NIGHT_END_DEFAULT = 6f;
    /** float */
    public static final String PREF_POLL_NIGHT_START = "pref_poll_night_start";
    public static final float PREF_POLL_NIGHT_START_DEFAULT = 23f;
    /** boolean */
    public static final String PREF_POLL_OVER_MOBILE = "pref_poll_over_mobile";
    public static final boolean PREF_POLL_OVER_MOBILE_DEFAULT = false;
    /** String: proxyserver:port */
    public static final String PREF_PROXY_SERVER = "pref_proxy_server";
    /** String: DIRECT, HTTP or SOCKS */
    public static final String PREF_PROXY_TYPE = "pref_proxy_type";
    /** boolean */
    public static final String PREF_RECOMMENDATIONS_ENABLED = "pref_recommendations_enabled";
    public static final boolean PREF_RECOMMENDATIONS_ENABLED_DEFAULT = false;
    public static final String PREF_REGIONS = "pref_regions";
    /** boolean: show a link for htmlEmbed content */
    public static final String PREF_SHOW_EMBEDDED_HTML_LINKS = "pref_show_enbedded_html_links";
    public static final boolean PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT = true;
    /** boolean: show where the shared content went */
    public static final String PREF_SHOW_LATEST_SHARE_TARGET = "pref_show_share_target";
    public static final boolean PREF_SHOW_LATEST_SHARE_TARGET_DEFAULT = false;
    /** boolean: show or hide top video in news - the default depends on the device size and is therefore defined in the resources as {@link R.bool#pref_show_topvideo_default pref_show_topvideo_default} */
    public static final String PREF_SHOW_TOP_VIDEO = "pref_show_top_video";
    public static final boolean PREF_SHOW_TOP_VIDEO_DEFAULT = true;
    /** boolean: show errors in web pages */
    public static final String PREF_SHOW_WEB_ERRORS = "pref_show_web_errors";
    public static final boolean PREF_SHOW_WEB_ERRORS_DEFAULT = true;
    /** boolean: allow to close dialogs by swiping (see {@link android.view.Window#FEATURE_SWIPE_TO_DISMISS}; seems not to work on tablets! */
    public static final String PREF_SWIPE_TO_DISMISS = "pref_swipe_to_dismiss";
    /** boolean: allow text selection in News content view */
    public static final String PREF_TEXT_SELECTION = "pref_text_selection";
    public static final boolean PREF_TEXT_SELECTION_DEFAULT = true;
    /** boolean: controls whether time is displayed in a absolute way ("12.34.56 12:34") (false) or in a relative way ("10 minutes ago") (true) */
    public static final String PREF_TIME_MODE_RELATIVE = "pref_time_mode";
    public static final boolean PREF_TIME_MODE_RELATIVE_DEFAULT = true;
    /** int: number of times the topline marquee animation is repeated (see also <a href="https://developer.android.com/reference/android/widget/TextView.html?hl=en#attr_android:marqueeRepeatLimit">here</a>) */
    public static final String PREF_TOPLINE_MARQUEE = "pref_topline_marquee";
    public static final boolean PREF_TOPLINE_MARQUEE_DEFAULT = false;
    /** int: 0 closes the app; 1 navigates to home category; 2 navigates to recent section; see {@link BackButtonBehaviour} */
    public static final String PREF_USE_BACK_IN_APP = "pref_use_back";
    /** boolean */
    public static final String PREF_WARN_MUTE = "pref_warn_mute";
    public static final boolean PREF_WARN_MUTE_DEFAULT = true;
    public static final TimeZone TIMEZONE = TimeZone.getTimeZone("Europe/Berlin");
    public static final String URL_PREFIX = "https://www.tagesschau.de/api2u/";
    /** the user agent to be used in the http requests */
    public static final String USER_AGENT;
    /** default proxy port (if not set by user) */
    static final int DEFAULT_PROXY_PORT = 80;
    static final String EXTRA_CRASH = BuildConfig.APPLICATION_ID + ".crash";
    static final String EXTRA_SCREENSHOT = BuildConfig.APPLICATION_ID + ".screenshot";
    static final String ORIENTATION_AUTO = "AUTO";
    static final String ORIENTATION_LANDSCAPE = "LANDSCAPE";
    static final String ORIENTATION_PORTRAIT = "PORTRAIT";
    /** String, one of {@link Orientation} */
    static final String PREF_ORIENTATION = "pref_orientation";
    @Orientation static final String PREF_ORIENTATION_DEFAULT = ORIENTATION_AUTO;
    /** the AudioManager stream type to be used throughout the app */
    static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    /** teletext url without page number (must be appended) */
    static final String URL_TELETEXT_WO_PAGE = "https://www.ard-text.de/mobil/";
    /** teletext url */
    static final String URL_TELETEXT = URL_TELETEXT_WO_PAGE + "100";
    /** teletext host */
    static final String URI_TELETEXT_HOST = Uri.parse(URL_TELETEXT).getHost();
    /** back button behaviour: pressing back navigates to the most recent category (if possible) */
    static final int USE_BACK_BACK = 2;
    /** back button behaviour: pressing back stops the app (respectively the Android default behaviour) */
    static final int USE_BACK_FINISH = 0;
    /** back button behaviour: pressing back navigates to the 'Home' category (if possible) */
    static final int USE_BACK_HOME = 1;
    private static final Set<String> BLOCKED_SUBDOMAINS = new HashSet<>(1);
    private static final Set<String> PERMITTED_HOSTS = new HashSet<>(38);
    private static final Set<String> PERMITTED_HOSTS_NO_SCRIPT = new HashSet<>(3);
    /** used to build a preferences key to store the most recent <em>user-initiated</em> update of a {@link Source} */
    private static final String PREFS_PREFIX_MOST_RECENT_MANUAL_UPDATE = "latest_manual_";
    /** used to build a preferences key to store the most recent update of a {@link Source} */
    private static final String PREFS_PREFIX_MOST_RECENT_UPDATE = "latest_";
    /** long: last known value of {@link BuildConfig#BUILD_TIME}; defaults to 0 */
    private static final String PREF_LAST_KNOWN_BUILD_DATE = "pref_last_known_build_date";
    private static final String TAG = "App";

    /*
     * For older apps, possible app versions and os versions are merged into the user agent.
     * For version 3.0.1, the user-agent is "okhttp/4.5.0", for version 3.2.0 and 3.2.3, the user-agent is "okhttp/4.7.2", for 3.3.6 it's "okhttp/4.9.3".
     * For version 3.4.0, it's "4.10.0".
     */
    static {
        boolean beV3 = Math.random() < 0.75;
        if (beV3) {
            String[] OKHTTP_VERSIONS = new String[] {"4.5.0", "4.7.2", "4.9.3", "4.10.0"};
            USER_AGENT = "okhttp/" + OKHTTP_VERSIONS[(int) (Math.random() * OKHTTP_VERSIONS.length)];
        } else {
            //                                                                            2.5.0         2.5.1           2.5.2       2.5.3
            String[] VERSIONS = new String[] {"2018080901", "2018102216", "2019011010", "2019032813", "2019040312", "2019071716", "2019080809"};
            // https://en.wikipedia.org/wiki/Android_version_history
            String[] OSS = new String[] {"6.0", "6.0.1", "7.0.1", "7.1.0", "7.1.1", "7.1.2", "8.0.0", "8.1.0", "9.0.0", "10.0.0", "11.0.0", "12.0.0"};
            USER_AGENT = "Tagesschau/de.tagesschau (" + VERSIONS[(int) (Math.random() * VERSIONS.length)] + ", Android: " + OSS[(int) (Math.random() * OSS.length)] + ")";
        }
    }

    private final Handler handler = new Handler();
    private final ScheduleChecker scheduleChecker = new ScheduleChecker();
    long appStart = 0L;
    @Nullable
    private NotificationChannel notificationChannel;
    @Nullable
    private NotificationChannel notificationChannelHiPri;
    @Nullable
    private NotificationChannel notificationChannelUpdates;
    @Nullable
    private Activity currentActivity;
    @Nullable
    private OkHttpClient client;
    @Nullable
    private LatestShare latestShare;
    private ExtractorsFactory exo_ef;
    private MediaSource.Factory exo_mf;
    private LoadControl exo_lc;
    private RenderersFactory exo_rf;

    /**
     * Creates an OkHttpClient instance.
     * @param ctx Context
     * @param cacheDir cache directory
     * @param maxSize the maximum number of bytes the cache should use to store
     * @return OkHttpClient
     * @throws NullPointerException if {@code ctx} is {@code null}
     * @throws IllegalArgumentException if {@code maxSize} is &lt;= 0
     */
    @NonNull
    private static OkHttpClient createOkHttpClient(@NonNull Context ctx, final File cacheDir, final long maxSize) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15_000L, TimeUnit.MILLISECONDS)
                .readTimeout(10_000L, TimeUnit.MILLISECONDS)
                .writeTimeout(10_000L, TimeUnit.MILLISECONDS)
                .cache(new okhttp3.Cache(cacheDir, maxSize))
                ;
        // optional Proxy
        Proxy proxy = determineProxy(ctx);
        if (proxy != null) {
            builder.proxy(proxy);
        }
        // In autumn of 2018 as well as in spring of 2020, TLS V1.2 was used
        ConnectionSpec connectionSpec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                .build();
        builder.connectionSpecs(Collections.singletonList(connectionSpec));
        //
        return builder.build();
    }

    /**
     * Determines the proxy server to use based on the preferences.<br>
     * The proxy port defaults to {@link App#DEFAULT_PROXY_PORT}, if not given as "address:portnumber".
     * @param ctx Context
     * @return java.net.Proxy or {@code null} which means direct connection
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @Nullable
    private static Proxy determineProxy(@NonNull Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
        String defaultProxyValue = Proxy.Type.DIRECT.toString();
        String type = prefs.getString(PREF_PROXY_TYPE, defaultProxyValue);
        if (defaultProxyValue.equals(type)) return null;
        String proxyServerAndPort = prefs.getString(PREF_PROXY_SERVER, null);
        if (TextUtils.isEmpty(proxyServerAndPort)) return null;
        //
        String proxyServer;
        int proxyPort;
        //noinspection ConstantConditions
        int colon = proxyServerAndPort.indexOf(':');
        if (colon > -1) {
            proxyServer = proxyServerAndPort.substring(0, colon).trim();
            try {
                proxyPort = Integer.parseInt(proxyServerAndPort.substring(colon + 1).trim());
                if (proxyPort < 0 || proxyPort > 65535) proxyPort = DEFAULT_PROXY_PORT;
            } catch (RuntimeException e) {
                proxyPort = DEFAULT_PROXY_PORT;
            }
        } else {
            proxyServer = proxyServerAndPort.trim();
            proxyPort = DEFAULT_PROXY_PORT;
        }
        SocketAddress sa = InetSocketAddress.createUnresolved(proxyServer, proxyPort);
        try {
            return new Proxy(Proxy.Type.valueOf(type), sa);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        return null;
    }

    /**
     * Returns the {@link androidx.core.content.FileProvider FileProvider}.
     * @return file provider as defined in AndroidManifest.xml
     */
    @NonNull
    public static String getFileProvider() {
        return BuildConfig.APPLICATION_ID + ".fileprovider";
    }

    /**
     * Determines whether the given host is on the {@link #PERMITTED_HOSTS whitelist}.<br>
     * Returns {@code false} if {@code host} is {@code null}.<br>
     * <em>Will return {@code false} if the host is not in lower case!</em>
     * @param host host in lowercase chars
     * @return {@code true} / {@code false}
     */
    public static synchronized boolean isHostAllowed(@Nullable final String host) {
        if (host == null) return false;
        for (String allowedHost : PERMITTED_HOSTS) {
            if (host.endsWith(allowedHost)) {
                for (String blockedSubdomain : BLOCKED_SUBDOMAINS) {
                    if (host.endsWith(blockedSubdomain)) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Host " + host + " (subdomain) is not allowed!");
                        return false;
                    }
                }
                return true;
            }
        }
        if (BuildConfig.DEBUG) Log.w(TAG, "Host " + host + " is not allowed!");
        return false;
    }

    /**
     * Determines whether the given host is on the {@link #PERMITTED_HOSTS_NO_SCRIPT no-script-gr(e|a)ylist}.<br>
     * Data may be loaded from these hosts only if it does not represent executable code.<br>
     * These hosts must be on the {@link #PERMITTED_HOSTS white list}, too!<br>
     * Returns {@code false} if {@code host} is {@code null}.<br>
     * <em>Will return {@code false} if the host is not in lower case!</em>
     * @param host host in lowercase chars
     * @return {@code true} / {@code false}
     */
    public static synchronized boolean isHostRestrictedToNonScript(@Nullable final String host) {
        if (host == null) return false;
        for (String restrictedHost : PERMITTED_HOSTS_NO_SCRIPT) {
            if (host.endsWith(restrictedHost)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given uri scheme is "https".
     * @param scheme scheme to check
     * @return {@code true} / {@code false}
     */
    public static boolean isSchemeAllowed(@Nullable String scheme) {
        return "https".equalsIgnoreCase(scheme);
    }

    /**
     * For new behaviour in Android 13 / API 33:<br>
     * If the user stopped the app from the notification area, ask user whether to stop background updates.
     * @param prefs SharedPreferences
     */
    @TargetApi(Build.VERSION_CODES.R)
    private void askWhetherToStopPolling(@NonNull final SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        // if the latest process exit was user-initiated AND if background updates are active AND if the user wishes to be asked…
        if (prefs.getBoolean(PREF_POLL_ASK_SWITCHOFF, PREF_POLL_ASK_SWITCHOFF_DEFAULT)
                && prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT)
                && Util.isRecentExitStopApp(this)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (this.currentActivity == null || this.currentActivity.isFinishing()) return;
                AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this.currentActivity)
                        .setTitle(R.string.pref_header_polling)
                        .setMessage(R.string.msg_background_switchoff)
                        .setNeutralButton(R.string.label_dont_ask_again, (dialog, which) -> {
                            dialog.cancel();
                            SharedPreferences.Editor ed = prefs.edit();
                            ed.putBoolean(PREF_POLL_ASK_SWITCHOFF, false);
                            ed.apply();
                        })
                        .setNegativeButton(R.string.label_no, (dialog, which) -> dialog.cancel())
                        .setPositiveButton(R.string.label_yes, (dialog, which) -> {
                            dialog.dismiss();
                            SharedPreferences.Editor ed = prefs.edit();
                            ed.putBoolean(PREF_POLL, false);
                            ed.apply();
                        });
                builder.show();
            }, 5_000L);
        }
    }

    /**
     * Clears search suggestions if this is a new build.
     */
    @AnyThread
    private void clearSearchSuggestionsOnNewBuild() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getLong(PREF_LAST_KNOWN_BUILD_DATE, 0L) == BuildConfig.BUILD_TIME) return;
        // stored search suggestions contain resource ids; these change with every build and therefore must be deleted now
        SearchHelper.deleteAllSearchSuggestions(this);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putLong(PREF_LAST_KNOWN_BUILD_DATE, BuildConfig.BUILD_TIME);
        ed.apply();
    }

    /**
     * Closes the {@link #client OkHttpClient}.
     */
    private void closeClient() {
        try {
            if (this.client != null) {
                final OkHttpClient cc = this.client;
                this.client = null;
                Util.close(cc.cache());
                cc.dispatcher()
                        .executorService()
                        .shutdown();
                // connectionPool().evictAll() might throw android.os.NetworkOnMainThreadException (@ okhttp3.internal.Util.closeQuietly(Util.java:155))
                new Thread() {
                    @Override
                    public void run() {
                        cc.connectionPool().evictAll();
                    }
                }.start();
            }
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Log.e(TAG, t.toString(), t);
        }
    }

    @Nullable
    //@VisibleForTesting
    public Activity getCurrentActivity() {
        return this.currentActivity;
    }

    /**
     * Returns the current cache size in bytes. Method takes about 50 ms for a 15 MB cache.
     * @return current cache size in bytes
     */
    long getCurrentCacheSize() {
        return Util.getOccupiedSpace(Util.listFiles(getCacheDir()));
    }

    @NonNull
    public ExoSupply getExoSupply() {
        return this;
    }

    @NonNull @Override public ExtractorsFactory getExtractorsFactory() {
        if (this.exo_ef == null) {
            this.exo_ef = new Mp34ExtractorsFactory();
        }
        return this.exo_ef;
    }

    @Nullable
    public LatestShare getLatestShare() {
        return this.latestShare;
    }

    /** {@inheritDoc} */
    @NonNull
    public LoadControl getLoadControl() {
        if (this.exo_lc == null) {
            this.exo_lc = new DefaultLoadControl();
        }
        return this.exo_lc;
    }

    /**
     * @param source Source
     * @return the local file that the json data is stored in (does not necessarily exist)
     * @throws NullPointerException if {@code source} is {@code null}
     */
    @NonNull
    public File getLocalFile(@NonNull Source source) {
        return new File(getFilesDir(), source + Source.FILE_SUFFIX);
    }

    /**
     * Returns the maximum disk cache size in bytes.
     * @return max. cache size in bytes
     */
    @IntRange(from = 1_048_576)
    @AnyThread
    private long getMaxDiskCacheSize() {
        long maxCacheSize;
        String maxCacheSizeString = PreferenceManager.getDefaultSharedPreferences(this).getString(App.PREF_CACHE_MAX_SIZE, null);
        if (maxCacheSizeString == null) {
            maxCacheSize = DEFAULT_CACHE_MAX_SIZE_MB * 1_048_576L;
        } else {
            try {
                maxCacheSize = Long.parseLong(maxCacheSizeString) << 20;
            } catch (Exception ignored) {
                maxCacheSize = DEFAULT_CACHE_MAX_SIZE_MB * 1_048_576L;
            }
        }
        return Math.max(maxCacheSize, 1_048_576L);
    }

    @NonNull @Override public MediaSource.Factory getMediaSourceFactory() {
        if (this.exo_mf == null) {
            this.exo_mf = new DefaultMediaSourceFactory(this, getExtractorsFactory());
        }
        return exo_mf;
    }

    /**
     * Returns the most recent <em>successful user-initiated</em> update of the given Source.<br>
     * Returns 0 if no recent update has been recorded yet.<br>
     * The local file's <em>lastModified</em> attribute cannot be used because it states the date of the newest article in that file.
     * @param source Source
     * @return timestamp
     */
    @IntRange(from = 0)
    long getMostRecentManualUpdate(@NonNull Source source) {
        return PreferenceManager.getDefaultSharedPreferences(this).getLong(PREFS_PREFIX_MOST_RECENT_MANUAL_UPDATE + source, 0L);
    }

    /**
     * Returns the most recent <em>successful</em> update of the given Source.<br>
     * Returns 0 if no recent update has been recorded yet.<br>
     * The local file's <em>lastModified</em> attribute cannot be used because it states the date of the newest article in that file.
     * @param source Source
     * @return timestamp
     */
    @IntRange(from = 0)
    public long getMostRecentUpdate(@NonNull Source source) {
        return PreferenceManager.getDefaultSharedPreferences(this).getLong(PREFS_PREFIX_MOST_RECENT_UPDATE + source, 0L);
    }

    /**
     * @return NotificationChannel for standard notifications
     */
    @Nullable
    NotificationChannel getNotificationChannel() {
        return this.notificationChannel;
    }

    /**
     * @return NotificationChannel for important notifications
     */
    @Nullable
    NotificationChannel getNotificationChannelHiPri() {
        return this.notificationChannelHiPri;
    }

    @Nullable
    NotificationChannel getNotificationChannelUpdates() {
        return this.notificationChannelUpdates;
    }

    /**
     * Returns the OkHttpClient. Creates it if it did not exist.
     * @return OkHttpClient
     */
    @NonNull
    public synchronized OkHttpClient getOkHttpClient() {
        if (this.client == null) {
            this.client = createOkHttpClient(this, getCacheDir(), getMaxDiskCacheSize());
        }
        return this.client;
    }

    /** {@inheritDoc} */
    @NonNull
    public RenderersFactory getRenderersFactory() {
        if (this.exo_rf == null) {
            this.exo_rf = new DefaultRenderersFactory(this);
        }
        return this.exo_rf;
    }

    /** {@inheritDoc} */
    @NonNull
    public TrackSelector getTrackSelector() {
        return new DefaultTrackSelector(this);
    }

    /**
     * @return {@code true} if there is currently an Activity in the resumed state
     */
    public boolean hasCurrentActivity() {
        return this.currentActivity != null;
    }

    /**
     * @return the interval in ms if there is a scheduled job with the id {@link UpdateJobService#JOB_ID}; 0 otherwise
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @IntRange(from = 0)
    public long isBackgroundJobScheduled() {
        JobScheduler js = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return 0L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            JobInfo job = js.getPendingJob(UpdateJobService.JOB_ID);
            return job != null ? job.getIntervalMillis() : 0L;
        } else {
            List<JobInfo> steve = js.getAllPendingJobs();
            for (JobInfo job : steve) {
                if (job.getId() == UpdateJobService.JOB_ID) return job.getIntervalMillis();
            }
        }
        return 0L;
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity != this.currentActivity) return;
        this.currentActivity = null;
        this.handler.postDelayed(this.scheduleChecker, 1_000L);
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        this.handler.removeCallbacks(this.scheduleChecker);
        this.currentActivity = activity;
        if (activity instanceof MainActivity) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                int[] ids = UpdateJobService.getAllNotifications(this);
                if (ids.length > 0) {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    for (int id : ids) nm.cancel(id);
                }
            } else {
                //TODO is this ok? are there any other notifications apart thosee from UpdateJobService that should not be cancelled here?
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (!BuildConfig.DEBUG && android.app.ActivityManager.isUserAMonkey()) {
            android.widget.Toast.makeText(activity, "\uD83D\uDC12.", android.widget.Toast.LENGTH_SHORT).show();
            activity.finishAndRemoveTask();
            System.exit(0);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        this.appStart = System.currentTimeMillis();
        super.onCreate();

        /*
         Get {@link UserManager} once here to avoid a memory leak.
         Occurs in Android API >= 18 (Android 4.3)
         (reported by LeakCanary; introduced via https://github.com/android/platform_frameworks_base/commit/27db46850b708070452c0ce49daf5f79503fbde6)
         See https://github.com/square/leakcanary/blob/v1.5.1/leakcanary-android/src/main/java/com/squareup/leakcanary/AndroidExcludedRefs.java
        */
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) getSystemService(USER_SERVICE);

        if (BuildConfig.DEBUG) {
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_WARNING);
            androidx.media3.common.util.Log.setLogStackTraces(true);
        } else {
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);
            androidx.media3.common.util.Log.setLogStackTraces(false);
        }

        /*
         Conscrypt enables TLS V1.3 for Android before 10 (API 29)
         See
         https://square.github.io/okhttp/security_providers/
         and
         https://developer.android.com/reference/javax/net/ssl/SSLSocket#default-configuration-for-different-android-versions

         Accessing Conscrypt via reflection allows to simply remove the reference in build.gradle without any other modifications
         */
        try {
            // https://github.com/square/okhttp#requirements
            Class<?> conscryptClass = Class.forName("org.conscrypt.Conscrypt");
            Method newProvider = conscryptClass.getMethod("newProvider");
            // the method might throw UnsatisfiedLinkError
            Object provider = newProvider.invoke(null);
            if (provider instanceof Provider) {
                int installedAt = Security.insertProviderAt((Provider) provider, 1);
                if (BuildConfig.DEBUG) {
                    if (installedAt < 0) Log.e(TAG, "Failed to insert " + provider);
                }
            }
        } catch (ClassNotFoundException cnfe) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Running without Conscrypt.");
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to instantiate Conscrypt: " + t);
        }

        DynamicColors.applyToActivitiesIfAvailable(this);

        // loading the permitted hosts asynchronously saves ca. 10 to 20 ms
        new Thread() {
            @Override
            public void run() {
                PERMITTED_HOSTS.addAll(ResourceUtil.loadResourceTextFile(App.this, R.raw.permitted_hosts, 39, true));
                BLOCKED_SUBDOMAINS.addAll(ResourceUtil.loadResourceTextFile(App.this, R.raw.blocked_subdomains, 1, true));
                PERMITTED_HOSTS_NO_SCRIPT.addAll(ResourceUtil.loadResourceTextFile(App.this, R.raw.permitted_hosts_no_js, 2, true));
            }
        }.start();

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (BuildConfig.DEBUG) {
                Activity activity = getCurrentActivity();
                boolean isCurrentThread = Thread.currentThread().equals(t);
                Log.wtf(TAG, "*** Uncaught Exception in " + (activity != null ? activity.getClass().getSimpleName() : "no activity") + " in "  + (isCurrentThread ? "current thread: " : "another thread: ") + e, e);
            }
            System.exit(-1);
        });

        // from O on, a NotificationChannel is required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                String channelStandard = getString(R.string.label_notification_channel);
                String channelHiPri = getString(R.string.label_notification_channel_hipri);
                String channelFrequentUpdates = getString(R.string.label_notification_channel_updates);

                this.notificationChannel = new NotificationChannel(channelStandard, channelStandard, NotificationManager.IMPORTANCE_DEFAULT);
                this.notificationChannel.setDescription(getString(R.string.label_notification_channel_desc));
                this.notificationChannel.enableLights(false);
                this.notificationChannel.setSound(null, null);
                this.notificationChannel.setShowBadge(false);
                nm.createNotificationChannel(this.notificationChannel);

                this.notificationChannelUpdates = new NotificationChannel(channelFrequentUpdates, channelFrequentUpdates, NotificationManager.IMPORTANCE_LOW);
                this.notificationChannelUpdates.setDescription(getString(R.string.label_notification_channel_desc_updates));
                this.notificationChannelUpdates.enableLights(false);
                this.notificationChannelUpdates.setSound(null, null);
                this.notificationChannelUpdates.setShowBadge(false);
                nm.createNotificationChannel(this.notificationChannelUpdates);

                Uri ringtone = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
                this.notificationChannelHiPri = new NotificationChannel(channelHiPri, channelHiPri, NotificationManager.IMPORTANCE_HIGH);
                this.notificationChannelHiPri.setDescription(getString(R.string.label_notification_channel_desc_hipri));
                this.notificationChannelHiPri.enableLights(true);
                this.notificationChannelHiPri.setLightColor(getResources().getColor(R.color.colorAccent, getTheme()));
                this.notificationChannelHiPri.enableVibration(true);
                this.notificationChannelHiPri.setSound(ringtone, Notification.AUDIO_ATTRIBUTES_DEFAULT);
                this.notificationChannelHiPri.setShowBadge(false);
                nm.createNotificationChannel(this.notificationChannelHiPri);
            }
        }

        registerActivityLifecycleCallbacks(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        setNightMode(prefs, 0L);

        FileDeleter.run();

        // Android 13 - if the user stopped the app from the notification area, ask user whether to stop background updates
        askWhetherToStopPolling(prefs);

        new Thread() {
            @Override
            public void run() {
                postCreate();
            }
        }.start();
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (PREF_PROXY_SERVER.equals(key) || PREF_PROXY_TYPE.equals(key)) {
            // OkHttpClient needs to be rebuilt next time
            closeClient();
        } else if (PREF_BACKGROUND.equals(key)) {
            setNightMode(prefs, ButtonPreference.ROTATION);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onTrimMemory(int level) {
        if (level > android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            closeClient();
            this.exo_ef = null;
            this.exo_lc = null;
            this.exo_rf = null;
        }
        super.onTrimMemory(level);
    }

    /**
     * Performs some one-time initialisations after a cold start.
     * Run on a worker thread.
     */
    @WorkerThread
    private void postCreate() {
        clearSearchSuggestionsOnNewBuild();
        Util.clearExports(this, 120_000L);
        Util.clearAppWebview(this);
        // new DefaultExtractorsFactory() is fast
        this.exo_ef = new DefaultExtractorsFactory();
        // new DefaultMediaSourceFactory() is fast
        this.exo_mf = new DefaultMediaSourceFactory(this, this.exo_ef);
        // new DefaultLoadControl() is fast
        this.exo_lc = new DefaultLoadControl();
        // new DefaultRenderersFactory() is fast
        this.exo_rf = new DefaultRenderersFactory(this);
        // creating the first ever ExoPlayer takes quite a while on slower devices (ca. 200 ms)
        // by building a dummy ExoPlayer here the time needed to build relevant ones later is reduced significantly
        ExoFactory.makeExoPlayer(this);
     }

    /**
     * Schedules automatic background updates.
     */
    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    @AnyThread
    void scheduleStart() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (PrefsHelper.getStringAsInt(prefs, UpdateJobService.hasNightFallenOverBerlin(prefs) ? PREF_POLL_INTERVAL_NIGHT : PREF_POLL_INTERVAL, PREF_POLL_INTERVAL_DEFAULT) < UpdateJobService.getMinimumIntervalInMinutes()) {
            // Skipping periodic background job because the interval is less than 15 minutes
            return;
        }
        JobScheduler js = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putLong(PREF_POLL_FAILED, System.currentTimeMillis());
            ed.apply();
            return;
        }
        JobInfo jobInfo = UpdateJobService.makeJobInfo(this);
        if (js.schedule(jobInfo) == JobScheduler.RESULT_FAILURE) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putLong(PREF_POLL_FAILED, System.currentTimeMillis());
            ed.apply();
        } else {
            if (prefs.getLong(PREF_POLL_FAILED, 0L) != 0L) {
                SharedPreferences.Editor ed = prefs.edit();
                ed.remove(PREF_POLL_FAILED);
                ed.apply();
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "Periodic job scheduled at " + DateFormat.getDateTimeInstance().format(new java.util.Date(System.currentTimeMillis())) + " for every " + (jobInfo.getIntervalMillis() / 60000) + " minutes");
        }
    }

    /**
     * Cancels the background update if it is currently scheduled. Also removes the notification.
     * @return {@code true} if the job has been cancelled, {@code false} if it was not scheduled
     */
    boolean scheduleStop() {
        JobScheduler js = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return false;
        boolean cancelled = false;
        final List<JobInfo> pending = js.getAllPendingJobs();
        for (JobInfo job : pending) {
            int id = job.getId();
            if (id == UpdateJobService.JOB_ID) {
                js.cancel(id);
                cancelled = true;
                if (BuildConfig.DEBUG) Log.i(TAG, "Schedule for periodic job with interval of " + job.getIntervalMillis() + " ms cancelled.");
                break;
            }
        }
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
        return cancelled;
    }

    public void setLatestShare(@Nullable ComponentName componentName) {
        this.latestShare = componentName != null ? new LatestShare(componentName) : null;
    }

    /**
     * Sets the most recent <em>successful</em> update of the given Source.<br>
     * Set the timestamp to 0 to remove it.
     * @param source Source
     * @param timestamp timestamp
     * @param userInitiated {@code true} if the user initiated the update, {@code false} if the {@link UpdateJobService} did it
     */
    void setMostRecentUpdate(@NonNull Source source, @IntRange(from = 0) long timestamp, boolean userInitiated) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor ed = prefs.edit();
        if (timestamp > 0L) {
            ed.putLong(PREFS_PREFIX_MOST_RECENT_UPDATE + source, timestamp);
        } else {
            ed.remove(PREFS_PREFIX_MOST_RECENT_UPDATE + source);
        }
        if (userInitiated) {
            if (timestamp > 0L) {
                ed.putLong(PREFS_PREFIX_MOST_RECENT_MANUAL_UPDATE + source, timestamp);
            } else {
                ed.remove(PREFS_PREFIX_MOST_RECENT_MANUAL_UPDATE + source);
            }
        }
        ed.apply();
    }

    /**
     * Sets the app's background mode according to the preferences.
     * @param prefs SharedPreferences
     * @param delay delay in ms
     * @throws NullPointerException if {@code prefs} is {@code null}
     */
    private void setNightMode(@NonNull SharedPreferences prefs, long delay) {
        @BackgroundSelection int background = prefs.getInt(PREF_BACKGROUND, BACKGROUND_AUTO);
        switch (background) {
            case BACKGROUND_DAY:
                this.handler.postDelayed(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO), delay);
                break;
            case BACKGROUND_NIGHT:
                this.handler.postDelayed(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES), delay);
                break;
            case BACKGROUND_AUTO:
            default:
                this.handler.postDelayed(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM), delay);
        }
    }

    /**
     * Trims the persistent cache down to its maximum size.
     */
    void trimCacheIfNeeded() {
        try {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    long maxCacheSize = getMaxDiskCacheSize();
                    if (getCurrentCacheSize() > maxCacheSize) {
                        Util.deleteOldestCacheFiles(App.this, maxCacheSize);
                    }
                }
            };
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.start();
        } catch (Throwable e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({USE_BACK_FINISH, USE_BACK_HOME, USE_BACK_BACK})
    @interface BackButtonBehaviour {}

    /**
     * The possible values of the {@link #PREF_BACKGROUND background preference}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BACKGROUND_AUTO, BACKGROUND_NIGHT, BACKGROUND_DAY})
    public @interface BackgroundSelection {}

    /**
     * The possible values of the {@link #PREF_BACKGROUND background preference} after BACKGROUND_AUTO has been resolved.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BACKGROUND_NIGHT, BACKGROUND_DAY})
    public @interface ResolvedBackgroundSelection {}

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ORIENTATION_AUTO, ORIENTATION_LANDSCAPE, ORIENTATION_PORTRAIT})
    @interface Orientation {}

    /**
     * Represents an action during which the honorable user ventured to pass some data, possibly even information, to another app¹.<br>
     * Colloquially, and to the peasants, known as "sharing".<br><br>
     * ¹: An "app" is known as, in order not to mislead you, an electronic manifestation of machine instructions to be executed, limited to and bordered by the realms of a portable² electronic brain. 🆒<br>
     * ²: "portable" is in the arms of the, Gee! , holder³<br>
     * ³: literally<br>
     * <br>
     * Now, this is a piece of documentation up with which you will have to put!<br>
     */
    public static class LatestShare {
        /** Apparently the user likes that app…<br>What does it have that <i>{@link BuildConfig#APPLICATION_ID this}</i> has not?!? */
        @NonNull public final ComponentName target;
        /** Heck, what could this mean? Something about postage? A stamped stamp? Stamps me. */
        private final long timestamp;

        /**
         * This is the place to go if you want a new LatestShare!
         * @param target ComponentName
         */
        LatestShare(@NonNull ComponentName target) {
            super();
            this.target = target;
            this.timestamp = System.currentTimeMillis();
        }

        /** @return {@code true} if this happened only a blink of an I ago */
        boolean wasVeryRecent() {
            return System.currentTimeMillis() - this.timestamp < 60_000L;
        }
    }

    /**
     * Checks whether the {@link UpdateJobService background job} is scheduled.
     */
    private class ScheduleChecker implements Runnable {

        @Override
        public void run() {
            if (isBackgroundJobScheduled() == 0L) {
                scheduleStart();
            }
        }
    }
}
