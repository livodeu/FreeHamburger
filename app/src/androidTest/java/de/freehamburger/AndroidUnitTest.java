package de.freehamburger;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.Build;
import android.security.NetworkSecurityPolicy;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.JsonReader;
import android.view.View;

import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;
import androidx.test.filters.SmallTest;
import de.freehamburger.adapters.NewsRecyclerAdapter;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.Source;
import de.freehamburger.supp.SearchHelper;
import de.freehamburger.util.MediaSourceHelper;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView;
import okhttp3.OkHttpClient;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
@SmallTest
public class AndroidUnitTest {

    private static final Source SOURCE = Source.HOME;
    private static final String FILENAME = SOURCE.name() + Source.FILE_SUFFIX;
    /** the preferred region to assume for the test */
    private static final Region PREFERRED_REGION = Region.BW;
    /** assuming the download does not take more than 3 seconds */
    private static final long DOWNLOAD_TIMEOUT = 6_000L;
    /** max age of the downloaded json data before we download again */
    private static final long FILE_MAX_AGE = 3_600_000L;
    private Context ctx;
    private File file;
    private DownloadManager dm;
    private long downloadId;
    @Nullable
    private Set<String> regions;

    @After
    public void cleanup() {
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
        ed.apply();
    }

    /**
     * Downloads the json data for {@link Source#HOME} and stores it in {@link #FILENAME}.
     */
    private void downloadHomepage() {
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

    @Before
    public void init() {
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
        assertNotNull("BlobParser returned null", blob);
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

    /**
     * Tests {@link BootReceiver}.
     */
    @Test
    public void testBootReceiver() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean pollWasEnabled = prefs.getBoolean(App.PREF_POLL, false);
        if (!pollWasEnabled) {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(App.PREF_POLL, true);
            ed.apply();
        }
        BootReceiver br = new BootReceiver();
        br.onReceive(ctx, new Intent(Intent.ACTION_BOOT_COMPLETED));
        NotificationManager nm = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(nm);
        StatusBarNotification[] n = nm.getActiveNotifications();
        assertTrue(n.length > 0);
        // a little time to have a look at the notification
        try {
            Thread.sleep(5_000L);
        } catch (Exception ignored) {
        }
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(App.PREF_POLL, pollWasEnabled);
        ed.apply();
    }

    @Test
    @RequiresApi(24)
    public void testCleartextTraffic() {
        if (Build.VERSION.SDK_INT < 24) return;
        NetworkSecurityPolicy nsp = NetworkSecurityPolicy.getInstance();
        assertFalse("Cleartext traffic to www.tagesschau.de is allowed", nsp.isCleartextTrafficPermitted("www.tagesschau.de"));
        assertFalse("Cleartext traffic to www.google.com is allowed", nsp.isCleartextTrafficPermitted("www.google.com"));
    }

    /**
     * Tests {@link App#isHostAllowed(String)}.
     */
    @Test
    public void testHostAllowed() {
        Uri uri;
        uri = Uri.parse("https://www.google.com");
        assertFalse(App.isHostAllowed(uri.getHost()));
        uri = Uri.parse("https://www.tagesschau.de");
        assertTrue(App.isHostAllowed(uri.getHost()));
        uri = Uri.parse(App.URL_PREFIX);
        assertTrue(App.isHostAllowed(uri.getHost()));
        uri = Uri.parse(App.URL_TELETEXT);
        assertTrue(App.isHostAllowed(uri.getHost()));
        uri = Uri.parse("https://www.facebook.com");
        assertFalse(App.isHostAllowed(uri.getHost()));
    }

    /**
     * Tests {@link MediaSourceHelper}.
     */
    @Test
    public void testMediaSourceHelper() {
        App app = (App)ctx.getApplicationContext();
        MediaSourceHelper msh = new MediaSourceHelper();
        Uri uri = Uri.parse("http://tagesschau-lh.akamaihd.net/i/tagesschau_3@66339/master.m3u8");
        MediaSource ms = msh.buildMediaSource(app.getOkHttpClient(), uri);
        assertNotNull(ms);
        assertTrue(ms instanceof HlsMediaSource);
        uri = Uri.parse("https://media.tagesschau.de/video/2019/0718/TV-20190718-0659-2401.webm.h264.mp4");
        ms = msh.buildMediaSource(app.getOkHttpClient(), uri);
        assertNotNull(ms);
        assertTrue(ms instanceof ExtractorMediaSource);
    }

    /**
     * Tests the application of {@link News} objects to {@link NewsView NewsViews}.
     */
    @Test
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
                assertEquals(nv.imageView.getVisibility(), View.VISIBLE);
            } else {
                assertNotEquals(nv.imageView.getVisibility(), View.VISIBLE);
            }

        }
    }

    /**
     * Tests the creation of notification channels.
     */
    @Test
    public void testNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            App app = (App)ctx.getApplicationContext();
            assertNotNull(app.getNotificationChannel());
            assertNotNull(app.getNotificationChannelHiPri());
        }
    }

    /**
     * Tests {@link App#getOkHttpClient()}.
     */
    @Test
    public void testOkHttp() {
        App app = (App)ctx.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String proxyServer = prefs.getString(App.PREF_PROXY_SERVER, null);
        String proxyType = prefs.getString(App.PREF_PROXY_TYPE, "DIRECT");
        OkHttpClient c = app.getOkHttpClient();
        assertNotNull(c);
        if (!TextUtils.isEmpty(proxyServer) && !"DIRECT".equals(proxyType)) {
            assertNotNull(c.proxy());
        } else {
            assertNull(c.proxy());
        }
    }

    /**
     * This method tests the creation of search suggestions (w/o actually inserting them into the database).
     */
    @Test
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

    @Test
    public void testShortcutInvocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        ShortcutManager shortcutManager = (ShortcutManager)ctx.getSystemService(Context.SHORTCUT_SERVICE);
        assertNotNull(shortcutManager);
        if (!shortcutManager.isRequestPinShortcutSupported()) return;
        try {
            App app = (App)ctx.getApplicationContext();
            final Source[] sources = Source.values();
            for (; ; ) {
                int i = (int) (Math.random() * sources.length);
                Source source = sources[i];
                if (source.getAction() == null) continue;
                assertTrue(Util.createShortcut(ctx, source, shortcutManager));
                Intent intent = new Intent(ctx, MainActivity.class);
                intent.setAction(source.getAction());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                assertNotNull(ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY));
                ctx.startActivity(intent);
                Thread checker = new Thread() {
                    @Override
                    public void run() {
                        try {
                            sleep(1000);
                            Activity current = app.getCurrentActivity();
                            assertTrue("Current activity is " + current, current instanceof MainActivity);
                            assertTrue(((MainActivity)current).handleShortcutIntent(intent));
                        } catch (Exception e) {
                            fail(e.getMessage());
                        }
                    }
                };
                checker.start();
                checker.join();
                break;
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    /**
     * Tests {@link TtfInfo}.
     */
    @Test
    public void testTtf() {
        File file = new File("/system/fonts/DroidSans.ttf");
        assertTrue("Not a file: " + file.getAbsolutePath(), file.isFile());
        try {
            TtfInfo tti = TtfInfo.getTtfInfo(file);
            assertNotNull(tti);
            String fontName = tti.getFontFullName();
            assertNotNull(fontName);
            assertTrue(fontName.contains("Roboto"));
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Tests scheduling the background update service.
     */
    @Test
    public void testUpdateService() {
        JobInfo jobInfo = UpdateJobService.makeJobInfo(ctx);
        assertNotNull(jobInfo);
        JobScheduler js = (JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertNotNull(js);
        int result = js.schedule(jobInfo);
        App app = (App)ctx.getApplicationContext();
        assertTrue(app.isBackgroundJobScheduled() != 0L);
        js.cancel(jobInfo.getId());
        assertEquals(result, JobScheduler.RESULT_SUCCESS);
    }

}
