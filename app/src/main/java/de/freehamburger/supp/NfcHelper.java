package de.freehamburger.supp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.MainActivity;
import de.freehamburger.R;
import de.freehamburger.model.Source;
import de.freehamburger.util.CoordinatorLayoutHolder;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class NfcHelper {

    public static final String ACTION_FOREGROUND_DISPATCH = "ACTION_NFC_FOREGROUND_DISPATCH";
    public static final int ERROR_IO = 3;
    public static final int ERROR_NOT_NDEF = 6;
    public static final int ERROR_NOT_WRITABLE = 2;
    public static final int ERROR_OTHER = Integer.MAX_VALUE;
    public static final int ERROR_TAGLOST = 4;
    public static final int ERROR_TOOLONG = 5;
    @VisibleForTesting public static final String SCHEME_H = "hamburger";
    public static final int SUCCESS = 0;
    private static final String TAG = "NfcHelper";
    private static volatile boolean foregroundDispatchEnabled = false;

    /**
     * See {@link NfcAdapter#disableForegroundDispatch(Activity)}.
     * @param activity Activity
     * @throws IllegalArgumentException if activity is null
     */
    @MainThread
    public static void disableForegroundDispatch(@NonNull Activity activity) {
        NfcAdapter nfca = NfcAdapter.getDefaultAdapter(activity);
        if (nfca == null) return;
        try {
            nfca.disableForegroundDispatch(activity);
            foregroundDispatchEnabled = false;
        } catch (Exception ignored) {
        }
    }

    /**
     * See {@link NfcAdapter#enableForegroundDispatch(Activity, PendingIntent, IntentFilter[], String[][])}.<br>
     * Also: <a href="https://developer.android.com/guide/topics/connectivity/nfc/advanced-nfc.html#foreground-dispatch">https://developer.android.com/guide/topics/connectivity/nfc/advanced-nfc.html#foreground-dispatch</a>
     * @param activity Activity
     * @throws IllegalArgumentException if activity is null
     */
    @MainThread
    @RequiresPermission(Manifest.permission.NFC)
    public static void enableForegroundDispatch(@NonNull Activity activity) {
        if (!PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT)) {
            return;
        }
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
        if (nfcAdapter == null || !nfcAdapter.isEnabled()) return;
        try {
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            ndef.addCategory(Intent.CATEGORY_DEFAULT);
            ndef.addDataScheme("http");
            ndef.addDataScheme("https");
            ndef.addDataScheme(SCHEME_H);
            IntentFilter tag = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            tag.addCategory(Intent.CATEGORY_DEFAULT);
            Intent launchIntent = new Intent(activity, activity.getClass()).setAction(ACTION_FOREGROUND_DISPATCH).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            int piflags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                piflags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            } else {
                piflags = PendingIntent.FLAG_UPDATE_CURRENT;
            }
            PendingIntent pi = PendingIntent.getActivity(activity, 0, launchIntent, piflags);
            nfcAdapter.enableForegroundDispatch(activity, pi, new IntentFilter[] {ndef, tag}, null);
            foregroundDispatchEnabled = true;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
    }

    @Nullable
    public static Uri extraxtNfcUrl(@NonNull Intent intent) {
        final Parcelable[] ndefMessages = intent.getParcelableArrayExtra(android.nfc.NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (ndefMessages == null) return null;
        Uri extracted = null;
        for (Parcelable rawMsg : ndefMessages) {
            if (!(rawMsg instanceof android.nfc.NdefMessage)) continue;
            android.nfc.NdefMessage msg = (android.nfc.NdefMessage) rawMsg;
            for (android.nfc.NdefRecord record : msg.getRecords()) {
                if (!Arrays.equals(android.nfc.NdefRecord.RTD_URI, record.getType())) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Skipping record of type \"" + new String(record.getType(), StandardCharsets.UTF_8) + "\"=" + Arrays.toString(record.getType()));
                    continue;
                }
                Uri uri = record.toUri();
                if (uri == null || (!SCHEME_H.equals(uri.getScheme()) && !App.isHostAllowed(uri.getHost()))) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Skipping record " + new String(record.getPayload(), StandardCharsets.UTF_8));
                    continue;
                }
                extracted = uri;
                break;
            }
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "Found via NFC: \"" + extracted + "\"");
        return extracted;
    }

    /**
     * Attempts to format the given tag <em>if necessary</em>.
     * @param ndefFormatable NdefFormatable to format
     * @param msg NdefMessage to write
     * @return error code: {@link #SUCCESS} if successfully formatted; a different error code otherwise
     */
    @RequiresPermission(Manifest.permission.NFC)
    @WorkerThread
    @Code
    private static int format(@NonNull NdefFormatable ndefFormatable, @Nullable NdefMessage msg) {
        int result = ERROR_OTHER;
        try {
            ndefFormatable.connect();
            ndefFormatable.format(msg);
            result = SUCCESS;
            if (BuildConfig.DEBUG) Log.i(TAG, "Tag has been formatted and written successfully.");
        } catch (TagLostException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While trying to format: " + e);
            result = ERROR_TAGLOST;
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While trying to format: " + e);
            result = ERROR_IO;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While trying to format: " + e);
        } finally {
            Util.close(ndefFormatable);
        }
        return result;
    }

    /**
     * <em>Do not call with code set to {@link #SUCCESS} or a wrong error message is returned!.</em>
     * @param ctx Context
     * @param code error code, not {@link #SUCCESS}
     * @return error message
     */
    @SuppressLint("SwitchIntDef")
    @NonNull
    public static String getErrorMsg(@NonNull Context ctx, @Code int code) {
        assert code != SUCCESS;
        final String msg;
        switch (code) {
            case ERROR_IO:
                msg = ctx.getString(R.string.error_nfc_io);
                break;
            case ERROR_NOT_NDEF:
                msg = ctx.getString(R.string.error_nfc_not_ndef);
                break;
            case ERROR_NOT_WRITABLE:
                msg = ctx.getString(R.string.error_nfc_not_writeable);
                break;
            case ERROR_TAGLOST:
                msg = ctx.getString(R.string.error_nfc_conn_lost);
                break;
            case ERROR_TOOLONG:
                msg = ctx.getString(R.string.error_nfc_too_long);
                break;
            default:
                msg = ctx.getString(R.string.error_nfc_other);
        }
        return msg;
    }

    @TestOnly
    public static boolean isForegroundDispatchEnabled(MainActivity ma) {
        if (ma == null || ma.isDestroyed()) return false;
        return foregroundDispatchEnabled;
    }

    @Nullable
    public static Source sourceFromUri(@Nullable Uri uri) {
        if (uri == null) return null;
        Source source = null;
        if (SCHEME_H.equals(uri.getScheme())) {
            // expected "hamburger://<source>"
            for (Source s : Source.values()) {
                if (s.name().equals(uri.getHost())) {
                    source = s;
                    break;
                }
            }
        } else {
            final String uris = uri.toString();
            for (Source s : Source.values()) {
                if (uris.equalsIgnoreCase(s.getUrl())) {
                    source = s;
                    break;
                }
            }
        }
        return source;
    }

    /**
     * Toggles NFC availability by either enabling or disabling an alias Activity
     * that has suitable NFC Intent filters for actions like {@link NfcAdapter#ACTION_NDEF_DISCOVERED} or others.
     * @param activity Activity that has got an alias in the manifest
     * @param aliasClass alias (for and having the same package name as the {@code activity}) as defined in the manifest, starting with '.'
     * @param prefs SharedPreferences (optional)
     * @throws NullPointerException if {@code activity} is {@code null}
     * @throws IllegalArgumentException if {@code aliasClass} is {@code null} or if it does not start with '.'
     */
    public static void toggleNfcUse(@NonNull Activity activity, String aliasClass, @Nullable SharedPreferences prefs) {
        if (aliasClass == null || !aliasClass.startsWith(".")) throw new IllegalArgumentException("Invalid alias!");
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean enable = prefs.getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT);
        PackageManager pm = activity.getPackageManager();
        Class<? extends Activity> activityClass = activity.getClass();
        ComponentName alias = new ComponentName(activity, activityClass.getName().substring(0, activityClass.getName().lastIndexOf('.')) + aliasClass);
        try {
            // setting to default value means disabled as that's the value in the manifest
            pm.setComponentEnabledSetting(alias, enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While toggling NFC use: " + e);
        }
    }

    /**
     * Handles a NFC tag that has possibly been sent by an NFC Intent.
     * @param ctx Context
     * @param tag NFC tag
     * @param source Source to store
     * @throws NullPointerException if any parameter is {@code null}
     */
    public static void write(@NonNull final Context ctx, @NonNull final Tag tag, @NonNull final Source source) {
        final String sourceLabel = ctx.getString(source.getLabel());
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.label_confirmation)
                .setIcon(R.drawable.ic_baseline_nfc_content_24)
                .setMessage(ctx.getString(R.string.msg_nfc_write_confirm, sourceLabel))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    @Code final int result = write(ctx, tag, NdefRecord.createUri(Uri.parse(SCHEME_H + "://" + source.name())));
                    new Handler(Looper.getMainLooper()).post(() -> {
                        CoordinatorLayout cl = ctx instanceof CoordinatorLayoutHolder ? ((CoordinatorLayoutHolder)ctx).getCoordinatorLayout() : null;
                        String msg;
                        int color;
                        if (result == SUCCESS) {
                            color = 0;
                            msg = ctx.getString(R.string.msg_nfc_write_success, sourceLabel);
                        } else {
                            color = Util.getColor(ctx, R.color.color_error);
                            msg = getErrorMsg(ctx, result);
                        }
                        if (cl != null) {Snackbar sb = Snackbar.make(cl, msg, Snackbar.LENGTH_LONG); if (color != 0) sb.setTextColor(color); sb.show();}
                        else Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
                    });
                }))
                ;
        builder.show();
    }

    @RequiresPermission(Manifest.permission.NFC)
    @Code
    static int write(Context ctx, @NonNull final Tag tag, final NdefRecord ndefRecordPayload) {
        final NdefMessage ndefMessage;
        // https://stackoverflow.com/questions/25504418/get-nfc-tag-with-ndef-android-application-record-aar
        NdefRecord ndefRecordApp = NdefRecord.createApplicationRecord(ctx.getPackageName());
        ndefMessage = new NdefMessage(ndefRecordPayload, ndefRecordApp);
        //
        Ndef ndefTag = Ndef.get(tag);
        if (ndefTag == null) {
            NdefFormatable ndefFormatable = NdefFormatable.get(tag);
            if (ndefFormatable != null) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Trying to format and write…");
                return format(ndefFormatable, ndefMessage);
            }
            return ERROR_NOT_NDEF;
        }
        int success = SUCCESS;
        try {
            ndefTag.connect();
            if (!ndefTag.isWritable()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Tag is not writable!");
                Util.close(ndefTag);
                return ERROR_NOT_WRITABLE;
            }
            int maxSize = ndefTag.getMaxSize();
            if (BuildConfig.DEBUG) Log.i(TAG, "NDEF tag type: \"" + ndefTag.getType() + "\", maximum tag size is " + maxSize + " bytes");
            if (ndefMessage.getByteArrayLength() > maxSize) {
                if (BuildConfig.DEBUG) Log.e(TAG, "The NDEF message length (" + ndefMessage.getByteArrayLength() + ") is larger than the tag's max. size (" + maxSize + ")!");
                Util.close(ndefTag);
                return ERROR_TOOLONG;
            }
            ndefTag.writeNdefMessage(ndefMessage);
        } catch (TagLostException e) {
            success = ERROR_TAGLOST;
            if (BuildConfig.DEBUG) Log.e(TAG, "While writing to tag: " + e);
        } catch (IOException e) {
            success = ERROR_IO;
            if (BuildConfig.DEBUG) Log.e(TAG, "While writing to tag: " + e, e);
        } catch (Exception e) {
            success = ERROR_OTHER;
            if (BuildConfig.DEBUG) Log.e(TAG, "While writing to tag: " + e, e);
        } finally {
            Util.close(ndefTag);
        }
        return success;
    }

    /**
     * The possible return code values.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SUCCESS, ERROR_NOT_WRITABLE, ERROR_IO, ERROR_TAGLOST, ERROR_TOOLONG, ERROR_NOT_NDEF, ERROR_OTHER})
    public @interface Code {}

}
