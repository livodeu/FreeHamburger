package de.freehamburger;

import android.Manifest;
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
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;
import android.widget.RemoteViews;

import java.io.File;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

/**
 * Performs periodic updates in the background.<br>
 * If a News is encountered that was previously unbeknownst to us, it is shown in a Notification.<br>
 * The size of the (unpacked, Content-Encoding gzip) json file will be about 400 to 500 kB while the actual amount of data transmitted will be around 70 kB.<br>
 * See <a href="https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129">here</a>.
 */
public class UpdateJobService extends JobService implements Downloader.DownloaderListener, BlobParser.BlobParserListener {

    /** Job id for the periodic job */
    static final int JOB_ID = "Hamburger".hashCode();
    /** Notification id */
    static final int NOTIFICATION_ID = 123;
    /** the Intent action passed to {@link MainActivity} when the Notification is tapped */
    static final String ACTION_NOTIFICATION = "de.freehamburger.action_notification";
    /** long: timestamp when statistics collection started */
    static final String PREF_STAT_START = "pref_background_stat_start";
    /** long: number of bytes received since {@link #PREF_STAT_START} */
    static final String PREF_STAT_RECEIVED = "pref_background_received";
    /** int: number of jobs run */
    static final String PREF_STAT_COUNT = "pref_background_count";
    /** boolean: at least once we did not receive a Content-Length; therefore the byte count is just an estimation */
    static final String PREF_STAT_ESTIMATED = "pref_background_estimated";
    /** boolean: show extended notification instead of standard one (only possible from {@link Build.VERSION_CODES#N Nougat}) */
    private static final String PREF_NOTIFICATION_EXTENDED = "pref_notification_extended";
    /** the category that is used here */
    static final Source SOURCE = Source.HOME;
    /** if set, contains {@link News#getExternalId() News.externalId} */
    static final String EXTRA_FROM_NOTIFICATION = "de.freehamburger.from_notification";
    /** String set: logs timestamps of all requests */
    private static final String PREF_STAT_ALL = "pref_stat_all";
    /** the time when the job was scheduled */
    private static final String EXTRA_TIMESTAMP = "ts";
    /** self-imposed minimum interval of 5 minutes regardless {@link JobInfo#getMinPeriodMillis() what Android says} */
    private static final int HARD_MINIMUM_INTERVAL_MINUTES = 5;
    /** int: {@link #JOB_SCHEDULE_DAY} or {@link #JOB_SCHEDULE_NIGHT} */
    private static final String EXTRA_NIGHT = "night";
    private static final int JOB_SCHEDULE_DAY = 0;
    private static final int JOB_SCHEDULE_NIGHT = 1;
    private static final String TAG = "UpdateJobService";
    private static final BitmapFactory.Options BITMAPFACTORY_OPTIONS = new BitmapFactory.Options();
    /** String: contains the {@link News#getExternalId() id} of the News that was shown as a Notification most recently */
    private static final String PREF_LATEST_NEWS_ID = "pref_background_latest_news_id";
    private static int nextid = 1;

    static {
        BITMAPFACTORY_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
        BITMAPFACTORY_OPTIONS.inSampleSize = 2;
        BITMAPFACTORY_OPTIONS.inPreferQualityOverSpeed = false;
    }

