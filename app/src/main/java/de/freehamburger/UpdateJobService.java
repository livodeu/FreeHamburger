package de.freehamburger;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.SparseArray;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.freehamburger.adapters.NewsRecyclerAdapter;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.model.TextFilter;
import de.freehamburger.util.Downloader;
import de.freehamburger.util.Log;
import de.freehamburger.util.OkHttpDownloader;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView;
import de.freehamburger.widget.WidgetProvider;

/**
 * Performs periodic updates in the background.<br>
 * If a News is encountered that was previously unbeknownst to us, it is shown in a Notification.<br>
 * The size of the (unpacked, Content-Encoding gzip) json file will be about 400 to 500 kB while the actual amount of data transmitted will be around 70 kB.<br>
 * See <a href="https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129">here</a>.
 * <hr>
 * Note: This class may reveal (not cause!) a memory leak under Android 23;
 * see <a href="https://github.com/square/leakcanary/issues/2175">Leakcanary issue 2175</a>)
 */
public class UpdateJobService extends JobService implements Downloader.DownloaderListener, BlobParser.BlobParserListener {

    /** the time when the job was scheduled */
    @VisibleForTesting
    public static final String EXTRA_TIMESTAMP = "ts";
    /** int: {@link #JOB_SCHEDULE_DAY} or {@link #JOB_SCHEDULE_NIGHT} */
    @VisibleForTesting
    public static final String EXTRA_NIGHT = "night";
    /** int: contains a value other than 0 if the job is a "one-off" job, that is a non-repeating job */
    public static final String EXTRA_ONE_OFF = "once";
    @VisibleForTesting
    public static final int JOB_SCHEDULE_DAY = 0;
    @VisibleForTesting
    public static final int JOB_SCHEDULE_NIGHT = 1;
    /** Job id for the periodic job */
    static final int JOB_ID = "Hamburger".hashCode();
    static final String ACTION_CLEAR_NOTIFICATION = "de.freehamburger.action.clear_notification";
    /** the Intent action passed to {@link MainActivity} when the Notification is tapped */
    static final String ACTION_NOTIFICATION = "de.freehamburger.action.notification";
    /** long: timestamp when statistics collection started */
    static final String PREF_STAT_START = "pref_background_stat_start";
    /** long: number of bytes received since {@link #PREF_STAT_START} */
    static final String PREF_STAT_RECEIVED = "pref_background_received";
    /** int: number of jobs run */
    static final String PREF_STAT_COUNT = "pref_background_count";
    /** boolean: at least once we did not receive a Content-Length; therefore the byte count is just an estimation */
    static final String PREF_STAT_ESTIMATED = "pref_background_estimated";
    /** String Set: sources to load and display in notifications */
    static final String PREF_SOURCES_FOR_NOTIFICATIONS = "pref_background_sources";
    /** the default sources to load for notifications ({@link Source#HOME HOME} only) */
    static final Set<String> PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT = Collections.singleton(Source.HOME.name());
    /** if set, contains {@link News#getExternalId() News.externalId} - can be used to remove a Notification */
    static final String EXTRA_FROM_NOTIFICATION = BuildConfig.APPLICATION_ID + ".from_notification";
    /** if set, contains a Notification id (int) that can be used to {@link NotificationManager#cancel(int) cancel a Notification} */
    static final String EXTRA_NOTIFICATION_ID = BuildConfig.APPLICATION_ID + ".notification_id";
    /** if set, contains {@link Source#name() a Source name} */
    static final String EXTRA_SOURCE = BuildConfig.APPLICATION_ID + ".source";
    /** estimated byte count for one retrieval (average recorded over several months) */
    static final long ESTIMATED_NETWORK_BYTES = 72_000L;
    /** boolean: show extended notification instead of standard one (only possible from {@link Build.VERSION_CODES#N Nougat}) */
    @TargetApi(Build.VERSION_CODES.N)
    static final String PREF_NOTIFICATION_EXTENDED = "pref_notification_extended";
    @TargetApi(Build.VERSION_CODES.N)
    static final boolean PREF_NOTIFICATION_EXTENDED_DEFAULT = false;
    private static final int NOTIFICATION_ID_SUMMARY = 122;
    /** Notification id offset */
    private static final int NOTIFICATION_ID_OFFSET = 123;
    private static final String ACTION_DISABLE_POLLING = "de.freehamburger.action.disable_polling";
    /** wait this many milliseconds after loading the Source data (for additional images to load and notifications to be shown) */
    private static final long DELAY_TO_DESTRUCTION = 10_000L;
    /** String set: logs timestamps of all requests */
    private static final String PREF_STAT_ALL = "pref_stat_all";
    /** self-imposed minimum interval of 5 minutes regardless {@link JobInfo#getMinPeriodMillis() what Android says} */
    private static final int HARD_MINIMUM_INTERVAL_MINUTES = 5;
    private static final String TAG = "UpdateJobService";
    private static final BitmapFactory.Options BITMAPFACTORY_OPTIONS = new BitmapFactory.Options();
    /** to be added to values stored in {@link #PREF_STAT_ALL}<br><em>(DEBUG versions only!)</em> */
    private static final long ADD_TO_PREF_STAT_ALL_VALUE = 1_500_000_000_000L;
    /** remember that a particular News item has been shown in a notification for this period of time - if its {@link News#getExternalId() external id} should be reused after that, the News will be displayed again */
    private static final int REMEMBER_NOTIFICATIONS_FOR_THIS_MANY_HOURS = 48;
    /** goes into {@link Notification.Builder#setGroup(String)} */
    private static final String NOTIFICATION_GROUP_KEY = BuildConfig.APPLICATION_ID + ".bgn";
    /** contains News' external ids plus timestamps for News that have been shown in a notification - obsolete entries will be discarded in {@link #previouslyShownNewsLoad()} */
    private static final String NOTIFIED_NEWS_FILE = "notified.txt";
    /** separates a News' external id from a timestamp in {@link #NOTIFIED_NEWS_FILE} */
    private static final char NOTIFIED_NEWS_FILE_SEP = '@';
    private static int nextid = 1;

    static {
        BITMAPFACTORY_OPTIONS.inSampleSize = 2;
        BITMAPFACTORY_OPTIONS.inPreferQualityOverSpeed = false;
    }

