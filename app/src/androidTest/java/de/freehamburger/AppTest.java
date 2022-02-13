package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.security.NetworkSecurityPolicy;
import android.service.notification.StatusBarNotification;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.util.FileDeleter;
import de.freehamburger.util.MediaSourceHelper;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;
import okhttp3.Call;
import okhttp3.OkHttpClient;

/**
 * Tests some app components that need no interaction with a remote host.
 */
@LargeTest
public class AppTest {

    private static Context ctx;

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
        assertTrue(ctx instanceof App);
    }

    @Nullable
    private static String styleIdToString(@StyleRes final int resid) {
        try {
            final Field[] ff = R.style.class.getFields();
            for (Field f : ff) {
                Object o = f.get(R.style.class);
                if (o instanceof Integer) {
                    if (resid == (int)o) return f.getName();
                }
            }
        } catch (Exception e) {
            fail(e.toString());
        }
        return null;
    }


    /**
     * Tests {@link BootReceiver}.
     */
    @Test
    @LargeTest
    public void testBootReceiver() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean pollWasEnabled = prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
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
        assertTrue("No active notifications", n.length > 0);
        // a little time to have a look at the notification
        try {
            Thread.sleep(5_000L);
        } catch (Exception ignored) {
        }
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(App.PREF_POLL, pollWasEnabled);
        ed.apply();
    }

    /**
     * Tests whether cleartext (http) traffic is allowed.<br>
     * Does not test whether the given host is allowed!
     * (see {@link #testHostAllowed()})
     */
    @Test
    @RequiresApi(24)
    @SmallTest
    public void testCleartextTraffic() {
        Assume.assumeTrue("This test needs API 24 (N)", Build.VERSION.SDK_INT >= 24);
        NetworkSecurityPolicy nsp = NetworkSecurityPolicy.getInstance();
        assertFalse("Cleartext traffic is allowed", nsp.isCleartextTrafficPermitted());
        assertFalse("Cleartext traffic to www.tagesschau.de is allowed", nsp.isCleartextTrafficPermitted("www.tagesschau.de"));
        assertFalse("Cleartext traffic to www.google.com is allowed", nsp.isCleartextTrafficPermitted("www.google.com"));
    }

    @Test
    @SmallTest
    public void testCurrentActivity() {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        Assume.assumeFalse("Device is locked.", km.isDeviceLocked());
        App app = (App)ctx.getApplicationContext();
        assertNull(app.getCurrentActivity());
        ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class);
        asn.moveToState(Lifecycle.State.RESUMED);
        asn.onActivity(activity -> {
            assertNotNull(activity);
            assertNotNull(app.getCurrentActivity());
            activity.finish();
        });
    }

    @Test
    @SmallTest
    public void testFileDeleter() {
        try {
            File tmp1 = File.createTempFile("tmp",".tmp");
            assertTrue(tmp1.isFile());
            FileDeleter.add(tmp1);
            assertTrue(FileDeleter.MORITURI.isFile());
            FileDeleter.run();
            assertFalse(tmp1.isFile());
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    @SmallTest
    public void testFixQuotationMarks() {
        final String[] wrongs = new String[] {
                "\"Hello World.\"", "Hello World.", "Hello <font color=\"red\">World</font>", "Hello <font color=\"red\">\"World\"</font>",
                "Dort zeigt ein Verkehrsschild in Richtung \"Arena\", und in einem roten Kasten ist die Mail-Adresse für die Bewerbung als Arenakämpfer angegeben.<br /> <br />\"Es fehlen Arenakämpfer\", bestätigt auch C. Longus",
                " \"Wir werden das schaffen, wenn sich noch ein paar melden\", sagt Brömse",
                "<br>\"Du willst Deinen Teil zur Bekämpfung der palästinensischen Volksfront beitragen?“, fragt deshalb die Volksfront für Palästina <a href=\"https://www.vfp.py/\" type=\"extern\">auf ihrer Internet-Seite [vfp.py]</a>. Dort zeigt ein Verkehrsschild in Richtung \"Arena\","
        };
        final String[] rights = new String[] {
                "„Hello World.“", "Hello World.", "Hello <font color=\"red\">World</font>", "Hello <font color=\"red\">„World“</font>",
                "Dort zeigt ein Verkehrsschild in Richtung „Arena“, und in einem roten Kasten ist die Mail-Adresse für die Bewerbung als Arenakämpfer angegeben.<br /> <br />„Es fehlen Arenakämpfer“, bestätigt auch C. Longus",
                " „Wir werden das schaffen, wenn sich noch ein paar melden“, sagt Brömse",
                "<br>„Du willst Deinen Teil zur Bekämpfung der palästinensischen Volksfront beitragen?“, fragt deshalb die Volksfront für Palästina <a href=\"https://www.vfp.py/\" type=\"extern\">auf ihrer Internet-Seite [vfp.py]</a>. Dort zeigt ein Verkehrsschild in Richtung „Arena“,"
        };
        assertEquals(rights.length, wrongs.length);
        for (String right : rights) {
            assertTrue(right + " contains " + '\u201d',right.indexOf('\u201d') < 0);
            assertTrue(right + " contains " + '\u201f',right.indexOf('\u201f') < 0);
        }
        final int n = wrongs.length;
        for (int i = 0; i < n; i++) {
            String corrected = Util.fixQuotationMarks(wrongs[i]).toString();
            if (!corrected.equals(rights[i])) {
                final int o = Math.min(corrected.length(), rights[i].length());
                StringBuilder log = new StringBuilder(o);
                log.append("\n");
                for (int j = 0; j < o; j++) {
                    if (corrected.charAt(j) == rights[i].charAt(j)) log.append("*");
                    else log.append("|");
                }
                android.util.Log.e(getClass().getSimpleName(), log.toString());
            }
            assertEquals("Corrected String was not\n" + rights[i] + "\nbut\n" + corrected + "\n", rights[i], corrected);
        }
        try {
            Thread.sleep(1000);
        } catch (Exception ignored) {
        }
    }

    /*

    "\"Du willst Deinen Teil zur Bekämpfung der Pandemie beitragen?“, fragen deshalb die Berliner Malteser <a href=\"https://www.malteser-berlin.de/\" type=\"extern\">auf ihrer Internet-Seite [malteser-berlin.de]</a>. Dort zeigt ein Verkehrsschild in Richtung \"Impfzentrum\", und in einem roten Kasten ist die Mail-Adresse für die Bewerbung als Impfhelfer angegeben.<br /> <br />\"Es fehlen Impfhelfer\", bestätigt auch Diana Bade, Sprecherin der Malteser in Berlin auf rbb-Anfrage. Die Malteser betreiben das Impfzentrum Messezentrum, vor dem es immer wieder Schlangen gibt. In den vergangenen zwei Wochen hat sich die Zahl der Impfungen Bade zufolge verdoppelt – auf jetzt bis zu 2.500 täglich."

     */

    /**
     * Tests {@link App#isHostAllowed(String)}.
     * Does not test the procotol/scheme!
     * (see {@link #testCleartextTraffic})
     */
    @Test
    @SmallTest
    public void testHostAllowed() {
        final String[] hosts = new String[] {
                "https://www.google.com", "https://www.tagesschau.de", App.URL_PREFIX, App.URL_TELETEXT, "https://www.facebook.com",
                "http://www.google.com", "http://www.tagesschau.de"
        };
        final boolean[] allowed = new boolean[] {
                false, true, true, true, false,
                false, true
        };
        final int n = hosts.length;
        assertEquals(allowed.length, n);
        for (int i = 0; i < n; i++) {
            String host = Uri.parse(hosts[i]).getHost();
            assertNotNull(host);
            assertEquals("Host " + host + " is allowed", allowed[i], App.isHostAllowed(host));
        }
    }

    /**
     * Tests {@link MediaSourceHelper}.
     */
    @Test
    @SmallTest
    public void testMediaSourceHelper() {
        final OkHttpClient okHttpClient = ((App)ctx.getApplicationContext()).getOkHttpClient();
        final MediaSourceHelper msh = new MediaSourceHelper();
        Uri uri;
        uri = Uri.parse("http://tagesschau-lh.akamaihd.net/i/tagesschau_3@66339/master.m3u8");
        MediaSource ms = msh.buildMediaSource((Call.Factory) okHttpClient, uri);
        assertNotNull(ms);
        assertTrue("Not a HlsMediaSource: " + ms, ms instanceof HlsMediaSource);
        uri = Uri.parse("https://media.tagesschau.de/video/2021/0910/TV-20210910-0717-4200.webm.h264.mp4");
        ms = msh.buildMediaSource((Call.Factory) okHttpClient, uri);
        assertNotNull(ms);
        assertTrue("Not a ProgressiveMediaSource: " + ms, ms instanceof ProgressiveMediaSource);
    }

    @Test
    @SmallTest
    public void testNightMode() {
        @App.BackgroundSelection int background = PreferenceManager.getDefaultSharedPreferences(ctx).getInt(App.PREF_BACKGROUND, App.BACKGROUND_AUTO);
        final int n = AppCompatDelegate.getDefaultNightMode();
        switch (background) {
            case App.BACKGROUND_DAY:
                assertEquals(AppCompatDelegate.MODE_NIGHT_NO, n);
                break;
            case App.BACKGROUND_NIGHT:
                assertEquals(AppCompatDelegate.MODE_NIGHT_YES, n);
                break;
            case App.BACKGROUND_AUTO:
                assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, n);
                break;
            default:
                fail("Undefined background value of " + background);
        }

    }

    /**
     * Tests the creation of notification channels.
     */
    @Test
    @RequiresApi(26)
    @SmallTest
    public void testNotificationChannel() {
        Assume.assumeTrue("This test needs API 26 (O)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        App app = (App)ctx.getApplicationContext();
        assertNotNull(app.getNotificationChannel());
        assertNotNull(app.getNotificationChannelHiPri());
    }

    /**
     * Tests {@link App#getOkHttpClient()}.
     */
    @Test
    @SmallTest
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

    @Test
    @RequiresApi(26)
    @MediumTest
    public void testShortcutInvocation() {
        Assume.assumeTrue("This test needs API 26 (O)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        ShortcutManager shortcutManager = (ShortcutManager)ctx.getSystemService(Context.SHORTCUT_SERVICE);
        assertNotNull(shortcutManager);
        Assume.assumeTrue("ShortcutManager does not support pinned shortcuts", shortcutManager.isRequestPinShortcutSupported());
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
     * Tests some String resources.
     */
    @Test
    @SmallTest
    public void testStrings() {
        // the ◀ is referred to in MainActivity.parseLocalFileAsync()
        String s = ctx.getString(R.string.hint_search_reset);
        assertTrue("hint_search_reset does not contain a back-facing triangle",s.indexOf('◀') >= 0);
    }

    /**
     *
     */
    @Test
    @LargeTest
    public void testTheme() {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        Assume.assumeFalse("Device is locked.", km.isDeviceLocked());
        ActivityScenario<NewsActivity> asn = ActivityScenario.launch(NewsActivity.class);
        asn.moveToState(Lifecycle.State.RESUMED);
        asn.onActivity(activity -> {
            assertNotNull(activity);
            boolean overflowButton = activity.hasMenuOverflowButton();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
            @App.BackgroundSelection int backgroundSelection = HamburgerActivity.applyTheme(activity, prefs,false);
            assertTrue(backgroundSelection == App.BACKGROUND_AUTO || backgroundSelection == App.BACKGROUND_DAY || backgroundSelection == App.BACKGROUND_NIGHT);
            int resid = HamburgerActivity.applyTheme(activity, backgroundSelection, true);
            assertTrue(resid != 0);
            activity.finish();
        });
    }

    /**
     * Tests {@link TtfInfo}. Relies on the presence of "DroidSans.ttf" in "/system/fonts".
     */
    @Test
    @SmallTest
    public void testTtf() {
        File file = new File("/system/fonts/DroidSans.ttf");
        Assume.assumeTrue("Not a file: " + file.getAbsolutePath(), file.isFile());
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

    @Test
    @SmallTest
    public void testUncaughtEx() {
        Thread.UncaughtExceptionHandler ue = Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull("UncaughtExceptionHandler not set", ue);
        assertTrue(ue.getClass().getName().startsWith("de.freehamburger."));
    }

    /**
     * Tests scheduling the background update service.
     */
    @Test
    @SmallTest
    @FlakyTest(detail = "The last part (App.hasCurrentActivity()) succeeds on some devices (APIs 23,31) and fails on others (API 28)")
    public void testUpdateService() {
        JobInfo jobInfo = UpdateJobService.makeJobInfo(ctx);
        assertNotNull(jobInfo);
        assertTrue(jobInfo.isPeriodic());
        if (Build.VERSION.SDK_INT >= 28) assertNotNull(jobInfo.getRequiredNetwork());
        JobScheduler js = (JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertNotNull(js);
        int result = js.schedule(jobInfo);
        App app = (App)ctx.getApplicationContext();
        assertTrue(app.isBackgroundJobScheduled() != 0L);
        js.cancel(jobInfo.getId());
        assertEquals(result, JobScheduler.RESULT_SUCCESS);

        // parse the (presumably existing) json file for the HOME category
        File existing = new File(ctx.getFilesDir(), Source.HOME.name() + Source.FILE_SUFFIX);
        Assume.assumeTrue("To fully test the " + UpdateJobService.class + ", the HOME source must have been downloaded at least once!", existing.isFile());
        Assume.assumeTrue("Source.HOME is locked", Source.HOME.getLockHolder() == null);
        BlobParser bp = new BlobParser(ctx, null);
        Blob blob = bp.doInBackground(new File[] {existing});
        assertNotNull("BlobParser returned null for " + existing, blob);
        List<News> list = blob.getAllNews();
        assertNotNull(list);
        assertFalse(list.isEmpty());

        // make a notification summary
        UpdateJobService.NotificationSummary un = new UpdateJobService.NotificationSummary();
        un.increase();
        assertEquals(2, un.getCount());
        Notification summary = un.build(app);
        assertEquals(un.getCount(), summary.number);
        assertNotNull(summary);
        assertTrue((summary.flags & Notification.FLAG_GROUP_SUMMARY) > 0);
        assertNotNull(summary.extras);

        // get the first News element and show it in the app as if it had been the subject of a notification
        News news = list.get(0);
        PendingIntent pi = UpdateJobService.makeIntentForStory(app, news, 4567);
        assertNotNull(pi);
        assertTrue(pi.isActivity());
        try {
            pi.send();
        } catch (PendingIntent.CanceledException e) {
            fail(e.toString());
        }
        // MainActivity should start now (but this will fail if the device is locked)…
        KeyguardManager km = (KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE);
        Assume.assumeFalse("Device is locked.", km.isDeviceLocked());
        try {Thread.sleep(2_000L);} catch (Exception ignored) {}
        assertTrue(app.hasCurrentActivity());
        try {Thread.sleep(2_000L);} catch (Exception ignored) {}
    }

    /**
     * Tests static methods from {@link Util}
     */
    @Test
    @SmallTest
    public void testUtil() {
        assertTrue(Util.TEST);
        //
        Throwable t = new IllegalArgumentException(new ArithmeticException());
        Throwable causeFound = Util.getSpecificCause(t, ArithmeticException.class);
        assertNotNull(causeFound);
        //
        Point ds = Util.getDisplaySize(ctx);
        assertNotNull(ds);
        assertTrue(ds.x > 0);
        assertTrue(ds.y > 0);
        //
        File[] files = ctx.getFilesDir().listFiles();
        assertNotNull(files);
        for (File file : files) {
            long fs = file.length();
            long os = Util.getOccupiedSpace(file);
            assertTrue(os >= fs);
        }
        //
        File fontFile = new File(ctx.getFilesDir(), App.FONT_FILE);
        Typeface typeface = Util.loadFont(ctx);
        if (fontFile.isFile()) assertNotNull(typeface); else assertNull(typeface);
        //
        assertTrue(Util.makeHttps("http://www.example.com").startsWith("https://"));
        assertTrue(Util.makeHttps("ftp://www.example.com").startsWith("ftp://"));
        //
        String htmlList = "<ul><li>Item 1</li><li>Item 2</li></ul>";
        StringBuilder nonHtmlList = Util.removeHtmlLists(htmlList);
        assertNotNull(nonHtmlList);
        assertTrue(nonHtmlList.indexOf("<br>") >= 0);
        assertTrue(nonHtmlList.indexOf("•") >= 0);
        //
        StringBuilder linked = new StringBuilder("Click here: <a href=\"https://www.example.com\">Example</a>!");
        StringBuilder unlinked = Util.removeLinks(linked);
        assertNotNull(unlinked);
        assertTrue(unlinked.indexOf("<a") < 0);
        assertTrue(unlinked.indexOf("</a>") < 0);
        //
        String r = "Example hjelpText\t";
        SpannableStringBuilder replaced = Util.replaceAll(r, new CharSequence [] {"hjelp", "\t"}, new CharSequence[] {"help", " "});
        assertNotNull(replaced);
        assertEquals("Example helpText ", replaced.toString());
        //
        List<String> split = Util.splitString("0123456789", 5);
        assertNotNull(split);
        assertEquals(2, split.size());
        assertEquals("01234", split.get(0));
    }

}
