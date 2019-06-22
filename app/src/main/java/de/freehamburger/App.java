package de.freehamburger;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.AnyThread;
import android.support.annotation.IntDef;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.text.DateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import de.freehamburger.model.Source;
import de.freehamburger.util.FileDeleter;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

/**
 *
 */
public class App extends Application implements Application.ActivityLifecycleCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String URL_PREFIX = "https://www.tagesschau.de/api2/";
    final static String URL_TELETEXT = "https://www.ard-text.de/mobil/100";
    //public final static String DATENSCHUTZERKLAERUNG = "datenschutzerklaerung100.json";
    /** the user agent to be used in the http requests */
    public static final String USER_AGENT;
    /** the directory that shared files are copied to (in the {@link #getCacheDir() cache dir}) */
    public static final String EXPORTS_DIR = "exports";
    /** the name of the file that imported fonts will be stored in (in the {@link #getFilesDir() files dir}) */
    public static final String FONT_FILE = "font.ttf";
    /** the data will be re-loaded if the data in the local file is older than this */
    public static final long LOCAL_FILE_MAXAGE = 15 * 60_000L;
    /** String: maximum 'disk' cache size in MB */
    public static final String PREF_CACHE_MAX_SIZE = "pref_cache_max_size";
    /** String: default maximum 'disk' cache size in MB */
    public static final String DEFAULT_CACHE_MAX_SIZE = "15";
    /** default maximum 'disk' cache size in MB */
    public static final long DEFAULT_CACHE_MAX_SIZE_MB = Long.parseLong(DEFAULT_CACHE_MAX_SIZE);
    /** String: maximum memory cache size in MB */
    public static final String PREF_MEM_CACHE_MAX_SIZE = "pref_mem_cache_max_size";
    /** String: default maximum memory cache size in MB (must be &gt; 0) */
    public static final String DEFAULT_MEM_CACHE_MAX_SIZE = "20";
    public static final int DEFAULT_MEM_CACHE_MAX_SIZE_MB = Integer.parseInt(DEFAULT_MEM_CACHE_MAX_SIZE);
    /** int: 0 automatic; 1 dark; 2 light; see {@link BackgroundSelection} */
    public static final String PREF_BACKGROUND = "pref_background";
    static final int BACKGROUND_AUTO = 0;
    static final int BACKGROUND_DARK = 1;
    public static final int BACKGROUND_LIGHT = 2;
    public static final String PREF_LOAD_OVER_MOBILE = "pref_load_over_mobile";
    public static final boolean DEFAULT_LOAD_OVER_MOBILE = true;
    public static final String PREF_LOAD_VIDEOS_OVER_MOBILE = "pref_load_videos_over_mobile";
    public static final boolean DEFAULT_LOAD_VIDEOS_OVER_MOBILE = false;
    /** boolean: open web links internally instead of posting an {@link Intent#ACTION_VIEW} intent */
    public static final String PREF_OPEN_LINKS_INTERNALLY = "pref_open_links_internally";
    /** String: DIRECT, HTTP or SOCKS */
    public static final String PREF_PROXY_TYPE = "pref_proxy_type";
    public static final String PREF_REGIONS = "pref_regions";
    /** boolean: controls whether time is displayed in a absolute way ("12.34.56 12:34") (false) or in a relative way ("10 minutes ago") (true) */
    public static final String PREF_TIME_MODE_RELATIVE = "pref_time_mode";
    public static final boolean PREF_TIME_MODE_RELATIVE_DEFAULT = true;
    /** boolean: ask before leaving the app */
    public static final String PREF_ASK_BEFORE_FINISH = "pref_ask_before_finish";
    /** int: 0 closes the app; 1 navigates to home category; 2 navigates to recent section; see {@link BackButtonBehaviour} */
    public static final String PREF_USE_BACK_IN_APP = "pref_use_back";
    /** int: number of columns shown on tablets in landscape orientation */
    public static final String PREF_MAIN_COLS_TABLET_LANDSCAPE = "pref_main_cols_tablet_landscape\uD83C\uDF54";
    /** int: number of columns shown on tablets in portrait orientation */
    public static final String PREF_MAIN_COLS_TABLET_PORTRAIT = "pref_main_cols_tablet_portrait";
    /** default number of columns shown on tablets in landscape orientation */
    public static final int PREF_MAIN_COLS_TABLET_LANDSCAPE_DEFAULT = 3;
    /** default number of columns shown on tablets in portrait orientation */
    public static final int PREF_MAIN_COLS_TABLET_PORTRAIT_DEFAULT = 2;
    /** boolean */
    public static final String PREF_WARN_MUTE = "pref_warn_mute";
    /** String: proxyserver:port */
    public static final String PREF_PROXY_SERVER = "pref_proxy_server";
    /** String set */
    public static final String PREF_FILTERS = "pref_filters";
    /** boolean */
    public static final String PREF_PLAY_INTRO = "pref_play_intro";
    /** boolean */
    public static final String PREF_POLL = "pref_poll";
    /** boolean */
    public static final String PREF_POLL_BREAKING_ONLY = "pref_poll_breaking_only";
    /** boolean */
    public static final String PREF_POLL_OVER_MOBILE = "pref_poll_over_mobile";
    /** String: polling interval in minutes */
    public static final String PREF_POLL_INTERVAL = "pref_poll_interval";
    /** String: polling interval during the night in minutes */
    public static final String PREF_POLL_INTERVAL_NIGHT = "pref_poll_interval_night";
    /** long: set to the current timestamp if scheduling the background job had failed */
    public static final String PREF_POLL_FAILED = "pref_poll_failed";
    public static final String PREF_POLL_NIGHT_START = "pref_poll_night_start";
    public static final String PREF_POLL_NIGHT_END = "pref_poll_night_end";
    /** boolean -  See <a href="https://en.wikipedia.org/wiki/Quotation_mark#German">here</a> */
    public static final String PREF_CORRECT_WRONG_QUOTATION_MARKS = "pref_correct_quotation_marks";
    public static final TimeZone TIMEZONE = TimeZone.getTimeZone("Europe/Berlin");
    static final String EXTRA_CRASH = "crash";
    /** back button behaviour: pressing back stops the app (respectively the Android default behaviour) */
    static final int USE_BACK_FINISH = 0;
    /** back button behaviour: pressing back navigates to the 'Home' category (if possible) */
    static final int USE_BACK_HOME = 1;
    /** back button behaviour: pressing back navigates to the most recent category (if possible) */
    static final int USE_BACK_BACK = 2;
    /** the AudioManager stream type to be used throughout the app */
    static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    /** default proxy port (if not set by user) */
    private static final int DEFAULT_PROXY_PORT = 80;
    /** used to build a preferences key to store the most recent update of a {@link Source} */
    private static final String PREFS_PREFIX_MOST_RECENT_UPDATE = "latest_";
    /** used to build a preferences key to store the most recent <em>user-initiated</em> update of a {@link Source} */
    private static final String PREFS_PREFIX_MOST_RECENT_MANUAL_UPDATE = "latest_manual_";
    private static final String TAG = "App";
    private static final Set<String> PERMITTED_HOSTS = new HashSet<>(27);

    static {
        String[] VERSIONS = new String[] {"2018080901", "2018102216"};
        String[] OSS = new String[] {"6.0.1", "7.0.1", "7.1.0", "7.1.1", "7.1.2", "8.0.0", "8.1.0", "9.0.0"};
        USER_AGENT = "Tagesschau/de.tagesschau (" + VERSIONS[(int)(Math.random() * VERSIONS.length)] + ", Android: " + OSS[(int)(Math.random() * OSS.length)] + ")";
    }

    private final Handler handler = new Handler();
    private final ScheduleChecker scheduleChecker = new ScheduleChecker();
    @Nullable
    private NotificationChannel notificationChannel;
    private NotificationChannel notificationChannelHiPri;
    @Nullable
    private Activity currentActivity;
    @Nullable
    private OkHttpClient client;

    /**
     * Creates an OkHttpClient instance.
     * @param ctx Context
     * @param cacheDir cache directory
     * @param maxSize the maximum number of bytes the cache should use to store
     * @return OkHttpClient
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
        // In autumn of 2018, TLS V1.2 was used
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
        SocketAddress sa = new InetSocketAddress(proxyServer, proxyPort);
        try {
            return new Proxy(Proxy.Type.valueOf(type), sa);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        return null;
    }

    /**
     * Returns the {@link android.support.v4.content.FileProvider FileProvider}.
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
    static synchronized boolean isHostAllowed(@Nullable final String host) {
        if (host == null) return false;
        for (String allowedHost : PERMITTED_HOSTS) {
            if (host.endsWith(allowedHost)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the current cache size in bytes. Method takes about 50 ms for a 15 MB cache.
     * @return current cache size in bytes
     */
    long getCurrentCacheSize() {
        return Util.getOccupiedSpace(Util.listFiles(getCacheDir()));
    }

    /**
     * @param source Source
     * @return the local file that the json data is stored in (does not necessarily exist)
     */
    @NonNull
    public File getLocalFile(@NonNull Source source) {
        return new File(getFilesDir(), source.toString() + ".source");
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
        return maxCacheSize >= 1_048_576L ? maxCacheSize : 1_048_576L;
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
    long getMostRecentUpdate(@NonNull Source source) {
        return PreferenceManager.getDefaultSharedPreferences(this).getLong(PREFS_PREFIX_MOST_RECENT_UPDATE + source, 0L);
    }

    /**
     * @return NotificationChannel
     */
    @Nullable
    NotificationChannel getNotificationChannel() {
        return this.notificationChannel;
    }

    NotificationChannel getNotificationChannelHiPri() {
        return this.notificationChannelHiPri;
    }

    /**
     * @return OkHttpClient
     */
    @NonNull
    public synchronized OkHttpClient getOkHttpClient() {
        if (this.client == null) {
            this.client = createOkHttpClient(this, getCacheDir(), getMaxDiskCacheSize());
        }
        return this.client;
    }

    /**
     * @return {@code true} if there is currently an Activity in the resumed state
     */
    boolean hasCurrentActivity() {
        return this.currentActivity != null;
    }

    /**
     * @return the interval in ms if there is a scheduled job with the id {@link UpdateJobService#JOB_ID}; 0 otherwise
     */
    private long isBackgroundJobScheduled() {
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
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityDestroyed(Activity activity) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityPaused(Activity activity) {
        if (activity != this.currentActivity) return;
        this.currentActivity = null;
        this.handler.postDelayed(this.scheduleChecker, 1_000L);
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityResumed(Activity activity) {
        this.handler.removeCallbacks(this.scheduleChecker);
        this.currentActivity = activity;
        if (activity instanceof MainActivity) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(UpdateJobService.NOTIFICATION_ID);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityStarted(Activity activity) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onActivityStopped(Activity activity) {
        /* no-op */
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        super.onCreate();

        /*
         Get {@link UserManager} once here to avoid a memory leak.
         Occurs in Android API >= 18 (Android 4.3)
         (reported by LeakCanary; introduced via https://github.com/android/platform_frameworks_base/commit/27db46850b708070452c0ce49daf5f79503fbde6)
         See https://github.com/square/leakcanary/blob/master/leakcanary-android/src/main/java/com/squareup/leakcanary/AndroidExcludedRefs.java
        */
        getSystemService(USER_SERVICE);

        de.freehamburger.util.Log.init(this);

        if (BuildConfig.DEBUG) {
            com.google.android.exoplayer2.util.Log.setLogLevel(com.google.android.exoplayer2.util.Log.LOG_LEVEL_WARNING);
            com.google.android.exoplayer2.util.Log.setLogStackTraces(true);
        } else {
            com.google.android.exoplayer2.util.Log.setLogLevel(com.google.android.exoplayer2.util.Log.LOG_LEVEL_OFF);
            com.google.android.exoplayer2.util.Log.setLogStackTraces(false);
        }

        PERMITTED_HOSTS.addAll(Util.loadResourceTextFile(this, R.raw.permitted_hosts, 27));

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            /** {@inheritDoc} */
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (BuildConfig.DEBUG) {
                    boolean isCurrentThread = Thread.currentThread().equals(t);
                    Log.wtf(TAG, "*** Uncaught Exception in "  + (isCurrentThread ? "current thread: " : "another thread: ") + e.toString(), e);
                    return;
                }

                // if the user has been using the app, restart it
                if (hasCurrentActivity()) {
                    try {
                        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        if (am != null) {
                            Intent mainActivityIntent = new Intent(App.this, MainActivity.class);
                            mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mainActivityIntent.putExtra(EXTRA_CRASH, true);
                            PendingIntent intent = PendingIntent.getActivity(getBaseContext(), 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
                            am.set(AlarmManager.RTC, System.currentTimeMillis() + 2_000L, intent);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                System.exit(-2);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                String channelStandard = getString(R.string.label_notification_channel);
                String channelHiPri = getString(R.string.label_notification_channel_hipri);

                this.notificationChannel = new NotificationChannel(channelStandard, channelStandard, NotificationManager.IMPORTANCE_DEFAULT);
                this.notificationChannel.setDescription(getString(R.string.label_notification_channel_desc));
                this.notificationChannel.enableLights(false);
                this.notificationChannel.setSound(null, null);
                this.notificationChannel.setShowBadge(false);
                nm.createNotificationChannel(this.notificationChannel);

                Uri ringtone = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
                this.notificationChannelHiPri = new NotificationChannel(channelHiPri, channelHiPri, NotificationManager.IMPORTANCE_HIGH);
                this.notificationChannelHiPri.setDescription(getString(R.string.label_notification_channel_desc_hipri));
                this.notificationChannelHiPri.enableLights(true);
                this.notificationChannelHiPri.setLightColor(getResources().getColor(R.color.colorAccent, getTheme()));
                this.notificationChannelHiPri.enableVibration(true);
                this.notificationChannelHiPri.setSound(ringtone, null);
                this.notificationChannelHiPri.setShowBadge(false);
                nm.createNotificationChannel(this.notificationChannelHiPri);
            }
        }

        registerActivityLifecycleCallbacks(this);

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        FileDeleter.run();

        scheduleStart();
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PREF_POLL.equals(key)) {
            boolean on = prefs.getBoolean(key, false);
            if (on) {
                scheduleStart();
            } else {
                scheduleStop();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onTrimMemory(int level) {
        if (level > android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onTrimMemory(" + level + ")");
            try {
                if (this.client != null) {
                    Util.close(this.client.cache());
                    this.client.dispatcher()
                            .executorService()
                            .shutdown();
                    // connectionPool().evictAll() might throw android.os.NetworkOnMainThreadException (@ okhttp3.internal.Util.closeQuietly(Util.java:155))
                    new Thread() {
                        @Override
                        public void run() {
                            client.connectionPool().evictAll();
                            client = null;
                        }
                    }.start();
                }
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) Log.e(TAG, t.toString(), t);
            }
        }
        super.onTrimMemory(level);
    }

    /**
     * Schedules automatic background updates.
     */
    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    private void scheduleStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
            long failedBefore = prefs.getLong(PREF_POLL_FAILED, 0L);
            if (failedBefore != 0L) {
                SharedPreferences.Editor ed = prefs.edit();
                ed.remove(PREF_POLL_FAILED);
                ed.apply();
            }
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Periodic job scheduled at " + DateFormat.getDateTimeInstance().format(new java.util.Date(System.currentTimeMillis())) + " for every " + (jobInfo.getIntervalMillis() / 60000) + " minutes");
            }
        }
    }

    /**
     * Cancels automatic background updates.
     */
    private void scheduleStop() {
        JobScheduler js = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        List<JobInfo> pending = js.getAllPendingJobs();
        for (JobInfo job : pending) {
            int id = job.getId();
            if (id == UpdateJobService.JOB_ID) {
                js.cancel(id);
                if (BuildConfig.DEBUG) Log.i(TAG, "Schedule for periodic job cancelled.");
                break;
            }
        }
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(UpdateJobService.NOTIFICATION_ID);
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

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BACKGROUND_AUTO, BACKGROUND_DARK, BACKGROUND_LIGHT})
    public @interface BackgroundSelection {}

    /**
     * Checks whether the {@link UpdateJobService background job} is scheduled.
     */
    private class ScheduleChecker implements Runnable {

        @Override
        public void run() {
            if (isBackgroundJobScheduled() == 0L) {
                if (BuildConfig.DEBUG) Log.w(TAG + '-' + ScheduleChecker.class.getSimpleName(), "Periodic job was not scheduled! Doing that now.");
                scheduleStart();
            }
        }
    }
}