    private final List<Filter> filters = new ArrayList<>();
    private final int id;
    /** controls access to {@link #NOTIFIED_NEWS_FILE} */
    private final Object notifiedLock = new Object();
    /** key: News' {@link News#getExternalId() external id}, value: timestamp of notification */
    private final Map<String, Long> previouslyShownNews = Collections.synchronizedMap(new HashMap<>());
    /** {@code true} while the list of previously shown News has not been modified, {@code false} if the list needs to be stored */
    private volatile boolean previouslyShownNewsStored = true;
    private ThreadPoolExecutor loaderExecutor;
    private JobParameters params;
    private long scheduledAt;
    private volatile boolean stopJobReceived;
    private int nextNotificationId = NOTIFICATION_ID_OFFSET;
    private NotificationSummary summary = null;

    /**
     * Constructor.
     */
    public UpdateJobService() {
        super();
        this.id = nextid++;
    }

    /**
     * Calculates the interval between periodic job executions.<br>
     * Takes both the minimum interval imposed by Android and the user's preferences into account.
     * @param prefs SharedPreferences
     * @param forNight {@code true} if the schedule is for nighttime
     * @return interval <em>in milliseconds</em>
     * @throws NullPointerException if {@code prefs} is {@code null}
     */
    @IntRange(from = 300_000)
    private static long calcInterval(@NonNull SharedPreferences prefs, boolean forNight) {
        final int minimumIntervalInMinutes = getMinimumIntervalInMinutes();
        //
        int intervalInMinutes;
        try {
            //noinspection ConstantConditions
            intervalInMinutes = Integer.parseInt(prefs.getString(forNight ? App.PREF_POLL_INTERVAL_NIGHT : App.PREF_POLL_INTERVAL, String.valueOf(minimumIntervalInMinutes)));
            // do not set to a longer interval than the rest of the night or day! (don't wait 7 hours at 5 o'clock!)
            float nowInHours = getCurrentTimeInHours();
            if (forNight) {
                float nightEnd = getNightEnd();   // 6f
                float minutesToNightEnd = (nightEnd - nowInHours) * 60f;   // at 4:30 this is 90
                if (minutesToNightEnd < 0) minutesToNightEnd = 1440 + minutesToNightEnd;
                if (intervalInMinutes > minutesToNightEnd) {
                    intervalInMinutes = Math.round(minutesToNightEnd + 1); // +1 is to make sure that we end in day
                    if (intervalInMinutes < minimumIntervalInMinutes) {
                        intervalInMinutes = minimumIntervalInMinutes;
                        if (BuildConfig.DEBUG) Log.w(TAG, "Could not cap interval to " + intervalInMinutes + " min. for night end because it'd be below " + minimumIntervalInMinutes);
                    } else {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Interval capped to " + intervalInMinutes + " minutes because night ends at " + nightEnd + " o'clock");
                    }
                }
            } else {
                float nightStart = getNightStart(); // 23f
                float minutesToNightStart = (nightStart - nowInHours) * 60f; // at 22:50 this is 10
                if (minutesToNightStart < 0) minutesToNightStart = 1440 + minutesToNightStart;
                if (intervalInMinutes > minutesToNightStart) {
                    intervalInMinutes = Math.round(minutesToNightStart + 1); // +1 is to make sure that we end in night
                    if (intervalInMinutes < minimumIntervalInMinutes) {
                        intervalInMinutes = minimumIntervalInMinutes;
                        if (BuildConfig.DEBUG) Log.w(TAG, "Could not cap interval to " + intervalInMinutes + " min. for night start because it'd be below " + minimumIntervalInMinutes);
                    } else {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Interval capped to " + intervalInMinutes + " minutes because night begins at " + nightStart + " o'clock");
                    }
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
            intervalInMinutes = minimumIntervalInMinutes;
        }
        return intervalInMinutes * 60_000L;
    }

    /**
     * Returns all currently visible Notifications issued by {@link UpdateJobService}.
     * @param ctx Context
     * @return array of notification ids
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @NonNull
    static int[] getAllNotifications(@NonNull Context ctx) {
        final StatusBarNotification[] sbs = ((NotificationManager)ctx.getSystemService(NOTIFICATION_SERVICE)).getActiveNotifications();
        if (sbs == null || sbs.length == 0) return new int[0];
        final List<Integer> ids = new ArrayList<>(sbs.length);
        for (StatusBarNotification sb : sbs) {
            if (sb.getId() >= NOTIFICATION_ID_SUMMARY) ids.add(sb.getId());
        }
        final int n = ids.size();
        final int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = ids.get(i);
        return a;
    }

    /**
     * <em>For DEBUG versions only!</em>
     * @param ctx Context
     * @return List of timestamps
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    static List<Long> getAllRequests(@NonNull Context ctx) {
        final Set<String> allRequests = PreferenceManager.getDefaultSharedPreferences(ctx).getStringSet(PREF_STAT_ALL, new HashSet<>(0));
        //noinspection ConstantConditions
        if (allRequests.isEmpty()) return new ArrayList<>(0);
        final List<Long> ar = new ArrayList<>(allRequests.size());
        for (String r : allRequests) {
            ar.add(Long.parseLong(r) + ADD_TO_PREF_STAT_ALL_VALUE);
        }
        //noinspection ComparatorCombinators
        Collections.sort(ar, (o1, o2) -> o2.compareTo(o1));
        return ar;
    }

    /**
     * Returns the current time of day in hours. For example, at 22:30 (10:30 PM) this method returns 22.5.
     * @return current time of day in hours
     */
    @FloatRange(from = 0f, to = 23.999722f)
    private static float getCurrentTimeInHours() {
        final Calendar now = new GregorianCalendar(App.TIMEZONE);
        return now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f + now.get(Calendar.SECOND) / 3600f;
    }

    /**
     * Returns the minimum interval in minutes, either {@link #HARD_MINIMUM_INTERVAL_MINUTES} or the {@link JobInfo#getMinPeriodMillis() value imposed by Android}.
     * @return minimum interval <em>in minutes</em>
     */
    static int getMinimumIntervalInMinutes() {
        // the minimum interval is set by Android
        int minimumIntervalInMinutes;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            minimumIntervalInMinutes = (int) (JobInfo.getMinPeriodMillis() / 60_000L);
            // self-imposed minimum
            if (minimumIntervalInMinutes < HARD_MINIMUM_INTERVAL_MINUTES) minimumIntervalInMinutes = HARD_MINIMUM_INTERVAL_MINUTES;
        } else {
            minimumIntervalInMinutes = 15;
        }
        return minimumIntervalInMinutes;
    }

    /**
     * @return duration of nighttime in hours
     */
    @FloatRange(from = 0f, to = 24f)
    static float getNightDuration() {
        float delta = getNightEnd() - getNightStart();
        if (delta < 0f) delta = 24f + delta;
        return delta;
    }

    /**
     * @return the point at which the night ends (in hours)
     */
    @FloatRange(from = 0f, to = 24f)
    private static float getNightEnd() {
        return 6f;
    }

    /**
     * @return the point at which the night starts (in hours)
     */
    @FloatRange(from = 0f, to = 24f)
    private static float getNightStart() {
        return 23f;
    }

    /**
     * Tells whether it is colder than outside.
     * @return {@code true} in the range of 23:00:00 to 05:59:59
     */
    @VisibleForTesting
    public static boolean hasNightFallenOverBerlin() {
        float hour = getCurrentTimeInHours();
        return hour >= getNightStart() || hour < getNightEnd();
    }

    /**
     * Creates a PendingIntent designed to open the given News in MainActivity.
     * @param app App
     * @param news News
     * @return PendingIntent
     * @throws NullPointerException if app is {@code null}
     */
    @NonNull
    static PendingIntent makeIntentForMainActivity(@NonNull App app, @Nullable News news, Source source) {
        final Intent mainIntent = new Intent(app, MainActivity.class);
        mainIntent.setAction(ACTION_NOTIFICATION);
        if (news != null) mainIntent.putExtra(EXTRA_FROM_NOTIFICATION, news.getExternalId());
        if (source != null) mainIntent.putExtra(EXTRA_SOURCE, source.name());
        @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(app, 0, mainIntent, flags);
    }

    /**
     * Creates a PendingIntent meant to be sent to the MainActivity.
     * Its Intent will carry the Action {@link MainActivity#ACTION_SHOW_NEWS}.
     * @param app Ap
     * @param news News
     * @param notificationId notification id
     * @return PendingIntent
     */
    @NonNull
    @VisibleForTesting
    public static PendingIntent makeIntentForStory(@NonNull App app, @NonNull News news, int notificationId) {
        final Intent showNewsIntent = new Intent(app, MainActivity.class);
        showNewsIntent.setAction(MainActivity.ACTION_SHOW_NEWS);
        showNewsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // the data attribute is not used - it is here to make the Intent unique
        showNewsIntent.setData(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/" + news.getId()));
        Bundle extras = new Bundle(2);
        extras.putSerializable(MainActivity.EXTRA_NEWS, news);
        extras.putInt(EXTRA_NOTIFICATION_ID, notificationId);
        showNewsIntent.putExtras(extras);
        @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getActivity(app, 0, showNewsIntent, flags);
    }

    /**
     * Creates a PendingIntent designed to open the given News in {@link VideoActivity}.
     * @param app App
     * @param news News
     * @return PendingIntent
     * @throws NullPointerException if either parameter is {@code null}
     */
    @NonNull
    private static PendingIntent makeIntentForVideoActivity(@NonNull App app, @NonNull News news) {
        final Intent viewVideoIntent = new Intent(app, VideoActivity.class);
        // the data attribute is not used - it is here to make the Intent unique
        viewVideoIntent.setData(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/" + news.getId()));
        viewVideoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        viewVideoIntent.putExtra(VideoActivity.EXTRA_NEWS, news);
        @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getActivity(app, 0, viewVideoIntent, flags);
    }

    /**
     * Creates a PendingIntent designed to open the given News in WebViewActivity.
     * @param app App
     * @param news News
     * @return PendingIntent
     * @throws NullPointerException if either parameter is {@code null}
     */
    @NonNull
    private static PendingIntent makeIntentForWebViewActivity(@NonNull App app, @NonNull News news) {
        final Intent viewWebIntent = new Intent(app, WebViewActivity.class);
        // the data attribute is not used - it is here to make the Intent unique
        viewWebIntent.setData(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/" + news.getId()));
        viewWebIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        viewWebIntent.putExtra(WebViewActivity.EXTRA_NEWS, news);
        @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getActivity(app, 0, viewWebIntent, flags);
    }

    /**
     * Creates a PendingIntent to disable background updates.
     * @param app App
     * @return PendingIntent
     */
    @NonNull
    static PendingIntent makeIntentToDisable(@NonNull App app) {
        @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_ONE_SHOT;
        return PendingIntent.getService(app, 1, new Intent(ACTION_DISABLE_POLLING, null, app, UpdateJobService.class), flags);
    }

    /**
     * Generates a JobInfo that can be used to schedule the periodic job.<br>
     * <em>The returned JobInfo should be used immediately as it contains a timestamp.</em>
     * @param ctx Context
     * @return JobInfo
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    @RequiresPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
    static JobInfo makeJobInfo(@NonNull Context ctx) {
        final boolean night = hasNightFallenOverBerlin();
        final long intervalMs = calcInterval(PreferenceManager.getDefaultSharedPreferences(ctx), night);
        final JobInfo.Builder jib = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                .setPeriodic(intervalMs)
                // JobInfo.NETWORK_TYPE_UNMETERED is not a reliable equivalent for Wifi => decision whether to actually poll is deferred to onStartJob()
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(120_000L, JobInfo.BACKOFF_POLICY_LINEAR)
                .setPersisted(true);    // <- this one needs RECEIVE_BOOT_COMPLETED permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            jib.setEstimatedNetworkBytes(ESTIMATED_NETWORK_BYTES, 250L);
            jib.setPrefetch(true);
        }
        PersistableBundle extras = new PersistableBundle(2);
        extras.putLong(EXTRA_TIMESTAMP, System.currentTimeMillis());
        extras.putInt(EXTRA_NIGHT, night ? JOB_SCHEDULE_NIGHT : JOB_SCHEDULE_DAY);
        jib.setExtras(extras);
        return jib.build();
    }

    /**
     * Generates a JobInfo that can be used to schedule a one-time job.
     * @param ctx Context
     * @param expedited the {@link JobInfo.Builder#setExpedited(boolean) expedited} flag, ignored below Android S
     * @return JobInfo
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static JobInfo makeOneOffJobInfo(@NonNull Context ctx, boolean expedited) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) expedited = false;
        final JobInfo.Builder jib = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                // JobInfo.NETWORK_TYPE_UNMETERED is not a reliable equivalent for Wifi => decision whether to actually poll is deferred to onStartJob()
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                ;
        if (!expedited) jib.setOverrideDeadline(10_000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!expedited) jib.setImportantWhileForeground(true);
            jib.setEstimatedNetworkBytes(ESTIMATED_NETWORK_BYTES, 250L);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            jib.setExpedited(expedited);
        }
        PersistableBundle extras = new PersistableBundle(1);
        extras.putInt(EXTRA_ONE_OFF, 1);
        jib.setExtras(extras);
        return jib.build();
    }

    /**
     * Generates a JobInfo that can be used to schedule a one-time job.
     * @param ctx Context
     * @param delayMin minimum delay in ms
     * @param delayMax maximum deldy in ms
     * @return JobInfo
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static JobInfo makeOneOffJobInfoWithDelay(@NonNull Context ctx, long delayMin, long delayMax) {
        final JobInfo.Builder jib = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                // JobInfo.NETWORK_TYPE_UNMETERED is not a reliable equivalent for Wifi => decision whether to actually poll is deferred to onStartJob()
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                ;
        if (delayMin > delayMax) {long h = delayMin; delayMin = delayMax; delayMax = h;}
        jib.setMinimumLatency(delayMin).setOverrideDeadline(delayMax);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            jib.setImportantWhileForeground(true);
            jib.setEstimatedNetworkBytes(ESTIMATED_NETWORK_BYTES, 250L);
        }
        PersistableBundle extras = new PersistableBundle(1);
        extras.putInt(EXTRA_ONE_OFF, 1);
        jib.setExtras(extras);
        return jib.build();
    }

    /**
     * Determines whether the job needs to be rescheduled because it was scheduled during daytime and now it is night-time or vice versa.
     * @param params JobParameters
     * @return {@code true} if the job needs to be rescheduled
     * @throws NullPointerException if {@code params} is {@code null}
     */
    @VisibleForTesting()
    public static boolean needsReScheduling(@NonNull Context ctx, @NonNull JobParameters params) {
        if (params.getExtras().getInt(EXTRA_ONE_OFF, 0) != 0) return false;
        final boolean jobWasScheduledForNight = params.getExtras().getInt(EXTRA_NIGHT, JOB_SCHEDULE_DAY) == JOB_SCHEDULE_NIGHT;
        final boolean isNight = hasNightFallenOverBerlin();
        boolean yes = (isNight != jobWasScheduledForNight);
        if (!yes) {
            // the simple comparison above does not consider the case that it's now just before the day-night or night-day switch
            // like, for example, at 5:54 => this is still night so we might apply a 420 minutes interval => next update near 13:00!
            final float nowInHours = getCurrentTimeInHours();
            //noinspection ConstantConditions
            int intervalInMinutes = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString(jobWasScheduledForNight ? App.PREF_POLL_INTERVAL_NIGHT : App.PREF_POLL_INTERVAL, String.valueOf(getMinimumIntervalInMinutes())));
            float intervalInHours = intervalInMinutes / 60f;
            float nightStart = getNightStart();
            float nightEnd = getNightEnd();
            if (nowInHours < nightStart && nowInHours + intervalInHours >= nightStart + 10) {
                // 22:55 + 15 minutes = 23:10
                yes = true;
                if (BuildConfig.DEBUG) Log.i(TAG, "Job needs rescheduling: (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ") - night will begin in " + (60 * (nightStart - nowInHours)) + " min.");
            } else if (nowInHours < nightEnd && nowInHours + intervalInHours >= nightEnd + 10) {
                // e.g. 5:55 + 420 minutes = 12:55
                yes = true;
                if (BuildConfig.DEBUG) Log.i(TAG, "Job needs rescheduling: (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ") - day will begin in " + (60 * (nightEnd - nowInHours)) + " min.");
            }
        }
        return yes;
    }

    /**
     * Resets the data usage statistics.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    static void resetStatistics(@NonNull Context ctx) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .remove(PREF_STAT_START)
                .remove(PREF_STAT_RECEIVED)
                .remove(PREF_STAT_COUNT)
                .remove(PREF_STAT_ESTIMATED)
                .apply();
    }

    /**
     * Attempts to apply a {@link Notification.Builder#setCustomBigContentView(RemoteViews) "custom big content view"}
     * and a {@link Notification.DecoratedCustomViewStyle} to the given Notification.Builder.<br>
     * Catches all Exceptions and just returns {@code false} in case of an error.
     * @param builder Notification.Builder
     * @param news News
     * @param largeIcon Bitmap (optional)
     * @return {@code true} / {@code false} to indicate success or failure
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean applyCustomView(@NonNull final Notification.Builder builder, @NonNull final News news, @Nullable final Bitmap largeIcon) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            @LayoutRes int layout = NewsRecyclerAdapter.getViewType(news);
            final RemoteViews notificationLayout = new RemoteViews(getPackageName(), layout);
            final String ntopline = news.getTopline();
            final String ntitle = news.getTitle();
            final String nfirstSentence = news.getFirstSentence();
            notificationLayout.setTextViewText(R.id.textViewTopline, !TextUtils.isEmpty(ntopline) ? ntopline : ntitle);
            notificationLayout.setTextViewText(R.id.textViewTitle, !TextUtils.isEmpty(ntopline) ? ntitle : "");
            notificationLayout.setTextViewText(R.id.textViewFirstSentence, !TextUtils.isEmpty(nfirstSentence) ? nfirstSentence : news.getShorttext());
            notificationLayout.setTextViewText(R.id.textViewDate, NewsView.getRelativeTime(this, news.getDate(), null));
            if (largeIcon != null) notificationLayout.setImageViewBitmap(R.id.imageView, largeIcon);
            else notificationLayout.setViewVisibility(R.id.imageView, android.view.View.GONE);
            builder.setCustomBigContentView(notificationLayout);
            builder.setLargeIcon((Bitmap)null);
            builder.setStyle(new Notification.DecoratedCustomViewStyle());
            return true;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, e.toString(), e);
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void blobParsed(@Nullable final Blob blob, boolean ok, @Nullable Throwable oops) {
        if (this.stopJobReceived) {
            if (BuildConfig.DEBUG) Log.w(TAG + id, "BlobParser cancelled!");
            done();
            return;
        }
        if (!ok || blob == null || oops != null) {
            if (BuildConfig.DEBUG && oops != null) Log.e(TAG + id, "Parsing failed: " + oops);
            return;
        }
        // get the list of news which is sorted so that the newest news is at the top
        final List<News> jointList = blob.getAllNews();
        if (jointList.isEmpty()) return;
        //
        App app = (App) getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean breakingOnly = prefs.getBoolean(App.PREF_POLL_BREAKING_ONLY, App.PREF_POLL_BREAKING_ONLY_DEFAULT);
        //@Nullable final String latestNewsExtId = restoreLatestNews(blob.getSource());
        // get the time when the user has updated the news list himself/herself most recently, so that we know what the user has already seen
        final long latestSourceStateSeenByUser = app.getMostRecentManualUpdate(blob.getSource());

        // let it all run even if the Blob's source is meant to be displayed in a widget instead of a notification - notify(News, Source, Bitmap) will take care of that
        News newsToDisplay = null;
        for (News n : jointList) {
            // decision: skip weather (at the time of writing this, weather is the only news with no 'externalId' set)
            // reason for that decision is that the weather news is updated much more frequently than others so it would be a bit like notification spam…
            if (n.getExternalId() == null) {
                continue;
            }
            // compare the news' timestamp with the time when the user has refreshed the data
            if (latestSourceStateSeenByUser > 0L) {
                Date newsDate = n.getDate();
                if (newsDate != null && newsDate.getTime() < latestSourceStateSeenByUser) {
                    // the News is too old - it was already present when the user most recently updated the data
                    continue;
                }
            }
            // do we want only breaking news?
            if (breakingOnly && !n.isBreakingNews()) {
                continue;
            }
            // let the filters have a look at it...
            if (Filter.refusedByAny(this.filters, n)) {
                continue;
            }
            newsToDisplay = n;
            break;
        }

        if (newsToDisplay == null) {
            // Got list of news in the background but it did not contain anything newsworthy
            return;
        }

        if (newsToDisplay.getExternalId() != null) {
            if (this.previouslyShownNews.containsKey(newsToDisplay.getExternalId())) {
                if (BuildConfig.DEBUG) Log.i(TAG + id, "As News \"" + newsToDisplay.getExternalId() + "\" has already been shown, it will be skipped now.");
                return;
            }
            this.previouslyShownNews.put(newsToDisplay.getExternalId(), System.currentTimeMillis());
            this.previouslyShownNewsStored = false;
            if (BuildConfig.DEBUG) Log.i(TAG + id, "News \"" + newsToDisplay.getExternalId() + "\" has now been shown in a notification.");
        }

        TeaserImage teaserImage = newsToDisplay.getTeaserImage();
        if (teaserImage != null) {
            TeaserImage.MeasuredImage mi = teaserImage.getBestImageForWidth(Util.getDisplaySize(this).x, News.NEWS_TYPE_VIDEO.equals(newsToDisplay.getType()));
            String imageUrl = mi != null && mi.url != null ? mi.url : null;
            if (imageUrl != null && Util.isNetworkAvailable(app)) {
                try {
                    final News finalCopy = newsToDisplay;
                    final File temp = File.createTempFile("temp", ".jpg");
                    loadFile(imageUrl, temp, (completed, result) -> {
                        Bitmap bm = (completed && result != null && result.file != null && result.rc < 400) ? BitmapFactory.decodeFile(result.file.getAbsolutePath(), BITMAPFACTORY_OPTIONS) : null;
                        notify(finalCopy, blob.getSource(), bm);
                        Util.deleteFile(temp);
                    });
                    return;
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG + id, e.toString());
                }
            }
        }
        notify(newsToDisplay, blob.getSource(), null);
    }

    /**
     * Calls {@link #jobFinished(JobParameters, boolean) jobFinished(JobParameters, false)}.
     */
    private void done() {
        previouslyShownNewsUpdate();
        if (this.params == null) return;
        jobFinished(this.params, false);
        this.params = null;
    }

    /** {@inheritDoc} */
    @Override
    public void downloaded(boolean completed, @Nullable Downloader.Result result) {
        if (!completed || result == null || result.file == null || result.rc != HttpURLConnection.HTTP_OK) {
            if (BuildConfig.DEBUG) {
                if (result != null) {
                    String errMsg = "Download of \"" + result.sourceUri + "\": HTTP " + result.rc + " " + result.msg;
                    if (result.rc < 400) Log.i(TAG + id, errMsg); else Log.w(TAG + id, errMsg);
                    //TODO if (result.rc >= 500) retry soon (example: java.net.ConnectException: Failed to connect to www.tagesschau.de/104.111.238.138:443)
                } else {
                    Log.w(TAG + id, "Download: No result");
                }
            }
            return;
        }
        // update statistics
        long now = System.currentTimeMillis();
        App app = (App) getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        long tsStatisticsStart = prefs.getLong(PREF_STAT_START, 0L);
        int numberOfJobsSoFar = prefs.getInt(PREF_STAT_COUNT, 0);
        long bytesReceivedSoFar = prefs.getLong(PREF_STAT_RECEIVED, 0L);
        SharedPreferences.Editor editor = prefs.edit();
        if (tsStatisticsStart == 0L) editor.putLong(PREF_STAT_START, now);
        editor.putInt(PREF_STAT_COUNT, numberOfJobsSoFar + 1);
        if (result.contentLength <= 0L) {
            editor.putBoolean(PREF_STAT_ESTIMATED, true);
            editor.putLong(PREF_STAT_RECEIVED, bytesReceivedSoFar + ESTIMATED_NETWORK_BYTES);
        } else {
            editor.putLong(PREF_STAT_RECEIVED, bytesReceivedSoFar + result.contentLength);
        }
        editor.apply();
        Source source = Source.getSourceFromFile(result.file);
        if (source != null) {
            app.setMostRecentUpdate(source, now, false);
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "Could not determine source from " + result.file);
        }
        //
        if (this.stopJobReceived) {
            // Job stopped before parser could run
            done();
            return;
        }
        new BlobParser(app, this).executeOnExecutor(THREAD_POOL_EXECUTOR, result.file);
    }

    /**
     * Loads a remote resource.
     * @param url Url to load from
     * @param localFile local file to save to
     * @param listener DownloaderListener
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    private void loadFile(@NonNull String url, @NonNull File localFile, @NonNull Downloader.DownloaderListener listener) {
        OkHttpDownloader downloader = new OkHttpDownloader(this);
        try {
            App app = (App) getApplicationContext();
            if (this.loaderExecutor == null || this.loaderExecutor.isShutdown()) {
                downloader.execute(new Downloader.Order(url, localFile.getAbsolutePath(), 0L, true, listener));
            } else {
                downloader.executeOnExecutor(this.loaderExecutor, new Downloader.Order(url, localFile.getAbsolutePath(), 0L, true, listener));
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "loadFile(\"" + url + "\", …, …) failed: " + e, e);
            listener.downloaded(false, null);
        }
    }

    /**
     * Loads data for a given Source.
     * @param source Source
     * @param listener DownloaderListener to notify about the download
     * @return true if the download has been initiated successfully
     * @throws NullPointerException if any parameter is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    private boolean loadFile(@NonNull Source source, @NonNull Downloader.DownloaderListener listener) {
        App app = (App) getApplicationContext();
        String url = source.getUrl();
        if (source == Source.REGIONAL) {
            url = url + Source.getParamsForRegional(this);
        }
        OkHttpDownloader downloader = new OkHttpDownloader(this);
        try {
            downloader.executeOnExecutor(this.loaderExecutor,
                    new Downloader.Order(url, app.getLocalFile(source).getAbsolutePath(), app.getMostRecentUpdate(source), true, listener));
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "loadFile(" + source + ", …) failed: " + e);
            listener.downloaded(false, null);
            return false;
        }
        return true;
    }

    /**
     * Displays the given News in a Notification.
     * @param news News
     * @param source Source that the News originated from
     * @param largeIcon optional picture
     * @throws NullPointerException if {@code news} or {@code source} are {@code null}
     */
    @SuppressLint("NewApi")
    private void notify(@NonNull final News news, @NonNull final Source source, @Nullable Bitmap largeIcon) {
        if (BuildConfig.DEBUG) Log.i(TAG + id, "notify(\"" + news.getTitle()  + "\" (" + news.getExternalId() + "), " + source + ", …)");
        App app = (App) getApplicationContext();
        NotificationManager nm = (NotificationManager) app.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        // check whether a News from the given Source should be displayed in a Notification
        Set<String> sourcesForNotifications = prefs.getStringSet(PREF_SOURCES_FOR_NOTIFICATIONS, PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT);
        if (sourcesForNotifications == null || !sourcesForNotifications.contains(source.name())) {
            // at this point, the teaserImage may be passed on to the Waiter, "who" could pass it on to the WidgetProvider
            // if (news.getExternalId() != null && largeIcon != null) this.waiter.bitmapCache.put(news.getExternalId(), largeIcon);
            return;
        }
        // prepare notification summary
        if (this.summary == null) this.summary = new NotificationSummary(); else this.summary.increase();
        // show extended notification?
        boolean applyExtendedStyle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && prefs.getBoolean(PREF_NOTIFICATION_EXTENDED, PREF_NOTIFICATION_EXTENDED_DEFAULT);
        //
        long when = news.getDate() != null ? news.getDate().getTime() : 0L;
        String title, content, bigtext;
        title = news.getTopline();
        if (TextUtils.isEmpty(title)) {
            title = news.getTitle();
            content = news.getFirstSentence();
            bigtext = null;
        } else {
            content = news.getTitle();
            bigtext = news.getFirstSentence();
            if (TextUtils.isEmpty(content)) {
                content = news.getFirstSentence();
                bigtext = news.getShorttext();
            }
        }
        if (TextUtils.isEmpty(content)) {
            content = news.getShorttext();
            bigtext = null;
        }

        final PendingIntent contentIntent;
        final String type = news.getType() != null ? news.getType() : "";
        switch (type) {
            case News.NEWS_TYPE_STORY:
                contentIntent = makeIntentForStory(app, news, this.nextNotificationId);
                break;
            case News.NEWS_TYPE_VIDEO:
                contentIntent = makeIntentForVideoActivity(app, news);
                break;
            case News.NEWS_TYPE_WEBVIEW:
                contentIntent = makeIntentForWebViewActivity(app, news);
                break;
            default:
                contentIntent = makeIntentForMainActivity(app, news, source);
                if (BuildConfig.DEBUG) Log.w(TAG + id, "Intent for " + news.getTitle() + " is for MainActivity - news type is \"" + type + "\"");
        }
        final Notification.Builder builder = new Notification.Builder(app)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText(getString(source.getLabel())) // on Android 8, subText would appear in the very top line between "Hamburger" and the 'when' value
                .setContentTitle(title)
                .setContentText(content)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setSortKey(String.valueOf((char)('A' + source.ordinal()))) // this assigns 'A' to HOME and ascending chars to the other ones
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                // according to the docs, the category is used to determine whether to disturb the user in "Do Not Disturb mode"
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                // the notification might be updated (see below) when a News is delivered in more than one category; therefore "alert only once"
                .setOnlyAlertOnce(true)
                .setOngoing(false);

        boolean extendedStyleApplied = applyExtendedStyle && applyCustomView(builder, news, largeIcon);
        if (!extendedStyleApplied) {
            if (largeIcon != null) builder.setLargeIcon(largeIcon);
            if (!TextUtils.isEmpty(bigtext)) {
                builder.setStyle(new Notification.BigTextStyle()
                                .bigText(bigtext)
                                .setBigContentTitle(title)
                                // .setSummaryText() here affects the same area as setSubText() in the std. notification
                                );
            } else if (largeIcon != null) {
                builder.setStyle(new Notification.BigPictureStyle()
                        .bigLargeIcon((Bitmap) null)
                        .bigPicture(largeIcon)
                        .setBigContentTitle(title)
                        .setSummaryText(content));
            }
        }

        if (when > 0L) {
            builder.setWhen(when).setShowWhen(true);
        }

        NotificationChannel nc = null;
        if (news.isBreakingNews()) {
            this.summary.setHipri();
            builder.setColor(Util.getColor(this, R.color.colorAccent));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setColorized(true);
                nc = app.getNotificationChannelHiPri();
            } else {
                builder.setPriority(Notification.PRIORITY_HIGH);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nc = app.getNotificationChannel();
            } else {
                builder.setPriority(Notification.PRIORITY_DEFAULT);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nc != null) {
            builder.setChannelId(nc.getId());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setAllowSystemGeneratedContextualActions(false);
        }
        // check existing notifications for one with the same tag (= external id attribute of a News)
        final StatusBarNotification[] active = nm.getActiveNotifications();
        for (StatusBarNotification sbn : active) {
            String extId = sbn.getTag();
            if (extId != null && extId.equals(news.getExternalId())) {
                Notification existing = sbn.getNotification();
                // add source label to existing one, so it will be like "Nachrichten, Sport" or similar
                existing.extras.putCharSequence(Notification.EXTRA_SUB_TEXT, existing.extras.getCharSequence(Notification.EXTRA_SUB_TEXT) + ", " + getString(source.getLabel()));
                // update existing notification
                nm.notify(extId, sbn.getId(), existing);
                if (BuildConfig.DEBUG) Log.i(TAG + id, "News \"" + news.getTitle() + "\" appears more than once.");
                // notification updated, nothing else to do here
                return;
            }
        }
        nm.notify(news.getExternalId(), this.nextNotificationId++, builder.build());
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        super.onCreate();
        previouslyShownNewsLoad();
        this.loaderExecutor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        this.loaderExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        this.filters.addAll(TextFilter.createTextFiltersFromPreferences(this));
        //
        try {
            BITMAPFACTORY_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String cs = PreferenceManager.getDefaultSharedPreferences(this).getString(App.PREF_COLORSPACE, ColorSpace.Named.SRGB.name());
                BITMAPFACTORY_OPTIONS.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.valueOf(cs));
            }
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, t.toString(), t);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG) {
            if (!previouslyShownNewsStored && id > 1) Log.w(TAG + id, "onDestroy(): previously shown news not stored!");
        }

