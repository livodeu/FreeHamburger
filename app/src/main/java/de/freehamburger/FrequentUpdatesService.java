package de.freehamburger;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.List;

import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * Provides updates more frequently than {@link UpdateJobService}.
 */
public class FrequentUpdatesService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** boolean: enable frequent (&lt; 15 minutes) updates */
    public static final String PREF_FREQUENT_UPDATES_ENABLED = "pref_frequent_updates_enabled";
    public static final boolean PREF_FREQUENT_UPDATES_ENABLED_DEFAULT = false;
    /** int: update every x <b>minutes</b> */
    public static final String PREF_FREQUENT_UPDATES = "pref_frequent_updates";
    /** minimum interval in <b>minutes</b> */
    public static final int PREF_FREQUENT_UPDATES_MIN = 1;
    /** default interval in <b>minutes</b> */
    public static final int PREF_FREQUENT_UPDATES_DEFAULT = BuildConfig.DEBUG ? PREF_FREQUENT_UPDATES_MIN : 10;
    @VisibleForTesting static final int NOTIFICATION_ID = 10_000;
    private static final long INTERVAL_MIN_MS = PREF_FREQUENT_UPDATES_MIN * 60_000L;
    private static final String TAG = "FrequentUpdatesService";

    private final FrequentUpdatesServiceBinder binder;
    @VisibleForTesting Ticker ticker;
    private Notification.Builder builder;
    private boolean enabled = PREF_FREQUENT_UPDATES_ENABLED_DEFAULT;
    private long latestUpdate = 0L;
    /** requested interval in milliseconds */
    @IntRange(from = INTERVAL_MIN_MS)
    private long requestedInterval = Math.max(INTERVAL_MIN_MS, PREF_FREQUENT_UPDATES_DEFAULT * 60_000L);

    /**
     * Constructor.
     */
    public FrequentUpdatesService() {
        super();
        this.binder = new FrequentUpdatesServiceBinder(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!BuildConfig.DEBUG) return;
        writer.println(getClass().getSimpleName());
        writer.println("----------------------");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writer.println("Foreground service type: " + getForegroundServiceType());
        }
        try {
            ServiceInfo si = getPackageManager().getServiceInfo(new ComponentName(this, getClass()), Build.VERSION.SDK_INT >= 23 ? PackageManager.MATCH_ALL | PackageManager.GET_META_DATA : PackageManager.GET_META_DATA);
            si.dump(new android.util.PrintWriterPrinter(writer), "");
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    /**
     * Invokes {@link #stopForeground(boolean)}.
     */
    void foregroundEnd() {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "foregroundEnd()");
            Toast.makeText(this, "Stopping frequent updates", Toast.LENGTH_LONG).show();
        }
        unregisterReceiver(this.ticker);
        stopForeground(true);
    }

    /**
     * Invokes {@link #startForeground(int, Notification)}.<br>
     * Also registers to {@link Intent#ACTION_TIME_TICK ACTION_TIME_TICK} broadcasts.
     */
    void foregroundStart() {
        if (BuildConfig.DEBUG) Log.i(TAG, "foregroundStart()");
        registerReceiver(this.ticker, new IntentFilter(Intent.ACTION_TIME_TICK));
        // skipping the rest if getForegroundServiceType() returns something other than ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE does not work
        try {
            if (isForeground()) return;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While checking foreground state: " + e);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, this.builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, this.builder.build());
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "UpdateService started in foreground");
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "While starting foreground service: " + e);
                Toast.makeText(this, "Foreground: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Determines whether this Service should run (in the foreground).
     * @param prefs SharedPreferences
     * @return true / false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    boolean isEnabled(@NonNull SharedPreferences prefs) {
        return prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT)
                && (!Util.isNetworkMobile(this) || prefs.getBoolean(App.PREF_POLL_OVER_MOBILE, App.PREF_POLL_OVER_MOBILE_DEFAULT))
                && prefs.getBoolean(PREF_FREQUENT_UPDATES_ENABLED, PREF_FREQUENT_UPDATES_ENABLED_DEFAULT);
    }

    /**
     * Determines whether this service runs as a foreground service.
     * @return true / false
     * @throws RuntimeException if Doodle "thinks" it's appropriate to do so
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    boolean isForeground() {
        try {
            final List<ActivityManager.RunningServiceInfo> runningServices = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getRunningServices(Integer.MAX_VALUE);
            if (runningServices != null) {
                for (ActivityManager.RunningServiceInfo running : runningServices) {
                    if (getClass().getName().equals(running.service.getClassName())) {
                        if (BuildConfig.DEBUG) Log.i(TAG, "Apparently" + (running.foreground ? "" : " NOT") + " running in the foreground.");
                        return running.foreground;
                    }
                }
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "Apparently NOT running.");
            return false;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    /** {@inheritDoc} */
    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG) Log.i(TAG, "onCreate()");
        super.onCreate();
        try {
            startService(new Intent(this, getClass()));
        } catch (IllegalStateException e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onCreate() - startService(): " + e);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.enabled = isEnabled(prefs);
        this.requestedInterval = Math.max(INTERVAL_MIN_MS, prefs.getInt(PREF_FREQUENT_UPDATES, PREF_FREQUENT_UPDATES_DEFAULT) * 60_000L);
        setupNotification();
        prefs.registerOnSharedPreferenceChangeListener(this);
        this.ticker = new Ticker();
    }

    /** {@inheritDoc} */
    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG) Log.w(TAG, "onDestroy()");
        unregisterReceiver(this.ticker);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (PREF_FREQUENT_UPDATES.equals(key)) {
            this.requestedInterval = Math.max(INTERVAL_MIN_MS, prefs.getInt(PREF_FREQUENT_UPDATES, PREF_FREQUENT_UPDATES_DEFAULT) * 60_000L);
            if (BuildConfig.DEBUG) Log.i(TAG, "onSharedPreferenceChanged(…, " + key + ") - interval: " + this.requestedInterval);
            this.enabled = isEnabled(prefs);
            if (this.enabled) updateNotification();
        } else if (PREF_FREQUENT_UPDATES_ENABLED.equals(key)) {
            this.enabled = isEnabled(prefs);
            if (BuildConfig.DEBUG) Log.i(TAG, "onSharedPreferenceChanged(…, " + key + ") - enabled: " + this.enabled);
            if (this.enabled) foregroundStart(); else foregroundEnd();
        } else if (App.PREF_POLL.equals(key)) {
            this.enabled = isEnabled(prefs);
            boolean poll = prefs.getBoolean(key, App.PREF_POLL_DEFAULT);
            if (BuildConfig.DEBUG) Log.i(TAG, "onSharedPreferenceChanged(…, " + key + ") - poll: " + poll);
            if (poll) foregroundStart(); else foregroundEnd();
        } else if (App.PREF_POLL_OVER_MOBILE.equals(key)) {
            this.enabled = isEnabled(prefs);
            if (Util.isNetworkMobile(this)) {
                boolean poll = prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
                boolean pollOverMobile = prefs.getBoolean(App.PREF_POLL_OVER_MOBILE, App.PREF_POLL_OVER_MOBILE_DEFAULT);
                if (BuildConfig.DEBUG) Log.i(TAG, "onSharedPreferenceChanged(…, " + key + ") - pollOverMobile: " + pollOverMobile);
                if (poll && pollOverMobile) foregroundStart(); else foregroundEnd();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onStartCommand(" + intent + ", " + flags + ", " + startId + ")");
        foregroundStart();
        return START_STICKY;
    }

    /**
     * Prepares {@link #builder}.
     */
    @SuppressWarnings("ConstantConditions")
    private void setupNotification() {
        int intervalInMinutes = (int)(this.requestedInterval / 60_000L);
        String msg = getResources().getQuantityString(R.plurals.msg_frequent_updates, intervalInMinutes, intervalInMinutes);
        this.builder = new Notification.Builder(this)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentTitle(msg)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(BuildConfig.DEBUG)
                .setSmallIcon(R.drawable.ic_updates)
        ;
        Intent contentIntent = new Intent(this, SettingsActivity.class);
        contentIntent.setAction(SettingsActivity.ACTION_CONFIGURE_BACKGROUND_UPDATES);
        this.builder.setContentIntent(PendingIntent.getActivity(this, 0, contentIntent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.builder.setChannelId(((App) getApplicationContext()).getNotificationChannelUpdates().getId());
        } else {
            this.builder.setPriority(Notification.PRIORITY_LOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.builder.setAllowSystemGeneratedContextualActions(false);
        }
    }

    /**
     * Updates the notification message.
     */
    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            String msg = getString(R.string.msg_frequent_updates_next, DateFormat.getTimeInstance(DateFormat.SHORT).format(new java.util.Date(this.latestUpdate + this.requestedInterval)));
            if (BuildConfig.DEBUG) Log.i(TAG, "updateNotification() - " + msg + " - latest: "
                    + DateFormat.getTimeInstance(DateFormat.SHORT).format(new java.util.Date(this.latestUpdate))
                    + ", interval: " + this.requestedInterval + " ms");
            // now, this should not happen, but better check instead of having a NPE…
            if (this.builder == null) setupNotification();
            //
            this.builder.setContentTitle(msg);
            nm.notify(NOTIFICATION_ID, this.builder.build());
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While updating notification: " + e);
        }
    }

    /**
     * The Binder implementation.
     */
    static class FrequentUpdatesServiceBinder extends Binder {

        private final Reference<FrequentUpdatesService> ref;

        FrequentUpdatesServiceBinder(@NonNull FrequentUpdatesService service) {
            super();
            this.ref = new WeakReference<>(service);
        }

        @Nullable
        FrequentUpdatesService getFrequentUpdatesService() {
            return this.ref.get();
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    /**
     * BroadcastReceiver that invokes {@link JobScheduler#schedule(JobInfo)} with the JobInfo made with {@link UpdateJobService#makeOneOffJobInfo(Context)}.
     */
    private class Ticker extends BroadcastReceiver {

        final JobScheduler js;

        /**
         * Constructor.
         */
        Ticker() {
            super();
            setDebugUnregister(BuildConfig.DEBUG);
            this.js = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }

        /** {@inheritDoc} */
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (this.js == null) {
                if (BuildConfig.DEBUG) Log.e(TAG, "TIME_TICK - no JobScheduler!");
                return;
            }
            if (!FrequentUpdatesService.this.enabled) {
                if (BuildConfig.DEBUG) Log.w(TAG, "TIME_TICK - disabled");
                return;
            }
            long now = System.currentTimeMillis();
            long delta = now - FrequentUpdatesService.this.latestUpdate;
            // we may execute up to 30 s earlier because the ticks arrive only on the full minute
            if (delta > (FrequentUpdatesService.this.requestedInterval - 30_000L)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "TIME_TICK: Performing update now.");
                // here the real work is done
                int result = this.js.schedule(UpdateJobService.makeOneOffJobInfo(FrequentUpdatesService.this, true));
                if (result != JobScheduler.RESULT_SUCCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to schedule expedited one-off job!");
                    result = this.js.schedule(UpdateJobService.makeOneOffJobInfo(FrequentUpdatesService.this, false));
                }
                if (result == JobScheduler.RESULT_SUCCESS) {
                    FrequentUpdatesService.this.latestUpdate = now;
                    updateNotification();
                } else {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to schedule one-off job!");
                }
            }
        }

    }
}
