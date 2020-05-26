package de.freehamburger;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

/**
 * Base for {@link MainActivity} and {@link NewsActivity}.
 */
public abstract class HamburgerActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection {

    static final long HIDE_FAB_AFTER = 2_000L;
    private static final String TAG = "HamburgerActivity";

    @NonNull final Handler handler = new Handler();
    HamburgerService service;
    CoordinatorLayout coordinatorLayout;
    @App.BackgroundSelection private int background;

    /**
     * Apply the preferred orientation.
     * @param activity Activity to apply the orientation to
     * @param prefs SharedPreferences
     * @throws NullPointerException if any parameter is {@code null}
     */
    @SuppressLint("SourceLockedOrientationActivity")
    static void applyOrientation(@NonNull AppCompatActivity activity, @NonNull SharedPreferences prefs) {
        @App.Orientation String orientation = prefs.getString(App.PREF_ORIENTATION, null);
        if (orientation == null) {
            orientation = App.PREF_ORIENTATION_DEFAULT;
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString(App.PREF_ORIENTATION, orientation);
            ed.apply();
        }
        switch (orientation) {
            case App.ORIENTATION_PORTRAIT: activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT); break;
            case App.ORIENTATION_LANDSCAPE: activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE); break;
            default: activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /**
     * Applies a theme to the given activity.
     * @param activity  AppCompatActivity
     * @param lightBackground true / false
     * @param again true / false
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    private static void applyTheme(@NonNull AppCompatActivity activity, boolean lightBackground, boolean again) {
        if (BuildConfig.DEBUG) Log.i(TAG, "applyTheme(" + activity.getClass().getSimpleName() + ", " + lightBackground + ", " + again + ")");
        @StyleRes int resid;
        if (activity instanceof HamburgerActivity) {
            HamburgerActivity ha = (HamburgerActivity) activity;
            if (lightBackground) {
                resid = ha.hasMenuOverflowButton() ? R.style.AppTheme_NoActionBar_Light : R.style.AppTheme_NoActionBar_Light_NoOverflowButton;
            } else {
                resid = ha.hasMenuOverflowButton() ? R.style.AppTheme_NoActionBar : R.style.AppTheme_NoActionBar_NoOverflowButton;
            }
        } else {
            if (lightBackground) {
                resid = R.style.AppTheme_NoActionBar_Light;
            } else {
                resid = R.style.AppTheme_NoActionBar;
            }
        }
        if (again) {
            activity.getTheme().applyStyle(resid, true);
        } else {
            activity.setTheme(resid);
        }
    }

    /**
     * Selects and applies a theme according to the preferences.<br>
     * Returns the selected background, too ({@link App#BACKGROUND_LIGHT light} or {@link App#BACKGROUND_DARK dark}),
     * so that information can be used to style Views.
     * @param activity AppCompatActivity
     * @param prefs SharedPreferences
     * @param again true / false
     * @return {@link App#BACKGROUND_LIGHT} or {@link App#BACKGROUND_DARK}
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    @App.BackgroundSelection
    static int applyTheme(@NonNull final AppCompatActivity activity, @Nullable SharedPreferences prefs, boolean again) {
        // determine whether a light or a dark background is applicable
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        @App.BackgroundSelection int background  = prefs.getInt(App.PREF_BACKGROUND, App.BACKGROUND_AUTO);
        // select theme based on the background
        if (background == App.BACKGROUND_DARK) {
            applyTheme(activity, false, again);
        } else if (background == App.BACKGROUND_LIGHT) {
            applyTheme(activity, true, again);
        } else {
            boolean nightMode;
            if (BuildConfig.DEBUG) {
                int n = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                nightMode = (n == 32);
                Log.i(TAG, "Night mode: " + (n == 32 ? "yes" : (n == 16 ? "no" : "undefined")));
            } else {
                nightMode = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            }
            applyTheme(activity, !nightMode, again);
        }
        //
        return background;
    }

    @App.BackgroundSelection
    public int getBackground() {
        return this.background;
    }

    @Nullable
    public final HamburgerService getHamburgerService() {
        return this.service;
    }

    @LayoutRes
    abstract int getMainLayout();

    /**
     * Controls whether the Activity displays the 3-dot ⠇overflow menu button.<br>
     * This method must return {@code true} if the menu definition contains items with {@code app:showAsAction="never"} or they wouldn't be accessible.
     * @return {@code true} to show the overflow button in the options menu
     */
    abstract boolean hasMenuOverflowButton();

    /** {@inheritDoc} */
    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        this.background = applyTheme(this, prefs, false);
        setContentView(getMainLayout());
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) setSupportActionBar(toolbar);
        this.coordinatorLayout = findViewById(R.id.coordinator_layout);
        applyOrientation(this, prefs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onDestroy() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    protected void onPause() {
        if (this.service != null) {
            try {
                unbindService(this);
            } catch (Throwable e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "While unbinding from service: " + e.toString());
            }
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    protected void onResume() {
        try {
            super.onResume();
            bindService(new Intent(this, HamburgerService.class), this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        } catch (Throwable e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.toString(), e);
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((HamburgerService.HamburgerServiceBinder) service).getHamburgerService();
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_BACKGROUND.equals(key)) {
            this.background = applyTheme(this, prefs, true);
        } else if (App.PREF_ORIENTATION.equals(key)) {
            applyOrientation(this, prefs);
        }
    }

    /**
     * Displays a Snackbar that says "No network.".
     */
    void showNoNetworkSnackbar() {
        Snackbar sb;
        Intent settingsIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        if (getPackageManager().resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            sb = Snackbar.make(coordinatorLayout, R.string.error_no_network, Snackbar.LENGTH_LONG);
            // the unicode wrench symbol 🔧 (0x1f527)
            sb.setAction("\uD83D\uDD27", v -> {
                sb.dismiss();
                startActivity(settingsIntent);
            });
        } else {
            sb = Snackbar.make(coordinatorLayout, R.string.error_no_network, Snackbar.LENGTH_SHORT);
        }
        sb.show();
    }

}
