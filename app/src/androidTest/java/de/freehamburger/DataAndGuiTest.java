package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.JsonReader;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.freehamburger.adapters.NewsRecyclerAdapter;
import de.freehamburger.exo.ExoFactory;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.Source;
import de.freehamburger.model.TextFilter;
import de.freehamburger.supp.SearchHelper;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView2;
import de.freehamburger.views.NewsViewNoContentNoTitle2;

/**
 * Tests the data download and its representation in the gui.
 */
@LargeTest
public class DataAndGuiTest {

    /** assuming the download does not take more than 3 seconds */
    private static final long DOWNLOAD_TIMEOUT = 6_000L;
    /** max age of the downloaded json data before we download again */
    private static final long FILE_MAX_AGE = 3_600_000L;
    /** the preferred region to assume for the test */
    private static final Region PREFERRED_REGION = Region.BW;
    private static final Source SOURCE = Source.HOME;
    private static final String FILENAME = SOURCE.name() + Source.FILE_SUFFIX;
    private static Context ctx;
    private static File file;
    private static DownloadManager dm;
    private static long downloadId;
    @Nullable
    private static Set<String> regions;

    @AfterClass
    public static void cleanup() {
        // delete temporary file
        if (file != null && file.isFile() && file.lastModified() - System.currentTimeMillis() >= FILE_MAX_AGE) {
            if (!file.delete()) file.deleteOnExit();
            dm.remove(downloadId);
        }
        // restore preferred regions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor ed = prefs.edit();
        if (regions != null) ed.putStringSet(App.PREF_REGIONS, regions);
        else ed.remove(App.PREF_REGIONS);
        ed.commit();
    }

