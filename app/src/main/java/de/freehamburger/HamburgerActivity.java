package de.freehamburger;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * Base for {@link MainActivity} and {@link NewsActivity}.
 */
public abstract class HamburgerActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection {

    static final long HIDE_FAB_AFTER = 2_000L;
    private static final String TAG = "HamburgerActivity";

    @NonNull final Handler handler = new Handler();
    HamburgerService service;
    FrequentUpdatesService frequentUpdatesService;
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

    @VisibleForTesting
    @SuppressLint("SwitchIntDef")
    public static int applyTheme(@NonNull final AppCompatActivity activity, @App.ResolvedBackgroundSelection final int bg, final boolean again) {
        // determine whether the Activity should display a ⁝ menu item
        boolean overflowButton = (!(activity instanceof HamburgerActivity) || ((HamburgerActivity) activity).hasMenuOverflowButton());
        @StyleRes final int resid = overflowButton ? R.style.AppTheme : R.style.AppTheme_NoOverflowButton;
        if (again) {
            activity.getTheme().applyStyle(resid, true);
        } else {
            activity.setTheme(resid);
        }
        return resid;
    }

    /**
     * Selects and applies a theme according to the preferences.<br>
     * Returns the selected background, too ({@link App#BACKGROUND_DAY light} or {@link App#BACKGROUND_NIGHT dark}),
     * so that information can be used to style Views.<br>
     * Note: The system-provided "default night mode" is set independently in {@link App#setNightMode(SharedPreferences)}.
     * @param activity AppCompatActivity
     * @param prefs SharedPreferences
     * @param again true / false to determine whether to call {@link android.content.res.Resources.Theme#applyStyle(int, boolean) applyStyle()} or {@link android.app.Activity#setTheme(int) setTheme()}
     * @return {@link App#BACKGROUND_DAY} or {@link App#BACKGROUND_NIGHT}
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @App.BackgroundSelection
    public static int applyTheme(@NonNull final AppCompatActivity activity, @Nullable SharedPreferences prefs, boolean again) {
        // determine whether a light or a dark background is applicable
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        @App.BackgroundSelection int background = prefs.getInt(App.PREF_BACKGROUND, App.BACKGROUND_AUTO);
        // select theme based on the background
        if (background == App.BACKGROUND_AUTO) background = Util.isNightMode(activity) ? App.BACKGROUND_NIGHT : App.BACKGROUND_DAY;
        applyTheme(activity, background, again);
        return background;
    }

    @App.BackgroundSelection
    public int getBackground() {
        return this.background;
    }

    public CoordinatorLayout getCoordinatorLayout() {
        return coordinatorLayout;
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

    /**
     * The preferred number of columns for either portrait or landscape orientation has changed.
     * @param prefs SharedPreferences
     */
    void onColumnCountChanged(SharedPreferences prefs)  {
        //NOP
    }

    /** {@inheritDoc} */
    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        //App.setNightMode(prefs);
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
                if (BuildConfig.DEBUG) Log.e(TAG, "While unbinding from service: " + e);
            }
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    protected void onResume() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        @UpdatesController.Run int r = UpdatesController.whatShouldRun(this);

        try {
            super.onResume();
            bindService(new Intent(this, HamburgerService.class), this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        } catch (Throwable e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, e.toString(), e);
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
            }
        }

        new UpdatesController(this).run();

        // display a Snackbar if something has been shared lately
        if (prefs.getBoolean(App.PREF_SHOW_LATEST_SHARE_TARGET, App.PREF_SHOW_LATEST_SHARE_TARGET_DEFAULT)) {
            App app = (App) getApplicationContext();
            App.LatestShare ls = app.getLatestShare();
            if (ls != null) {
                if (ls.wasVeryRecent()) {
                    try {
                        PackageManager pm = getPackageManager();
                        ApplicationInfo ai = pm.getApplicationInfo(ls.target.getPackageName(), Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS : 0);
                        Snackbar sb = Snackbar.make(this.coordinatorLayout, getString(R.string.msg_shared_with, pm.getApplicationLabel(ai)), Snackbar.LENGTH_SHORT);
                        View textView = sb.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                        if (textView instanceof TextView) {
                            ((TextView) textView).setGravity(Gravity.CENTER_VERTICAL);
                            ((TextView) textView).setMaxLines(2);
                            textView.setElevation(textView.getElevation() + 20f);
                            ((TextView) textView).setCompoundDrawablesWithIntrinsicBounds(pm.getApplicationIcon(ai), null, null, null);
                            ((TextView) textView).setCompoundDrawablePadding(16);
                        }
                        sb.show();
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                    }
                }
                app.setLatestShare(null);
            }
        }
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onServiceConnected(" + name + ", " + service + ")");
        if (service instanceof HamburgerService.HamburgerServiceBinder) {
            this.service = ((HamburgerService.HamburgerServiceBinder) service).getHamburgerService();
        } else if (service instanceof FrequentUpdatesService.FrequentUpdatesServiceBinder) {
            this.frequentUpdatesService = ((FrequentUpdatesService.FrequentUpdatesServiceBinder) service).getFrequentUpdatesService();
            if (this.frequentUpdatesService != null) this.frequentUpdatesService.foregroundStart();
        }
    }

    /** {@inheritDoc} */
    @CallSuper
    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onServiceDisconnected(" + name + ")");
        if (name == null) return;
        String clazz = name.getClassName();
        if (HamburgerService.class.getName().equals(clazz)) {
            this.service = null;
        } else if (FrequentUpdatesService.class.getName().equals(clazz)) {
            this.frequentUpdatesService = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_BACKGROUND.equals(key)) {
            this.background = applyTheme(this, prefs, true);
        } else if (App.PREF_ORIENTATION.equals(key)) {
            applyOrientation(this, prefs);
        } else if (App.PREF_COLS_PORTRAIT.equals(key) || App.PREF_COLS_LANDSCAPE.equals(key)) {
            onColumnCountChanged(prefs);
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
            // the unicode wrench symbol (0x1f527)
            sb.setAction("\uD83D\uDD27", v -> {
                sb.dismiss();
                startActivity(settingsIntent);
            });
        } else {
            sb = Snackbar.make(coordinatorLayout, R.string.error_no_network, Snackbar.LENGTH_SHORT);
        }
        sb.show();
    }

    /**
     * Modifies the splash screen. For Android 12+.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    void sploosh() {
        getSplashScreen().setOnExitAnimationListener(splashScreenView -> {
            final ObjectAnimator sx = ObjectAnimator.ofFloat(splashScreenView.getIconView(), "scaleX", 1f, 0f);
            final ObjectAnimator sy = ObjectAnimator.ofFloat(splashScreenView.getIconView(), "scaleY", 1f, 0f);
            final ObjectAnimator tr = ObjectAnimator.ofFloat(splashScreenView, "alpha", 1f, 0f);
            final AnimatorSet s = new AnimatorSet().setDuration(500L);
            s.setInterpolator(new AccelerateInterpolator(1.5f));
            s.playTogether(sx, sy, tr);
            s.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });
            s.start();
        });
    }

}
