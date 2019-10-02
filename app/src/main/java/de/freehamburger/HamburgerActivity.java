package de.freehamburger;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.GregorianCalendar;

import de.freehamburger.util.Sun;

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
        @App.BackgroundSelection int background = resolveBackground(activity, prefs);
        // select theme based on the background
        if (background == App.BACKGROUND_DARK) {
            applyTheme(activity, false, again);
        } else {
            applyTheme(activity, true, again);
        }
        //
        return background;
    }

    /**
     * Applies a theme to the given activity.
     * @param activity  AppCompatActivity
     * @param lightBackground true / false
     * @param again true / false
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    private static void applyTheme(@NonNull AppCompatActivity activity, boolean lightBackground, boolean again) {
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
     * Determines the theme to use.<br>
     * <ul>
     * <li>
     * If the {@link App#PREF_BACKGROUND background preference} is set to {@link App#BACKGROUND_AUTO auto},
     * the sun position is used to determined whether a light or dark theme is used.
     * If the user has given permission to access the device location, the location is used to determine sunrise and sunset;
     * otherwise the location will be estimated.
     * After sunset, a dark background is applied, before sunset a light background is applied.
     * </li>
     * <li>
     * If the preference is set to {@link App#BACKGROUND_LIGHT light} or {@link App#BACKGROUND_DARK dark}, a light resp. dark background is set.
     * </li>
     * </ul>
     * @param ctx Context
     * @param prefs SharedPreferences (optional)
     * @return {@link App#BACKGROUND_LIGHT} or {@link App#BACKGROUND_DARK}
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @App.BackgroundSelection
    private static int resolveBackground(@NonNull Context ctx, @Nullable SharedPreferences prefs) {
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        @App.BackgroundSelection int background = prefs.getInt(App.PREF_BACKGROUND, App.BACKGROUND_AUTO);
        if (background == App.BACKGROUND_AUTO) {
            Calendar now = new GregorianCalendar();
            Sun sun = null;
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationManager lm = (LocationManager) ctx.getSystemService(LOCATION_SERVICE);
                if (lm != null) {
                    Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (location == null) location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (location == null) location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                    if (location != null) {
                        sun = Sun.sunriseAndSunset(now, location.getLatitude(), location.getLongitude());
                    }
                }
            }
            if (sun == null) {
                sun = Sun.sunriseAndSunset(now);
            }
            background = sun.isDay(now) ? App.BACKGROUND_LIGHT : App.BACKGROUND_DARK;
        }
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
