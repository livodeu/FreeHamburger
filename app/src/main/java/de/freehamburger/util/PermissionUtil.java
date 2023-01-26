package de.freehamburger.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.freehamburger.BuildConfig;
import de.freehamburger.R;

public final class PermissionUtil {

    private static final String TAG = "PermissionUtil";

    private PermissionUtil() {
    }

    /**
     * Checks whether this app has got the permission to display notifications.
     * Consequently asks for the permission.<br>
     * See also <a href="https://developer.android.com/develop/ui/views/notifications/notification-permission">here</a>.
     * @param a Activity
     * @param requestCode request code, unique for the given Activity
     * @throws NullPointerException if {@code a} is {@code null}
     * @throws IllegalArgumentException if {@code requestCode} is &lt; 0
     */
    @SuppressLint("InlinedApi")
    public static void checkNotifications(@NonNull final Activity a, @IntRange(from = 0) final int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        NotificationManager nm = (NotificationManager)a.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.areNotificationsEnabled()) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Notifications are enabled.");
            return;
        }
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ActivityCompat.checkSelfPermission(a, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Notification permission has been granted.");
            return;
        }
        //
        if (ActivityCompat.shouldShowRequestPermissionRationale(a, Manifest.permission.POST_NOTIFICATIONS)) {
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(a)
                    .setTitle(R.string.label_confirmation)
                    .setMessage(R.string.msg_notification_permission_rationale)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(a, new String[] {Manifest.permission.POST_NOTIFICATIONS}, requestCode))
                    ;
            builder.show();
            return;
        }
        ActivityCompat.requestPermissions(a, new String[] {Manifest.permission.POST_NOTIFICATIONS}, requestCode);
    }
}