    /**
     * If {@link #file} does not exist yet,
     * downloads the json data for {@link Source#HOME} and stores it in {@link #FILENAME}.
     */
    private static void downloadHomepage() {
        File files = ctx.getFilesDir();
        file = new File(files, FILENAME);
        if (file.isFile() && file.length() > 0L && file.lastModified() - System.currentTimeMillis() < FILE_MAX_AGE) return;
        assertTrue("No network", Util.isNetworkAvailable(ctx));
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(SOURCE.getUrl()));
        r.setVisibleInDownloadsUi(true);
        r.setMimeType("application/json");
        r.setDestinationInExternalFilesDir(ctx, null, FILENAME);
        r.setDescription("Hamburger Test");
        r.addRequestHeader("Cache-Control", "max-age=0");
        r.addRequestHeader("User-Agent", App.USER_AGENT);
        downloadId = dm.enqueue(r);
    }

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
        // save preferred regions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        regions = prefs.getStringSet(App.PREF_REGIONS, null);
        // modify preferred regions
        SharedPreferences.Editor ed = prefs.edit();
        Set<String> ss = new HashSet<>(1);
        ss.add(String.valueOf(PREFERRED_REGION.getId()));
        ed.putStringSet(App.PREF_REGIONS, ss);
        ed.apply();

        dm = (DownloadManager)ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        assertNotNull(dm);
        downloadHomepage();
    }

    /**
     * This method tests the {@link BlobParser} which delegates to {@link Blob#parseApi(Context, JsonReader)}.
     */
    @Test(timeout = DOWNLOAD_TIMEOUT + 2_000L)
    @MediumTest
    public void testBlobParser() {
        assertNotNull(file);
        if (!file.isFile() || file.length() == 0L) {
            try {
                Thread.sleep(DOWNLOAD_TIMEOUT);
            } catch (Exception e) {
                fail(e.toString());
            }
            assertTrue(file.getAbsolutePath() + " does not exist (yet)", file.isFile());
            assertTrue(FILENAME + " is empty", file.length() > 0L);
        }
        assertTrue(FILENAME + " cannot be read", file.canRead());
        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {file});
        assertNotNull("BlobParser returned null for " + file, blob);
        // regional news
        List<News> regionalNews = blob.getRegionalNewsList();
        assertNotNull("Regional news list is null", regionalNews);
        // all news
        List<News> list = blob.getAllNews();
        assertNotNull("News list is null", list);
        assertTrue("News list is empty", list.size() > 0);
        // perform some checks for each News
        final Date now = new Date();
        for (News news : list) {
            assertNotNull("News list contains null element", news);
            assertTrue("News list contains invalid id", news.getId() > 0L);
            assertNotNull("News has null title", news.getTitle());
            assertTrue("News has empty title", news.getTitle().length() > 0);
            Date date = news.getDate();
            assertNotNull("News has null date", date);
            assertTrue("News has invalid date (a): " + date, date.getTime() > 0L);
            assertTrue("News has invalid date (b): " + date + " not before now (" + now + ")", date.before(now));
            assertNotNull("News has null regions", news.getRegions());
            if (PREFERRED_REGION.toString().equals(news.getTopline())) {
                assertTrue("News with top line '" + PREFERRED_REGION + "' is not regional", news.isRegional());
            }
        }
    }

    @Test
    public void testDrawer() {
        try (ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                assertNotNull(activity.drawerLayout);
                if (activity.drawerLayout.isDrawerOpen(GravityCompat.END))
                    activity.drawerLayout.closeDrawer(GravityCompat.END, false);
                assertFalse(activity.drawerLayout.isDrawerOpen(GravityCompat.END));
                KeyEvent keLeft = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
                activity.dispatchKeyEvent(keLeft);
                assertTrue("Drawer is not open", activity.drawerLayout.isDrawerOpen(GravityCompat.END));
                KeyEvent keRight = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
                activity.dispatchKeyEvent(keRight);
                assertFalse("Drawer is not closed", activity.drawerLayout.isDrawerOpen(GravityCompat.END));
                //
                boolean tapToOpen = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(App.PREF_CLICK_FOR_CATS, App.PREF_CLICK_FOR_CATS_DEFAULT);
                if (tapToOpen) {
                    assertTrue("ClockView text does not have a listener", activity.clockView.hasOnTextClickListener());
                } else {
                    assertFalse("ClockView text has a listener", activity.clockView.hasOnTextClickListener());
                }
                //
                activity.finish();
            });
        }
    }

    /**
     * Tests {@link TextFilter TextFilters}
     */
    @Test
    @MediumTest
    public void testFilter() {
        assertNotNull(file);
        assertNotNull(ctx);
        assertTrue("Does not exist: " + file, file.isFile());
        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {file});
        assertNotNull("BlobParser returned null for " + file, blob);
        List<News> list = blob.getAllNews();
        assertFalse(list.isEmpty());
        News news0 = list.get(0);
        assertNotNull(news0);
        // must be a 'story' for a meaningful test
        Assume.assumeTrue(News.NEWS_TYPE_STORY.equals(news0.getType()));
        // get a phrase from the News to test against
        String partofNews = news0.getFirstSentence();
        if (partofNews == null || partofNews.indexOf(' ') <= 0) partofNews = news0.getTopline();
        if (partofNews == null || partofNews.indexOf(' ') <= 0) partofNews = news0.getTitle();
        assertNotNull(partofNews);
        partofNews = partofNews.toLowerCase(Locale.GERMAN);
        int space = partofNews.indexOf(' ');
        assertTrue(space > 0);
        String firstWord = partofNews.substring(0, space).trim();
        // a phrase very unlikely to occur in the News (let's hope that)
        String notPartOfNews = "donald loves adi!";
        // tf1 should NOT let news0 pass because it is directly derived from it
        TextFilter tf1 = new TextFilter(partofNews);
        assertFalse(tf1 + " accepts " + news0, tf1.accept(news0));
        // tf1i is the inversed tf1
        TextFilter tf1i = new TextFilter(partofNews, false, true);
        assertTrue(tf1i + " does not accept " + news0, tf1i.accept(news0));
        // tf2 should let news0 pass because it contains a phrase very unlikely to occur in the News
        TextFilter tf2 = new TextFilter(notPartOfNews);
        assertTrue(tf2 + " does not accept " + news0, tf2.accept(news0));
        // tf2i is the inversed tf2
        TextFilter tf2i = new TextFilter(notPartOfNews, false, true);
        assertFalse(tf2i + " accepts " + news0, tf2i.accept(news0));
        // tf3 should NOT let news0 pass because it is directly derived from it
        TextFilter tf3 = new TextFilter(firstWord, true, false, false, false);
        assertFalse(tf3 + " accepts " + news0, tf3.accept(news0));
        // tf4 and tf5 should let news0 pass because HTML tags should not be included in the comparison
        TextFilter tf4 = new TextFilter("<em>", false, false, false, false);
        assertTrue(tf4 + " does not accept " + news0, tf4.accept(news0));
        TextFilter tf5 = new TextFilter("<br>", false, false, false, false);
        assertTrue(tf5 + " does not accept " + news0, tf5.accept(news0));
    }

    @Test
    public void testMainActivity() {
        try (ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                // drawer
                assertNotNull(activity.drawerLayout);
                com.google.android.material.navigation.NavigationView navigationView = activity.findViewById(R.id.navigationView);
                assertNotNull(navigationView);
                View kid0 = navigationView.getChildAt(0);
                assertNotNull(kid0);
                assertTrue(kid0 instanceof RecyclerView);
                // swipe refresh
                assertTrue(activity.findViewById(R.id.swiperefresh) instanceof SwipeRefreshLayout);
                // CoordinatorLayout
                assertTrue(activity.findViewById(R.id.coordinator_layout) instanceof CoordinatorLayout);
                // fab
                assertTrue(activity.findViewById(R.id.fab) instanceof FloatingActionButton);
                // quick view
                assertTrue(activity.findViewById(R.id.quickView) instanceof ImageView);
                // Recycler
                RecyclerView recyclerView = activity.findViewById(R.id.recyclerView);
                assertNotNull(recyclerView);
                assertNotNull(recyclerView.getLayoutManager());
                NewsRecyclerAdapter adapter = activity.getAdapter();
                assertNotNull(adapter);
                // clock
                assertNotNull(activity.clockView);
                // blocking plane
                assertNotNull(activity.findViewById(R.id.plane));
                // intro steps
                assertEquals(MainActivity.INTRO_DELAYS.length, activity.introSteps.length);
                //
                activity.finish();
            });
        }
    }

    /** tests the correct application of filters to the categories menu */
    @Test
    public void testMenuFilter() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        assumeTrue("filters are not applied to categories", prefs.getBoolean(App.PREF_APPLY_FILTERS_TO_CATEGORIES, App.PREF_APPLY_FILTERS_TO_CATEGORIES_DEFAULT));
        final String hideMe = new TextFilter(ctx.getString(Source.WIRTSCHAFT.getLabel()).toLowerCase(Locale.GERMAN)).getText().toString();
        final Set<String> preferredFilters = prefs.getStringSet(App.PREF_FILTERS, new HashSet<>(0));
        assumeFalse("user-defined filters already contain " + hideMe, preferredFilters.contains(hideMe));
        final Set<String> testFilters = new HashSet<>(preferredFilters.size() + 1);
        testFilters.addAll(preferredFilters);
        testFilters.add(hideMe);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putStringSet(App.PREF_FILTERS, testFilters);
        ed.apply();
        try(ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                com.google.android.material.navigation.NavigationView nv = activity.findViewById(R.id.navigationView);
                assertNotNull(nv);
                Menu menu = nv.getMenu();
                assertNotNull(menu);
                final int n = menu.size();
                for (int i = 0; i < n; i++) {
                    if (!menu.getItem(i).isVisible()) continue;
                    String menuTitle = menu.getItem(i).getTitle().toString().toLowerCase(Locale.GERMAN);
                    assertNotEquals("Found category \"" + hideMe + "\"!", hideMe, menuTitle);
                }
                SharedPreferences.Editor ed2 = prefs.edit();
                ed2.putStringSet(App.PREF_FILTERS, preferredFilters);
                assertTrue(ed2.commit());
                activity.finish();
            });
        }
    }

    /**
     * Tests the {@link NewsActivity}.
     */
    @Test
    public void testNewsActivity() {
        try (ActivityScenario<NewsActivity> asn = ActivityScenario.launch(NewsActivity.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                // ensure that required Views are present
                View fab = activity.findViewById(R.id.fab);
                assertNotNull(fab);
                StyledPlayerView topVideoView = activity.findViewById(R.id.topVideoView);
                assertNotNull(topVideoView);
                View textViewTitle = activity.findViewById(R.id.textViewTitle);
                assertNotNull(textViewTitle);
                View audioBlock = activity.findViewById(R.id.audioBlock);
                assertNotNull(audioBlock);
                View buttonAudio = activity.findViewById(R.id.buttonAudio);
                assertNotNull(buttonAudio);
                View textViewAudioTitle = activity.findViewById(R.id.textViewAudioTitle);
                assertNotNull(textViewAudioTitle);
                View recyclerViewRelated = activity.findViewById(R.id.recyclerViewRelated);
                assertNotNull(recyclerViewRelated);
                View dividerRelated = activity.findViewById(R.id.dividerRelated);
                assertNotNull(dividerRelated);
                TextView textViewRelated = activity.findViewById(R.id.textViewRelated);
                assertNotNull(textViewRelated);
                ViewGroup bottomVideoBlock = activity.findViewById(R.id.bottomVideoBlock);
                assertNotNull(bottomVideoBlock);
                View textViewBottomVideoPeek = activity.findViewById(R.id.textViewBottomVideoPeek);
                assertNotNull(textViewBottomVideoPeek);
                StyledPlayerView bottomVideoView = activity.findViewById(R.id.bottomVideoView);
                assertNotNull(bottomVideoView);
                View textViewBottomVideoViewOverlay = activity.findViewById(R.id.textViewBottomVideoViewOverlay);
                assertNotNull(textViewBottomVideoViewOverlay);
                View bottomVideoViewWrapper = activity.findViewById(R.id.bottomVideoViewWrapper);
                assertNotNull(bottomVideoViewWrapper);
                // make sure that the ExoPlayer instances exist
                if (activity.loadVideo) {
                    assertNotNull(activity.exoPlayerTopVideo);
                    assertNotNull(activity.exoPlayerBottomVideo);
                    assertNotNull(topVideoView.getPlayer());
                    assertNotNull(bottomVideoView.getPlayer());
                    // this makes sure that the ExoPlayers were built in the ExoFactory
                    assertTrue(activity.exoPlayerTopVideo.getAnalyticsCollector() instanceof ExoFactory.NirvanaAnalyticsCollector);
                    assertTrue(activity.exoPlayerBottomVideo.getAnalyticsCollector() instanceof ExoFactory.NirvanaAnalyticsCollector);
                }
                if (activity.exoPlayerAudio != null) {
                    // this makes sure that the ExoPlayer was built in the ExoFactory
                    assertTrue(activity.exoPlayerAudio.getAnalyticsCollector() instanceof ExoFactory.NirvanaAnalyticsCollector);
                }
                // make sure that some Views react to clicks
                assertTrue(buttonAudio.hasOnClickListeners());
                // hasOnLongClickListeners() is available only from Android 11 (R) on
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    assertTrue(buttonAudio.hasOnLongClickListeners());
                assertTrue(textViewBottomVideoPeek.hasOnClickListeners());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    assertTrue(textViewBottomVideoPeek.hasOnLongClickListeners());
                assertTrue(bottomVideoViewWrapper.hasOnClickListeners());
                if (activity.loadVideo) {
                    assertNotNull(topVideoView.getVideoSurfaceView());
                    assertTrue(topVideoView.getVideoSurfaceView().hasOnClickListeners());
                }
                // make sure the top video View is not visible if videos must not be shown
                if (!activity.loadVideo) {
                    assertFalse(topVideoView.getVisibility() == View.VISIBLE);
                }
                //
                assertNotNull(activity.bottomSheetBehavior);
                //
                Intent intent = activity.getIntent();
                assertNotNull(intent);
                News news = (News) intent.getSerializableExtra(NewsActivity.EXTRA_NEWS);
                assertNull(news);
                //
                activity.finish();
            });
        }
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.R)
    static Context getVisualContext(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ctx;
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        Display primaryDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Context dctx = ctx.createDisplayContext(primaryDisplay);
        //noinspection deprecation
        return dctx.createWindowContext(WindowManager.LayoutParams.TYPE_TOAST, null);
    }

    /**
     * Tests the application of {@link News} objects to {@link NewsView2 NewsViews}.
     */
    @Test
    @MediumTest
    @RequiresApi(Build.VERSION_CODES.R)
    public void testNewsView() {
        assumeTrue("This test needs API 30",Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        assertNotNull(file);
        if (!file.isFile() || file.length() == 0L) {
            try {
                Thread.sleep(DOWNLOAD_TIMEOUT);
            } catch (Exception e) {
                fail(e.toString());
            }
            assertTrue(FILENAME + " does not exist (yet)", file.isFile());
            assertTrue(FILENAME + " is empty", file.length() > 0L);
        }
        assertTrue(FILENAME + " cannot be read", file.canRead());
        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {file});
        assertNotNull("BlobParser returned null", blob);
        // all news
        List<News> list = blob.getAllNews();
        assertNotNull("News list is null", list);
        assertTrue("News list is empty", list.size() > 0);

        final Context visualContext = getVisualContext(ctx);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final List<String> REGION_LABELS = Region.getValidLabels();

        NewsView2 nv;
        for (News news : list) {
            assertNotNull(news);
            //
            nv = NewsRecyclerAdapter.instantiateView(visualContext, null, NewsRecyclerAdapter.getViewType(news));
            assertNotNull(nv);

            // all NewsView subtypes have these 3 views
            assertNotNull(nv.textViewDate);
            assertNotNull(nv.textViewTopline);
            assertNotNull(nv.imageView);
            //
            assertNotNull(nv.textViewDate.getLayoutParams());
            assertNotNull(nv.textViewTopline.getLayoutParams());
            assertNotNull(nv.imageView.getLayoutParams());
            //
            @News.NewsType String type = news.getType();
            if (news.getTitle() != null && !news.getTitle().contains("Wetter")) {
                assertNotNull(type);
            }
            boolean hasTopline = !TextUtils.isEmpty(news.getTopline());
            boolean hasTitle = !TextUtils.isEmpty(news.getTitle());
            boolean hasFirstSentence = !TextUtils.isEmpty(news.getFirstSentence());
            boolean hasShorttext= !TextUtils.isEmpty(news.getShorttext());
            boolean hasPlainText = news.getContent() != null && !TextUtils.isEmpty(news.getContent().getPlainText());
            boolean hasTextFor3rdView = hasFirstSentence || hasShorttext || hasPlainText;
            // video type
            if (News.NEWS_TYPE_VIDEO.equals(type)) {
                assertTrue(nv instanceof NewsViewNoContentNoTitle2);
                assertNull(nv.textViewTitle);
                assertNull(nv.textViewFirstSentence);
                continue;
            }
            // apply the News
            nv.setNews(news, null, prefs);
            //
            if (hasTopline) {
                // news topline text should have gone into textViewTopline
                assertTrue(TextUtils.equals(nv.textViewTopline.getText(), news.getTopline()));
            } else if (hasTitle) {
                // news title text should have gone into textViewTopline
                assertTrue(TextUtils.equals(nv.textViewTopline.getText(), news.getTitle()));
            }
            if (prefs.getBoolean(App.PREF_TOPLINE_MARQUEE, false)) {
                assertTrue(nv.textViewTopline.isSelected());
                assertSame(TextUtils.TruncateAt.MARQUEE, nv.textViewTopline.getEllipsize());
            } else {
                assertNotSame(TextUtils.TruncateAt.MARQUEE, nv.textViewTopline.getEllipsize());
            }
            if (news.isBreakingNews()) {
                assertEquals(nv.textViewTopline.getCurrentTextColor(), Util.getColor(ctx, R.color.colorBreakingNews));
            } else if (REGION_LABELS.contains(nv.textViewTopline.getText().toString())) {
                assertEquals(nv.textViewTopline.getCurrentTextColor(), Util.getColor(ctx, R.color.colorRegionalNews));
            } else {
                assertEquals(nv.textViewTopline.getCurrentTextColor(), Util.getColor(ctx, R.color.colorContent));
            }
            // date
            if (news.getDate() != null) {
                assertFalse(TextUtils.isEmpty(nv.textViewDate.getText()));
            } else {
                assertTrue(TextUtils.isEmpty(nv.textViewDate.getText()));
            }
            // image
            if (news.getTeaserImage() != null && news.getTeaserImage().hasImage()) {
                assertEquals("Image view is not visible for " + news, nv.imageView.getVisibility(), View.VISIBLE);
            } else {
                assertNotEquals("Image view is visible for imageless " + news, nv.imageView.getVisibility(), View.VISIBLE);
            }

        }
    }

    @Test
    public void testPictureActivity() {
        Intent intent = new Intent(ctx, PictureActivity.class);
        intent.setData(Uri.parse("https://www.tagesschau.de/res/assets/image/favicon/apple-touch-icon-144x144.png"));
        ActivityScenario<PictureActivity> asn = null;
        try {
            asn = ActivityScenario.launch(intent);
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                View pv = activity.findViewById(R.id.pictureView);
                assertTrue(pv instanceof com.github.chrisbanes.photoview.PhotoView);
                com.github.chrisbanes.photoview.PhotoView phv = (com.github.chrisbanes.photoview.PhotoView) pv;
                try {
                    Thread.sleep(2_000L);
                } catch (Exception ignored) {
                }
                assertNotNull("No service!", activity.service);
                try {
                    Thread.sleep(2_000L);
                } catch (Exception ignored) {
                }
            });
        } catch (Throwable ignored) {
            if (asn != null) Util.close(asn);
        }
    }

    /**
     * This method tests the creation of search suggestions (w/o actually inserting them into the database).
     */
    @Test
    @MediumTest
    public void testSearchSuggestions() {
        Set<String> s = new HashSet<>();
        SearchHelper.splitSentence("Linux is the world's most popular computing platform. " +
                "It powers PCs, mainframes, phones, tablets, watches, TVs, cars, and anything your weird imagination can dream up.", s);
        assertEquals(25, s.size());
        //
        assertNotNull(file);
        if (!file.isFile() || file.length() == 0L) {
            try {
                Thread.sleep(DOWNLOAD_TIMEOUT);
            } catch (Exception e) {
                fail(e.toString());
            }
            assertTrue(FILENAME + " does not exist (yet)", file.isFile());
            assertTrue(FILENAME + " is empty", file.length() > 0L);
        }
        assertTrue(FILENAME + " cannot be read", file.canRead());
        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {file});
        assertNotNull("BlobParser returned null", blob);
        Collection<SearchHelper.Inserter> inserters = SearchHelper.createSearchSuggestions(ctx, SOURCE, blob.getAllNews(), true);
        assertNotNull(inserters);
        assertTrue(inserters.size() > 0);
        try {
            for (SearchHelper.Inserter inserter : inserters) inserter.join();
            for (SearchHelper.Inserter inserter : inserters) {
                assertFalse(inserter.isAlive());
                ContentValues[] cv = inserter.cv;
                assertNotNull(cv);
                assertTrue(cv.length > 0);
                for (ContentValues c : cv) {
                    assertNotNull(c);
                    assertEquals(5, c.size());
                    assertNotNull(c.getAsLong("date"));
                    assertNotNull(c.getAsString("display1"));
                    assertNotNull(c.getAsString("display2"));
                    assertNotNull(c.getAsString("query"));
                    assertNotNull(c.getAsString("symbol"));
                }
            }
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}
