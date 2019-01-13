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
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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

    final Handler handler = new Handler();
    HamburgerService service;
    CoordinatorLayout coordinatorLayout;

    /**
     * Selects a theme according to the preferences.
     * @param activity AppCompatActivity
     * @param prefs SharedPreferences
     * @param again true / false
     * @return App.BACKGROUND_LIGHT or App.BACKGROUND_DARK
     */
    @App.BackgroundSelection
    static int pickTheme(@NonNull final AppCompatActivity activity, @Nullable SharedPreferences prefs, boolean again) {
        @App.BackgroundSelection int background = resolveBackground(activity, prefs);
        if (background == App.BACKGROUND_DARK) {
            pickTheme(activity, false, again);
        } else {
            pickTheme(activity, true, again);
        }
        return background;
    }

    private static void pickTheme(@NonNull AppCompatActivity activity, boolean lightBackground, boolean again) {
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
            activity.getTheme()
                    .applyStyle(resid, true);
        } else {
            activity.setTheme(resid);
        }
    }

    /**
     * Determines the theme to use. If it's on auto, the sun position is used to determined whether a light or dark theme is used.
     * @param ctx Context
     * @param prefs SharedPreferences (optional)
     * @return {@link App#BACKGROUND_LIGHT} or {@link App#BACKGROUND_DARK}
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static int resolveBackground(@NonNull Context ctx, @Nullable SharedPreferences prefs) {
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
        pickTheme(this, prefs, false);
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
            if (BuildConfig.DEBUG) Log.i(TAG, "Binding to HamburgerService");
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
        if (BuildConfig.DEBUG) Log.i(TAG, "Bound to HamburgerService");
        this.service = ((HamburgerService.HamburgerServiceBinder) service).getHamburgerService();
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
        if (BuildConfig.DEBUG) Log.i(TAG, "Disconnected from HamburgerService");
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_BACKGROUND.equals(key)) {
            pickTheme(this, prefs, true);
        }
    }

}
