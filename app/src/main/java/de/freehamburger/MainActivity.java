package de.freehamburger;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.JsonReader;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import de.freehamburger.adapters.NewsRecyclerAdapter;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.Content;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.Source;
import de.freehamburger.model.StreamQuality;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.model.TextFilter;
import de.freehamburger.model.Video;
import de.freehamburger.supp.PopupManager;
import de.freehamburger.supp.SearchContentProvider;
import de.freehamburger.supp.SearchHelper;
import de.freehamburger.util.Downloader;
import de.freehamburger.util.FileDeleter;
import de.freehamburger.util.Intro;
import de.freehamburger.util.Log;
import de.freehamburger.util.PrintUtil;
import de.freehamburger.util.SpaceBetween;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;
import de.freehamburger.views.ClockView;
import de.freehamburger.widget.WidgetProvider;

public class MainActivity extends NewsAdapterActivity implements SwipeRefreshLayout.OnRefreshListener {

    public static final String ACTION_SHOW_NEWS = BuildConfig.APPLICATION_ID + ".action.show_news";
    public static final String EXTRA_NEWS = BuildConfig.APPLICATION_ID + ".extra.news";
    /** ConnectException andSocketTimeoutException messages.toLowerCase() usually start with this (the first one with "Failed", the latter one with "failed") */
    private static final String ERROR_CONNECTION_FAILED_MSG_PREFIX = "failed to connect to ";
    @VisibleForTesting static final long[] INTRO_DELAYS = new long[] {500L, 1000L, 1000L, 2500L, 1000L, 4500L, 1000L, 1000L, 1000L, 1000L, 2000L, 1000L};
    /** maximum number of recent sources/categories to keep */
    private static final int MAX_RECENT_SOURCES = 10;
    private static final BitmapFactory.Options OPTS_FOR_QUICKVIEW = new BitmapFactory.Options();
    /** used to colorise the progress image in {@link #swipeRefreshLayout } - this designates the percentage completed when download is done and parsing starts  */
    @FloatRange(from = 0f, to = 255f) private static final float PROGRESS_DOWNLOAD_PARSE = 128f;
    /** used when the user has picked a font file to import */
    private static final int REQUEST_CODE_FONT_IMPORT = 815;
    private static final String STATE_LIST_POS = BuildConfig.APPLICATION_ID + ".state.list.pos";
    /** stores {@link #msgFoundShown} */
    private static final String STATE_MSG_FOUND_SHOWN = BuildConfig.APPLICATION_ID + ".state.msgfoundshown";
    /** contains a News object if the {@link #quickView} should be restored */
    private static final String STATE_QUIKVIEW = BuildConfig.APPLICATION_ID + ".state.quikview";
    private static final String STATE_RECENT_SOURCES = BuildConfig.APPLICATION_ID + ".state.recentsources";
    private static final String STATE_SOURCE = BuildConfig.APPLICATION_ID + ".state.source";
    private static final String TAG = "MainActivity";
    /** file tag for TTF files */
    private static final String TTF_TAG = ".ttf";
    private static final int UI_FLAGS_FOR_QUICKVIEW = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            OPTS_FOR_QUICKVIEW.inPreferredConfig = Bitmap.Config.HARDWARE;
        } else {
            OPTS_FOR_QUICKVIEW.inPreferredConfig = Bitmap.Config.RGB_565;
            OPTS_FOR_QUICKVIEW.inDither = true;
        }
        OPTS_FOR_QUICKVIEW.inMutable = false;
    }

    /** remembers Sources used recently to provide a 'back' navigation */
    private final Stack<Source> recentSources = new Stack<>();
    private final SparseArray<Source> sourceForMenuItem = new SparseArray<>();
    private final ConnectivityReceiver connectivityReceiver = new ConnectivityReceiver();
    @VisibleForTesting public DrawerLayout drawerLayout;
    @VisibleForTesting ClockView clockView;
    private CoordinatorLayout coordinatorLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fab;
    private RecyclerView recyclerView;
    private NewsRecyclerAdapter newsAdapter;
    private View plane;
    private Filter searchFilter = null;
    /** {@code true} when the message given in {@link R.string#msg_found msg_found} or {@link R.string#msg_not_found msg_not_found} has been shown */
    private boolean msgFoundShown = false;
    @IntRange(from = -1) private int listPositionToRestore = RecyclerView.NO_POSITION;
    @NonNull private Source currentSource = Source.HOME;
    private Snackbar snackbarMaybeQuit;
    private Intro intro;
    /** displays the article picture when the corresponding menu item is invoked */
    private ImageView quickView;
    /** the {@link News} that corresponds to the image displayed in {@link #quickView} */
    private News newsForQuickView;
    private boolean quickViewRequestCancelled;
    /** point of time when the user paused this Activity most recently */
    private long pausedAt = -1L;
    private AlertDialog infoDialog;
    /** timestamp indicating when a refresh failed because there was no network available */
    private long lastTimeRefreshFailedDueToNoNetwork = 0L;
    private PopupManager popupManager;
    /** Runnables to perform to display an introduction to the app - played sequentially with delays given in {@link #INTRO_DELAYS}. */
    @VisibleForTesting final Runnable[] introSteps = new Runnable[] {
            // show plane and open drawer, then display "Select a category in the right-hand drawer."
            () -> {
                if (this.isFinishing()) return;
                this.recyclerView.scrollToPosition(0);
                findViewById(R.id.plane).setVisibility(View.VISIBLE);
                this.drawerLayout.openDrawer(GravityCompat.END);
                this.popupManager.showPopup(this.drawerLayout, getString(R.string.intro_1), 4_500L, false);
            },
            // apply a reddish hue to the drawer
            () -> findViewById(R.id.navigationView).setBackgroundTintList(ColorStateList.valueOf(Util.getColor(this, R.color.colorIntro))),
            // reset drawer color
            () -> findViewById(R.id.navigationView).setBackgroundTintList(null),
            // close drawer
            () -> this.drawerLayout.closeDrawer(GravityCompat.END),
            // colorize clock and click it => the menu will be shown, then display "Search, apply filters and change other settings here."
            () -> {
                this.clockView.setTint(Util.getColor(this, R.color.colorIntro));
                this.clockView.performClick();
                this.popupManager.showPopup(this.coordinatorLayout, getString(R.string.intro_2), 4_500L, false);
            },
            // reset clock color and close the menu
            () -> {
                this.clockView.setTint(Color.TRANSPARENT);
                closeOptionsMenu();
            },
            // scroll downwards and display "Scroll through the list to see the articles."
            () -> {
                Point ds = Util.getDisplaySize(this);
                this.recyclerView.smoothScrollBy(0, ds.y >> 1);
                this.popupManager.showPopup(this.recyclerView, getString(R.string.intro_3), 3_000L, false);
                this.recyclerView.smoothScrollBy(0, ds.y >> 1);
            },
            // scroll to top
            () -> this.recyclerView.smoothScrollToPosition(0),
            // display "tap an article to see details"
            () -> this.popupManager.showPopup(this.recyclerView, getString(R.string.intro_4), 3_000L, false),
            // tap the first list entry
            () -> {
                RecyclerView.ViewHolder vh = this.recyclerView.findViewHolderForAdapterPosition(0);
                if (vh == null) return;
                findViewById(R.id.plane).setTranslationY(vh.itemView.getHeight());
                ColorStateList originalTint = vh.itemView.getBackgroundTintList();
                vh.itemView.setTag(originalTint);
                vh.itemView.setBackgroundTintList(ColorStateList.valueOf(Util.getColor(this, R.color.colorIntro)));
                vh.itemView.setPressed(true);
            },
            // reset the first entry's appearance
            () -> {
                RecyclerView.ViewHolder vh = this.recyclerView.findViewHolderForAdapterPosition(0);
                if (vh == null) return;
                ColorStateList originalTint = (ColorStateList)vh.itemView.getTag();
                vh.itemView.setPressed(false);
                vh.itemView.setBackgroundTintList(originalTint);
                vh.itemView.setTag(null);
                findViewById(R.id.plane).setTranslationY(0);
            },
            // hide blocking plane and display last message ("Enjoy the app!" or similar)
            () -> {
                findViewById(R.id.plane).setVisibility(View.GONE);
                this.popupManager.showPopup(this.coordinatorLayout, getString(R.string.intro_5), 3_000L, true);
            }
    };
    /** used to remember the preferences before SettingsActivity is invoked, should be set in {@link #onMenuItemSelected(MenuItem)} */
    private Map<String, ?> recentPreferences = null;
    /** used to remember the font before SettingsActivity is invoked (separate from recentPreferences because, {@link SharedPreferences#getAll() according to docs}, they must not be modified) */
    private long recentFontTimestamp = 0L;
    private News newsToLoadWhenServiceConnected = null;

    /**
     * Switches to another {@link Source}.<br>
     * Does not do anything if the given Source is the current one.
     * @param newSource Source
     * @param addToRecent {@code true} to add the Source to the Stack of recent Sources
     * @param cacheOnly {@code true} to never load data from remote, instead use local cache only
     */
    private void changeSource(@NonNull Source newSource, boolean addToRecent, boolean cacheOnly) {
        if (newSource == this.currentSource) return;
        //
        if (addToRecent) {
            this.recentSources.push(this.currentSource);
        }
        while (this.recentSources.size() > MAX_RECENT_SOURCES) {
            this.recentSources.remove(0);
        }
        //
        this.currentSource = newSource;
        // scroll to the top because the list will have new contents
        this.listPositionToRestore = 0;
        //
        updateTitle();
        //
        updateMenu();
        //
        onRefreshUseCache(cacheOnly);
    }

    /**
     * Removes the temporary search filter.
     */
    private void clearSearch() {
        this.searchFilter = null;
        if (this.newsAdapter != null) this.newsAdapter.clearTemporaryFilters();
        this.clockView.setTint(Color.TRANSPARENT);
        this.msgFoundShown = false;
        // clean the Intent
        Intent intent = getIntent();
        if (intent == null) return;
        intent.setAction(Intent.ACTION_MAIN);
        intent.removeExtra(SearchManager.QUERY);
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int cc;
            LinearLayoutManager lm;
            boolean withoutShift;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MOVE_HOME:
                    this.recyclerView.scrollToPosition(0);
                    break;
                case KeyEvent.KEYCODE_MOVE_END:
                    cc = this.newsAdapter.getItemCount();
                    if (cc > 0) this.recyclerView.scrollToPosition(cc - 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    if (this.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        NavigationView navigationView = findViewById(R.id.navigationView);
                        Menu menu = navigationView.getMenu();
                        int n = menu.size();
                        for (int i = 0; i < n - 1; i++) {
                            MenuItem item = menu.getItem(i);
                            if (item.isChecked()) {
                                MenuItem next = menu.getItem(i + 1);
                                Source src = this.sourceForMenuItem.get(next.getItemId());
                                changeSource(src, true, false);
                                break;
                            }
                        }
                        return true;
                    }
                    lm = (LinearLayoutManager) this.recyclerView.getLayoutManager();
                    if (lm != null) {
                        cc = this.newsAdapter.getItemCount();
                        int last = lm.findLastCompletelyVisibleItemPosition();
                        if (last < cc - 1) this.recyclerView.scrollToPosition(last + 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    if (this.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        NavigationView navigationView = findViewById(R.id.navigationView);
                        Menu menu = navigationView.getMenu();
                        int n = menu.size();
                        for (int i = 1; i < n; i++) {
                            MenuItem item = menu.getItem(i);
                            if (item.isChecked()) {
                                MenuItem prev = menu.getItem(i - 1);
                                Source src = this.sourceForMenuItem.get(prev.getItemId());
                                changeSource(src, true, false);
                                break;
                            }
                        }
                        return true;
                    }
                    lm = (LinearLayoutManager) this.recyclerView.getLayoutManager();
                    if (lm != null) {
                        int first = lm.findFirstCompletelyVisibleItemPosition();
                        if (first > 0) this.recyclerView.scrollToPosition(first - 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    // on multi-column layouts, shift must be pressed along with dpad-left to open the drawer
                    lm = (LinearLayoutManager) this.recyclerView.getLayoutManager();
                    withoutShift = lm != null && (!(lm instanceof GridLayoutManager) || ((GridLayoutManager) lm).getSpanCount() == 1);
                    if (withoutShift || event.isShiftPressed()) {
                        // no animation in tests
                        this.drawerLayout.openDrawer(GravityCompat.END, !Util.TEST);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // on multi-column layouts, shift must be pressed along with dpad-right to close the drawer
                    lm = (LinearLayoutManager) this.recyclerView.getLayoutManager();
                    withoutShift = lm != null && (!(lm instanceof GridLayoutManager) || ((GridLayoutManager) lm).getSpanCount() == 1);
                    if (withoutShift || event.isShiftPressed()) {
                        // no animation in tests
                        this.drawerLayout.closeDrawer(GravityCompat.END, !Util.TEST);
                    }
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public NewsRecyclerAdapter getAdapter() {
        return this.newsAdapter;
    }

    /** {@inheritDoc} */
    @Override
    int getMainLayout() {
        return R.layout.activity_main;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Set<View> getVisibleNewsViews() {
        return Util.getVisibleKids(this.recyclerView);
    }

    /**
     * Deals with the Intent that the Acticity has received.
     * @param intent Intent
     */
    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) intent = getIntent();
        final String action = intent.getAction();

        if (ACTION_SHOW_NEWS.equals(action)) {
            // to not invite jerks, don't cast to News here just yet
            Object potentialNews = intent.getSerializableExtra(EXTRA_NEWS);
            int notificationId = intent.getIntExtra(UpdateJobService.EXTRA_NOTIFICATION_ID, Integer.MIN_VALUE);
            int appWidgetId = intent.getIntExtra(WidgetProvider.EXTRA_FROM_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID);
            intent.setAction(Intent.ACTION_MAIN);
            intent.removeExtra(EXTRA_NEWS);
            intent.removeExtra(UpdateJobService.EXTRA_NOTIFICATION_ID);
            intent.removeExtra(WidgetProvider.EXTRA_FROM_WIDGET);
            if (notificationId != Integer.MIN_VALUE) {
                NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(notificationId);
            }
            if (!(potentialNews instanceof News)) return;
            // loadDetails(news) does not work here because the HamburgerService does not exist yet!
            Snackbar.make(this.coordinatorLayout, R.string.msg_justamoment, 1_000).show();
            this.newsToLoadWhenServiceConnected = (News)potentialNews;
            return;
        }

        if (UpdateJobService.ACTION_NOTIFICATION.equals(action)) {
            final String newsExternalId = intent.getStringExtra(UpdateJobService.EXTRA_FROM_NOTIFICATION);
            if (newsExternalId != null) {
                intent.removeExtra(UpdateJobService.EXTRA_FROM_NOTIFICATION);
                // scroll to matching article
                this.handler.postDelayed(() -> {
                    int index = MainActivity.this.newsAdapter.findNews(newsExternalId);
                    if (index >= 0) MainActivity.this.recyclerView.smoothScrollToPosition(index);
                }, 1_000L);
            }
            String sourceName = intent.getStringExtra(UpdateJobService.EXTRA_SOURCE);
            if (sourceName != null) {
                try {
                    this.currentSource = Source.valueOf(sourceName);
                } catch (IllegalArgumentException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Unknown Source: " + sourceName);
                }
            } else if (BuildConfig.DEBUG) Log.e(TAG, "Missing EXTRA_SOURCE!");
            this.listPositionToRestore = 0;
            updateTitle();
            return;
        }

        if (getString(R.string.main_action_font_delete).equals(action)) {
            File fontFile = new File(getFilesDir(), App.FONT_FILE);
            if (fontFile.isFile()) {
                Util.deleteFile(fontFile);
                if (this.newsAdapter != null) {
                    this.newsAdapter.setTypeface(null);
                }
                Toast.makeText(getApplicationContext(), R.string.msg_font_deleted, Toast.LENGTH_SHORT).show();
            }
            Intent settingsActivityIntent = new Intent(this, SettingsActivity.class);
            settingsActivityIntent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsActivity.AppearancePreferenceFragment.class.getName());
            startActivity(settingsActivityIntent);
            return;
        }
        if (getString(R.string.main_action_font_import).equals(action)) {
            Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
            pickIntent.setType("*/*");    // Android does not know a suitable mime type for ttf
            pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
            pickIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            if (getPackageManager().resolveActivity(pickIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                //noinspection deprecation
                startActivityForResult(pickIntent, REQUEST_CODE_FONT_IMPORT);
            }
            return;
        }
        if (Intent.ACTION_SEARCH.equals(action) || getString(R.string.app_search_action).equals(action)) {
            // R.string.app_search_action is set in searchable.xml
            /*
            if the user has picked a suggestion, intent.getData().getLastPathSegment() will contain "suggestion#source" (something like "a61#NEWS",
            see SearchContentProvider.SUGGESTION_PROJECTION, from SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID)
             */
            String selectedSuggestion = intent.getData() != null ? intent.getData().getLastPathSegment() : null;
            String query = selectedSuggestion != null ? selectedSuggestion : intent.getStringExtra(SearchManager.QUERY);
            if (query == null) return;
            int sep = query.lastIndexOf(SearchHelper.WORD_SOURCE_SEPARATOR);
            String queryString = sep > 0 ? query.substring(0, sep) : query;
            @Nullable Source source = null;
            try {
                if (sep > 0) source = Source.valueOf(query.substring(sep + 1));
            } catch (IllegalArgumentException ignored) {
            }
            this.searchFilter = new TextFilter(queryString.toLowerCase(Locale.GERMAN).trim(), true, true);
            if (source == null) source = this.currentSource;
            if (source != this.currentSource) {
                changeSource(source, true, true);
            } else {
                File file = ((App)getApplicationContext()).getLocalFile(source);
                parseLocalFileAsync(file);
            }
            return;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            handleViewAction(intent);
            return;
        }
        if (handleShortcutIntent(intent)) {
            return;
        }
        if (BuildConfig.DEBUG && action != null && !Intent.ACTION_MAIN.equals(action)) {
            Log.w(TAG, "Unhandled action: " + action);
        }
    }

    @VisibleForTesting
    public boolean handleShortcutIntent(@NonNull Intent intent) {
        Source source = Source.getSourceFromAction(intent.getAction());
        if (source != null) {
            changeSource(source, true, false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                try {
                    ShortcutManager shortcutManager = (ShortcutManager) getSystemService(Context.SHORTCUT_SERVICE);
                    if (shortcutManager != null) shortcutManager.reportShortcutUsed(source.name());
                } catch (Exception ignored) {
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Handles {@link Intent#ACTION_VIEW}.
     * @param intent Intent
     */
    private void handleViewAction(@NonNull Intent intent) {
        Uri data = intent.getData();
        String scheme = intent.getScheme();
        if (BuildConfig.DEBUG) Log.i(TAG, "ACTION_VIEW with data = \"" + data + "\", scheme=" + scheme + ", type=" + intent.getType());
        if (data == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "No url received!");
            finish();
            return;
        }
        String host = data.getHost();
        if (host != null && host.equals(App.URI_TELETEXT_HOST)) {
            String lps = data.getLastPathSegment();
            int page = 100;
            if (lps != null) {
                try {
                    page = Integer.parseInt(lps);
                    if (page < 100 || page > 899) page = 100;
                } catch (Exception ignored) {
                }
            }
            startTeletext(true, page);
            return;
        }
        // check whether it is a ttf file
        if (data.toString().toLowerCase(java.util.Locale.US).endsWith(TTF_TAG)) {
            // it is a ttf file -> import it, and then proceed normally
            Typeface tf = importTtf(data, true);
            if (this.newsAdapter != null) this.newsAdapter.setTypeface(tf);
            return;
        } else if ("content".equals(scheme) && "*/*".equals(intent.getType())) {
            // some stoopid doodle siftware has passed meaningless nonsense to this app
            // (data like "content://com.android.providers.downloads.documents/document/476", type="*/*")
            // let's try to find out whether it is a ttf file
            Typeface tf = importTtf(data, false);
            if (tf != null) {
                // apparently if was indeed a ttf file -> proceed normally
                if (this.newsAdapter != null) this.newsAdapter.setTypeface(tf);
                return;
            }
        }
        if (("https".equals(scheme) || "http".equals(scheme))
                && !getString(R.string.viewable_host_1).equalsIgnoreCase(host)
                && !getString(R.string.viewable_host_2).equalsIgnoreCase(host)) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Invalid host \"" + host + "\"");
            finish();
        }
        // if we are here, we did not find anything we could do with that VIEW intent, so proceed normally
    }

    /** {@inheritDoc} */
    @Override
    boolean hasMenuOverflowButton() {
        return false;
    }

    /**
     * The user has tapped the 'quick view' which, when visible, displays the article's {@link News#getTeaserImage() teaser image}.
     * @param ignored ignored View
     */
    public void hideQuickView(@Nullable View ignored) {
        if (this.quickView.getVisibility() != View.VISIBLE) this.quickViewRequestCancelled = true; else this.quickView.setVisibility(View.GONE);
        this.quickView.setImageBitmap(null);
        this.newsForQuickView = null;
        this.plane.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    /**
     * Imports a ttf font file.<br>
     * Upon success, a {@link Snackbar} will be displayed.
     * @param uri Uri to read from
     * @param showMsgUponFail {@code true} to display a Snackbar upon failure
     * @return Typeface
     */
    @Nullable
    private Typeface importTtf(@RequiresPermission @NonNull Uri uri, final boolean showMsgUponFail) {
        InputStream in = null;
        Typeface tf = null;
        //
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                if (showMsgUponFail) {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_failed, Snackbar.LENGTH_LONG).show();
                }
                return null;
            }
            // copy data to a temporary file
            File tempFile = File.createTempFile("tempfont", TTF_TAG);
            // set a safety barrier of 4 MB; assumed that a truetype font file will not be bigger
            Util.copyFile(in, tempFile, -1, 1_048_576L << 2);
            Util.close(in);
            in = null;
            // check whether the temporary file contains indeed a ttf file
            TtfInfo ttfInfo = TtfInfo.getTtfInfo(tempFile);
            String fontName = ttfInfo.getFontFullName();
            if (TextUtils.isEmpty(fontName)) {
                if (BuildConfig.DEBUG) Log.w(TAG, "The data apparently does not belong to a ttf file!");
                if (showMsgUponFail) {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_failed, Snackbar.LENGTH_LONG).show();
                }
                Util.deleteFile(tempFile);
                return null;
            }
            // rename temp file to the font file
            File fontFile = new File(getFilesDir(), App.FONT_FILE);
            if (!tempFile.renameTo(fontFile)) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Could not rename temp file to \"" + fontFile.getName() + "\"!");
                if (showMsgUponFail) {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_failed, Snackbar.LENGTH_LONG).show();
                }
                Util.deleteFile(tempFile);
                return null;
            }
            tf = Util.loadFont(this);
            if (tf != null) {
                Snackbar sb;
                if (!TextUtils.isEmpty(fontName)) {
                    sb = Snackbar.make(this.coordinatorLayout, getString(R.string.msg_font_import_done_ext, fontName), Snackbar.LENGTH_LONG);
                } else {
                    sb = Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_done, Snackbar.LENGTH_LONG);
                }
                sb.show();
            } else {
                if (showMsgUponFail) {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_failed, Snackbar.LENGTH_LONG).show();
                }
                Util.deleteFile(fontFile);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (TextUtils.isEmpty(msg)) msg = e.toString();
            if (BuildConfig.DEBUG) Log.e(TAG, msg);
            if (showMsgUponFail) {
                //noinspection ConstantConditions
                if (msg.contains("EACCES")) {
                    Snackbar.make(this.coordinatorLayout, R.string.error_permission_denied, Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_font_import_failed, Snackbar.LENGTH_LONG).show();
                }
            }
        } finally {
            Util.close(in);
        }
        return tf;
    }

    /**
     * Displays the News' details in a {@link NewsActivity}.
     * @param news News
     */
    private void loadDetails(@NonNull News news) {
        String url = news.getDetails();
        // check that url is non-null
        if (url == null) {
            Snackbar.make(this.coordinatorLayout, R.string.error_news_not_found, Snackbar.LENGTH_LONG).show();
            return;
        }
        try {
            File tempFile = File.createTempFile("details", news.isRegional() ? News.FILE_TAG_REGIONAL : News.FILE_TAG);
            this.service.loadFile(url, tempFile, (completed, result) -> {
                if (!completed || result == null) {
                    Snackbar.make(this.coordinatorLayout, R.string.error_download_failed2, Snackbar.LENGTH_LONG).show();
                    return;
                }
                if (result.rc >= 400) {
                    if (result.rc == 404) Snackbar.make(this.coordinatorLayout, R.string.error_news_not_found, Snackbar.LENGTH_LONG).show();
                    else Snackbar.make(this.coordinatorLayout, getString(R.string.error_download_failed, result.toString()), Snackbar.LENGTH_LONG).show();
                    return;
                }
                JsonReader reader = null;
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    reader = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(tempFile)), StandardCharsets.UTF_8));
                    reader.setLenient(true);
                    @News.Flag int flags = 0;
                    boolean htmlEmbed = prefs.getBoolean(App.PREF_SHOW_EMBEDDED_HTML_LINKS, App.PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT);
                    if (htmlEmbed) flags |= News.FLAG_INCLUDE_HTMLEMBED;
                    News parsed = News.parseNews(reader, news.isRegional(), flags);
                    Util.close(reader);
                    reader = null;
                    if (prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT)) {
                        News.correct(parsed);
                    }
                    Intent intent = new Intent(this, NewsActivity.class);
                    intent.putExtra(NewsActivity.EXTRA_NEWS, parsed);
                    intent.putExtra(NewsActivity.EXTRA_JSON, tempFile.getAbsolutePath());
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fadein, R.anim.fadeout).toBundle());
                } catch (Exception e) {
                    Util.close(reader);
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                    Snackbar.make(this.coordinatorLayout, R.string.error_parsing, Snackbar.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
        }
    }

    /**
     * Quits the app, if the user agrees.
     */
    private void maybeQuit() {
        if (this.snackbarMaybeQuit != null && this.snackbarMaybeQuit.isShown()) {
            this.snackbarMaybeQuit.dismiss();
            finish();
            return;
        }
        boolean ask = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_ASK_BEFORE_FINISH, App.PREF_ASK_BEFORE_FINISH_DEFAULT);
        if (ask) {
            // close drawer because otherwise the snackbar action is not visible
            this.drawerLayout.closeDrawer(GravityCompat.END, true);
            // ask user whether (s)he had enough
            this.snackbarMaybeQuit = Snackbar.make(this.coordinatorLayout, R.string.action_quit, 5_000);
            this.snackbarMaybeQuit.setAction(R.string.label_yes, v -> finish());
            Util.fadeSnackbar(this.snackbarMaybeQuit, this.handler,5_001L);
        } else {
            finish();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        if (resultCode != RESULT_OK || intent == null) return;
        if (requestCode == REQUEST_CODE_FONT_IMPORT) {
            Uri fontUri = intent.getData();
            if (fontUri != null) {
                Typeface tf = importTtf(fontUri, true);
                if (tf != null) {
                    if (this.newsAdapter != null) this.newsAdapter.setTypeface(tf);
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    /**
     * The user has pressed ◀ (the back button) for a veery loong time.
     * @return {@code true} if the action has been dealt with
     */
    private boolean onBackLongPressed() {
        if (this.recentSources.isEmpty()) return false;
        ListAdapter adapter = new BaseAdapter() {

            final int n = MainActivity.this.recentSources.size();

            @Override
            public int getCount() {
                return n;
            }

            @Override
            public Object getItem(int position) {
                return MainActivity.this.recentSources.get(n - position - 1);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView)convertView;
                } else {
                    tv = new TextView(MainActivity.this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        tv.setTextAppearance(R.style.TextAppearance_Menu);
                    } else {
                        tv.setTextAppearance(MainActivity.this, R.style.TextAppearance_Menu);
                    }
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); // standard seems to be 14 which is a wee bit small
                    tv.setMaxLines(1);
                    tv.setPadding(80,8,8,8);
                }
                Source source = (Source)getItem(position);
                tv.setText(getString(source.getLabel()));
                return tv;
            }
        };
        AlertDialog ad = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.label_recent_sources)
                .setAdapter(adapter, (dialog, which) -> {
                    Source selected = (Source)adapter.getItem(which);
                    // remove the sources from recentSources that follow the selected one
                    int n = adapter.getCount();
                    for (int i = which + 1; i < n; i++) {
                        MainActivity.this.recentSources.pop();
                    }
                    //
                    changeSource(selected, false, false);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .setPositiveButton(R.string.action_finish, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .create();
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_SWIPE_TO_DISMISS, false)) {
            ad.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
        }
        ad.show();
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (this.intro != null && this.intro.isPlaying()) {
            return;
        }
        // if a search filter had been applied, accept the back button as command to remove it
        if (this.newsAdapter != null && this.newsAdapter.hasTemporaryFilter()) {
            clearSearch();
            return;
        }
        // if the quickView is shown, accept the back button as command to hide it
        if (this.quickView.getVisibility() == View.VISIBLE) {
            hideQuickView(this.quickView);
            return;
        }
        @App.BackButtonBehaviour int useBack = PreferenceManager.getDefaultSharedPreferences(this).getInt(App.PREF_USE_BACK_IN_APP, App.USE_BACK_FINISH);
        switch (useBack) {
                case App.USE_BACK_FINISH:
                    maybeQuit();
                    return;
                case App.USE_BACK_HOME:
                    if (this.currentSource != Source.HOME) {
                        this.listPositionToRestore = 0;
                        this.recentSources.clear();
                        changeSource(Source.HOME, false, false);
                    } else {
                        maybeQuit();
                    }
                    return;
                case App.USE_BACK_BACK:
                    if (!this.recentSources.isEmpty()) {
                        this.currentSource = this.recentSources.pop();
                        this.listPositionToRestore = 0;
                        updateTitle();
                        updateMenu();
                        onRefreshUseCache(false);
                    } else {
                        maybeQuit();
                    }
                    return;
        }

        super.onBackPressed();
    }

    @Override
    void onColumnCountChanged(@NonNull SharedPreferences prefs) {
        selectLayoutManager(prefs, this.recyclerView);
    }

    /** {@inheritDoc} <br><br>
     * For preparation of the context menu, see {@link NewsRecyclerAdapter.ViewHolder#onCreateContextMenu(ContextMenu, View, ContextMenu.ContextMenuInfo)}
     */
    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        final int id = menuItem.getItemId();
        if (id == R.id.action_view_in_browser) {
            News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            if (news.getDetailsWeb() == null) return true;
            // opens the news' detailsWeb URL
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(news.getDetailsWeb()), "text/html");
            try {
                startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle());
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                Snackbar.make(this.coordinatorLayout, R.string.error_browser_failed, Snackbar.LENGTH_LONG).show();
            }
            return true;
        }
        if (id == R.id.action_share_news) {
            News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            if (news.getDetailsWeb() == null) return true;
            // prefer title over topline because it's usually more informative
            String title = news.getTitle();
            if (TextUtils.isEmpty(title)) title = news.getTopline();
            // shares the news' detailsWeb URL
            Util.sendUrl(this, news.getDetailsWeb(), title);
            return true;
        }
        if (id == R.id.action_share_video) {
            // share bottom video if present (the top video (from News.streams) is generally not interesting and thus ignored here)
            News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            if (!news.hasBottomVideo()) return true;
            @SuppressWarnings("ConstantConditions")
            List<Video> videoList = news.getContent().getVideoList();
            if (BuildConfig.DEBUG && videoList.size() > 1) Log.w(TAG, "Has more than one video: " + news);
            //
            final Map<StreamQuality, String> streams = videoList.get(0).getStreams();
            if (streams.size() > 1) {
                CharSequence[] items = new CharSequence[streams.size()];
                int i = 0;
                final List<StreamQuality> qualities = new ArrayList<>(streams.keySet());
                Collections.sort(qualities, (o1, o2) -> {
                    int w1 = o1.getWidth();
                    int w2 = o2.getWidth();
                    if (w1 == w2) return o1.name().compareToIgnoreCase(o2.name());
                    if (w1 == -1) return 1; else if (w2 == -1) return -1;
                    return Integer.compare(w1, w2);
                });
                for (StreamQuality q : qualities) {
                    int labelRes = q.getLabel();
                    int width = q.getWidth();
                    items[i++] = labelRes > -1 ? (width > 0 ? getString(labelRes, width) : getString(labelRes)) : q.name();
                }
                AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.action_quality_select)
                        .setItems(items, (dialog, which) -> {
                            StreamQuality selectedQuality = qualities.get(which);
                            String source = streams.get(selectedQuality);
                            if (source != null) Util.sendUrl(this, source, news.getTitle());
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
                if (qualities.contains(StreamQuality.ADAPTIVESTREAMING)) {
                    Intent wotswotwot = new Intent(Intent.ACTION_VIEW);
                    wotswotwot.setDataAndType(Uri.parse(getString(R.string.url_wikipedia_http_streaming)), "text/html");
                    if (getPackageManager().resolveActivity(wotswotwot, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        builder.setNeutralButton(getString(R.string.label_streamquality_adaptive) + "? (Wikipedia)", (dialog, which) -> {
                            dialog.cancel();
                            try {
                                startActivity(wotswotwot);
                            } catch (Exception ignored) {
                                Snackbar.make(coordinatorLayout, R.string.error_browser_failed, Snackbar.LENGTH_LONG).show();
                            }
                        });
                    }
                }
                AlertDialog d = builder.create();
                if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_SWIPE_TO_DISMISS, false)) {
                    d.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
                }
                d.show();
            } else {
                Util.sendUrl(this, streams.values().iterator().next(), news.getTitle());
            }
            return true;
        }
        if (id == R.id.action_share_image) {
            News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            TeaserImage image = news.getTeaserImage();
            if (image == null) return true;
            String url = image.getBestImage();
            if (url == null) return true;
            String title = news.getTitle();
            Util.sendUrl(this, url, title);
            return true;
        }
        if (id == R.id.action_view_picture) {
            // the menu item should have been disabled if there is no image (see NewsRecyclerAdapter.onCreateContextMenu())
            News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            viewImage(news, true);
            return true;
        }
        if (id == R.id.action_print_picture) {
            // the menu item should have been disabled if there is no image (see NewsRecyclerAdapter.onCreateContextMenu())
            final News news = this.newsAdapter.getItem(this.newsAdapter.getContextMenuIndex());
            TeaserImage image = news.getTeaserImage();
            if (image == null) return true;
            String url = image.getBestImage();
            if (url == null) return true;
            try {
                final File temp = File.createTempFile("photo", null);
                this.service.loadFile(Util.makeHttps(url), temp, (completed, result) -> {
                    if (!completed || result == null || result.rc >= 400 || result.file == null) {
                        Util.deleteFile(temp);
                        Snackbar.make(this.coordinatorLayout, R.string.msg_print_failed, Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    Bitmap bmp = BitmapFactory.decodeFile(result.file.getAbsolutePath());
                    if (bmp == null) {
                        Snackbar.make(this.coordinatorLayout, R.string.msg_print_failed, Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    PrintUtil.printImage(this, bmp, news.getTitle() != null ? news.getTitle() : news.getFirstSentence());
                });
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            }
        }
        return super.onContextItemSelected(menuItem);
    }

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            this.currentSource = Source.valueOf(savedInstanceState.getString(STATE_SOURCE, Source.HOME.name()));
            updateTitle();
            this.recentSources.clear();
            String[] recentSources = savedInstanceState.getStringArray(STATE_RECENT_SOURCES);
            if (recentSources != null) {
                for (String rs : recentSources) {
                    try {
                        Source source = Source.valueOf(rs);
                        this.recentSources.add(source);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            this.listPositionToRestore = savedInstanceState.getInt(STATE_LIST_POS, RecyclerView.NO_POSITION);
            this.newsForQuickView = (News)savedInstanceState.getSerializable(STATE_QUIKVIEW);
            this.msgFoundShown = savedInstanceState.getBoolean(STATE_MSG_FOUND_SHOWN);
        }
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sploosh();

        setVolumeControlStream(App.STREAM_TYPE);

        this.popupManager = new PopupManager();

        this.drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navigationView = findViewById(R.id.navigationView);
        // add some space between the menu items
        try {
            RecyclerView navMenuView = (RecyclerView)navigationView.getChildAt(0);
            navMenuView.addItemDecoration(new SpaceBetween(this, getResources().getDimensionPixelSize(R.dimen.space_between_menu_items)));
            // hide the useless, ugly scrollbar that is showing at the right edge of smaller screens
            navMenuView.setVerticalScrollBarEnabled(false);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        // prepare SparseArray to match menu item id to Source
        this.sourceForMenuItem.put(R.id.action_section_homepage, Source.HOME);
        this.sourceForMenuItem.put(R.id.action_section_news, Source.NEWS);
        this.sourceForMenuItem.put(R.id.action_section_inland, Source.INLAND);
        this.sourceForMenuItem.put(R.id.action_section_ausland, Source.AUSLAND);
        this.sourceForMenuItem.put(R.id.action_section_wirtschaft, Source.WIRTSCHAFT);
        this.sourceForMenuItem.put(R.id.action_section_sport, Source.SPORT);
        this.sourceForMenuItem.put(R.id.action_section_regional, Source.REGIONAL);
        this.sourceForMenuItem.put(R.id.action_section_wissen, Source.WISSEN);
        this.sourceForMenuItem.put(R.id.action_section_video, Source.VIDEO);
        this.sourceForMenuItem.put(R.id.action_section_channels, Source.CHANNELS);
        this.sourceForMenuItem.put(R.id.action_section_weather, Source.WEATHER);
        this.sourceForMenuItem.put(R.id.action_section_iv, Source.IV);
        // check menuItem matching currentSource on initialisation
        updateMenu();
        // react to selections being made in the navigation menu
        navigationView.setNavigationItemSelectedListener(menuItem -> {
                 // ignore if intro is playing
                if (this.intro != null && this.intro.isPlaying()) return true;
                // when item is tapped, close drawer (after a brief pause to let the user see the new selection)
                this.handler.postDelayed(() -> this.drawerLayout.closeDrawer(navigationView, true), 150L);
                // select source that matches the menu item
                changeSource(this.sourceForMenuItem.get(menuItem.getItemId()), true, false);
                return true;
            });
        // refresh via top-bottom swipe
        this.swipeRefreshLayout = findViewById(R.id.swiperefresh);
        if (this.swipeRefreshLayout != null) {
            // the circular progress indicator will start with a transparent background and will receive an increasing alpha with a progressing download
            this.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
            this.swipeRefreshLayout.setOnRefreshListener(this);
            this.swipeRefreshLayout.setNestedScrollingEnabled(false);
        }
        //
        this.coordinatorLayout = findViewById(R.id.coordinator_layout);
        this.fab = findViewById(R.id.fab);
        this.plane = findViewById(R.id.plane);
        this.plane.setOnClickListener(this::hideQuickView);
        this.quickView = findViewById(R.id.quickView);
        this.quickView.setOnClickListener(this::hideQuickView);
        this.recyclerView = findViewById(R.id.recyclerView);
        //this.recyclerView.setHasFixedSize(true);
        selectLayoutManager(null, this.recyclerView);
        // enable context menus for news items
        this.recyclerView.setOnCreateContextMenuListener(this);
        //
        this.newsAdapter = new NewsRecyclerAdapter(this, Util.loadFont(this));
        this.recyclerView.setAdapter(this.newsAdapter);
        this.clockView = findViewById(R.id.clockView);
        updateTitle();
        this.clockView.setOnClickListener(v -> openOptionsMenu());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.fab.setOnClickListener(v -> {
                MainActivity.this.recyclerView.smoothScrollToPosition(0);
                ((AppBarLayout)findViewById(R.id.appbar_layout)).setExpanded(true);
            });
            this.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

                private final int displayHeight = Util.getDisplayHeight(MainActivity.this);
                private final Runnable fabHider = () -> MainActivity.this.fab.hide(null);
                private int verticalScrollPosition = 0;

                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    MainActivity.this.handler.removeCallbacks(this.fabHider);
                    this.verticalScrollPosition += dy;
                    if (this.verticalScrollPosition > this.displayHeight) {
                        MainActivity.this.fab.show(null);
                        // set elevation because for some reason it is beneath other views as defined in the xmls
                        MainActivity.this.fab.setElevation(48f);
                        MainActivity.this.handler.postDelayed(this.fabHider, HIDE_FAB_AFTER);
                    } else {
                        MainActivity.this.fab.hide(null);
                    }
                }
             });

        } else {
            this.fab.hide(null);
        }

        handleIntent(null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.clock_menu, menu);
        // if there is a qwert? keyboard connected, display menu shortcuts (btw, they are activated via the ctrl modifier but only if the menu is not visible)
        if (getResources().getConfiguration().keyboard == Configuration.KEYBOARD_QWERTY) {
            Util.decorateMenuWithShortcuts(menu);
        }
        //
        if (!BuildConfig.DEBUG) {
            MenuItem getLog = menu.findItem(R.id.action_get_log);
            if (getLog != null) getLog.setVisible(false);
            MenuItem delLog = menu.findItem(R.id.action_delete_log);
            if (delLog != null) delLog.setVisible(false);
            MenuItem showUpdates = menu.findItem(R.id.action_show_updates);
            if (showUpdates != null) showUpdates.setVisible(false);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void onDestroy() {
        this.popupManager.destroy();
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (onBackLongPressed()) return true;
            maybeQuit();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    /**
     * Handles the user's menu item selection.
     * @param item MenuItem
     * @return {@code true} if the menu item selection has been handled
     * @throws NullPointerException if {@code item} is {@code null}
     */
    private boolean onMenuItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_search) {
            // check whether search suggestions had been deleted in the past and, if so, inform the user accordingly
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            long sd = prefs.getLong(SearchContentProvider.PREF_SEARCHSUGGESTIONS_DELETED_LOCALE_CHANGE, 0L);
            if (sd > 0L) {
                Snackbar.make(this.coordinatorLayout, R.string.msg_search_sugg_deleted_locale, Snackbar.LENGTH_LONG).show();
                SharedPreferences.Editor ed = prefs.edit();
                ed.remove(SearchContentProvider.PREF_SEARCHSUGGESTIONS_DELETED_LOCALE_CHANGE);
                ed.apply();
            }
            // launch the system's search dialog (https://developer.android.com/guide/topics/search/search-dialog#SearchDialog)
            onSearchRequested();
            return true;
        }
        if (id == R.id.action_filter) {
            startActivity(new Intent(this, FilterActivity.class));
            return true;
        }
        if (id == R.id.action_select_regions) {
            AlertDialog ad = Region.selectRegions(this);
            ad.setOnDismissListener(dialog -> onRefreshUseCache(false));
            return true;
        }
        if (id == R.id.action_teletext) {
            startTeletext(false, 100);
            return true;
        }
        if (id == R.id.action_archive) {
            startActivity(new Intent(this, Archive.class));
            return true;
        }
        if (id == R.id.action_shortcut_create) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!Util.createShortcut(this, this.currentSource, null)) {
                    Snackbar.make(coordinatorLayout, R.string.error_shortcut_fail, Snackbar.LENGTH_LONG).show();
                }
            }
            return true;
        }
        if (id == R.id.action_settings) {
            // remember current preferences to be able to detect changes in onRestart()
            this.recentPreferences = PreferenceManager.getDefaultSharedPreferences(this).getAll();
            this.recentFontTimestamp = new File(getFilesDir(), App.FONT_FILE).lastModified();
            //
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_info) {
            final boolean swipeToDismiss = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_SWIPE_TO_DISMISS, false);
            final SpannableStringBuilder info = new SpannableStringBuilder().append('\n');
            SpannableString title = new SpannableString(getString(R.string.app_name));
            title.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableString version = new SpannableString(BuildConfig.VERSION_NAME);
            version.setSpan(new RelativeSizeSpan(0.75f), 0, version.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            info.append(title).append(' ').append(version);
            info.append("\n\n").append(getString(R.string.app_build_date, DateFormat.getDateTimeInstance().format(new Date(BuildConfig.BUILD_TIME))));
            info.append("\n\n").append(getString(R.string.app_license));
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_info)
                    .setMessage(info)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                    .setNeutralButton(R.string.label_license, (dialog, which) -> {
                        dialog.dismiss();
                        List<String> l = Util.loadResourceTextFile(MainActivity.this, R.raw.agpl, 544, false);
                        StringBuilder sb = new StringBuilder(34523);
                        for (String line : l) sb.append(line).append('\n');
                        @SuppressLint("InflateParams")
                        ScrollView sv = (ScrollView)getLayoutInflater().inflate(R.layout.multi_text_view, null);
                        TextView tv = sv.findViewById(R.id.textView);
                        // reduce text width for small to medium devices
                        if (Util.getDisplayDim(MainActivity.this).x < 8f) {
                            tv.setTextScaleX(0.75f);
                        }
                        tv.setText(sb);
                        tv.setContentDescription(getString(R.string.label_license));
                        ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        AlertDialog.Builder lb = new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle(R.string.label_license)
                                .setView(sv)
                                .setPositiveButton(android.R.string.ok, (dialog1, which1) -> dialog1.dismiss());
                        if (cm != null) {
                            lb.setNeutralButton(android.R.string.copy, (dialog12, which12) -> {
                                cm.setPrimaryClip(ClipData.newPlainText("AGPL-3.0", sb));
                                Snackbar.make(coordinatorLayout, R.string.msg_text_copied, Snackbar.LENGTH_SHORT).show();
                            });
                        }
                        this.infoDialog = lb.create();
                        if (swipeToDismiss) this.infoDialog.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
                        this.infoDialog.show();
                    })
                    ;
            this.infoDialog = builder.create();
            if (swipeToDismiss) this.infoDialog.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
            this.infoDialog.show();
        }
        // --- the following commands are for debug only ---
        if (id == R.id.action_show_updates) {
            List<Long> tss = UpdateJobService.getAllRequests(this);
            int n = tss.size();
            if (n < 1) {
                Toast.makeText(this, R.string.label_poll_stats_none, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (n > 150) tss = tss.subList(0, 150);
            final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            final StringBuilder sb = new StringBuilder(tss.size() << 5);
            long latest = tss.get(0);
            if (n > 1) {
                int i = 1;
                for (Long ts : tss) {
                    sb.append(i++).append(": ").append(df.format(new Date(ts))).append(" (").append(Math.round((latest - ts) / 60_000f)).append(" ')\n");
                    latest = ts;
                }
            } else {
                sb.append("1: ").append(df.format(new Date(latest))).append('\n');
            }
            AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.pref_title_background);
            builder.setMessage(sb);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
            builder.show();
            return true;
        }
        if (id == R.id.action_delete_log) {
            File logFile = Log.getFile();
            if (logFile == null || !logFile.isFile()) return true;
            boolean deleted = Log.deleteFile();
            Snackbar.make(this.coordinatorLayout, "Log file deleted: " + deleted, Snackbar.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_get_log) {
            if (!BuildConfig.DEBUG) return true;
            File logFile = Log.getFile();
            if (logFile == null || !logFile.isFile()) return true;
            File subdir = new File(getCacheDir(), App.EXPORTS_DIR);
            if (!subdir.isDirectory()) {
                if (!subdir.mkdirs()) return true;
            }
            Log.sync();
            String now = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date());

            File tempFile = new File(subdir, "log_" + now.replace(' ', '-').replace(':', '_').replace('.', '_').replace('/', '_') + ".txt");
            synchronized (Log.FILE_LOCK) {
                if (!Util.copyFile(logFile, tempFile, 0L)) {
                    Log.e(TAG, "Failed to copy " + logFile + " to " + tempFile);
                    return true;
                }
                if (tempFile.length() != logFile.length()) Log.e(TAG, "Exported log file differs from actual log file!");
            }
            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, App.getFileProvider(), tempFile));
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Log " + now);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (getPackageManager().resolveActivity(shareIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                startActivity(shareIntent);
            } else {
                Snackbar.make(this.coordinatorLayout, R.string.error_no_app, Snackbar.LENGTH_LONG).show();
            }
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void onNewIntent(final Intent newIntent) {
        super.onNewIntent(newIntent);
        if (newIntent == null) return;
        // copy relevant data to our "real" intent (the one accessible via getIntent())
        getIntent().fillIn(newIntent, Intent.FILL_IN_ACTION | Intent.FILL_IN_DATA);
        //
        handleIntent(newIntent);
    }

    /** {@inheritDoc} */
    @Override
    public void onNewsClicked(@NonNull News news, @NonNull View v, float x, float y) {
        if (this.intro != null && this.intro.isPlaying()) return;
        final Intent intent;
        @News.NewsType String type = news.getType();
        String urlToDetailsJson = news.getDetails();
        Content content = news.getContent();
        boolean isStory = News.NEWS_TYPE_STORY.equals(type);
        boolean isWebVw = News.NEWS_TYPE_WEBVIEW.equals(type);
        boolean isVideo = News.NEWS_TYPE_VIDEO.equals(type);
        //
        if (isStory && content != null) {
            intent = new Intent(this, NewsActivity.class);
            intent.putExtra(NewsActivity.EXTRA_NEWS, news);
        } else if (isStory && !TextUtils.isEmpty(urlToDetailsJson)) {
            loadDetails(news);
            intent = null;
        } else if (isWebVw
                || (isStory && TextUtils.isEmpty(urlToDetailsJson) && !TextUtils.isEmpty(news.getDetailsWeb()))) {   // <- in the "news" section the items do not have a "content"
            if (!Util.isNetworkAvailable(this)) {
                showNoNetworkSnackbar();
                intent = null;
            } else {
                intent = new Intent(this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.EXTRA_NEWS, news);
            }
        } else if (isVideo) {
            if (!Util.isNetworkAvailable(this)) {
                showNoNetworkSnackbar();
                intent = null;
            } else {
                if (Util.isNetworkMobile(this)) {
                    boolean loadVideos = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_LOAD_VIDEOS_OVER_MOBILE, App.DEFAULT_LOAD_VIDEOS_OVER_MOBILE);
                    if (!loadVideos) {
                        intent = null;
                        Snackbar.make(this.coordinatorLayout, R.string.pref_title_pref_load_videos_over_mobile_off, Snackbar.LENGTH_SHORT).show();
                    } else {
                        intent = new Intent(this, VideoActivity.class);
                        intent.putExtra(VideoActivity.EXTRA_NEWS, news);
                    }
                } else {
                    intent = new Intent(this, VideoActivity.class);
                    intent.putExtra(VideoActivity.EXTRA_NEWS, news);
                }
            }
        } else {
            intent = new Intent(this, NewsActivity.class);
            intent.putExtra(NewsActivity.EXTRA_NEWS, news);
        }
        //
        if (intent != null) {
            startActivity(intent, ActivityOptionsCompat.makeScaleUpAnimation(v, (int) x, (int) y, v.getWidth(), v.getHeight()).toBundle());
            clearSearch();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (this.intro != null && this.intro.isPlaying()) return false;
        return onMenuItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        this.pausedAt = System.currentTimeMillis();
        unregisterReceiver(this.connectivityReceiver);
        if (this.infoDialog != null && this.infoDialog.isShowing()) this.infoDialog.dismiss();
        if (this.intro != null && this.intro.isPlaying()) {
            this.intro.cancel();
            // play the intro again next time
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(App.PREF_PLAY_INTRO, true);
            ed.apply();
            // we either need to a) start anew or b) know what state the ui is currently in (is the blurry plane visible or not, etc.) to be able to clean up
            // a) is easier
            finish();
        }
        super.onPause();
        // next lifecycle method to be called will be onStop()
    }

    /** {@inheritDoc} */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
       boolean network = Util.isNetworkAvailable(this);
        MenuItem itemTeletext = menu.findItem(R.id.action_teletext);
        itemTeletext.setVisible(network);
        // pinned shortcuts are available only from Oreo on and only in some launchers (https://developer.android.com/guide/topics/ui/shortcuts/creating-shortcuts#pinned)
        MenuItem itemShortcutCreate = menu.findItem(R.id.action_shortcut_create);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.currentSource.getAction() != null) {
            ShortcutManager shortcutManager = (ShortcutManager)getSystemService(Context.SHORTCUT_SERVICE);
            itemShortcutCreate.setVisible(Util.canCreateShortcut(shortcutManager)
                    && !Util.hasShortcut(this, this.currentSource, shortcutManager));
        } else {
            itemShortcutCreate.setVisible(false);
        }
        MenuItem menuItemArchive = menu.findItem(R.id.action_archive);
        menuItemArchive.setVisible(Archive.hasItems(this));
        return super.onPrepareOptionsMenu(menu);
    }

    /** {@inheritDoc} */
    @Override
    public void onRefresh() {
        onRefresh(true);
    }

    /**
     * Refreshes the data.
     * @param showMsgOnNetworkFailure if {@code true} show a message if there is no network connection
     */
    private void onRefresh(boolean showMsgOnNetworkFailure) {
        if (this.currentSource.getLockHolder() != null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Cannot refresh - " + currentSource + " is locked!");
            this.swipeRefreshLayout.setRefreshing(false);
            return;
        }
        if (this.intro != null && this.intro.isPlaying()) {
            this.swipeRefreshLayout.setRefreshing(false);
            return;
        }
        App app = (App) getApplicationContext();
        // check whether loading is possible
        if (!Util.isNetworkAvailable(app)) {
            this.swipeRefreshLayout.setRefreshing(false);
            lastTimeRefreshFailedDueToNoNetwork = System.currentTimeMillis();
            if (showMsgOnNetworkFailure) showNoNetworkSnackbar();
            return;
        }
        if (this.service == null) {
            this.swipeRefreshLayout.setRefreshing(false);
            return;
        }
        this.lastTimeRefreshFailedDueToNoNetwork = 0L;

        // load remote file
        String url = this.currentSource.getUrl();
        if (this.currentSource.needsParams()) {
            if (this.currentSource == Source.REGIONAL) {
                url = url + Source.getParamsForRegional(this);
            } else {
                // unhandled case; source needs parameters but we don't know how to append them => revert to default source
                changeSource(Source.HOME, true, false);
                url = this.currentSource.getUrl();
            }
        }

        long mostRecentUpdate = app.getMostRecentUpdate(this.currentSource);
        this.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT);
        this.service.loadFile(url, app.getLocalFile(this.currentSource), mostRecentUpdate, new Downloader.DownloaderListener() {

            // let's remember the Source that we are loading now - in case the user changes it while we are loading...
            private final Source sourceToSetOnSuccess = MainActivity.this.currentSource;
            @ColorInt
            private final int c = Util.getColor(MainActivity.this, R.color.color_primary);

            /** {@inheritDoc} */
            @Override
            @MainThread
            public void downloadProgressed(@FloatRange(from = 0, to = 1) float progress) {
                int p = (int)(PROGRESS_DOWNLOAD_PARSE * progress) << 24;
                MainActivity.this.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(p + c);
            }

            /** {@inheritDoc} */
            @Override
            @MainThread
            public void downloaded(boolean completed, @Nullable final Downloader.Result result) {
                if (!completed || result == null || result.file == null) {
                    MainActivity.this.swipeRefreshLayout.setRefreshing(false);
                    return;
                }
                if (result.rc == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    Snackbar.make(MainActivity.this.coordinatorLayout, R.string.msg_not_modified, Snackbar.LENGTH_SHORT).show();
                    parseLocalFileAsync(result.file);
                    return;
                }
                if (result.rc != HttpURLConnection.HTTP_OK) {
                    MainActivity.this.swipeRefreshLayout.setRefreshing(false);
                    String msg = result.msg != null ? result.msg : "HTTP " + result.rc;
                    if ("Pin verification failed".equals(msg)) {
                        msg = getString(R.string.error_pin_verification);
                    } else if ("timeout".equals(msg)) {
                        msg = getString(R.string.error_timeout);
                    } else if (msg.toLowerCase(Locale.US).startsWith(ERROR_CONNECTION_FAILED_MSG_PREFIX)) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        String proxyType = prefs.getString(App.PREF_PROXY_TYPE, Proxy.Type.DIRECT.toString());
                        String pp = prefs.getString(App.PREF_PROXY_SERVER, null);
                        if (!Proxy.Type.DIRECT.toString().equals(proxyType) && pp != null && msg.toLowerCase(Locale.US).contains(pp.toLowerCase(Locale.US))) {
                            int colon = pp.indexOf(':');
                            String proxyServer, proxyPort;
                            if (colon > 0) {
                                proxyServer = pp.substring(0, colon)
                                        .trim();
                                proxyPort = pp.substring(colon + 1)
                                        .trim();
                            } else {
                                proxyServer = pp;
                                proxyPort = String.valueOf(App.DEFAULT_PROXY_PORT);
                            }
                            msg = getString(R.string.error_proxy_connection_failed, proxyServer, proxyPort);
                        } else {
                            String host = result.sourceUri != null ? Uri.parse(result.sourceUri).getHost() : null;
                            msg = getString(R.string.error_connection_failed, host != null ? host : msg.substring(ERROR_CONNECTION_FAILED_MSG_PREFIX.length()).trim());
                        }
                    }
                    Snackbar sb = Snackbar.make(MainActivity.this.coordinatorLayout, msg, BuildConfig.DEBUG ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG);
                    Util.setSnackbarMaxLines(sb, 6);
                    sb.show();
                    return;
                }

                if (MainActivity.this.currentSource != this.sourceToSetOnSuccess) {
                    MainActivity.this.currentSource = this.sourceToSetOnSuccess;
                    updateTitle();
                }
                ((App)getApplicationContext()).setMostRecentUpdate(MainActivity.this.currentSource, System.currentTimeMillis(), true);
                parseLocalFileAsync(result.file);
            }
        });
    }

    /**
     * Similar to {@link #onRefresh()} but tries the cache first.
     * @param cacheOnly if this is {@code true} then remote data will never be loaded
     */
    private void onRefreshUseCache(boolean cacheOnly) {
        App app = (App)getApplicationContext();
        boolean networkAvailable = Util.isNetworkAvailable(this);
        // first try the local file
        final File localFile = app.getLocalFile(this.currentSource);
        if (localFile.isFile()) {
            /*
            NOTE:
            the last modified timestamp denotes the point in time of the most recent article in that file
            It does not denote the most recent attempt (successful or not) to download the data!
             */
            boolean fileIsQuiteNew = System.currentTimeMillis() - app.getMostRecentUpdate(this.currentSource) < App.LOCAL_FILE_MAXAGE;
            if (cacheOnly || fileIsQuiteNew || !networkAvailable) {
            parseLocalFileAsync(localFile);
                if (!cacheOnly && !networkAvailable) {
                    showNoNetworkSnackbar();
                }
                return;
            }
        }
        if (cacheOnly) return;
        // if there is neither a local file nor a network connection, there is nothing we can do here
        if (!networkAvailable) {
            showNoNetworkSnackbar();
            return;
        }
        // if there is no local file or if it is too old, download the resource
        this.swipeRefreshLayout.setRefreshing(true);
            onRefresh();
        }

    /** {@inheritDoc} */
    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onRestart() {
        super.onRestart();
        //
        if (this.pausedAt > 0L && System.currentTimeMillis() - this.pausedAt > 300_000L) {
            // scroll to the top if the most recent user interaction was quite a while ago (so the user will likely have forgotten)
            this.listPositionToRestore = 0;
        }
        // check whether preferences have changed that affect the display (they might have if the user has just left the SettingsActivity)
        if (this.recentPreferences != null) {
            SharedPreferences currentPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            // recent* are the settings that applied before SettingsActivity was invoked
            Object recentBackground = this.recentPreferences.get(App.PREF_BACKGROUND);
            Object recentTimeMode = this.recentPreferences.get(App.PREF_TIME_MODE_RELATIVE);
            Object recentCorrectQuotationMarks = this.recentPreferences.get(App.PREF_CORRECT_WRONG_QUOTATION_MARKS);
            // current* are the current settings
            @App.BackgroundSelection int currentBackground = currentPrefs.getInt(App.PREF_BACKGROUND, App.BACKGROUND_AUTO);
            boolean currentTimeMode = currentPrefs.getBoolean(App.PREF_TIME_MODE_RELATIVE, App.PREF_TIME_MODE_RELATIVE_DEFAULT);
            boolean currentCorrectQuotationMarks = currentPrefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT);
            long currentFontTimestamp = new File(getFilesDir(), App.FONT_FILE).lastModified();
            // if any of them changed…
            if ((recentBackground instanceof Integer && (Integer)recentBackground != currentBackground)
                    || (recentTimeMode instanceof Boolean && (Boolean)recentTimeMode != currentTimeMode)
                    || (recentCorrectQuotationMarks instanceof Boolean && (Boolean)recentCorrectQuotationMarks != currentCorrectQuotationMarks)
                    || this.recentFontTimestamp != currentFontTimestamp) {
                // then the adapter must be updated
                this.newsAdapter.notifyDataSetChanged();
            }
            //
            this.recentPreferences = null;
            this.recentFontTimestamp = 0L;
        }
        // next lifecycle method to be called will be onStart() followed by onResume()
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hasTemporaryFilter = this.newsAdapter.setFilters(TextFilter.createTextFiltersFromPreferences(this));
        this.clockView.setTint(hasTemporaryFilter ? Util.getColor(this, R.color.colorFilter) : Color.TRANSPARENT);

        // let the user open the categories drawer via clicking the title (useful if the phone uses nav. gestures)
        if (prefs.getBoolean(App.PREF_CLICK_FOR_CATS, App.PREF_CLICK_FOR_CATS_DEFAULT)) {
            this.clockView.setOnTextClickListener(v -> this.drawerLayout.openDrawer(GravityCompat.END, true));
        } else {
            this.clockView.setOnTextClickListener(null);
        }

        // if the adapter is already filled, we are able to show the intro now (playIntro will return quickly if the adapter is empty)
        if (prefs.getBoolean(App.PREF_PLAY_INTRO, App.PREF_PLAY_INTRO_DEFAULT)) {
            if (playIntro()) {
                SharedPreferences.Editor ed = prefs.edit();
                ed.putBoolean(App.PREF_PLAY_INTRO, false);
                ed.apply();
            }
        }
        registerReceiver(this.connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /** {@inheritDoc} */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_SOURCE, this.currentSource.name());
        String[] recentSourcesArray = new String[this.recentSources.size()];
        for (int i = 0; i < recentSourcesArray.length; i++) {
            recentSourcesArray[i] = this.recentSources.get(i).name();
        }
        outState.putStringArray(STATE_RECENT_SOURCES, recentSourcesArray);
        //
        outState.putSerializable(STATE_QUIKVIEW, this.quickView != null && this.quickView.getVisibility() == View.VISIBLE ? this.newsForQuickView : null);
        //
        outState.putBoolean(STATE_MSG_FOUND_SHOWN, this.msgFoundShown);
        //
        RecyclerView.LayoutManager rlm = this.recyclerView.getLayoutManager();
        int top = RecyclerView.NO_POSITION;
        if (rlm instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) rlm;
            top = lm.findFirstVisibleItemPosition();
        } else if (rlm instanceof StaggeredGridLayoutManager) {
            StaggeredGridLayoutManager slm = (StaggeredGridLayoutManager)rlm;
            int[] position = new int[slm.getSpanCount()];
            slm.findFirstVisibleItemPositions(position);
            top = position[0];
        }
        if (top == RecyclerView.NO_POSITION) {
            super.onSaveInstanceState(outState);
            return;
        }
        outState.putInt(STATE_LIST_POS, top);
        //
        super.onSaveInstanceState(outState);
    }

    /** {@inheritDoc} */
    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        super.onServiceConnected(name, binder);
        if (HamburgerService.class.getName().equals(name.getClassName())) {
            if (this.newsToLoadWhenServiceConnected != null) {
                loadDetails(this.newsToLoadWhenServiceConnected);
                this.newsToLoadWhenServiceConnected = null;
                return;
            }
            if (this.newsForQuickView != null) {
                viewImage(this.newsForQuickView, true);
            }
            // refresh data only if there is currently no search filter
            if (this.searchFilter == null) {
                // check whether we need to refresh (if the data is too old or if the adapter is empty or if it displays the wrong data)
                boolean adapterIsEmpty = this.newsAdapter.getItemCount() == 0;
                boolean adapterDataIsOld = System.currentTimeMillis() - this.newsAdapter.getUpdated() >= App.LOCAL_FILE_MAXAGE;
                boolean wrongSource = this.currentSource != this.newsAdapter.getSource();
                boolean missingImages = NewsRecyclerAdapter.hasMissingImages(this);
                if (adapterDataIsOld || adapterIsEmpty || wrongSource || missingImages) {
                    onRefreshUseCache(false);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        super.onSharedPreferenceChanged(prefs, key);
        if (App.PREF_FILTERS.equals(key) || App.PREF_APPLY_FILTERS_TO_CATEGORIES.equals(key)) {
            updateMenu();
        }
    }

    /**
     * Parses the given json file and updates the {@link #newsAdapter adapter} afterwards.<br>
     * Does not do anything if the file does not exist.
     * @param file File to parse
     */
    private synchronized void parseLocalFileAsync(@Nullable File file) {
        if (file == null) return;
        BlobParser blobParser = new BlobParser(this, new BlobParser.BlobParserListener() {
            @ColorInt
            private final int c = Util.getColor(MainActivity.this, R.color.color_primary);

            @Override
            public void blobParsed(@Nullable Blob blob, boolean ok, @Nullable Throwable oops) {

                if (!ok || blob == null || oops != null) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Parsing failed: " + oops, oops);
                    Snackbar sb = Snackbar.make(MainActivity.this.coordinatorLayout, R.string.error_parsing, Snackbar.LENGTH_INDEFINITE);
                    sb.setAction("↻", v -> handler.postDelayed(MainActivity.this::onRefresh, 500L));
                    //sb.setActionTextColor(Util.getColor(MainActivity.this, R.color.colorPrimaryLight));
                    Util.setSnackbarActionFont(sb, Typeface.DEFAULT_BOLD, getResources().getInteger(R.integer.snackbar_action_font_size));
                    sb.show();
                    MainActivity.this.swipeRefreshLayout.setRefreshing(false);
                    return;
                }
                List<News> sortedJointList = blob.getAllNews();
                // limit number of RecyclerView columns to number of News items (effect noticeable, for example, in Weather section on tablets in landscape mode)
                int nItems = sortedJointList.size();
                RecyclerView.LayoutManager layoutManager = MainActivity.this.recyclerView.getLayoutManager();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                if (layoutManager instanceof GridLayoutManager) {
                    if (nItems >= 1 && nItems < ((GridLayoutManager)layoutManager).getSpanCount()) {
                        // less News items than columns -> reduce number of columns (must be at least 1)
                        ((GridLayoutManager)layoutManager).setSpanCount(nItems);
                    } else {
                        // reset span count
                        selectLayoutManager(prefs, MainActivity.this.recyclerView);
                    }
                }
                //
                MainActivity.this.newsAdapter.setNewsList(sortedJointList, MainActivity.this.currentSource);
                Date blobDate = blob.getDate();
                if (blobDate != null) {
                    if (!file.setLastModified(blobDate.getTime())) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Failed to set last modified date!");
                    }
                }
            /*
            The different timestamps
            -------------------------
            - Blob.getDate():                                               the newest News item in the current Source
            - localFile.lastModified():                                     equals Blob.getDate()
            - prefs.getLong(PREF_PREFIX_SEARCHSUGGESTIONS + source.name()): last time the search suggestions for a Source were updated
            - App.getMostRecentManualUpdate(Source):                        most recent successful user-initiated update of the Source
             */
                MainActivity.this.newsAdapter.addFilter(MainActivity.this.searchFilter);
                boolean hasTemporaryFilter = MainActivity.this.newsAdapter.hasTemporaryFilter();
                MainActivity.this.clockView.setTime(blobDate != null ? blobDate.getTime() : file.lastModified());
                MainActivity.this.clockView.setTint(hasTemporaryFilter ? Util.getColor(MainActivity.this, R.color.colorFilter) : 0);
                if (hasTemporaryFilter) {
                    boolean hintResetSearchShown = prefs.getBoolean(SearchContentProvider.PREF_SEARCH_HINT_RESET_SHOWN, false);
                    if (!hintResetSearchShown) {
                        int navigationMode = Util.getNavigationMode(MainActivity.this);
                        String orig = getString(navigationMode == 2 ? R.string.hint_search_reset_gesture : R.string.hint_search_reset);
                        SpannableString ss = new SpannableString(orig);
                        MainActivity.this.popupManager.showPopup(MainActivity.this.clockView, ss, 5_000L);
                        SharedPreferences.Editor ed = prefs.edit();
                        ed.putBoolean(SearchContentProvider.PREF_SEARCH_HINT_RESET_SHOWN, true);
                        ed.apply();
                    }
                }

                MainActivity.this.swipeRefreshLayout.setRefreshing(false);
                if (hasTemporaryFilter && !MainActivity.this.msgFoundShown) {
                    CharSequence searchFilterText = MainActivity.this.searchFilter.getText();
                    int found = MainActivity.this.newsAdapter.getItemCount();
                    if (found == 0) {
                        Snackbar.make(MainActivity.this.coordinatorLayout, getString(R.string.msg_not_found, searchFilterText), Snackbar.LENGTH_LONG).show();
                    } else {
                        Snackbar.make(MainActivity.this.coordinatorLayout, getString(R.string.msg_found, searchFilterText, found), Snackbar.LENGTH_SHORT).show();
                    }
                    MainActivity.this.msgFoundShown = true;
                }

                App app = (App)getApplicationContext();

                // if the search suggestions for the Source are older than the Blob that we have just parsed, update the search suggestions
                if (blobDate != null) {
                    Source source = Source.getSourceFromFile(file);
                    if (source != null) {
                        long ts = SearchHelper.getCreationTime(app, source);
                        if (ts < blobDate.getTime()) {
                            SearchHelper.createSearchSuggestions(app, source, sortedJointList, false);
                        }
                    }
                }

                if (MainActivity.this.listPositionToRestore >= 0) {
                    if (layoutManager != null) layoutManager.scrollToPosition(MainActivity.this.listPositionToRestore);
                    if (MainActivity.this.listPositionToRestore == 0) {
                        // if we scroll to the top, display the title
                        ((AppBarLayout)findViewById(R.id.appbar_layout)).setExpanded(true);
                    }
                    MainActivity.this.listPositionToRestore = RecyclerView.NO_POSITION;
                }

                app.trimCacheIfNeeded();

                // if we were not able to show the intro in onResume(), we should be now
                if (prefs.getBoolean(App.PREF_PLAY_INTRO, App.PREF_PLAY_INTRO_DEFAULT)) {
                    if (playIntro()) {
                        SharedPreferences.Editor ed = prefs.edit();
                        ed.putBoolean(App.PREF_PLAY_INTRO, false);
                        ed.apply();
                    }
                }

            }

            /** {@inheritDoc} */
            @Override
            public void parsingProgressed(@FloatRange(from = 0, to = 1) float progress) {
                int p = (int)(PROGRESS_DOWNLOAD_PARSE + (255f - PROGRESS_DOWNLOAD_PARSE) * progress) << 24;
                MainActivity.this.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(p + c);
            }
        });
        blobParser.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, file);
    }

    /**
     * Plays the introduction sequence.
     * @return {@code true} if it has been started
     */
    private boolean playIntro() {
        if (this.intro != null && this.intro.isPlaying()) return false;
        if (!Util.isNetworkAvailable(this)) {
            // display a hint to the user if (s)he starts the app for the first time and there is no network connection
            Snackbar sb = Snackbar.make(this.coordinatorLayout, R.string.error_no_network_ext, Snackbar.LENGTH_INDEFINITE);
            Util.setSnackbarFont(sb, Util.CONDENSED, 13f);
            Intent settingsIntent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
            if (getPackageManager().resolveActivity(settingsIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                // the unicode wrench symbol 🔧 (0x1f527)
                sb.setAction("\uD83D\uDD27", v -> {
                    sb.dismiss();
                    if (MainActivity.this.intro != null && MainActivity.this.intro.isPlaying()) {
                        MainActivity.this.intro.cancel();
                    }
                    startActivity(settingsIntent);
                });
            } else {
                sb.setAction(android.R.string.ok, v -> sb.dismiss());
                sb.setActionTextColor(Color.RED);
            }
            sb.show();
            return false;
        }
        // do not play Intro if adapter is empty because there isn't anything to show
        if (this.newsAdapter.getItemCount() == 0) return false;
        //
        this.intro = new Intro(this);
        final int n = this.introSteps.length;
        for (int i = 0; i < n; i++) {
            this.intro.addStep(this.introSteps[i], INTRO_DELAYS[i]);
        }
        this.handler.post(this.intro);
        //
        return true;
    }


    /**
     * Starts the {@link TeletextActivity}.
     * @param fromExternal {@code true} if the request originated outside of this app (via Intent.ACTION_VIEW)
     * @param page page number
     */
    private void startTeletext(boolean fromExternal, @IntRange(from = 100, to = 899) int page) {
        final Intent intent = new Intent(this, TeletextActivity.class);
        if (page == 100) {
            intent.putExtra(WebViewActivity.EXTRA_URL, App.URL_TELETEXT);
        } else {
            intent.putExtra(WebViewActivity.EXTRA_URL, App.URL_TELETEXT_WO_PAGE + page);
        }
        if (fromExternal) {
            intent.putExtra(WebViewActivity.EXTRA_NO_HOME_AS_UP, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        startActivity(intent);
    }

    /**
     * Selects the menu item in the navigation view that matches the current source.
     */
    private void updateMenu() {
        int index = this.sourceForMenuItem.indexOfValue(this.currentSource);
        final int menuid = index >= 0 ? this.sourceForMenuItem.keyAt(index): Integer.MIN_VALUE;
        final Menu menu = ((NavigationView)findViewById(R.id.navigationView)).getMenu();
        final int n = menu.size();
        // possibly apply filters to the menu
        final List<Filter> filters = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_APPLY_FILTERS_TO_CATEGORIES, App.PREF_APPLY_FILTERS_TO_CATEGORIES_DEFAULT) ? TextFilter.createTextFiltersFromPreferences(this) : null;
        //
        for (int i = 0; i < n; i++) {
            MenuItem item = menu.getItem(i);
            item.setChecked(menuid == item.getItemId());
            if (filters == null) {
                item.setVisible(true);
                continue;
            }
            // don't hide active category!
            if (item.isChecked()) continue;
            // hide category if its displayed name matches one of the filters
            String titleLower = item.getTitle().toString().toLowerCase(Locale.GERMAN);
            boolean visible = true;
            for (Filter filter : filters) {
                if (!(filter instanceof TextFilter)) continue;
                if (!((TextFilter)filter).accept(titleLower)) {
                    visible = false;
                    break;
                }
            }
            item.setVisible(visible);
        }
    }

    /**
     * To be called whenever the current source has changed:
     * Updates the title shown in {@link #clockView} and the task description based on {@link #currentSource}.
     */
    private void updateTitle() {
        if (this.clockView == null) {
            this.handler.postDelayed(this::updateTitle, 500L);
            return;
        }
        String s = getString(this.currentSource.getLabel());
        this.clockView.setText(s);
        setTaskDescription(new ActivityManager.TaskDescription(getString(R.string.app_task_description, s), null, Util.getColor(this, R.color.color_primary)));
    }

    /**
     * Lets the user view the associated image ({@link TeaserImage#getBestImage()}) in the largest possible size.
     * @param news News
     * @param retryIfNoService set to {@code true} to retry after 500 ms if {@link #service} is {@code null}
     */
    private void viewImage(@NonNull final News news, boolean retryIfNoService) {
        TeaserImage image = news.getTeaserImage();
        if (image == null) {
            // the menu item action_view_picture should have been disabled if there is no image (see NewsRecyclerAdapter.onCreateContextMenu())
            // therefore we should never be here
            return;
        }
        String url = image.getBestImage();
        if (url == null) {
            // see above
            return;
        }
        url = Util.makeHttps(url);
        this.newsForQuickView = news;
        if (this.service == null) {
            if (retryIfNoService) this.handler.postDelayed(() -> viewImage(news, false), 500L);
            else this.newsForQuickView = null;
            return;
        }
        String newsid = news.getExternalId();
        if (newsid == null) newsid = "temp";
        final File temp = new File(getCacheDir(), "quik_" + newsid.replace('/', '_').replace('\0', '_') + ".jpg");
        this.plane.setVisibility(View.VISIBLE);
        this.service.loadFile(url, temp, temp.lastModified(), (completed, result) -> {
            if (this.quickViewRequestCancelled) {
                this.quickViewRequestCancelled = false;
                FileDeleter.add(temp);
                this.plane.setVisibility(View.GONE);
                this.newsForQuickView = null;
                return;
            }
            if (!completed || result == null || result.rc >= 400 || temp.length() == 0L) {
                FileDeleter.add(temp);
                this.plane.setVisibility(View.GONE);
                this.newsForQuickView = null;
                if (result != null && !TextUtils.isEmpty(result.msg)) {
                    Snackbar.make(this.coordinatorLayout, getString(R.string.error_download_failed, result.msg), Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(this.coordinatorLayout, R.string.error_download_failed2, Snackbar.LENGTH_SHORT).show();
                }
                return;
            }
            Bitmap bm = BitmapFactory.decodeFile(temp.getAbsolutePath(), OPTS_FOR_QUICKVIEW);
            if (bm != null) {
                getWindow().getDecorView().setSystemUiVisibility(UI_FLAGS_FOR_QUICKVIEW);
                this.quickView.setImageBitmap(bm);
                this.quickView.setVisibility(View.VISIBLE);
            } else {
                if (BuildConfig.DEBUG) Log.w(TAG, "Failed to decode bitmap from " + result.sourceUri);
                this.plane.setVisibility(View.GONE);
                this.newsForQuickView = null;
                if (!TextUtils.isEmpty(result.msg)) {
                    Snackbar.make(this.coordinatorLayout, getString(R.string.error_download_failed, result.msg), Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(this.coordinatorLayout, R.string.error_download_failed2, Snackbar.LENGTH_SHORT).show();
                }
            }
            FileDeleter.add(temp);
        });
    }

    /**
     * You just came home, tried to refresh, only to find out that your device hasn't connected to wifi yet.<br>
     * Seconds later the connection is established and, annoyingly, you'd have to do the swipe again.<br>
     * This is where the ***NEW*** ConnectivityReceiver steps in.<br>
     * Get yours now while stocks last!<br>
     * <hr>
     * This Receiver gets called when the network connection changes.<br>
     * Used to refresh the data without further user interaction
     * <em>if</em> the user has {@link #ONE_MINUTE recently} tried &amp; failed to refresh the data.
     *
     */
    private class ConnectivityReceiver extends BroadcastReceiver {

        /** the definition of 'recently' */
        private static final long ONE_MINUTE = 60_000L;

        /** {@inheritDoc} */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) return;
            // if we have just established a network connection AND the user had recently tried to refresh to no avail, we do it here for her/him
            if (intent.getBooleanExtra("noConnectivity", false)) return;
            NetworkInfo info = intent.getParcelableExtra("networkInfo");
            if (info == null || info.getDetailedState() != NetworkInfo.DetailedState.CONNECTED) return;
            if (System.currentTimeMillis() - MainActivity.this.lastTimeRefreshFailedDueToNoNetwork < ONE_MINUTE) {
                // onRefresh() checks the network connection by itself again (important in case loading via mobile is prohibited)
                onRefresh(false);
            }
        }

    }

}
