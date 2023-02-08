package de.freehamburger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import de.freehamburger.model.Source;
import de.freehamburger.prefs.ButtonPreference;
import de.freehamburger.prefs.DisablingValueListPreference;
import de.freehamburger.prefs.SummarizingEditTextPreference;
import de.freehamburger.supp.PopupManager;
import de.freehamburger.util.EditTextIntegerLimiter;
import de.freehamburger.util.Log;
import de.freehamburger.util.PermissionUtil;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;
import de.freehamburger.widget.WidgetProvider;

/**
 *
 */
public class SettingsActivity extends AppCompatActivity implements ServiceConnection, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    static final String ACTION_CONFIGURE_BACKGROUND_UPDATES = "de.freehamburger.configure.backgroundupdates";
    private static final String EXTRA_STORAGE_ACTIVITY = "de.freehamburger.extra.storage.activity";
    private static final int REQUEST_CODE_GET_NOTIFICATION_PERMISSION = 1234;
    private static final String TAG = "SettingsActivity";
    @Nullable
    private HamburgerService service;
    /** {@code true} if the user has clicked "Manage Storage" in the system settings for this app - identified by:<ol><li>{@link Intent} action is {@link Intent#ACTION_VIEW ACTION_VIEW},</li><li>Intent data is {@code null}</li></ol> */
    private boolean isManageStorageActivity;
    private boolean fromBackgroundTile = false;
    private Snackbar snackbar;
    private WebView webViewForHelp;
    private AlertDialog helpDialog;
    private UpdatesController updatesController;

    /**
     * Configures an EditText that is used to enter integer values.
     * @param editText EditText to configure
     * @param hint hint to set
     * @param imeFlags IME flags
     * @param min min. allowed value
     * @param max max. allowed value
     */
    @SuppressWarnings("SameParameterValue")
    static void configIntegerEditText(@NonNull final EditText editText, CharSequence hint, int imeFlags, long min, long max) {
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) imeFlags |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        editText.setImeOptions(imeFlags);
        editText.setHint(hint);
        editText.addTextChangedListener(new EditTextIntegerLimiter(editText, min, max));
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (this.snackbar != null && this.snackbar.isShown()) {
            this.snackbar.dismiss();
            this.snackbar = null;
        }
        if (this.isManageStorageActivity) {
            // this leads back to the system settings
            finishAffinity();
            return;
        }
        if (this.fromBackgroundTile) {
            finish();
            return;
        }
        super.onBackPressed();
    }

    /** {@inheritDoc} */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // we are assuming here that the background preference 3-state-button has been rotated
        getDelegate().applyDayNight();
        FragmentManager fragmentManager = getSupportFragmentManager();
        // for some cool reason, this works and appears to be necessary
        fragmentManager.popBackStack();
        // re-create the assumedly current fragment
        //TODO a way to determine the current fragment?
        fragmentManager
                .beginTransaction()
                .replace(R.id.settings, new AppearancePreferenceFragment())
                .commit();
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.colorWindowBackground));
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            // update the navigation icon color and the toolbar text color
            int toolbarTextColor = getResources().getColor(R.color.colorToolbarText);
            toolbar.setTitleTextColor(toolbarTextColor);
            Drawable icon = toolbar.getNavigationIcon();
            if (icon != null) icon.setTint(toolbarTextColor);
        }
    }

    /** {@inheritDoc} */
    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.isManageStorageActivity = extras.getBoolean(EXTRA_STORAGE_ACTIVITY);
            ComponentName cn = extras.getParcelable("android.intent.extra.COMPONENT_NAME");
            this.fromBackgroundTile = cn != null && cn.getClassName().endsWith(BackgroundTile.class.getSimpleName());
        }
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) setSupportActionBar(toolbar);

        if (!this.isManageStorageActivity && !this.fromBackgroundTile) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // onCreate() runs significantly faster if the WebView initialisation (three-digits runtime in ms on slower devices) is postponed.
        // As the webview is accessed only via the menu, it may be assumed that the user is not faster than the delay given in postDelayed().
        // Setup the WebView that might be used to display the help (it will be reused every time because constructing that thing takes quite some time)
        new Handler().postDelayed(() -> {
            this.webViewForHelp = new WebView(this);
            WebSettings ws = this.webViewForHelp.getSettings();
            ws.setBlockNetworkLoads(true);
            ws.setAllowContentAccess(false);
            ws.setGeolocationEnabled(false);
            this.webViewForHelp.setNetworkAvailable(false);
            this.webViewForHelp.setBackgroundColor(getResources().getColor(R.color.colorPrimarySemiTrans));
            this.webViewForHelp.setWebChromeClient(new NonLoggingWebChromeClient());
        }, 333L);

        this.updatesController = new UpdatesController(this);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, this.fromBackgroundTile ? new PollingPreferenceFragment() : new RootPreferenceFragment())
                .commit();
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        if (this.helpDialog != null && this.helpDialog.isShowing()) {
            this.helpDialog.dismiss();
            this.helpDialog = null;
        }
        if (this.service != null) {
            try {
                unbindService(this);
            } catch (Exception ignored) {
                this.service = null;
            }
        }
        super.onPause();
    }

    /** {@inheritDoc} <br>
     * As Androodle invented this, don't you dare bother me with "Oh this call is deprecated! Fix it though it's not broken!" stuff…
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onPreferenceStartFragment(" + caller + ", " + pref + ")");
        // Instantiate the new Fragment
        Bundle args = pref.getExtras();
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.getFragmentFactory().instantiate(getClassLoader(), Objects.requireNonNull(pref.getFragment()));
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        fm.beginTransaction()
                .replace(R.id.settings, fragment, pref.getKey())
                .addToBackStack(null)
                .setBreadCrumbTitle(pref.getTitle())
                .commit();
        return true;
    }

    @SuppressLint("RestrictedApi")
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_GET_NOTIFICATION_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                final int[] widgetIds = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this, WidgetProvider.class));
                if (widgetIds == null || widgetIds.length == 0) {
                    // no widgets exist, therefore we don't have to poll
                    Fragment f = getSupportFragmentManager().findFragmentByTag(getString(R.string.polling_category));
                    if (f instanceof PreferenceFragmentCompat) {
                        Preference prefPoll = ((PreferenceFragmentCompat)f).findPreference("pref_poll");
                        if (prefPoll instanceof TwoStatePreference) {
                            ((TwoStatePreference)prefPoll).setChecked(false);
                            final CharSequence summary = ((TwoStatePreference)prefPoll).getSummaryOff();
                            ((TwoStatePreference)prefPoll).setSummaryOff(getString(R.string.pref_title_poll_off_permission_denied));
                            new Handler(Looper.getMainLooper()).postDelayed(() -> ((TwoStatePreference)prefPoll).setSummaryOff(summary), 4_000L);
                        }
                    }
                }
            }
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() == null) {
            // button "manage storage" has been clicked (Action is VIEW, data is null)
            this.isManageStorageActivity = true;
            // hide the back arrow in the action bar because we don't want to navigate within the app (only exit)
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(false);
            // create a StoragePreferenceFragment and switch to it
            FragmentManager fm = getSupportFragmentManager();
            Fragment fragment = fm.getFragmentFactory().instantiate(getClassLoader(), StoragePreferenceFragment.class.getName());
            fm.beginTransaction()
                    .replace(R.id.settings, fragment)
                    .addToBackStack(null)
                    .commit();
        } else if (this.fromBackgroundTile) {
            // hide the back arrow in the action bar because we don't want to navigate within the app (only exit)
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(false);
        } else if (ACTION_CONFIGURE_BACKGROUND_UPDATES.equals(intent.getAction())) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment fragment = fm.getFragmentFactory().instantiate(getClassLoader(), PollingPreferenceFragment.class.getName());
            fm.beginTransaction()
                    .replace(R.id.settings, fragment)
                    .addToBackStack(null)
                    .commit();
        }
        if (this.service == null) {
            bindService(new Intent(this, HamburgerService.class), this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((HamburgerService.HamburgerServiceBinder)service).getHamburgerService();
        invalidateOptionsMenu();
    }

    /** {@inheritDoc} */
    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
    }

    /** {@inheritDoc} */
    @Override
    public void startActivity(final Intent intent) {
        String bai = intent.getStringExtra("com.android.browser.application_id");
        if (BuildConfig.APPLICATION_ID.equals(bai)) {
            // the user has tapped a link in a WebView
            if (this.helpDialog != null && this.helpDialog.isShowing()) {
                this.helpDialog.dismiss();
                this.helpDialog = null;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.removeCategory(Intent.CATEGORY_BROWSABLE);
            if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                super.startActivity(intent);
            } else {
                CoordinatorLayout cl = findViewById(R.id.coordinator_layout);
                if (cl != null) Snackbar.make(cl, R.string.error_no_app, Snackbar.LENGTH_SHORT).show();
                else Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        ComponentName cn = intent.getComponent();
        if (cn != null) intent.setClassName(BuildConfig.APPLICATION_ID, cn.getClassName());

        if (this.isManageStorageActivity) {
            intent.putExtra(EXTRA_STORAGE_ACTIVITY, true);
        }
        super.startActivity(intent);
    }

    //******************************************************************************************************************

    /**
     * WebChromeClient that suppresses console messages (in release versions).
     */
    private static class NonLoggingWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (BuildConfig.DEBUG) {
                String src = consoleMessage.sourceId();
                Log.i(TAG, (!TextUtils.isEmpty(src) ? src + ": " : "") + consoleMessage.message());
            }
            return true;
        }

    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class RootPreferenceFragment extends PreferenceFragmentCompat {

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            setHasOptionsMenu(false);
        }
    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class AppearancePreferenceFragment extends PreferenceFragmentCompat {
        private final Handler handler = new Handler();

        @NonNull
        private CharSequence makeColumnsSummary(@NonNull SharedPreferences prefs) {
            int colsLandscape = prefs.getInt(App.PREF_COLS_LANDSCAPE, 0);
            int colsPortrait = prefs.getInt(App.PREF_COLS_PORTRAIT, 0);
            if (colsLandscape < 1 && colsPortrait < 1) {
                return getString(R.string.label_cols_default);
            }
            return new StringBuilder(33)
                    .append(getString(R.string.pref_title_cols_landscape_short, colsLandscape < 1 ? getString(R.string.label_cols_default) : String.valueOf(colsLandscape)))
                    .append(", ")
                    .append(getString(R.string.pref_title_cols_portrait_short, colsPortrait < 1 ? getString(R.string.label_cols_default) : String.valueOf(colsPortrait)));
        }

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_appearance, rootKey);
            setHasOptionsMenu(true);
            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            ButtonPreference prefBackground = findPreference(App.PREF_BACKGROUND);
            Preference prefCorrectQuotationMarks = findPreference(App.PREF_CORRECT_WRONG_QUOTATION_MARKS);
            Preference prefImportFont = findPreference("pref_import_font");
            Preference prefDeleteFont = findPreference("pref_delete_font");
            Preference prefShowTopVideo = findPreference(App.PREF_SHOW_TOP_VIDEO);
            SeekBarPreference prefFontZoom = findPreference(App.PREF_FONT_ZOOM);

            PreferenceCategory prefcatCols = findPreference("pref_cat_cols");
            if (prefcatCols != null) prefcatCols.setSummary(makeColumnsSummary(prefs));

            SeekBarPreference prefColsLandscape = findPreference(App.PREF_COLS_LANDSCAPE);
            SeekBarPreference prefColsPortrait = findPreference(App.PREF_COLS_PORTRAIT);

            if (prefColsLandscape != null && prefColsPortrait != null) {
                int colsLandscape = prefs.getInt(App.PREF_COLS_LANDSCAPE, 0);
                int colsPortrait = prefs.getInt(App.PREF_COLS_PORTRAIT, 0);
                prefColsLandscape.setTitle(activity.getString(R.string.pref_title_cols_landscape) + ": " + (colsLandscape < 1 ? activity.getString(R.string.label_cols_default) : String.valueOf(colsLandscape)));
                prefColsPortrait.setTitle(activity.getString(R.string.pref_title_cols_portrait) + ": " + (colsPortrait < 1 ? activity.getString(R.string.label_cols_default) : String.valueOf(colsPortrait)));
                prefColsLandscape.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!(newValue instanceof Integer)) return false;
                    int c = (int)newValue;
                    preference.setTitle(activity.getString(R.string.pref_title_cols_landscape) + ": " + (c < 1 ? activity.getString(R.string.label_cols_default) : String.valueOf(c)));
                    if (prefcatCols != null) handler.postDelayed(() -> prefcatCols.setSummary(makeColumnsSummary(prefs)), 333L);
                    return true;
                });
                prefColsPortrait.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!(newValue instanceof Integer)) return false;
                    int c = (int)newValue;
                    preference.setTitle(activity.getString(R.string.pref_title_cols_portrait) + ": " + (c < 1 ? activity.getString(R.string.label_cols_default) : String.valueOf(c)));
                    if (prefcatCols != null) handler.postDelayed(() -> prefcatCols.setSummary(makeColumnsSummary(prefs)), 333L);
                    return true;
                });
            }

            if (prefImportFont != null && prefDeleteFont != null) {
                File fontFile = new File(activity.getFilesDir(), App.FONT_FILE);
                boolean fontExists = fontFile.isFile();
                if (fontExists) {
                    try {
                        TtfInfo ttfInfo = TtfInfo.getTtfInfo(fontFile);
                        String fontName = ttfInfo.getFontFullName();
                        prefImportFont.setSummary(getString(R.string.pref_summary_font_import_replace, fontName));
                        prefDeleteFont.setSummary(getString(R.string.label_quoted, fontName));
                    } catch (IOException e) {
                        fontExists = false;
                        prefImportFont.setSummary(null);
                        prefDeleteFont.setSummary(R.string.msg_font_none);
                    }
                } else {
                    prefImportFont.setSummary(null);
                    prefDeleteFont.setSummary(R.string.msg_font_none);
                }
                prefDeleteFont.setEnabled(fontExists);
            }

            if (prefFontZoom != null) {
                prefFontZoom.setSeekBarIncrement(25);
                prefFontZoom.setOnPreferenceChangeListener((preference, newValue) -> {
                    preference.setSummary(newValue + "%");
                    int v = (Integer)newValue;
                    int r = v % 5;
                    if (r != 0) {
                        final int adjusted = r < 3 ? v - r : v + 5 - r;
                        this.handler.postDelayed(() -> {
                            SharedPreferences.Editor ed = prefFontZoom.getSharedPreferences().edit();
                            ed.putInt(App.PREF_FONT_ZOOM, adjusted);
                            ed.apply();
                            prefFontZoom.callChangeListener(adjusted);
                        }, 150L);
                        return true;
                    }
                    return true;
                });
                prefFontZoom.callChangeListener(prefs.getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT));
            }

            if (prefBackground != null) {
                prefBackground.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (Integer.valueOf(App.BACKGROUND_AUTO).equals(newValue)) {
                        boolean granted = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                        if (!granted) {
                            boolean askMe = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION);
                            if (askMe) {
                                View v = getView();
                                if (v == null) v = getActivity().getWindow().getDecorView();
                                activity.snackbar = Snackbar.make(v, R.string.hint_permission_location, 15_000);
                                activity.snackbar.setAction(R.string.label_yes, v1 -> ActivityCompat.requestPermissions(activity, new String[] {
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                }, 456));
                                activity.snackbar.setActionTextColor(getResources().getColor(android.R.color.holo_green_light));
                                activity.snackbar.show();
                            }
                        }
                    } else {
                        if (activity.snackbar != null && activity.snackbar.isShown()) {
                            activity.snackbar.dismiss();
                        }
                    }
                    return true;
                });
            }

            if (prefShowTopVideo != null) {
                int iconSize = getResources().getDimensionPixelSize(R.dimen.pref_icon_size);
                // 0x1f918 -> https://en.wikibooks.org/wiki/Unicode/Character_reference/1F000-1FFFF
                prefShowTopVideo.setIcon(new BitmapDrawable(activity.getResources(),
                        Util.makeCharBitmap("\uD83C\uDFA5", 0f, iconSize, iconSize, Color.BLACK, Color.TRANSPARENT,
                                new PorterDuffColorFilter(getResources().getColor(R.color.colorContent), PorterDuff.Mode.SRC_ATOP))));
            }

            if (prefCorrectQuotationMarks != null) {
                int iconSize = getResources().getDimensionPixelSize(R.dimen.pref_icon_size);
                // 0x1f918 -> https://en.wikibooks.org/wiki/Unicode/Character_reference/1F000-1FFFF
                prefCorrectQuotationMarks.setIcon(new BitmapDrawable(activity.getResources(),
                        Util.makeCharBitmap("\uD83E\uDD18", 0f, iconSize, iconSize, Color.BLACK, Color.TRANSPARENT,
                                new PorterDuffColorFilter(getResources().getColor(R.color.colorContent), PorterDuff.Mode.SRC_ATOP))));
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_appearance_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class StoragePreferenceFragment extends PreferenceFragmentCompat {

        private SharedPreferences prefs;
        private EditTextPreference prefMaxCacheSize;

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_storage, rootKey);
            setHasOptionsMenu(true);
            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;
            this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            this.prefMaxCacheSize = findPreference(App.PREF_CACHE_MAX_SIZE);
            Preference prefClearCache = findPreference("pref_clear_cache");

            this.prefMaxCacheSize.setDefaultValue(App.DEFAULT_CACHE_MAX_SIZE);
            this.prefMaxCacheSize.setOnBindEditTextListener(editText -> configIntegerEditText(editText, getString(R.string.pref_hint_pref_cache_max_size),  EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN, App.PREF_CACHE_MAX_SIZE_MIN >> 20, Long.MAX_VALUE));
            this.prefMaxCacheSize.setOnPreferenceChangeListener((preference, o) -> {
                App app = (App)getActivity().getApplicationContext();
                long maxCacheSize;
                try {
                    maxCacheSize = Long.parseLong(o.toString()) << 20;
                } catch (Exception ignored) {
                    maxCacheSize = App.DEFAULT_CACHE_MAX_SIZE_MB << 20;
                }
                long current = app.getCurrentCacheSize();
                if (current < 1_048_576L) {
                    preference.setSummary(getString(R.string.label_current_cache_size_kb, current >> 10, maxCacheSize >> 20));
                } else {
                    preference.setSummary(getString(R.string.label_current_cache_size, current >> 20, maxCacheSize >> 20));
                }
                return (maxCacheSize >= App.PREF_CACHE_MAX_SIZE_MIN);
            });
            //noinspection ConstantConditions
            this.prefMaxCacheSize.getOnPreferenceChangeListener().onPreferenceChange(this.prefMaxCacheSize, this.prefs.getString(App.PREF_CACHE_MAX_SIZE, App.DEFAULT_CACHE_MAX_SIZE));

            if (prefClearCache != null) {
                prefClearCache.setOnPreferenceClickListener(preference -> {
                    Util.deleteOldestCacheFiles(getActivity(), 0L);
                    preference.setEnabled(false);
                    prefMaxCacheSize.getOnPreferenceChangeListener().onPreferenceChange(prefMaxCacheSize, this.prefs.getString(App.PREF_CACHE_MAX_SIZE, App.DEFAULT_CACHE_MAX_SIZE));
                    return true;
                });
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_storage_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {

        private final Handler handler = new Handler(Looper.getMainLooper());

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_general, rootKey);
            setHasOptionsMenu(true);
            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            String originalMaxMemCacheSize = prefs.getString(App.PREF_MEM_CACHE_MAX_SIZE, App.DEFAULT_MEM_CACHE_MAX_SIZE);

            EditTextPreference prefMaxMemCacheSize = findPreference(App.PREF_MEM_CACHE_MAX_SIZE);

            if (prefMaxMemCacheSize != null) {
                prefMaxMemCacheSize.setDefaultValue(App.DEFAULT_MEM_CACHE_MAX_SIZE);
                prefMaxMemCacheSize.setOnBindEditTextListener(editText -> configIntegerEditText(editText, getString(R.string.pref_hint_pref_cache_max_size),EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN,App.PREF_MEM_CACHE_MAX_SIZE_MIN >> 20,App.PREF_MEM_CACHE_MAX_SIZE_MAX >> 20));
                prefMaxMemCacheSize.setOnPreferenceChangeListener((preference, o) -> {
                    final int maxCacheSize;
                    try {
                        maxCacheSize = Integer.parseInt(o.toString().trim()) << 20;
                    } catch (NumberFormatException nfe) {
                        return false;
                    }
                    if (maxCacheSize < App.PREF_MEM_CACHE_MAX_SIZE_MIN || maxCacheSize > App.PREF_MEM_CACHE_MAX_SIZE_MAX) {
                        return false;
                    }

                    // update the displayed amount currently used after the preference has actually been modified
                    this.handler.postDelayed(() -> {
                        if (activity.service == null) return;
                        int current = activity.service.getMemoryCacheSize();
                        if (current < 1_048_576) {
                            preference.setSummary(getString(R.string.label_current_cache_size_kb, current >> 10, maxCacheSize >> 20));
                        } else {
                            preference.setSummary(getString(R.string.label_current_cache_size, current >> 20, maxCacheSize >> 20));
                        }
                    }, 400L);
                    return true;
                });
                //noinspection ConstantConditions
                prefMaxMemCacheSize.getOnPreferenceChangeListener().onPreferenceChange(prefMaxMemCacheSize, originalMaxMemCacheSize);
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_general_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PollingPreferenceFragment extends PreferenceFragmentCompat {

        private final Handler handler = new Handler();
        private SwitchPreferenceCompat prefPoll;
        private Preference prefPollStats;
        /** minimum polling interval in minutes */
        private int min;
        private int maxNightInterval;

        /**
         * Display a warning regarding app standby buckets.<br>
         * See <a href="https://developer.android.com/topic/performance/power/power-details.html">docs</a>.
         */
        @SuppressLint("SwitchIntDef")
        @RequiresApi(Build.VERSION_CODES.P)
        private void checkBucket() {
            Activity a = getActivity();
            if (a == null) return;
            UsageStatsManager usm = (UsageStatsManager)a.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            final int bucket = usm.getAppStandbyBucket();
            // bucket will be 5 if the app is whitelisted, 10 if the app is active
            View v = getView();
            View anchor = v != null ? Util.findTextView(v.findViewById(android.R.id.list), getString(R.string.pref_hint_poll_interval)) : null;
            if (anchor == null) anchor = a.getWindow().getDecorView();
            @StringRes final int msg;
            switch (bucket) {
                case UsageStatsManager.STANDBY_BUCKET_WORKING_SET:
                    msg = R.string.msg_standby_bucket_workingset;
                    break;
                case UsageStatsManager.STANDBY_BUCKET_FREQUENT:
                    msg = R.string.msg_standby_bucket_frequent;
                    break;
                case UsageStatsManager.STANDBY_BUCKET_RARE:
                case UsageStatsManager.STANDBY_BUCKET_RESTRICTED:
                    msg = R.string.msg_standby_bucket_rare;
                    break;
                default:
                    msg = 0;
            }
            if (msg != 0) {
                new PopupManager().showPopup(anchor, getString(msg), 4_000L);
            }
        }

        /**
         * Configures the preference that allows to request ignoring battery optimizations.
         * @param ctx Context
         */
        private void configurePrefRequestIgnoreBattOptimizations(@Nullable final Context ctx) {
            Preference prefRequestIgnoreBattOptimizations = findPreference("pref_request_ignore_batt_optimizations");
            if (prefRequestIgnoreBattOptimizations == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ctx != null) {
                String pkg = ctx.getPackageName();
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(pkg)) {
                    prefRequestIgnoreBattOptimizations.setOnPreferenceClickListener(null);
                    prefRequestIgnoreBattOptimizations.setVisible(false);
                } else {
                    prefRequestIgnoreBattOptimizations.setOnPreferenceClickListener(preference -> {
                        @SuppressLint("BatteryLife") Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
                        if (!(ctx instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            ctx.startActivity(i);
                        } catch (Exception e) {
                            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                            Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                    prefRequestIgnoreBattOptimizations.setVisible(true);
                }
            } else {
                prefRequestIgnoreBattOptimizations.setVisible(false);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @SuppressLint("BatteryLife") @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_polling, rootKey);
            setHasOptionsMenu(true);

            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            this.min = 1;
            this.maxNightInterval = Math.round(UpdateJobService.getNightDuration(prefs) * 60f);

            this.prefPoll = findPreference(App.PREF_POLL);
            if (this.prefPoll != null) {
                long failedBefore = prefs.getLong(App.PREF_POLL_FAILED, 0L);
                if (failedBefore != 0L) {
                    String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(failedBefore));
                    this.prefPoll.setSummaryOn(getString(R.string.pref_title_poll_on) + '\n' + getString(R.string.error_poll_failed, date));
                    this.prefPoll.setSummaryOff(getString(R.string.pref_title_poll_off) + '\n' + getString(R.string.error_poll_failed, date));
                }
                this.prefPoll.setOnPreferenceChangeListener((preference, newValue) -> {
                    this.handler.removeCallbacks(activity.updatesController);
                    this.handler.postDelayed(activity.updatesController, 1_000L);
                    if (Boolean.FALSE.equals(newValue)) {
                        AppWidgetManager aw = AppWidgetManager.getInstance(activity);
                        ComponentName provider = new ComponentName(activity, WidgetProvider.class);
                        final int[] widgetIds = aw.getAppWidgetIds(provider);
                        if (widgetIds != null && widgetIds.length > 0) {
                            // ask user to enable updates because there is at least one widget
                            Snackbar sb = Util.makeSnackbar(activity, getResources().getQuantityString(R.plurals.msg_widget_dont_disable_updates, widgetIds.length, widgetIds.length), Snackbar.LENGTH_INDEFINITE);
                            sb.setAction(android.R.string.ok, v -> {
                                this.prefPoll.setChecked(true);
                                Objects.requireNonNull(this.prefPoll.getOnPreferenceChangeListener()).onPreferenceChange(this.prefPoll, true);
                            });
                            Util.setSnackbarMaxLines(sb, 3);
                            sb.show();
                        }
                    } else {
                        PermissionUtil.checkNotifications(activity, REQUEST_CODE_GET_NOTIFICATION_PERMISSION);
                    }
                    return true;
                });
            }

            SwitchPreferenceCompat prefPollBreakingOnly = findPreference(App.PREF_POLL_BREAKING_ONLY);

            MultiSelectListPreference prefSources = findPreference(UpdateJobService.PREF_SOURCES_FOR_NOTIFICATIONS);
            if (prefSources != null) {
                final Source[] sources = Source.values();
                final CharSequence[] entries = new CharSequence[sources.length];
                final CharSequence[] entryValues = new CharSequence[sources.length];
                for (int i = 0; i < sources.length; i++) {
                    entries[i] = getString(sources[i].getLabel());
                    entryValues[i] = sources[i].name();
                }
                prefSources.setEntries(entries);
                prefSources.setEntryValues(entryValues);
                Set<String> values = prefs.getStringSet(UpdateJobService.PREF_SOURCES_FOR_NOTIFICATIONS, UpdateJobService.PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT);
                prefSources.setValues(values);
                prefSources.setSummary(Source.makeLabel(activity, values));
                if (!UpdateJobService.PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT.equals(values) && prefPollBreakingOnly != null) {
                    prefPollBreakingOnly.setChecked(false);
                    prefPollBreakingOnly.setEnabled(false);
                }
                prefSources.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!(newValue instanceof Set) || ((Set<?>)newValue).isEmpty()) {
                        if (prefPoll != null) prefPoll.setChecked(false);
                        return false;
                    }
                    // the "breaking news only" switch must be switched off and disabled if there is a Source other than HOME enabled
                    if (prefPollBreakingOnly != null) {
                        if (!UpdateJobService.PREF_SOURCES_FOR_NOTIFICATIONS_DEFAULT.equals(newValue)) {
                            if (prefPollBreakingOnly.isChecked()) {
                                prefPollBreakingOnly.setChecked(false);
                                Toast.makeText(activity, R.string.msg_breaking_only_disabled, Toast.LENGTH_LONG).show();
                            }
                            prefPollBreakingOnly.setEnabled(false);
                        } else {
                            prefPollBreakingOnly.setEnabled(true);
                        }
                    }
                    preference.setSummary(Source.makeLabel(activity, (Set<?>)newValue));
                    return true;
                });
            }

            SwitchPreferenceCompat prefPollOverMobile = findPreference(App.PREF_POLL_OVER_MOBILE);
            if (prefPollOverMobile != null) {
                if (prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, App.PREF_POLL_OVER_MOBILE_DEFAULT)) {
                    prefPollOverMobile.setEnabled(true);
                } else {
                    prefPollOverMobile.setChecked(false);
                    prefPollOverMobile.setSummary(R.string.pref_title_pref_load_over_mobile_off);
                    prefPollOverMobile.setEnabled(false);
                }
            }

            SummarizingEditTextPreference prefPollInterval = findPreference(App.PREF_POLL_INTERVAL);
            SummarizingEditTextPreference prefPollIntervalNight = findPreference(App.PREF_POLL_INTERVAL_NIGHT);

            if (prefPollInterval != null) {
                prefPollInterval.setStringRes(R.string.label_every_minutes);
                prefPollInterval.setDefaultValue(String.valueOf(App.PREF_POLL_INTERVAL_DEFAULT));
                prefPollInterval.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
                prefPollInterval.setOnPreferenceChangeListener((preference, o) -> {
                    int interval;
                    try {
                        interval = Integer.parseInt(o.toString().trim());
                    } catch (NumberFormatException nfe) {
                        Toast.makeText(activity, R.string.error_invalid_not_a_number, Toast.LENGTH_SHORT).show();
                        return false;
                    } catch (Exception ignored) {
                        return false;
                    }
                    if (interval < PollingPreferenceFragment.this.min) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.error_poll_minimum_interval, min), Toast.LENGTH_LONG).show();
                    }
                    boolean valid = interval >= PollingPreferenceFragment.this.min;
                    if (valid) {
                        this.handler.removeCallbacks(activity.updatesController);
                        this.handler.postDelayed(activity.updatesController, 1_000L);
                    }
                    return valid;
                });
            }

            if (prefPollIntervalNight != null) {
                prefPollIntervalNight.setStringRes(R.string.label_every_minutes);
                prefPollIntervalNight.setDefaultValue(String.valueOf(App.PREF_POLL_INTERVAL_DEFAULT));
                prefPollIntervalNight.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
                prefPollIntervalNight.setOnPreferenceChangeListener((preference, o) -> {
                    int interval;
                    try {
                        interval = Integer.parseInt(o.toString().trim());
                    } catch (NumberFormatException nfe) {
                        Toast.makeText(getActivity(), R.string.error_invalid_not_a_number, Toast.LENGTH_SHORT).show();
                        return false;
                    } catch (Exception ignored) {
                        return false;
                    }
                    if (interval < PollingPreferenceFragment.this.min) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.error_poll_minimum_interval, PollingPreferenceFragment.this.min), Toast.LENGTH_LONG).show();
                    } else if (interval > PollingPreferenceFragment.this.maxNightInterval) {
                        Toast.makeText(getActivity(), getResources()
                                .getString(R.string.error_poll_maximum_interval, PollingPreferenceFragment.this.maxNightInterval), Toast.LENGTH_LONG).show();
                    }
                    boolean valid = interval >= PollingPreferenceFragment.this.min && interval <= PollingPreferenceFragment.this.maxNightInterval;
                    if (valid) {
                        this.handler.removeCallbacks(activity.updatesController);
                        this.handler.postDelayed(activity.updatesController, 1_000L);
                    }
                    return valid;
                });
            }

            Preference prefNightPeriod = findPreference("pref_night_period");
            if (prefNightPeriod != null) {
                prefNightPeriod.setSummary(getString(R.string.pref_summary_poll_nightis, Util.formatFloatTime(prefs.getFloat(App.PREF_POLL_NIGHT_START, App.PREF_POLL_NIGHT_START_DEFAULT)), Util.formatFloatTime(prefs.getFloat(App.PREF_POLL_NIGHT_END, App.PREF_POLL_NIGHT_END_DEFAULT))));
                prefNightPeriod.setOnPreferenceClickListener(preference -> {
                    Activity a = getActivity();
                    if (a == null) return false;
                    View v = LayoutInflater.from(a).inflate(R.layout.night, null);
                    TimePicker timeFrom = v.findViewById(R.id.timeFrom);
                    TimePicker timeTo = v.findViewById(R.id.timeTo);
                    timeFrom.setIs24HourView(true);
                    timeTo.setIs24HourView(true);
                    float from = prefs.getFloat(App.PREF_POLL_NIGHT_START, App.PREF_POLL_NIGHT_START_DEFAULT);
                    float to = prefs.getFloat(App.PREF_POLL_NIGHT_END, App.PREF_POLL_NIGHT_END_DEFAULT);
                    int fromHour = (int)from;
                    int fromMinute = (int)(60f * (from - fromHour));
                    int toHour = (int)to;
                    int toMinute = (int)(60f * (to - toHour));
                    timeFrom.setCurrentHour(fromHour);
                    timeFrom.setCurrentMinute(fromMinute);
                    timeTo.setCurrentHour(toHour);
                    timeTo.setCurrentMinute(toMinute);
                    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(a)
                            .setIcon(R.drawable.ic_baseline_mode_night_content_24)
                            .setTitle(R.string.pref_title_poll_nightis)
                            .setView(v)
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                float selectedFrom = timeFrom.getCurrentHour() + timeFrom.getCurrentMinute() / 60f;
                                float selectedTo = timeTo.getCurrentHour() + timeTo.getCurrentMinute() / 60f;
                                if (selectedFrom >= 0 && selectedFrom < 24f && selectedTo >= 0f && selectedTo < 24f && Math.abs(selectedTo - selectedFrom) > 0.1f) {
                                    SharedPreferences.Editor ed = prefs.edit();
                                    ed.putFloat(App.PREF_POLL_NIGHT_START, selectedFrom);
                                    ed.putFloat(App.PREF_POLL_NIGHT_END, selectedTo);
                                    ed.apply();
                                    prefNightPeriod.setSummary(getString(R.string.pref_summary_poll_nightis, Util.formatFloatTime(prefs.getFloat(App.PREF_POLL_NIGHT_START, App.PREF_POLL_NIGHT_START_DEFAULT)), Util.formatFloatTime(prefs.getFloat(App.PREF_POLL_NIGHT_END, App.PREF_POLL_NIGHT_END_DEFAULT))));
                                    this.handler.removeCallbacks(activity.updatesController);
                                    this.handler.postDelayed(activity.updatesController, 1_000L);
                                }
                                dialog.dismiss();
                            })
                            ;
                    builder.show();
                    return true;
                });
            }

            configurePrefRequestIgnoreBattOptimizations(activity);

            long statStart = prefs.getLong(UpdateJobService.PREF_STAT_START, 0L);
            int jobsSoFar = prefs.getInt(UpdateJobService.PREF_STAT_COUNT, 0);
            long receivedSoFar = prefs.getLong(UpdateJobService.PREF_STAT_RECEIVED, 0L);
            boolean estimation = prefs.getBoolean(UpdateJobService.PREF_STAT_ESTIMATED, false);

            this.prefPollStats = findPreference("pref_poll_stats");
            if (this.prefPollStats != null) {
                if (statStart > 0L && receivedSoFar > 0L) {
                    // fall 2018: 70531 bytes/job based on 734 jobs
                    // summer 2019: 71841 bytes/job based on 12042 jobs
                    String ds;
                    if (System.currentTimeMillis() - statStart > 24 * 3_600_000L) {
                        ds = DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(statStart));
                    } else {
                        ds = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(statStart));
                    }
                    String amount = receivedSoFar > 1_500_000L ? Math.round(receivedSoFar / 1_000_000f) + " MB" : Math.round(receivedSoFar / 1_000f) + " kB";
                    if (estimation) amount = getString(R.string.label_ca) + ' ' + amount;
                    this.prefPollStats.setSummary(getResources().getQuantityString(R.plurals.label_poll_stats, jobsSoFar, NumberFormat.getIntegerInstance().format(jobsSoFar), amount, ds));
                } else {
                    this.prefPollStats.setSummary(R.string.label_poll_stats_none);
                    this.prefPollStats.setEnabled(false);
                }
                this.prefPollStats.setOnPreferenceClickListener(preference -> {
                    View v = getView();
                    if (v == null) v = activity.getWindow().getDecorView();
                    activity.snackbar = Snackbar.make(v, R.string.label_reset_q, 5_000);
                    activity.snackbar.setAction(android.R.string.ok, v1 -> {
                        UpdateJobService.resetStatistics(activity);
                        PollingPreferenceFragment.this.prefPollStats.setSummary(R.string.label_poll_stats_none);
                        PollingPreferenceFragment.this.prefPollStats.setEnabled(false);
                    });
                    activity.snackbar.setActionTextColor(getResources().getColor(R.color.colorAccent));
                    activity.snackbar.show();
                    Util.fadeSnackbar(activity.snackbar, null,4_900L);
                    return false;
                });
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_polling_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onResume() {
            super.onResume();
            Context ctx = getContext();
            if (ctx == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT)) {
                new Handler().postDelayed(this::checkBucket, 3_000L);
            }
            configurePrefRequestIgnoreBattOptimizations(ctx);
        }

    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class VideoPreferenceFragment extends PreferenceFragmentCompat {

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootXml) {
            setPreferencesFromResource(R.xml.pref_video, rootXml);
            setHasOptionsMenu(true);

            SwitchPreferenceCompat prefPipEnabled = findPreference(VideoActivity.PREF_PIP_ENABLED);
            if (prefPipEnabled != null) {
                boolean pipSupported = VideoActivity.isPipSupported(getContext());
                if (!pipSupported) prefPipEnabled.setChecked(false);
                prefPipEnabled.setEnabled(pipSupported);
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_video_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataPreferenceFragment extends PreferenceFragmentCompat {
        private SharedPreferences prefs;
        private SwitchPreferenceCompat prefLoadVideosOverMobile;

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootXml) {
            setPreferencesFromResource(R.xml.pref_data, rootXml);
            setHasOptionsMenu(true);
            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;
            this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            SwitchPreferenceCompat prefLoadOverMobile = findPreference(App.PREF_LOAD_OVER_MOBILE);
            this.prefLoadVideosOverMobile = findPreference(App.PREF_LOAD_VIDEOS_OVER_MOBILE);

            if (prefLoadOverMobile != null) {
                prefLoadOverMobile.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (Boolean.FALSE.equals(newValue)) {
                        SharedPreferences.Editor ed = prefs.edit();
                        ed.putBoolean(App.PREF_LOAD_VIDEOS_OVER_MOBILE, false);
                        ed.putBoolean(App.PREF_POLL_OVER_MOBILE, false);
                        ed.apply();
                        prefLoadVideosOverMobile.setChecked(false);
                    }
                    return true;
                });
            }

            Preference prefProxyType = findPreference(App.PREF_PROXY_TYPE);
            if (prefProxyType != null) {
                prefProxyType.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                    final String[] labels = getResources().getStringArray(R.array.entries_list_proxytypes);
                    final String[] values = getResources().getStringArray(R.array.entryvalues_list_proxytypes);

                    /** {@inheritDoc} */
                    @Override
                    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                        if (newValue != null) {
                            final String type = newValue.toString();
                            boolean match = false;
                            for (int i = 0; i < values.length; i++) {
                                if (values[i].equals(type)) {
                                    match = true;
                                    preference.setSummary(labels[i]);
                                    break;
                                }
                            }
                            if (!match) preference.setSummary(type);
                        }
                        return true;
                    }
                });
                //noinspection ConstantConditions
                prefProxyType.getOnPreferenceChangeListener().onPreferenceChange(prefProxyType, prefs.getString(App.PREF_PROXY_TYPE, getString(R.string.pref_default_proxy_type)));
            }

            DisablingValueListPreference pref_proxy_type = findPreference("pref_proxy_type");
            if (pref_proxy_type != null) pref_proxy_type.setSelectionToDisableDependents("DIRECT");

            EditTextPreference prefProxyServer = findPreference(App.PREF_PROXY_SERVER);
            if (prefProxyServer != null) {
                prefProxyServer.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (newValue == null) {
                        preference.setSummary(R.string.pref_summary_proxy_server);
                    } else {
                        String s = newValue.toString().trim();
                        preference.setSummary(s.length() > 0 ? s : getString(R.string.pref_summary_proxy_server));
                    }
                    return true;
                });
                //noinspection ConstantConditions
                prefProxyServer.getOnPreferenceChangeListener().onPreferenceChange(prefProxyServer, prefs.getString(App.PREF_PROXY_SERVER, null));
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null && sa.webViewForHelp != null) sa.helpDialog = Util.showHelp(sa, R.raw.help_settings_data_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }


    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class OtherPreferenceFragment extends PreferenceFragmentCompat {

        private boolean hintIntroShown;

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootXml) {
            setPreferencesFromResource(R.xml.pref_other, rootXml);
            setHasOptionsMenu(false);
        }
    }

}