        if (this.loaderExecutor != null) {
            this.loaderExecutor.shutdown();
            this.loaderExecutor = null;
        }
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_CLEAR_NOTIFICATION.equals(intent.getAction())) {
                removeNotification();
                stopSelf(startId);
                return START_REDELIVER_INTENT;
            }
            if (ACTION_DISABLE_POLLING.equals(intent.getAction())) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor ed = prefs.edit();
                ed.putBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
                ed.apply();
                removeNotification();
                Toast.makeText(getApplicationContext(), R.string.msg_background_inactive, Toast.LENGTH_LONG).show();
                stopSelf(startId);
                return START_REDELIVER_INTENT;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onStartJob(JobParameters params) {
        if (BuildConfig.DEBUG) Log.i(TAG + id, "onStartJob(…)");
        this.params = params;
        App app = (App) getApplicationContext();
        // determine whether this is the periodic job or a one-time job
        final boolean oneOff = params.getExtras().getInt(EXTRA_ONE_OFF, 0) != 0;
        if (params.getJobId() == JOB_ID && app.hasCurrentActivity() && !oneOff) {
            // app is currently active; nothing to do here for now
            if (BuildConfig.DEBUG) Log.i(TAG, "onStartJob(…) bailing out due to current activity");
            done();
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        boolean poll = prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
        boolean loadOverMobile = prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE);
        boolean pollOverMobile = prefs.getBoolean(App.PREF_POLL_OVER_MOBILE, App.PREF_POLL_OVER_MOBILE_DEFAULT);
        if (!poll || (!loadOverMobile || !pollOverMobile) && Util.isNetworkMobile(app)) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onStartJob(…) bailing out due to mobile network");
            done();
            return false;
        }
        //
        if (!oneOff) {
            long ts = params.getExtras().getLong(EXTRA_TIMESTAMP, 0L);
            if (ts > 0L) this.scheduledAt = ts;
            if (this.scheduledAt == 0L) this.scheduledAt = System.currentTimeMillis();
        }
        //
        if (BuildConfig.DEBUG) {
            if (oneOff) {
                Log.i(TAG + id, "Hamburger update by one-time job");
            } else {
                //noinspection ConstantConditions
                Set<String> allRequests = new HashSet<>(prefs.getStringSet(PREF_STAT_ALL, new HashSet<>(0)));
                allRequests.add(String.valueOf(System.currentTimeMillis() - ADD_TO_PREF_STAT_ALL_VALUE));
                SharedPreferences.Editor ed = prefs.edit();
                ed.putStringSet(PREF_STAT_ALL, allRequests);
                ed.apply();
            }
        }
        if (needsReScheduling(this, params)) {
            reschedule();
        }
        //
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
        //
        boolean atLeastOneSourceLoading = false;
        // get sources for notifications
        @NonNull final Set<String> sourceNames = new HashSet<>(Objects.requireNonNull(prefs.getStringSet(PREF_SOURCES_FOR_NOTIFICATIONS, PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT)));
        // add sources for widgets
        SparseArray<Source> widgetSources = WidgetProvider.loadWidgetSources(this);
        for (int i = 0; i < widgetSources.size(); i++) {
            sourceNames.add(widgetSources.valueAt(i).name());
        }
        //
        for (String sourceName : sourceNames) {
            Source source = Source.valueOf(sourceName);
            if (source.getLockHolder() != null) {
                if (BuildConfig.DEBUG) Log.w(TAG + id, "Skipping Source " + source + " because it is locked!");
                continue;
            }
            atLeastOneSourceLoading |= loadFile(source, this);
        }
        if (!atLeastOneSourceLoading) {
            if (BuildConfig.DEBUG) Log.i(TAG + id, "No Source loading - finishing.");
            done();
            return false;
        }
        Waiter waiter = new Waiter();
        waiter.start();
        return true;
    }

    /** {@inheritDoc}
     * <hr>
     * <ul>
     * <li>This seems to be called on the UI thread.</li>
     * <li>This seems to be called, if at all, a few (ca. 5) seconds after {@link #onStartJob(JobParameters) onStartJob()}</li>
     * <li>{@link #onDestroy()} will be called a few ms afterwards</li>
     * </ul>
     */
    @SuppressLint({"WrongConstant", "ParcelClassLoader", "SwitchIntDef"})
    @Override
    public boolean onStopJob(JobParameters params) {
        this.stopJobReceived = true;
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int reason = params.getStopReason();
            String reasonMsg;
            switch (reason) {
                case JobParameters.STOP_REASON_APP_STANDBY: reasonMsg = "app standby"; break;
                case JobParameters.STOP_REASON_BACKGROUND_RESTRICTION: reasonMsg = "background restriction"; break;
                case JobParameters.STOP_REASON_CANCELLED_BY_APP: reasonMsg = "cancelled by app"; break;
                case JobParameters.STOP_REASON_DEVICE_STATE: reasonMsg = "device state"; break;
                case JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY: reasonMsg = "connectivity"; break;
                case JobParameters.STOP_REASON_CONSTRAINT_DEVICE_IDLE: reasonMsg = "device idle"; break;
                case JobParameters.STOP_REASON_TIMEOUT: reasonMsg = "timeout"; break;
                default: reasonMsg = String.valueOf(reason);
            }
            Log.w(TAG + id, "onStopJob(): reason for stopping the job is: " + reasonMsg);
        }
        return false;
    }

    /**
     * Loads all entries from {@link #NOTIFIED_NEWS_FILE}.
     * Also removes old (and therefore obsolete) entries.
     */
    private void previouslyShownNewsLoad() {
        File file = new File(getFilesDir(), NOTIFIED_NEWS_FILE);
        if (!file.isFile()) {
            return;
        }
        synchronized (this.notifiedLock) {
            final long now = System.currentTimeMillis();
            // keep all entries that are younger than 48 hours
            final long maxAge = REMEMBER_NOTIFICATIONS_FOR_THIS_MANY_HOURS * 3_600_000L;
            File tmp = null;
            BufferedReader reader = null;
            BufferedWriter writer = null;
            boolean ok = false;
            int removed = 0;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                for (; ; ) {
                    String line = reader.readLine();
                    if (line == null) break;
                    int at = line.lastIndexOf(NOTIFIED_NEWS_FILE_SEP);
                    long timestamp = Long.parseLong(line.substring(at + 1));
                    if (now - timestamp < maxAge) {
                        // News has been shown quite recently -> keep
                        this.previouslyShownNews.put(line.substring(0, at), timestamp);
                        if (writer == null) {
                            tmp = File.createTempFile("tmp", ".txt");
                            writer = new BufferedWriter(new FileWriter(tmp, false));
                        }
                        writer.write(line);
                        writer.newLine();
                    } else {
                        // News had been shown quite some time ago -> discard
                        removed++;
                    }
                }
                Util.close(writer, reader);
                writer = null; reader = null;
                if (removed == 0) {
                    // should not be necessary, but you never know…
                    Util.deleteFile(tmp);
                    ok = true;
                } else {
                    ok = tmp != null && tmp.renameTo(file);
                    if (!ok) throw new RuntimeException("Failed to rename " + tmp + " to " + file);
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG + id, "While loading/cleaning " + file + ": " + e);
            } finally {
                Util.close(writer, reader);
                if (!ok) Util.deleteFile(tmp);
            }
        }
    }

    /**
     * Writes {@link #previouslyShownNews} to {@link #NOTIFIED_NEWS_FILE}.
     */
    @AnyThread
    private void previouslyShownNewsUpdate() {
        if (this.previouslyShownNewsStored) {
            return;
        }
        synchronized (this.notifiedLock) {
            BufferedWriter writer = null;
            File file = new File(getFilesDir(), NOTIFIED_NEWS_FILE);
            File temp = null;
            try {
                temp = File.createTempFile("tmp", ".txt");
                writer = new BufferedWriter(new FileWriter(temp, false));
                final Set<Map.Entry<String, Long>> entries = this.previouslyShownNews.entrySet();
                for (Map.Entry<String, Long> entry : entries) {
                    writer.write(entry.getKey() + NOTIFIED_NEWS_FILE_SEP + entry.getValue());
                    writer.newLine();
                }
                this.previouslyShownNewsStored = temp.renameTo(file);
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG + id, "While storing " + NOTIFIED_NEWS_FILE + ": " + e);
            } finally {
                Util.close(writer);
                Util.deleteFile(temp);
            }
        }
    }

    /**
     * Removes the notification.
     */
    void removeNotification() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.cancel(NOTIFICATION_ID_OFFSET);
    }

    /**
     * Reschedules the job; see {@link #makeJobInfo(Context)}.
     */
    private void reschedule() {
        JobScheduler js = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo ji = makeJobInfo(this);
        if (js.schedule(ji) == JobScheduler.RESULT_SUCCESS) {
            if (BuildConfig.DEBUG) Log.i(TAG + id, "Job has been rescheduled to run every " + (ji.getIntervalMillis() / 60_000L) + " minutes.");
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "Failed to reschedule job!");
        }
    }

    /**
     * Holds information about a group summary notification.
     */
    static class NotificationSummary {
        private int count = 1;
        private boolean hipri;

        /**
         * Builds the summary Notification.
         * @param app App
         * @return Notification
         * @throws NullPointerException if {@code app} is {@code null}
         */
        @NonNull
        Notification build(@NonNull final App app) {
            Intent mainIntent = new Intent(app, MainActivity.class);
            mainIntent.setAction(ACTION_NOTIFICATION);
            mainIntent.putExtra(EXTRA_SOURCE, Source.HOME.name());
            @SuppressLint("InlinedApi") int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(app, 0, mainIntent, flags);
            Notification.Builder builder = new Notification.Builder(app)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(app.getString(R.string.app_name))
                    .setContentText(app.getString(R.string.label_notification_count, this.count))
                    .setContentIntent(contentIntent)
                    .setNumber(this.count)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setGroup(NOTIFICATION_GROUP_KEY)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setShowWhen(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel nc = this.hipri ? app.getNotificationChannelHiPri() : app.getNotificationChannel();
                if (nc != null) builder.setChannelId(nc.getId());
            } else {
                builder.setPriority(this.hipri ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
            }
            return builder.build();
        }

        @IntRange(from = 1)
        int getCount() {
            return this.count;
        }

        void increase() {
            this.count++;
        }

        void setHipri() {
            this.hipri = true;
        }
    }

    /**
     * Waits for {@link #loaderExecutor} to terminate.<br>
     * <em>Note: This might run after {@link #onDestroy()}.</em>
     */
    private class Waiter extends Thread {
        private final long wid = System.currentTimeMillis() - 1647000000000L;

        @Override
        public void run() {
            if (UpdateJobService.this.loaderExecutor != null) {
                try {
                    UpdateJobService.this.loaderExecutor.shutdown();
                    //TODO the maximum wait time should be shorter if frequent updates are enabled
                    if (!UpdateJobService.this.loaderExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                        if (BuildConfig.DEBUG) Log.e(TAG + id + "-W-" + wid, "Not finished after 10 minutes!");
                        UpdateJobService.this.loaderExecutor.shutdownNow();
                    }
                    // All loaders have finished. Job will be stopped in DELAY_TO_DESTRUCTION ms
                    // wait DELAY_TO_DESTRUCTION/2 ms or embedded images to be loaded and notifications to be shown…
                    Thread.sleep(DELAY_TO_DESTRUCTION / 2L);
                    if (BuildConfig.DEBUG) Log.i(TAG + id + "-W-" + wid, "Updating widgets");
                    WidgetProvider.updateWidgets(UpdateJobService.this, WidgetProvider.loadWidgetSources(UpdateJobService.this));
                    // Show a notification group summary if there is more than one notification
                    if (UpdateJobService.this.summary != null && UpdateJobService.this.summary.getCount() >= 2) {
                        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID_SUMMARY, UpdateJobService.this.summary.build((App) getApplicationContext()));
                        UpdateJobService.this.summary = null;
                    }
                    // store list of previously displayed notifications
                    if (!UpdateJobService.this.previouslyShownNewsStored) previouslyShownNewsUpdate();
                    if (!UpdateJobService.this.stopJobReceived) {
                        // job will be stopped in DELAY_TO_DESTRUCTION/2 ms
                        Thread.sleep(DELAY_TO_DESTRUCTION / 2L);
                    }
                } catch (InterruptedException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG + id + "-W-" + wid, "While waiting: " + e, e);
                }
            } else {
                if (BuildConfig.DEBUG) Log.e(TAG + id + "-W-" + wid, "No LoaderExecutor!");
            }
            done();
        }
    }
}
