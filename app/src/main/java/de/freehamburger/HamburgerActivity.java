package de.freehamburger;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageButton;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Map;

import de.freehamburger.util.CoordinatorLayoutHolder;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * Base for {@link MainActivity} and {@link NewsActivity}.
 */
public abstract class HamburgerActivity extends StyledActivity implements CoordinatorLayoutHolder, SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection {

    static final long HIDE_FAB_AFTER = 2_000L;
    private static final String TAG = "HamburgerActivity";

    /** an activity-alias defined in the manifest; listening to {@link Intent#CATEGORY_APP_WEATHER} */
    static final String ACTIVITY_ALIAS_WEATHER = ".WeatherActivity";

    @NonNull final Handler handler = new Handler();
    HamburgerService service;
    FrequentUpdatesService frequentUpdatesService;
    CoordinatorLayout coordinatorLayout;
    @App.BackgroundSelection private int background;
    Snackbar snackbarAskUserForDomainAssociation;

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
    //@SuppressLint("SwitchIntDef")
    public static int applyTheme(@NonNull final AppCompatActivity activity, final boolean again) {
        // determine whether the Activity should display a ⁝ menu item
        boolean overflowButton = (!(activity instanceof HamburgerActivity) || ((HamburgerActivity) activity).hasMenuOverflowButton());
        @StyleRes final int resid = overflowButton ? R.style.AppTheme : R.style.AppTheme_NoOverflowButton;
        if (again) {
            activity.getTheme().applyStyle(resid, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.getTheme().rebase();
            }
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
        applyTheme(activity, again);
        return background;
    }

    /**
     * Checks whether the given domains have been associated with the given App:<br>
     * DOMAIN_STATE_SELECTED means the user has associated the host with the App.<br>
     * DOMAIN_STATE_NONE mans the user has not.
     * @param ctx Context representing an App
     * @param domains domains/hosts to check
     * @return int array with {@link DomainVerificationUserState state flags} for every domain/host, or {@code null}
     * @throws PackageManager.NameNotFoundException if the OS does
     */
    @Nullable
    private static int[] checkDomainAssociation(Context ctx, final String... domains) throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ctx == null || domains == null) return null;
        if (domains.length == 0) return new int[0];
        android.content.pm.verify.domain.DomainVerificationManager manager = ctx.getSystemService(android.content.pm.verify.domain.DomainVerificationManager.class);
        DomainVerificationUserState userState = manager.getDomainVerificationUserState(ctx.getPackageName());
        if (userState == null) return null;

