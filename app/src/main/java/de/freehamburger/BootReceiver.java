package de.freehamburger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import androidx.preference.PreferenceManager;

import androidx.annotation.NonNull;

import de.freehamburger.model.Source;
import de.freehamburger.prefs.PrefsHelper;
import de.freehamburger.util.Log;

/**
 * Receives {@link Intent#ACTION_BOOT_COMPLETED}.
 */
public class BootReceiver extends BroadcastReceiver {

    private static void logFailed(@NonNull SharedPreferences prefs) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putLong(App.PREF_POLL_FAILED, System.currentTimeMillis());
        ed.apply();
    }

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        // btw: the intent has got the extra "android.intent.extra.user_handle" with a java.lang.Integer value of 0
        App app = (App)ctx.getApplicationContext();
        @UpdatesController.Run final int r = UpdatesController.whatShouldRun(app);
        if (r == UpdatesController.RUN_NONE) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        int intervalMinutes = PrefsHelper.getStringAsInt(prefs, UpdateJobService.hasNightFallenOverBerlin(prefs) ? App.PREF_POLL_INTERVAL_NIGHT : App.PREF_POLL_INTERVAL, App.PREF_POLL_INTERVAL_DEFAULT);

        long intervalMs = intervalMinutes * 60_000L;
        if (r == UpdatesController.RUN_SERVICE)  {
            // According to https://developer.android.com/guide/components/foreground-services#background-start-restriction-exemptions, a foreground service may be started here
            Intent intentFrequentUpdates = new Intent(ctx, FrequentUpdatesService.class);
            try {
                ctx.startService(intentFrequentUpdates);
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), "While starting " + intentFrequentUpdates + ": " + e);
            }
        } else if (r == UpdatesController.RUN_JOB)  {
            intervalMs = app.isBackgroundJobScheduled();
            if (intervalMs == 0L) {
                app.scheduleStart();
                intervalMs = app.isBackgroundJobScheduled();
                if (intervalMs == 0L) {
                    logFailed(prefs);
                    return;
                }
            }
        }

        // display a notification indicating that background queries are active
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!nm.areNotificationsEnabled()) return;
        }

        @SuppressLint("InlinedApi")
        final Notification.Builder builder = new Notification.Builder(app)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(R.string.app_name))
                .setContentText(app.getString(R.string.msg_background_active_short, String.valueOf(intervalMs / 60_000L)))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getService(app, 1,
                        new Intent(UpdateJobService.ACTION_CLEAR_NOTIFICATION, null, app, UpdateJobService.class),
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? (PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE) : PendingIntent.FLAG_ONE_SHOT))
                .setStyle(new Notification.BigTextStyle()
                        .setBigContentTitle(app.getString(R.string.app_name))
                        .bigText(app.getString(R.string.msg_background_active, String.valueOf(intervalMs / 60_000L)))
                        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (r == UpdatesController.RUN_JOB) {
                builder.addAction(new Notification.Action.Builder(
                        Icon.createWithResource(app, R.drawable.ic_do_not_disturb_alt_ededed_24dp),
                        app.getString(R.string.action_background_disable),
                        UpdateJobService.makeIntentToDisable(app))
                        .build());
            }
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(app, R.drawable.ic_notification),
                    app.getString(R.string.action_open_app),
                    UpdateJobService.makeIntentForMainActivity(app, null, Source.HOME))
                    .build());
        } else {
            if (r == UpdatesController.RUN_JOB) builder.addAction(new Notification.Action(R.drawable.ic_do_not_disturb_alt_ededed_24dp, app.getString(R.string.action_background_disable), UpdateJobService.makeIntentToDisable(app)));
            builder.addAction(new Notification.Action(R.drawable.ic_notification, app.getString(R.string.action_open_app), UpdateJobService.makeIntentForMainActivity(app, null, Source.HOME)));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!BuildConfig.DEBUG) builder.setTimeoutAfter(10_000L);
            NotificationChannel nc = app.getNotificationChannel();
            if (nc != null) builder.setChannelId(nc.getId());
        } else {
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        nm.notify(1, builder.build());

        // invoke the background job once to allow the user to see whether there's anything news on the Rialto
        // note: App.isBackgroundScheduled() would return 0 once the one-off job is scheduled because it does not recur
        JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            int result = js.schedule(UpdateJobService.makeOneOffJobInfo(app, false));
            if (result != JobScheduler.RESULT_SUCCESS) {
                logFailed(prefs);
            }
        } else {
            logFailed(prefs);
        }

    }
}
