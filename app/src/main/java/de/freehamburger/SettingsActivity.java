package de.freehamburger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;
import de.freehamburger.prefs.ButtonPreference;
import de.freehamburger.prefs.DisablingValueListPreference;
import de.freehamburger.prefs.SummarizingEditTextPreference;
import de.freehamburger.supp.PopupManager;
import de.freehamburger.util.Log;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;

/**
 *
 */
public class SettingsActivity extends AppCompatActivity implements ServiceConnection, PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "SettingsActivity";
    private static final String EXTRA_STORAGE_ACTIVITY = "de.freehamburger.extra.storage.activity";
    @Nullable
    private HamburgerService service;
    private CoordinatorLayout coordinatorLayout;
    /** {@code true} if the user has clicked "Manage Storage" in the system settings for this app - identified by:<ol><li>{@link Intent} action is {@link Intent#ACTION_VIEW ACTION_VIEW},</li><li>Intent data is {@code null}</li></ol> */
    private boolean isManageStorageActivity;
    private Snackbar snackbar;
    private WebView webViewForHelp;
    private AlertDialog helpDialog;

    /**
     * @param activity Activity
     * @param rawRes raw res
     * @param webView WebView to (re-)use
     * @return AlertDialog
     */
    private static AlertDialog showHelp(@NonNull Activity activity, @RawRes int rawRes, @NonNull final WebView webView) {
        byte[] b = new byte[2048];
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        InputStream in = null;
        try {
            in = activity.getResources().openRawResource(rawRes);
            for (;;) {
                int read = in.read(b);
                if (read < 0) break;
                //noinspection ObjectAllocationInLoop
                sb.append(new String(b, 0, read));
            }
        } catch (Exception ignored) {
        } finally {
            Util.close(in);
        }
        webView.loadDataWithBaseURL("about:blank", sb.toString(), "text/html", "UTF-8", null);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.AppAlertDialogTheme)
                .setTitle(R.string.action_help)
                .setView(webView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                ;
        AlertDialog ad = builder.create();
        Window w = ad.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        ad.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
        ad.setCanceledOnTouchOutside(true);
        ad.setOnCancelListener(dialog -> {
            ViewParent p = webView.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup)p).removeView(webView);
            }
        });
        ad.setOnDismissListener(dialog -> {
            ViewParent p = webView.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup)p).removeView(webView);
            }
        });
        ad.show();
        return ad;
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (this.snackbar != null && this.snackbar.isShown()) {
            this.snackbar.dismiss();
            this.snackbar = null;
        }
        /*if (getFragmentManager().getBackStackEntryCount() == 0 && getIntent().getStringExtra(EXTRA_SHOW_FRAGMENT) == null) {
            this.currentHeader = null;
            invalidateOptionsMenu();
        }*/
        if (this.isManageStorageActivity) {
            // this leads back to the system settings
            finishAffinity();
            return;
        }
        super.onBackPressed();
    }

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.isManageStorageActivity = extras.getBoolean(EXTRA_STORAGE_ACTIVITY);
        }
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        this.coordinatorLayout = findViewById(R.id.coordinator_layout);

        if (!this.isManageStorageActivity) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // setup the WebView that might be used to display the help (it will be reused every time because constructing that thing takes quite some time)
        this.webViewForHelp = new WebView(this);
        WebSettings ws = this.webViewForHelp.getSettings();
        ws.setBlockNetworkLoads(true);
        ws.setAllowContentAccess(false);
        ws.setGeolocationEnabled(false);
        this.webViewForHelp.setNetworkAvailable(false);
        this.webViewForHelp.setBackgroundColor(getResources().getColor(R.color.colorPrimarySemiTrans));
        this.webViewForHelp.setWebChromeClient(new NonLoggingWebChromeClient());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferenceFragment())
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

    /** {@inheritDoc} */
    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        Bundle args = pref.getExtras();
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        fm.beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit();
        return true;
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
    public void startActivity(Intent intent) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(getClass().getSimpleName(), "startActivity(" + intent + ")");
            String bai = intent.getStringExtra("com.android.browser.application_id");
            if (BuildConfig.APPLICATION_ID.equals(bai)) {
                // the user has tapped a link in a WebView
                if (this.helpDialog != null && this.helpDialog.isShowing()) {
                    this.helpDialog.dismiss();
                    this.helpDialog = null;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                super.startActivity(intent);
                return;
            }
            ComponentName cn = intent.getComponent();
            if (cn != null) intent.setClassName(BuildConfig.APPLICATION_ID, cn.getClassName());
        }
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
            SeekBarPreference prefFontZoom = findPreference(App.PREF_FONT_ZOOM);

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

            if (prefCorrectQuotationMarks != null) {
                int iconSize = getResources().getDimensionPixelSize(R.dimen.pref_icon_size);
                // 0x1f918 -> https://en.wikibooks.org/wiki/Unicode/Character_reference/1F000-1FFFF
                prefCorrectQuotationMarks.setIcon(new BitmapDrawable(activity.getResources(),
                        Util.makeCharBitmap("\uD83E\uDD18", 0f, iconSize, iconSize, Color.BLACK, Color.TRANSPARENT,
                                new PorterDuffColorFilter(getResources().getColor(R.color.colorDirtyWhite), PorterDuff.Mode.SRC_ATOP))));
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null) sa.helpDialog = showHelp(sa, R.raw.help_settings_appearance_de, sa.webViewForHelp);
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
            this.prefMaxCacheSize.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
            this.prefMaxCacheSize.setOnPreferenceChangeListener((preference, o) -> {
                App app = (App)getActivity().getApplicationContext();
                long maxCacheSize;
                try {
                    maxCacheSize = Long.parseLong(o.toString()) << 20;
                } catch (Exception ignored) {
                    maxCacheSize = App.DEFAULT_CACHE_MAX_SIZE_MB << 20;
                }
                if (maxCacheSize < 1_048_576L) {
                    return false;
                }
                long current = app.getCurrentCacheSize();
                if (current < 1_048_576L) {
                    preference.setSummary(getString(R.string.label_current_cache_size_kb, current >> 10, maxCacheSize >> 20));
                } else {
                    preference.setSummary(getString(R.string.label_current_cache_size, current >> 20, maxCacheSize >> 20));
                }
                return true;
            });
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
                if (sa != null) sa.helpDialog = showHelp(sa, R.raw.help_settings_storage_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {

        private String originalMaxMemCacheSize;
        private boolean memCacheSizeModified;

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
            this.originalMaxMemCacheSize = prefs.getString(App.PREF_MEM_CACHE_MAX_SIZE, App.DEFAULT_MEM_CACHE_MAX_SIZE);

            EditTextPreference prefMaxMemCacheSize = findPreference(App.PREF_MEM_CACHE_MAX_SIZE);

            if (prefMaxMemCacheSize != null) {
                prefMaxMemCacheSize.setDefaultValue(App.DEFAULT_MEM_CACHE_MAX_SIZE);
                prefMaxMemCacheSize.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
                prefMaxMemCacheSize.setOnPreferenceChangeListener((preference, o) -> {
                    int maxCacheSize;
                    try {
                        maxCacheSize = Integer.parseInt(o.toString().trim()) << 20;
                    } catch (NumberFormatException nfe) {
                        Toast.makeText(getActivity(), R.string.error_invalid_not_a_number, Toast.LENGTH_SHORT).show();
                        return false;
                    } catch (Exception ignored) {
                        maxCacheSize = App.DEFAULT_MEM_CACHE_MAX_SIZE_MB << 20;
                    }
                    if (maxCacheSize < 1_048_576 || maxCacheSize > (100 << 20)) {
                        return false;
                    }

                    if (activity.service != null) {
                        int current = activity.service.getMemoryCacheSize();
                        if (current < 1_048_576) {
                            preference.setSummary(getString(R.string.label_current_cache_size_kb, current >> 10, maxCacheSize >> 20));
                        } else {
                            preference.setSummary(getString(R.string.label_current_cache_size, current >> 20, maxCacheSize >> 20));
                        }
                    }
                    if (!originalMaxMemCacheSize.equals(o.toString().trim())) {
                        memCacheSizeModified = true;
                    }
                    return true;
                });
                prefMaxMemCacheSize.getOnPreferenceChangeListener().onPreferenceChange(prefMaxMemCacheSize, this.originalMaxMemCacheSize);
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null) sa.helpDialog = showHelp(sa, R.raw.help_settings_general_de, sa.webViewForHelp);
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        /** {@inheritDoc} */
        @Override
        public void onPause() {
            if (this.memCacheSizeModified) {
                SettingsActivity activity = (SettingsActivity) getActivity();
                if (activity != null && activity.service != null) {
                    this.memCacheSizeModified = false;
                    activity.service.createMemoryCache();
                }
            }
            super.onPause();
        }


    }

    //******************************************************************************************************************

    @Keep
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PollingPreferenceFragment extends PreferenceFragmentCompat {

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
            int bucket = usm.getAppStandbyBucket();
            // bucket will be 5 if the app is whitelisted, 10 if the app is active
            View v = getView();
            View anchor = v != null ? Util.findTextView(v.findViewById(android.R.id.list), getString(R.string.pref_hint_poll_interval)) : null;
            if (anchor == null) anchor = a.getWindow().getDecorView();
            @StringRes int msg;
            switch (bucket) {
                case UsageStatsManager.STANDBY_BUCKET_WORKING_SET:
                    msg = R.string.msg_standby_bucket_workingset;
                    break;
                case UsageStatsManager.STANDBY_BUCKET_FREQUENT:
                    msg = R.string.msg_standby_bucket_frequent;
                    break;
                case UsageStatsManager.STANDBY_BUCKET_RARE:
                    msg = R.string.msg_standby_bucket_rare;
                    break;
                default:
                    msg = 0;
            }
            if (msg != 0) {
                new PopupManager().showPopup(anchor, getString(msg), 4_000L);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.settings_menu, menu);
            menu.setQwertyMode(true);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_polling, rootKey);
            setHasOptionsMenu(true);

            SettingsActivity activity = (SettingsActivity)getActivity();
            if (activity == null) return;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

            this.min = UpdateJobService.getMinimumIntervalInMinutes();
            this.maxNightInterval = Math.round(UpdateJobService.getNightDuration() * 60f);

            SwitchPreferenceCompat prefPoll = findPreference(App.PREF_POLL);
            if (prefPoll != null) {
                long failedBefore = prefs.getLong(App.PREF_POLL_FAILED, 0L);
                if (failedBefore != 0L) {
                    String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(failedBefore));
                    prefPoll.setSummaryOn(getString(R.string.pref_title_poll_on) + '\n' + getString(R.string.error_poll_failed, date));
                    prefPoll.setSummaryOff(getString(R.string.pref_title_poll_off) + '\n' + getString(R.string.error_poll_failed, date));
                }
            }

            SwitchPreferenceCompat prefPollOverMobile = findPreference(App.PREF_POLL_OVER_MOBILE);
            if (prefPollOverMobile != null) {
                if (prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, false)) {
                    prefPollOverMobile.setEnabled(true);
                } else {
                    prefPollOverMobile.setChecked(false);
                    prefPollOverMobile.setSummary(R.string.pref_title_pref_load_over_mobile_off);
                    prefPollOverMobile.setEnabled(false);
                }
            }

            SummarizingEditTextPreference prefPollInterval = findPreference(App.PREF_POLL_INTERVAL);
            if (prefPollInterval != null) {
                prefPollInterval.setStringRes(R.string.label_every_minutes);
                prefPollInterval.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
                prefPollInterval.setOnPreferenceChangeListener((preference, o) -> {
                    int interval;
                    try {
                        interval = Integer.parseInt(o.toString().trim());
                    } catch (NumberFormatException nfe) {
                        Toast.makeText(getActivity(), R.string.error_invalid_not_a_number, Toast.LENGTH_SHORT).show();
                        return false;
                    } catch (Exception ignored) {
                        return false;
                    }
                    if (interval < SettingsActivity.PollingPreferenceFragment.this.min) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.error_poll_minimum_interval, min), Toast.LENGTH_LONG).show();
                    }
                    return interval >= SettingsActivity.PollingPreferenceFragment.this.min;
                });
            }

            SummarizingEditTextPreference prefPollIntervalNight = findPreference(App.PREF_POLL_INTERVAL_NIGHT);
            if (prefPollIntervalNight != null) {
                prefPollIntervalNight.setStringRes(R.string.label_every_minutes);
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
                    if (interval < SettingsActivity.PollingPreferenceFragment.this.min) {
                        Toast.makeText(getActivity(), getResources().getString(R.string.error_poll_minimum_interval, SettingsActivity.PollingPreferenceFragment.this.min), Toast.LENGTH_LONG).show();
                    } else if (interval > SettingsActivity.PollingPreferenceFragment.this.maxNightInterval) {
                        Toast.makeText(getActivity(), getResources()
                                .getString(R.string.error_poll_maximum_interval, SettingsActivity.PollingPreferenceFragment.this.maxNightInterval), Toast.LENGTH_LONG).show();
                    }
                    return interval >= SettingsActivity.PollingPreferenceFragment.this.min && interval <= SettingsActivity.PollingPreferenceFragment.this.maxNightInterval;
                });
            }

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
                        SettingsActivity.PollingPreferenceFragment.this.prefPollStats.setSummary(R.string.label_poll_stats_none);
                        SettingsActivity.PollingPreferenceFragment.this.prefPollStats.setEnabled(false);
                    });
                    activity.snackbar.setActionTextColor(getResources().getColor(R.color.colorAccent));
                    activity.snackbar.show();
                    Util.fadeSnackbar(activity.snackbar, 4_900L);
                    return false;
                });
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null) sa.helpDialog = showHelp(sa, R.raw.help_settings_polling_de, sa.webViewForHelp);
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
                    && PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_POLL, false)) {
                new Handler().postDelayed(this::checkBucket, 3_000L);
            }
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
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
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
                prefProxyServer.getOnPreferenceChangeListener().onPreferenceChange(prefProxyServer, prefs.getString(App.PREF_PROXY_SERVER, null));
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_help) {
                SettingsActivity sa = (SettingsActivity)getActivity();
                if (sa != null) sa.helpDialog = showHelp(sa, R.raw.help_settings_data_de, sa.webViewForHelp);
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