    private final List<Filter> filters = new ArrayList<>();
    private final int id;
    private ExecutorService loaderExecutor;
    private JobParameters params;
    private long scheduledAt;
    private boolean stopJobReceived;

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
            intervalInMinutes = Integer.parseInt(prefs.getString(forNight ? App.PREF_POLL_INTERVAL_NIGHT : App.PREF_POLL_INTERVAL, String.valueOf(minimumIntervalInMinutes)));
            // do not set to a longer interval than the rest of the night or day! (don't wait 7 hours at 5 o'clock!)
            float nowInHours = getCurrentTimeInHours();
            if (forNight) {
                float nightEnd = getNightEnd();   // 6f
                float minutesToNightEnd = (nightEnd - nowInHours) * 60f;   // at 4:30 this is 90
                if (minutesToNightEnd < 0) minutesToNightEnd = 1440 + minutesToNightEnd;
                if (intervalInMinutes > minutesToNightEnd) {
                    intervalInMinutes = Math.round(minutesToNightEnd + 1); // +1 is to make sure that we end in day
                    if (BuildConfig.DEBUG) Log.i(TAG, "Interval capped to " + intervalInMinutes + " minutes because night ends at " + nightEnd + " o'clock");
                }
            } else {
                float nightStart = getNightStart(); // 23f
                float minutesToNightStart = (nightStart - nowInHours) * 60f; // at 22:50 this is 10
                if (minutesToNightStart < 0) minutesToNightStart = 1440 + minutesToNightStart;
                if (intervalInMinutes > minutesToNightStart) {
                    intervalInMinutes = Math.round(minutesToNightStart + 1); // +1 is to make sure that we end in night
                    if (BuildConfig.DEBUG) Log.i(TAG, "Interval capped to " + intervalInMinutes + " minutes because night begins at " + nightStart + " o'clock");
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
            intervalInMinutes = minimumIntervalInMinutes;
        }
        return intervalInMinutes * 60_000L;
    }

    /**
     * Returns the minimum interval in minutes, either {@link #HARD_MINIMUM_INTERVAL_MINUTES} or the value imposed by Android.
     * @return minimum interval <em>in minutes</em>
     */
    @IntRange(from = 300)
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
     * @param ctx Context
     * @return List of timestamps
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    static List<Long> getAllRequests(@NonNull Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        Set<String> allRequests = prefs.getStringSet(PREF_STAT_ALL, new HashSet<>());
        if (allRequests.isEmpty()) return new ArrayList<>();
        final List<Long> ar = new ArrayList<>(allRequests.size());
        for (String r : allRequests) {
            ar.add(Long.parseLong(r) + 1_500_000_000_000L);
        }
        Collections.sort(ar);
        return ar;
    }

