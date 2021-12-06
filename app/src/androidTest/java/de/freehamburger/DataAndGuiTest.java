package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.JsonReader;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.freehamburger.adapters.NewsRecyclerAdapter;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.Source;
import de.freehamburger.model.TextFilter;
import de.freehamburger.supp.SearchHelper;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView;

/**
 * Tests the data download and its representation in the gui.
 */
@LargeTest
public class DataAndGuiTest {

    private static final Source SOURCE = Source.HOME;
    private static final String FILENAME = SOURCE.name() + Source.FILE_SUFFIX;
    /** the preferred region to assume for the test */
    private static final Region PREFERRED_REGION = Region.BW;
    /** assuming the download does not take more than 3 seconds */
    private static final long DOWNLOAD_TIMEOUT = 6_000L;
    /** max age of the downloaded json data before we download again */
    private static final long FILE_MAX_AGE = 3_600_000L;
    private static Context ctx;
    private static File file;
    private static DownloadManager dm;
    private static long downloadId;
    @Nullable
    private static Set<String> regions;

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
            assertTrue("News has invalid date (a)", date.getTime() > 0L);
            assertTrue("News has invalid date (b)", date.before(now));
            assertNotNull("News has null regions", news.getRegions());
            if (PREFERRED_REGION.toString().equals(news.getTopline())) {
                assertTrue("News with top line '" + PREFERRED_REGION.toString() + "' is not regional", news.isRegional());
            }
        }
    }

    @Test
    public void testDrawer() {
        ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class);
        asn.moveToState(Lifecycle.State.RESUMED);
        asn.onActivity(activity -> {
            assertNotNull(activity.drawerLayout);
            if (activity.drawerLayout.isDrawerOpen(GravityCompat.END)) activity.drawerLayout.closeDrawer(GravityCompat.END, false);
            assertFalse(activity.drawerLayout.isDrawerOpen(GravityCompat.END));
            KeyEvent keLeft = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT);
            activity.dispatchKeyEvent(keLeft);
            assertTrue("Drawer is not open", activity.drawerLayout.isDrawerOpen(GravityCompat.END));
            KeyEvent keRight = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
            activity.dispatchKeyEvent(keRight);
            assertFalse("Drawer is not closed", activity.drawerLayout.isDrawerOpen(GravityCompat.END));
            activity.finish();
        });
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

    /**
     * Tests the application of {@link News} objects to {@link NewsView NewsViews}.
     */
    @Test
    @MediumTest
    public void testNewsView() {
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

        NewsView nv;
        for (News news : list) {
            assertNotNull(news);
            //
            nv = NewsRecyclerAdapter.instantiateView(ctx, NewsRecyclerAdapter.getViewType(news));
            assertNotNull(nv);
            // all NewsView subtypes have these 3 views
            assertNotNull(nv.textViewDate);
            assertNotNull(nv.textViewTopline);
            assertNotNull(nv.imageView);
            //
            @News.NewsType String type = news.getType();
            if (news.getTitle() != null && !news.getTitle().contains("Wetter")) {
                assertNotNull(type);
            }
            boolean hasTitle = !TextUtils.isEmpty(news.getTitle());
            boolean hasFirstSentence = !TextUtils.isEmpty(news.getFirstSentence());
            boolean hasShorttext= !TextUtils.isEmpty(news.getShorttext());
            boolean hasPlainText = news.getContent() != null && !TextUtils.isEmpty(news.getContent().getPlainText());
            boolean hasTextFor3rdView = hasFirstSentence || hasShorttext || hasPlainText;
            // video type
            if (News.NEWS_TYPE_VIDEO.equals(type)) {
                assertNull(nv.textViewTitle);
                assertNull(nv.textViewFirstSentence);
                continue;
            }
            if (hasTitle && !hasTextFor3rdView) {
                assertNull(nv.textViewFirstSentence);
            }
            if (!hasTitle && !hasTextFor3rdView) {
                assertNull(nv.textViewTitle);
                assertNull(nv.textViewFirstSentence);
            }
            // apply the News
            nv.setNews(news, null);
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
        SearchHelper.Inserter inserter = SearchHelper.createSearchSuggestions(ctx, SOURCE, blob.getAllNews(), true);
        assertNotNull(inserter);
        try {
            inserter.join();
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
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}
