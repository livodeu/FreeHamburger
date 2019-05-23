package de.freehamburger;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.test.filters.SmallTest;
import android.util.JsonReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.Source;
import de.freehamburger.util.Util;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

/**
 *
 */
@SmallTest
public class AndroidUnitTest {

    private static final String TAG = "AndroidUnitTest";
    private static final String FILENAME = "homepage.json";
    /** the preferred region to assume for the test */
    private static final Region PREFERRED_REGION = Region.BW;
    /** assuming the download does not take more than 3 seconds */
    private static final long DOWNLOAD_TIMEOUT = 3_000L;
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
        File externalFilesDir = ctx.getExternalFilesDir(null);
        file = new File(externalFilesDir, FILENAME);
        if (file.isFile() && file.length() > 0L && file.lastModified() - System.currentTimeMillis() < FILE_MAX_AGE) return;
        assertTrue("No network", Util.isNetworkAvailable(ctx));
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(Source.HOME.getUrl()));
        r.setVisibleInDownloadsUi(true);
        r.setAllowedOverMetered(false);
        r.setAllowedOverRoaming(false);
        r.setMimeType("application/json");
        r.setDestinationInExternalFilesDir(ctx, null, FILENAME);
        r.setDescription("Hamburger Test");
        r.addRequestHeader("Cache-Control", "max-age=0");
        r.addRequestHeader("User-Agent", App.USER_AGENT);
        downloadId = dm.enqueue(r);
    }

    @Before
    public void init() {
        ctx = getTargetContext();
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
            assertTrue(FILENAME + " does not exist (yet)", file.isFile());
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
            if ("ausland".equals(news.getRessort())) {
                assertTrue("No geo tags for ressort 'ausland'", news.getGeotags().size() > 0);
            }
        }
    }
}