    /**
     * Returns the current time of day in hours. For example, at 22:30 PM this method returns 22.5.
     * @return current time of day in hours
     */
    @FloatRange(from = 0f, to = 24f)
    private static float getCurrentTimeInHours() {
        Calendar now = new GregorianCalendar(App.TIMEZONE);
        return now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f + now.get(Calendar.SECOND) / 3600f;
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
    private static boolean hasNightFallenOverBerlin() {
        float hour = getCurrentTimeInHours();
        return hour >= getNightStart() || hour < getNightEnd();
    }

    /**
     * Generates a JobInfo that can be used to schedule the periodic job.<br>
     * <em>The returned JobInfo should be used immediately if it contains a timestamp.</em>
     * @param ctx Context
     * @return JobInfo
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    static JobInfo makeJobInfo(@NonNull Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean loadOverMobile = prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, false);
        final boolean pollOverMobile = prefs.getBoolean(App.PREF_POLL_OVER_MOBILE, false);
        final boolean night = hasNightFallenOverBerlin();
        final long intervalMs = calcInterval(prefs, night);
        final JobInfo.Builder jib = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                .setPeriodic(intervalMs)
                .setRequiredNetworkType(loadOverMobile && pollOverMobile ? JobInfo.NETWORK_TYPE_ANY : JobInfo.NETWORK_TYPE_UNMETERED)
                .setBackoffCriteria(120_000L, JobInfo.BACKOFF_POLICY_LINEAR)
                .setPersisted(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            jib.setEstimatedNetworkBytes(75_000L, 250L);
        }
        PersistableBundle extras = new PersistableBundle(2);
        extras.putLong(EXTRA_TIMESTAMP, System.currentTimeMillis());
        extras.putInt(EXTRA_NIGHT, night ? JOB_SCHEDULE_NIGHT : JOB_SCHEDULE_DAY);
        jib.setExtras(extras);
        return jib.build();
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
     * Constructor.
     */
    public UpdateJobService() {
        super();
        this.id = nextid++;
    }

    /** {@inheritDoc} */
    @Override
    public void blobParsed(@Nullable Blob blob, boolean ok, Throwable oops) {
        if (!ok || blob == null || oops != null) {
            if (BuildConfig.DEBUG && oops != null) {
                Log.e(TAG + id, "Parsing failed: " + oops.toString());
            }
            done();
            return;
        }
        if (this.stopJobReceived) {
            if (BuildConfig.DEBUG) Log.w(TAG + id, "BlobParser cancelled!");
            done();
            return;
        }
        //
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean breakingOnly = prefs.getBoolean(App.PREF_POLL_BREAKING_ONLY, true);
        final String latestNewsId = prefs.getString(PREF_LATEST_NEWS_ID, null);
        // get the list of news which is sorted so that the newest news is at the top
        final List<News> jointList = blob.getAllNews();
        if (jointList.isEmpty()) {
            done();
            return;
        }
        //
        App app = (App) getApplicationContext();
        // get the time when the user has updated the news list himself/herself most recently, so that we know what the user has already seen
        long latestStateSeenByUser = app.getMostRecentManualUpdate(SOURCE);

        News newsToDisplay = null;
        for (News n : jointList) {
            // decision: skip weather (at the time of writing this, weather is the only news with no 'externalId' set)
            // reason for that decision is that the weather news is updated much more frequently than others so it would be a bit like notification spam...
            if (n.getExternalId() == null) {
                continue;
            }
            // compare the news' timestamp with the time when the user has refreshed the data
            if (latestStateSeenByUser > 0L) {
                Date newsDate = n.getDate();
                if (newsDate != null && newsDate.getTime() < latestStateSeenByUser) {
                    // the News is too old - it was already present when the user most recently updated the data
                    continue;
                }
            }
            // do we want only breaking news?
            if (breakingOnly && !n.isBreakingNews()) {
                continue;
            }
            // let the filters have a look at it...
            boolean filtered = false;
            for (Filter filter : this.filters) {
                if (!filter.accept(n)) {
                    filtered = true;
                    break;
                }
            }
            if (!filtered) {
                newsToDisplay = n;
                break;
            }
        }

        if (newsToDisplay == null) {
            // Got list of news in the background but it did not contain anything newsworthy
            done();
            return;
        }

        String newsId = newsToDisplay.getExternalId();
        if (newsId == null || !newsId.equals(latestNewsId)) {
            if (newsId != null) {
                prefs.edit()
                        .putString(PREF_LATEST_NEWS_ID, newsId)
                        .apply();
            }

            TeaserImage teaserImage = newsToDisplay.getTeaserImage();
            if (teaserImage != null) {
                boolean mobile = Util.isNetworkMobile(this);
                String imageUrl = mobile
                        ? teaserImage.getImage(TeaserImage.Quality.S, TeaserImage.Quality.P1, TeaserImage.Quality.M)
                        : teaserImage.getImage(TeaserImage.Quality.M, TeaserImage.Quality.P1);
                if (imageUrl != null) {
                    try {
                        final News finalCopy = newsToDisplay;
                        final File temp = File.createTempFile("temp", ".jpg");
                        loadFile(imageUrl, temp, (completed, result) -> {
                            Bitmap bm = null;
                            if (completed && result != null && result.file != null && result.rc < 400) {
                                bm = BitmapFactory.decodeFile(result.file.getAbsolutePath(), BITMAPFACTORY_OPTIONS);
                            }
                            notify(finalCopy, bm);
                            done();
                        });
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                        notify(newsToDisplay, null);
                        done();
                    }
                    return;
                } else {
                    notify(newsToDisplay, null);
                }
            } else {
                notify(newsToDisplay, null);
            }
        } else {
            if (BuildConfig.DEBUG) Log.i(TAG + id, "Got news list in the background and got: \"" + newsToDisplay.getTitle() + "\" from " + newsToDisplay.getDate() + " that equals latest news");
        }

        done();
    }

    /**
     * Calls {@link #jobFinished(JobParameters, boolean)}.
     */
    private void done() {
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
                    if (result.rc < 400) Log.i(TAG + id, "Download: HTTP " + result.rc + " " + result.msg);
                    else Log.w(TAG + id, "Download: HTTP " + result.rc + " " + result.msg);
                } else {
                    Log.w(TAG + id, "Download: No result");
                }
            }
            done();
            return;
        }
        long now = System.currentTimeMillis();
        App app = (App) getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        long statStart = prefs.getLong(PREF_STAT_START, 0L);
        int jobsSoFar = prefs.getInt(PREF_STAT_COUNT, 0);
        long receivedSoFar = prefs.getLong(PREF_STAT_RECEIVED, 0L);
        SharedPreferences.Editor editor = prefs.edit();
        if (statStart == 0L) editor.putLong(PREF_STAT_START, now);
        editor.putInt(PREF_STAT_COUNT, jobsSoFar + 1);
        if (result.contentLength <= 0L) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "No content length received!");
            editor.putBoolean(PREF_STAT_ESTIMATED, true);
            editor.putLong(PREF_STAT_RECEIVED, receivedSoFar + 75_000L);
        } else {
            editor.putLong(PREF_STAT_RECEIVED, receivedSoFar + result.contentLength);
        }
        editor.apply();
        app.setMostRecentUpdate(SOURCE, now, false);
        if (this.stopJobReceived) {
            // Job stopped before parser could run
            done();
            return;
        }
        new BlobParser(app, this).executeOnExecutor(THREAD_POOL_EXECUTOR, result.file);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean extendedNotification(@NonNull final Notification.Builder builder, @NonNull final News news, @Nullable final Bitmap largeIcon) {
        try {
            @LayoutRes int layout = NewsRecyclerAdapter.getViewType(news);
            RemoteViews notificationLayout = new RemoteViews(getPackageName(), layout);
            String ntopline = news.getTopline();
            String ntitle = news.getTitle();
            String nfirstSentence = news.getFirstSentence();
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
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
        }
        return false;
    }

    /**
     * Loads a remote resource.
     * @param url Url to load from
     * @param localFile local file to save to
     * @param listener DownloaderListener
     * @throws NullPointerException if {@code localFile} is {@code null}
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    private void loadFile(@NonNull String url, @NonNull File localFile, @Nullable Downloader.DownloaderListener listener) {
        OkHttpDownloader downloader = new OkHttpDownloader(this);
        try {
            App app = (App) getApplicationContext();
            long mostRecentUpdate = app.getMostRecentUpdate(SOURCE);
            downloader.executeOnExecutor(this.loaderExecutor, new Downloader.Order(url, localFile.getAbsolutePath(), mostRecentUpdate, true, listener));
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "loadFile(\"" + url + "\", ..., ...) failed: " + e.toString());
            if (listener != null) listener.downloaded(false, null);
            done();
        }
    }

    /**
     * Determines whether the job needs to be rescheduled because it was scheduled during daytime and now it is nighttime or vice versa.
     * @param params JobParameters
     * @return {@code true} if the job needs to be rescheduled
     */
    private boolean needsReScheduling(@NonNull JobParameters params) {
        final boolean jobWasScheduledForNight = params.getExtras().getInt(EXTRA_NIGHT, JOB_SCHEDULE_DAY) == JOB_SCHEDULE_NIGHT;
        final boolean isNight = hasNightFallenOverBerlin();
        boolean yes = (isNight != jobWasScheduledForNight);
        if (!yes) {
            // the simple comparison above does not consider the case that it's now just before the day-night or night-day switch
            // like, for example, at 5:54 => this is still night so we might apply a 420 minutes interval => next update near 13:00!
            final float nowInHours = getCurrentTimeInHours();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int intervalInMinutes = Integer.parseInt(prefs.getString(jobWasScheduledForNight ? App.PREF_POLL_INTERVAL_NIGHT : App.PREF_POLL_INTERVAL, String.valueOf(getMinimumIntervalInMinutes())));
            float intervalInHours = intervalInMinutes / 60f;
            float nightStart = getNightStart(); // = 23
            float nightEnd = getNightEnd();     // = 6
            if (nowInHours < nightStart && nowInHours + intervalInHours >= nightStart + 10) {
                // 22:55 + 15 minutes = 23:10
                yes = true;
                if (BuildConfig.DEBUG) {
                    Log.i(TAG + id, "Job needs rescheduling: true (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ") - night will begin in " + (60 * (nightStart - nowInHours)) + " min.");
                }
            } else if (nowInHours < nightEnd && nowInHours + intervalInHours >= nightEnd + 10) {
                // e.g. 5:55 + 420 minutes = 12:55
                yes = true;
                if (BuildConfig.DEBUG) {
                    Log.i(TAG + id, "Job needs rescheduling: true (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ") - day will begin in " + (60 * (nightEnd - nowInHours)) + " min.");
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG + id, "Job needs rescheduling: false (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ")");
                }
            }
        } else if (BuildConfig.DEBUG) {
            Log.i(TAG + id, "Job needs rescheduling: true (night=" + isNight + "; scheduled for night=" + jobWasScheduledForNight + ")");
        }
        return yes;
    }

    /**
     * Displays the given News in a Notification.
     * @param news News
     * @param largeIcon optional picture
     */
    private void notify(@NonNull final News news, @Nullable Bitmap largeIcon) {
        if (BuildConfig.DEBUG) Log.i(TAG, "notify(\"" + news.getTitle() + "\", " + (largeIcon != null ? "icon" : "<null>") + ")");
        App app = (App) getApplicationContext();
        NotificationManager nm = (NotificationManager) app.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        // show extended notification?
        boolean extended = PreferenceManager.getDefaultSharedPreferences(app).getBoolean(PREF_NOTIFICATION_EXTENDED, false);
        //
        Intent mainIntent = new Intent(app, MainActivity.class);
        mainIntent.setAction(ACTION_NOTIFICATION);
        mainIntent.putExtra(EXTRA_FROM_NOTIFICATION, news.getExternalId());
        PendingIntent pendingIntentMain = PendingIntent.getActivity(app, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        //
        long when = news.getDate() != null ? news.getDate()
                .getTime() : 0L;
        String title, content, bigtext;
        String titleSrc, contentSrc, bigtextSrc;
        title = news.getTopline(); titleSrc = "topline";
        if (TextUtils.isEmpty(title)) {
            title = news.getTitle(); titleSrc = "title";
            content = news.getFirstSentence(); contentSrc = "firstSentence";
            bigtext = null; bigtextSrc = "<null>";
        } else {
            content = news.getTitle(); contentSrc = "title";
            bigtext = news.getFirstSentence(); bigtextSrc = "firstSentence";
            if (TextUtils.isEmpty(content)) {
                content = news.getFirstSentence(); contentSrc = "firstSentence";
                bigtext = news.getShorttext(); bigtextSrc = "shorttext";
            }
        }
        if (TextUtils.isEmpty(content)) {
            content = news.getShorttext(); contentSrc = "shortText";
            bigtext = null; bigtextSrc = "<null>";
        }
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "Notification title from news." + titleSrc + ", content from news." + contentSrc + ", bigtext from news." + bigtextSrc);
        }
        //
        final Notification.Builder builder = new Notification.Builder(app)
                .setDefaults(Notification.DEFAULT_SOUND)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText(null)   // on Android 8, subText would appear in the very top line between "Hamburger" and the 'when' value
                .setContentTitle(title)
                .setContentText(content)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntentMain)
                .setAutoCancel(true) // this affects only tapping the content; after intents originating from notification actions the notification is not dismissed
                .setOngoing(false);

        boolean extendedStyleApplied = false;
        if (extended && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            extendedStyleApplied = extendedNotification(builder, news, largeIcon);
        }

        if (!extendedStyleApplied) {
            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon);
            }
            if (!TextUtils.isEmpty(bigtext)) {
                builder.setStyle(new Notification.BigTextStyle()
                                // .setSummaryText() here affects the same area as setSubText() in the std. notification
                                .setBigContentTitle(title)
                                .bigText(bigtext)
                                );
            } else if (largeIcon != null) {
                builder.setStyle(new Notification.BigPictureStyle().bigPicture(largeIcon)
                        .bigLargeIcon((Bitmap) null)
                        .setSummaryText(content)
                        .setBigContentTitle(title));
            }
        }

        if (when > 0L) {
            builder.setWhen(when).setShowWhen(true);
        }

        if (News.NEWS_TYPE_STORY.equals(news.getType())) {
            final Intent viewDetailsIntent = new Intent(app, NewsActivity.class);
            viewDetailsIntent.setAction(Intent.ACTION_VIEW);
            viewDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Bundle extras = new Bundle(2);
            extras.putString(EXTRA_FROM_NOTIFICATION, news.getExternalId());
            extras.putSerializable(NewsActivity.EXTRA_NEWS, news);
            viewDetailsIntent.putExtras(extras);
            PendingIntent viewDetailsPendingIntent = PendingIntent.getActivity(app, 0, viewDetailsIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(new Notification.Action(R.drawable.ic_details_ededed_24dp, getString(R.string.action_details), viewDetailsPendingIntent));
        }

        NotificationChannel nc = null;
        if (news.isBreakingNews()) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nc != null) builder.setChannelId(nc.getId());
        }
        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        super.onCreate();
        this.loaderExecutor = Executors.newSingleThreadExecutor();
        this.filters.addAll(TextFilter.createTextFiltersFromPreferences(this));
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        this.loaderExecutor.shutdown();
        this.loaderExecutor = null;
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onStartJob(JobParameters params) {
        this.params = params;
        App app = (App) getApplicationContext();
        /*if (BuildConfig.DEBUG) {
            notify(News.test(), BitmapFactory.decodeResource(getResources(), R.drawable.test));
        }*/

        /*
24.11.18 04:07 I/UpdateJobService1454: Hamburger update, job scheduled at 24.11.2018 04:07:06
24.11.18 04:07 I/UpdateJobService1454: Job needs rescheduling: true (night=true; scheduled for night=true) - day will begin in 112.9 min.
24.11.18 04:07 I/UpdateJobService: Interval capped to 114 minutes because night ends at 6.0 o'clock
24.11.18 04:07 I/UpdateJobService1454: Job has been rescheduled to run every 114 minutes.
24.11.18 04:07 W/UpdateJobService1454: onStopJob(): reason for stopping the job is: cancelled
...
24.11.18 04:07 I/UpdateJobService1454: Download: HTTP 304 Not Modified
24.11.18 04:07 I/UpdateJobService1454: Job finished.
         */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        if (!prefs.getBoolean(App.PREF_POLL, false)) {
            done();
            return false;
        }
        if (params.getJobId() == JOB_ID && app.hasCurrentActivity()) {
            // app is currently active; nothing to do here for now
            done();
            return false;
        }
        //
        long ts = params.getExtras().getLong(EXTRA_TIMESTAMP, 0L);
        if (ts > 0L) this.scheduledAt = ts;
        if (this.scheduledAt == 0L) this.scheduledAt = System.currentTimeMillis();
        //
        if (BuildConfig.DEBUG) {
            String when = DateFormat.getDateTimeInstance()
                    .format(new Date(this.scheduledAt));
            Log.i(TAG + id, "Hamburger update, job scheduled at " + when);
            Set<String> allRequests = prefs.getStringSet(PREF_STAT_ALL, new HashSet<>());
            allRequests.add(String.valueOf(System.currentTimeMillis() - 1_500_000_000_000L));
            SharedPreferences.Editor ed = prefs.edit();
            ed.putStringSet(PREF_STAT_ALL, allRequests);
            ed.apply();
        }
        if (needsReScheduling(params)) {
            reschedule();
        }
        loadFile(SOURCE.getUrl(), app.getLocalFile(SOURCE), this);
        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("JavaReflectionMemberAccess")
    @Override
    public boolean onStopJob(JobParameters params) {
        this.stopJobReceived = true;
        if (BuildConfig.DEBUG) {
            try {
                Method getStopReason = JobParameters.class.getMethod("getStopReason");
                int reason = (int) getStopReason.invoke(params);
                String reasons;
                switch (reason) {
                    case 0:
                        reasons = "cancelled";
                        break;
                    case 1:
                        reasons = "constraints not satisfied";
                        break;
                    case 2:
                        reasons = "preempt";
                        break;
                    case 3:
                        reasons = "timeout";
                        break;
                    case 4:
                        reasons = "device idle";
                        break;
                    default:
                        reasons = "?";
                }
                Log.w(TAG + id, "onStopJob(): reason for stopping the job is: " + reasons);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void reschedule() {
        JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo ji = makeJobInfo(this);
        if (js.schedule(ji) == JobScheduler.RESULT_SUCCESS) {
            if (BuildConfig.DEBUG)
                Log.i(TAG + id, "Job has been rescheduled to run every " + (ji.getIntervalMillis() / 60_000L) + " minutes.");
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG + id, "Failed to reschedule job!");
        }
    }

}