        final int[] result = new int[domains.length];
        final Map<String, Integer> hostToStateMap = userState.getHostToStateMap();
        for (String host : hostToStateMap.keySet()) {
            int index = -1;
            for (int i = 0; i < domains.length; i++) {
                if (host.equals(domains[i])) {index = i; break;}
            }
            if (index == -1) continue;
            Integer stateValue = hostToStateMap.get(host);
            /*
            android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED  Domain has passed Android App Links verification.
            android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED  Domain hasn't passed Android App Links verification, but the user has associated it with an app.
             */
            if (stateValue == null) continue;
            result[index] = stateValue;
         }
        return result;
    }

    /**
     * Overrides the default tooltip text for the ← button in the top-left corner.
     * @param toolbar Toolbar
     * @param ttip tooltip text to set
     */
    @TargetApi(Build.VERSION_CODES.O)
    static void setHomeArrowTooltipText(@Nullable final Toolbar toolbar, @Nullable final CharSequence ttip) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || toolbar == null) return;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (!(child instanceof ImageButton)) continue;
            child.setTooltipText(ttip);
            break;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    static void setToolbarTitleMarquee(@Nullable final Toolbar toolbar) {
        if (toolbar == null) return;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView)child).setEllipsize(TextUtils.TruncateAt.MARQUEE);
                child.setSelected(true);
                // feisty freak fingers fone -> fly fresh flag
                child.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.setSelected(false);
                        v.setSelected(true);
                    }
                    return false;
                });
                break;
            }
        }
    }

    /**
     * Lets the user select supported web addresses to be opened in this app.<br>
     * Need API S, a.k.a Android 12.<br>
     * See <a href="https://developer.android.com/training/app-links/verify-android-applinks#java">https://developer.android.com/training/app-links/verify-android-applinks#java</a>
     */
    @Nullable
    @TargetApi(Build.VERSION_CODES.S)
    Snackbar askUserForDomainAssociation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isFinishing() || getCoordinatorLayout() == null) return null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // don't ask user if it has been requested not do so anymore or if the latest question was presented less than 1 hour ago
        if (!prefs.getBoolean(App.PREF_ASK_FOR_DOMAIN_ASSOCIATION, App.PREF_ASK_FOR_DOMAIN_ASSOCIATION_DEFAULT)
                || System.currentTimeMillis() - prefs.getLong(App.PREF_ASK_FOR_DOMAIN_ASSOCIATION_ASKED, 0L) < 3_600_000L) return null;
        final int snackbarDurationAsk = 10_000;
        final int snackbarDurationNeverAgain = 4_000;
        final String host1 = getString(R.string.viewable_host_1);
        final String host2 = getString(R.string.viewable_host_2);
        boolean askUser = false;
        try {
            int[] domainAssociations = checkDomainAssociation(this, host1, host2);
            if (domainAssociations == null) return null;
            for (int da : domainAssociations) {
                if (da == DomainVerificationUserState.DOMAIN_STATE_NONE) {askUser = true; break;}
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        if (!askUser) return null;
        SharedPreferences.Editor ed = prefs.edit();
        ed.putLong(App.PREF_ASK_FOR_DOMAIN_ASSOCIATION_ASKED, System.currentTimeMillis());
        ed.apply();
        final Snackbar sb = Snackbar.make(getCoordinatorLayout(), getString(R.string.msg_associate_domains, host1, host2), snackbarDurationAsk);
        Util.setSnackbarMaxLines(sb, 3);
        sb.setAction(R.string.label_yes, v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            }
        });
        sb.addCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar transientBottomBar, int event) {
                if (isFinishing()) return;
                if (event == Snackbar.Callback.DISMISS_EVENT_SWIPE) {
                    Snackbar sb2 = Snackbar.make(getCoordinatorLayout(), getString(R.string.label_dont_ask_again) + "?", snackbarDurationNeverAgain);
                    sb2.setAction(R.string.label_never_again, v -> {
                        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(HamburgerActivity.this).edit();
                        ed.putBoolean(App.PREF_ASK_FOR_DOMAIN_ASSOCIATION, false);
                        ed.apply();
                    });
                    sb2.show();
                    Util.fadeSnackbar(sb2, HamburgerActivity.this.handler, snackbarDurationNeverAgain);
                }
            }
        });
        sb.show();
        Util.fadeSnackbar(sb, this.handler, snackbarDurationAsk);
        return sb;
    }

    @App.BackgroundSelection
    public int getBackground() {
        return this.background;
    }

    @Override
    @Nullable public CoordinatorLayout getCoordinatorLayout() {
        return this.coordinatorLayout;
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
    void onColumnCountChanged(@NonNull SharedPreferences prefs)  {
        //NOP
    }

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
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
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
        if (this.snackbarAskUserForDomainAssociation != null) {
            if (this.snackbarAskUserForDomainAssociation.isShown()) this.snackbarAskUserForDomainAssociation.dismiss();
            this.snackbarAskUserForDomainAssociation = null;
        }
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
                if (ls.wasVeryRecent() && !getPackageName().equals(ls.target.getPackageName())) {
                    try {
                        PackageManager pm = getPackageManager();
                        ApplicationInfo ai = pm.getApplicationInfo(ls.target.getPackageName(), Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS : 0);
                        Snackbar sb = Util.makeSnackbar(this, getString(R.string.msg_shared_with, pm.getApplicationLabel(ai)), Snackbar.LENGTH_SHORT);
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
    @CallSuper
    public void onSharedPreferenceChanged(SharedPreferences prefs, @Nullable String key) {
        if (prefs == null) return;
        if (App.PREF_BACKGROUND.equals(key)) {
            this.background = applyTheme(this, prefs, true);
        } else if (App.PREF_BACKGROUND_VARIANT_INDEX.equals(key)) {
            setWindowBackground(prefs);
        } else if (App.PREF_ORIENTATION.equals(key)) {
            applyOrientation(this, prefs);
        } else if (App.PREF_COLS_PORTRAIT.equals(key) || App.PREF_COLS_LANDSCAPE.equals(key)) {
            onColumnCountChanged(prefs);
        }
    }

    /**
     * Selects the LayoutManager for the RecyclerView, based on the screen size.<br>
     * With horizontal separator values of 7.5 and 6,
     * a 10-inch tablet in landscape mode should show 3 columns,
     * a 7-inch tablet in landscape mode should show 2 columns,
     * all other devices/orientations should have 1 column.<br>
     * Note: the number of columns might be reduced after loading the data if the number of News items is less than the normal number of columns (see {@link #parseLocalFileAsync(File)})<br>
     * Screen sizes in dp:
     * <ul>
     * <li>10"-tablet avd is 1280 dp x 648 dp in landscape mode and 720 dp x 1208 dp in portrait mode</li>
     * <li>7"-tablet avd is 1024 dp x 528 dp in landscape mode and 600 dp x 952 dp in portrait mode</li>
     * <li>6.5" phone with 2400 px x 1080 px is 774 dp x 359 dp in landscape mode and 384 dp x 774 dp in portrait mode</li>
     * <li>5.2" phone with 1920 px x 1080 px is 592 dp x 336 dp in landscape mode and 360 dp x 568 dp in portrait mode</li>
     * </ul>
     * The aforementioned values are reduced by ca. 50% in one dimension if the app runs in multi-window mode!
     */
    protected void selectLayoutManager(@Nullable SharedPreferences prefs, RecyclerView recyclerView) {
        if (recyclerView == null) return;
        Configuration c = getResources().getConfiguration();
        RecyclerView.LayoutManager old = recyclerView.getLayoutManager();
        if (prefs == null) prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        int preferredCols = c.orientation == Configuration.ORIENTATION_PORTRAIT ? prefs.getInt(App.PREF_COLS_PORTRAIT, 0) : prefs.getInt(App.PREF_COLS_LANDSCAPE, 0);
        final int numColumns = preferredCols > 0 ? preferredCols : getResources().getInteger(R.integer.num_columns);
        if (numColumns == 1) {
            if (old instanceof LinearLayoutManager && !(old instanceof GridLayoutManager)) return;
            LinearLayoutManager llm = new LinearLayoutManager(this);
            recyclerView.setLayoutManager(llm);
            return;
        }
        if (old instanceof GridLayoutManager && ((GridLayoutManager)old).getSpanCount() == numColumns) return;
        GridLayoutManager glm = new GridLayoutManager(this, numColumns);
        recyclerView.setLayoutManager(glm);
    }

    /**
     * Displays a Snackbar that says "No network.".
     */
    void showNoNetworkSnackbar() {
        Snackbar sb;
        Intent settingsIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        if (getPackageManager().resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            sb = Util.makeSnackbar(this, R.string.error_no_network, Snackbar.LENGTH_LONG);
            // the unicode wrench symbol (0x1f527)
            sb.setAction("\uD83D\uDD27", v -> {
                sb.dismiss();
                startActivity(settingsIntent);
            });
        } else {
            sb = Util.makeSnackbar(this, R.string.error_no_network, Snackbar.LENGTH_SHORT);
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
