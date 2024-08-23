package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assume.assumeTrue;

import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.util.Util;

@LargeTest
public class ArchiveTest {

    private static Context ctx;
    private static DownloadManager dm;
    private static long downloadId;
    private static File tmp;

    @AfterClass
    public static void cleanup() {
        dm.remove(downloadId);
        Util.deleteFile(tmp);
    }

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
        assertTrue(ctx instanceof App);
        dm = (DownloadManager)ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        assertNotNull(dm);
    }

    /**
     *
     */
    static long startDownloadingJson(Context ctx, DownloadManager dm, String url, String dest) {
        File files = ctx.getFilesDir();
        assertTrue("No network", Util.isNetworkAvailable(ctx));
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        r.setMimeType("application/json");
        r.setDestinationInExternalFilesDir(ctx, null, dest);
        r.setDescription("Hamburger Test");
        r.addRequestHeader("Cache-Control", "max-age=0");
        r.addRequestHeader("User-Agent", App.USER_AGENT);
        return dm.enqueue(r);
    }

    static int waitForDownload(@NonNull DownloadManager downloadManager, final long downloadId, @IntRange(from = 0) final long maxMs) {
        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor;
        query.setFilterById(downloadId);
        int status;
        final long start = System.currentTimeMillis();
        do {
            try {Thread.sleep(500L);} catch (Exception e) {fail(e.toString());}
            cursor = downloadManager.query(query);
            cursor.moveToFirst();
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            assertTrue(statusIndex != -1);
            status = cursor.getInt(statusIndex);
            Util.close(cursor);
            if (status == DownloadManager.STATUS_PAUSED) android.util.Log.w(ArchiveTest.class.getSimpleName(), "Download paused!");
            if (System.currentTimeMillis() - start > maxMs) break;
        } while (status != DownloadManager.STATUS_FAILED && status != DownloadManager.STATUS_SUCCESSFUL);
        return status;
    }

    @Test
    @SdkSuppress(minSdkVersion = 24)
    @FlakyTest(detail = "Depends on working Android DownloadManager")
    public void testArchival() {
        assumeTrue("This test needs API 24 (N)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        File home = new File(ctx.getFilesDir(), "HOME.source");
        assumeTrue("HOME.source not found", home.isFile());

        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {home});
        assertNotNull("BlobParser returned null", blob);
        // all news
        List<News> list = blob.getAllNews();
        assertNotNull("News list is null", list);
        assertTrue("News list is empty", list.size() > 0);

        // stream needs API 24
        Optional<News> on = list.stream().filter(n -> n.getDetails() != null).findAny();
        assertTrue("No News with details found", on.isPresent());
        News news = on.get();
        assertNotNull(news);

        downloadId = startDownloadingJson(ctx, dm, news.getDetails(), "TEST.json");
        int status = waitForDownload(dm, downloadId, 60_000L);
        assumeTrue("Download from \"" + news.getDetails() + "\" was not successful!", DownloadManager.STATUS_SUCCESSFUL == status);

        tmp = new File(ctx.getExternalFilesDir(null), "TEST.json");
        assertTrue(tmp.isFile());
        assertTrue(tmp.length() > 0L);

        // here the real testing commences…
        boolean ok = Archive.saveNews(ctx, news, tmp);
        assertTrue(ok);
        File folder = new File(ctx.getFilesDir(), Archive.DIR);
        assertTrue(folder.isDirectory());
        File newArchivedJsonFile = new File(folder, Archive.makeFilename(news));
        assertTrue(newArchivedJsonFile.isFile());
        assertEquals(newArchivedJsonFile.length(), tmp.length());
        Util.deleteFile(newArchivedJsonFile);
    }

    @Test
    public void testInvocation() {
        // check for archived articles - if there aren't any the Archive activity will finish within onResume()
        File folder = new File(ctx.getFilesDir(), Archive.DIR);
        assertTrue(folder.isDirectory());
        File[] existing = folder.listFiles();
        assumeTrue("No archived data.", existing != null && existing.length > 0);
        //
        try (ActivityScenario<Archive> asn = ActivityScenario.launch(Archive.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                assertNotNull(activity);
                assertEquals(R.layout.activity_archive, activity.getMainLayout());
                assertTrue(activity.hasMenuOverflowButton());
                activity.finish();
            });
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    @Test
    public void testManifestDeclaration() {
        PackageManager pm = ctx.getPackageManager();
        try {
            ActivityInfo activityInfo = pm.getActivityInfo(new ComponentName(ctx, "de.freehamburger.Archive"), 0);
            assertNotNull(activityInfo);
            assertFalse(activityInfo.exported);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

}
