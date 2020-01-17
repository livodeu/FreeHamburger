package de.freehamburger;

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
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        if (!prefs.getBoolean(App.PREF_POLL, false)) return;
        long scheduled = app.isBackgroundJobScheduled();
        if (scheduled == 0L) {
            app.scheduleStart();
            scheduled = app.isBackgroundJobScheduled();
            if (scheduled == 0L) {
                logFailed(prefs);
                return;
            }
        }

        // display a notification indicating that background queries are active
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        final Notification.Builder builder = new Notification.Builder(app)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(app.getString(R.string.app_name))
                .setContentText(app.getString(R.string.msg_background_active_short, String.valueOf(scheduled / 60_000L)))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getService(app, 1, new Intent(UpdateJobService.ACTION_CLEAR_NOTIFICATION, null, app, UpdateJobService.class), PendingIntent.FLAG_ONE_SHOT))
                .setStyle(new Notification.BigTextStyle()
                        .setBigContentTitle(app.getString(R.string.app_name))
                        .bigText(app.getString(R.string.msg_background_active, String.valueOf(scheduled / 60_000L)))
                        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(app, R.drawable.ic_do_not_disturb_alt_ededed_24dp),
                    app.getString(R.string.action_background_disable),
                    UpdateJobService.makeIntentToDisable(app))
                    .build());
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(app, R.drawable.ic_notification),
                    app.getString(R.string.action_open_app),
                    UpdateJobService.makeIntentForMainActivity(app, null))
                    .build());
        } else {
            builder.addAction(new Notification.Action(R.drawable.ic_do_not_disturb_alt_ededed_24dp, app.getString(R.string.action_background_disable), UpdateJobService.makeIntentToDisable(app)));
            builder.addAction(new Notification.Action(R.drawable.ic_notification, app.getString(R.string.action_open_app), UpdateJobService.makeIntentForMainActivity(app, null)));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setTimeoutAfter(10_000L);
            NotificationChannel nc = app.getNotificationChannel();
            if (nc != null) builder.setChannelId(nc.getId());
        } else {
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        nm.notify(UpdateJobService.NOTIFICATION_ID, builder.build());

        // invoke the background job once to allow the user to see whether there's anything news on the Rialto
        // note: App.isBackgroundScheduled() would return 0 once the one-off job is scheduled because it does not recur
        JobScheduler js = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            int result = js.schedule(UpdateJobService.makeOneOffJobInfo(app));
            if (result != JobScheduler.RESULT_SUCCESS) {
                logFailed(prefs);
            }
        } else {
            logFailed(prefs);
        }

    }
}
