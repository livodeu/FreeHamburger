package de.freehamburger;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import de.freehamburger.prefs.PrefsHelper;
import de.freehamburger.util.Log;

/**
 * Controls how background updates are retrieved.<br>
 * If updates are requested more frequently than every 15 minutes, the {@link FrequentUpdatesService} is used to retrieve them,
 * otherwise the {@link UpdateJobService} is scheduled for periodic execution.<br>
 * Note that the FrequentUpdatesService itself schedules a one-time UpdateJobService, too.
 */
public class UpdatesController implements Runnable {

    /** the user does not want any background updates */
    @Run public static final int RUN_NONE = 0;
    /** the user wants background updates not more frequently than every 15 minutes */
    @Run public static final int RUN_JOB = 1;
    /** the user wants background updates more frequently than every 15 minutes */
    @Run public static final int RUN_SERVICE = 2;
    private final static String TAG = "UpdatesController";
    @NonNull private final Reference<Activity> refa;

    /**
     * Constructor.
     * @param a Activity
     */
    UpdatesController(@NonNull Activity a) {
        super();
        this.refa = new WeakReference<>(a);
    }

    /**
     * Determines how to retrieve background updates.<br>
     * If updates are requested more frequently than every 15 minutes, use the {@link FrequentUpdatesService} to retrieve them, otherwise use the {@link UpdateJobService}.
     * @param ctx Context
     * @return one of {@link #RUN_NONE}, {@link #RUN_JOB}, {@link #RUN_SERVICE}
     */
    @Run
    public static int whatShouldRun(@NonNull Context ctx) {
        final int minimumIntervalForBackgroundJobs = UpdateJobService.getMinimumIntervalInMinutes();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean poll = prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
        final boolean frequentUpdatesDay = PrefsHelper.getStringAsInt(prefs, App.PREF_POLL_INTERVAL, App.PREF_POLL_INTERVAL_DEFAULT) < minimumIntervalForBackgroundJobs;
        final boolean frequentUpdatesNight = PrefsHelper.getStringAsInt(prefs, App.PREF_POLL_INTERVAL_NIGHT, App.PREF_POLL_INTERVAL_DEFAULT) < minimumIntervalForBackgroundJobs;
        if (poll) {
            // if updates are requested more frequently than every 15 minutes, use the FrequentUpdatesService to retrieve them, otherwise use the UpdateJobService
            if ((frequentUpdatesDay || frequentUpdatesNight)) return RUN_SERVICE; else return RUN_JOB;
        }
        return RUN_NONE;
    }

    private static String state(@Run int r) {
        switch (r) {
            case RUN_JOB:
                return "JOB";
            case RUN_SERVICE:
                return "SERVICE";
            case RUN_NONE:
                return "NONE";
        }
        return "?";
    }

    @MainThread
    @Override
    public void run() {
        final Activity a = this.refa.get();
        if (a == null || a.isDestroyed()) {
            if (BuildConfig.DEBUG) Log.e(TAG, "UpdatesController cannot run - no valid Activity!");
            return;
        }
        StackTraceElement from = new Throwable().getStackTrace()[1];
        boolean fail = false;
        StringBuilder log = new StringBuilder(32);
        final App app = (App) a.getApplicationContext();
        @Run final int r = whatShouldRun(a);
        if (BuildConfig.DEBUG) log.append("state is ").append(state(r));
        if (r == RUN_NONE) {
            if (FrequentUpdatesService.isForeground(a, FrequentUpdatesService.class)) {
                if (log.length() > 0) log.append(", ");
                log.append("stopping frequent updates");
                // stop foreground service for frequent updates
                Intent intentStopFrequentUpdatesService = new Intent(a, FrequentUpdatesService.class);
                intentStopFrequentUpdatesService.setAction(FrequentUpdatesService.ACTION_FOREGROUND_STOP);
                try {
                    a.startService(intentStopFrequentUpdatesService);
                } catch (Exception e) {
                    fail = true;
                    if (log.length() > 0) log.append(", ");
                    log.append(e);
                }
            } else {
                if (log.length() > 0) log.append(", ");
                log.append("frequent updates service is already in the background or stopped");
            }
            if (app.isBackgroundJobScheduled() != 0L) {
                // stop periodic background job
                if (log.length() > 0) log.append(", ");
                log.append("stopping background job");
                app.scheduleStop();
            }
        } else if (r == RUN_SERVICE) {
            if (!FrequentUpdatesService.isForeground(a, FrequentUpdatesService.class)) {
                if (log.length() > 0) log.append(", ");
                log.append("starting frequent updates service");
                // start foreground service for frequent updates
                Intent intentStartFrequentUpdatesService = new Intent(a, FrequentUpdatesService.class);
                try {
                    ComponentName startedService = a.startService(intentStartFrequentUpdatesService);
                    if (startedService == null) {
                        fail = true;
                        if (log.length() > 0) log.append(", ");
                        log.append("failed to start: ").append(intentStartFrequentUpdatesService);
                    }
                } catch (Exception e) {
                    if (log.length() > 0) log.append(", ");
                    log.append(e);
                }
            } else {
                if (log.length() > 0) log.append(", ");
                log.append("frequent updates service is already in foreground");
            }
            // stop periodic background job
            if (app.isBackgroundJobScheduled() != 0L) {
                if (log.length() > 0) log.append(", ");
                if (app.scheduleStop()) {
                    log.append("stopped background job");
                } else {
                    log.append("failed to stop background job");
                    fail = true;
                }
            }
        } else {
            if (FrequentUpdatesService.isForeground(a, FrequentUpdatesService.class)) {
                if (log.length() > 0) log.append(", ");
                log.append("stopping frequent updates service");
                // stop foreground service for frequent updates
                Intent intentStopFrequentUpdatesService = new Intent(a, FrequentUpdatesService.class);
                intentStopFrequentUpdatesService.setAction(FrequentUpdatesService.ACTION_FOREGROUND_STOP);
                try {
                    a.startService(intentStopFrequentUpdatesService);
                } catch (Exception e) {
                    fail = true;
                    if (log.length() > 0) log.append(", ");
                    log.append(e);
                }
            } else {
                if (log.length() > 0) log.append(", ");
                log.append("frequent updates service is already in the background or stopped");
            }
            if (app.isBackgroundJobScheduled() == 0L) {
                if (log.length() > 0) log.append(", ");
                log.append("starting background job");
                // start periodic background job
                new Thread() {
                    @Override
                    public void run() {
                        app.scheduleStart();
                    }
                }.start();
            } else {
                if (log.length() > 0) log.append(", ");
                log.append("background job is already scheduled");
            }
        }
        if (BuildConfig.DEBUG) {
            log.append(" - from ").append(from.toString().replace("de.freehamburger.", ""));
            if (fail) Log.e(TAG, log.toString()); else Log.i(TAG, log.toString());
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({RUN_NONE, RUN_JOB, RUN_SERVICE})
    @interface Run {}

}
