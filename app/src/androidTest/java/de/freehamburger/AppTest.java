package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.security.NetworkSecurityPolicy;
import android.service.notification.StatusBarNotification;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
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
import com.google.android.exoplayer2.ui.StyledPlayerView;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.List;
import java.util.Set;

import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.util.FileDeleter;
import de.freehamburger.util.MediaSourceHelper;
import de.freehamburger.util.OkHttpDownloader;
import de.freehamburger.util.TtfInfo;
import de.freehamburger.util.Util;
import de.freehamburger.widget.WidgetProvider;
import okhttp3.Call;
import okhttp3.OkHttpClient;

/**
 * Tests some app components that need no interaction with a remote host.
 */
@LargeTest
public class AppTest {

    private static Context ctx;

    /**
     * Returns a resource's name.
     * @param ctx Context
     * @param id resource id
     * @return resource name
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    @CheckResult
    private static String getResourceName(@NonNull Context ctx, int id) {
        if (id == 0xffffffff) return "";
        Resources r = ctx.getResources();
        if (r == null) return "<NOR>";
        StringBuilder out = new StringBuilder();
        try {
            String pkgname;
            switch (id & 0xff000000) {
                case 0x7f000000:
                    pkgname = "app";
                    break;
                case 0x01000000:
                    pkgname = "android";
                    break;
                default:
                    pkgname = r.getResourcePackageName(id);
                    break;
            }
            String typename;
            try {
                typename = r.getResourceTypeName(id);
            } catch (UnsupportedOperationException ee) {
                typename = "<null>";
            }
            String entryname;
            try {
                entryname = r.getResourceEntryName(id);
            } catch (UnsupportedOperationException ee) {
                entryname = "<null>";
            }
            out.append(pkgname);
            out.append(":");
            out.append(typename);
            out.append("/");
            out.append(entryname);
        } catch (Resources.NotFoundException e) {
            out.append("0x").append(Integer.toHexString(id));
        }
        return out.toString();
    }

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
        // test presence of BootReceiver in manifest
        PackageManager pm = ctx.getPackageManager();
        try {
            ActivityInfo ri = pm.getReceiverInfo(new ComponentName(ctx, BootReceiver.class), 0);
            assertNotNull(ri);
            assertTrue(ri.enabled);
            assertTrue(ri.exported);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }

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
        ed.commit();
        try {
            Thread.sleep(1_000L);
        } catch (Exception ignored) {
        }
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
        assumeTrue("This test needs API 24 (N)", Build.VERSION.SDK_INT >= 24);
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

    /**
     * Tests DateFormats used in the app.
     */
    @Test
    @SmallTest
    public void testDateFormats() {
        final long now = System.currentTimeMillis();
        String result;
        //
        result = Util.formatTs(DateFormat.getDateTimeInstance(), now, null);
        assertNotNull(result);
        //
        assertNotNull(OkHttpDownloader.DF);
        result = Util.formatTs(OkHttpDownloader.DF, now, null);
        assertNotNull(result);
        //
        assertNotNull(News.DF);
        result = Util.formatTs(News.DF, now, null);
        assertNotNull(result);
    }

    /**
     * Tests that preference default values defined in the resources match those defined in the code.
     */
    @Test
    public void testDefaults() {
        final int[] resBoolValues = new int[] {
                R.bool.pref_ask_before_finish_default,
                R.bool.pref_click_for_cats_default,
                R.bool.pref_correct_quotation_marks_default,
                R.bool.pref_load_over_mobile_default,
                R.bool.pref_load_videos_over_mobile_default,
                R.bool.pref_notification_extended_default,
                R.bool.pref_open_links_internally_default,
                R.bool.pref_pip_enabled_default,
                R.bool.pref_play_intro_default,
                R.bool.pref_poll_breaking_only_default,
                R.bool.pref_poll_default,
                R.bool.pref_poll_over_mobile_default,
                R.bool.pref_show_share_target_default,
                R.bool.pref_time_mode_default,
                R.bool.pref_warn_mute_default
        };
        final boolean[] appBoolValues = new boolean[] {
                App.PREF_ASK_BEFORE_FINISH_DEFAULT,
                App.PREF_CLICK_FOR_CATS_DEFAULT,
                App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT,
                App.DEFAULT_LOAD_OVER_MOBILE,
                App.DEFAULT_LOAD_VIDEOS_OVER_MOBILE,
                UpdateJobService.PREF_NOTIFICATION_EXTENDED_DEFAULT,
                VideoActivity.PREF_PIP_ENABLED_DEFAULT,
                App.PREF_PLAY_INTRO_DEFAULT,
                App.PREF_OPEN_LINKS_INTERNALLY_DEFAULT,
                App.PREF_POLL_BREAKING_ONLY_DEFAULT,
                App.PREF_POLL_DEFAULT,
                App.PREF_POLL_OVER_MOBILE_DEFAULT,
                App.PREF_SHOW_LATEST_SHARE_TARGET_DEFAULT,
                App.PREF_TIME_MODE_RELATIVE_DEFAULT,
                App.PREF_WARN_MUTE_DEFAULT
        };
        final int[] resIntValues = new int[] {
                R.integer.pref_font_zoom_default,
                R.integer.pref_mem_cache_max_size_default,
                R.integer.pref_poll_interval_default
        };
        final int[] appIntValues = new int[] {
                App.PREF_FONT_ZOOM_DEFAULT,
                (int)App.DEFAULT_CACHE_MAX_SIZE_MB,
                App.PREF_POLL_INTERVAL_DEFAULT
        };
        final Resources res = ctx.getResources();
        final int nBools = resBoolValues.length;
        assertEquals(nBools, appBoolValues.length);
        for (int i = 0; i < nBools; i++) {
            assertEquals(getResourceName(ctx, resBoolValues[i]) + " is wrong; ", appBoolValues[i], res.getBoolean(resBoolValues[i]));
        }
        final int nInts = resIntValues.length;
        assertEquals(nInts, appIntValues.length);
        for (int i = 0; i < nInts; i++) {
            assertEquals(getResourceName(ctx, resIntValues[i]) + " is wrong; ", appIntValues[i], res.getInteger(resIntValues[i]));
        }
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

    @Test
    public void testFrequentUpdates() {
        // test presence of FrequentUpdatesService in manifest
        PackageManager pm = ctx.getPackageManager();
        try {
            ServiceInfo si = pm.getServiceInfo(new ComponentName(ctx, FrequentUpdatesService.class), 0);
            assertNotNull(si);
            assertTrue(si.enabled);
            assertFalse(si.exported);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }

        JobInfo jobInfo = UpdateJobService.makeOneOffJobInfo(ctx, false);
        assertNotNull(jobInfo);
        assertFalse(jobInfo.isPeriodic());
        if (Build.VERSION.SDK_INT >= 28) assertNotNull(jobInfo.getRequiredNetwork());

        int run = UpdatesController.whatShouldRun(ctx);
        assertTrue(run == UpdatesController.RUN_JOB || run == UpdatesController.RUN_SERVICE || run == UpdatesController.RUN_NONE);
    }

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

    @Test
    public void testManifest() {
        PackageManager pm = ctx.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_PERMISSIONS);
            assertNotNull(pi);
            assertNotNull(pi.permissions);
            boolean permInternet = false;
            boolean permNetworkState = false;
            for (String permission : pi.requestedPermissions) {
                if (Manifest.permission.INTERNET.equals(permission)) permInternet = true;
                if (Manifest.permission.ACCESS_NETWORK_STATE.equals(permission)) permNetworkState = true;
            }
            assertTrue(permInternet);
            assertTrue(permNetworkState);

            final ApplicationInfo ai = pm.getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA);
            assertNotNull(ai);
            ai.dump(new android.util.LogPrinter(android.util.Log.INFO, getClass().getSimpleName()), "");
            assertEquals("de.freehamburger.App", ai.name);
            assertEquals("de.freehamburger.App", ai.className);
            assertTrue(ai.minSdkVersion >= 21);
            assertTrue("No app label", ai.labelRes != 0);
            assertTrue("No app icon", ai.icon != 0);
            assertEquals(ApplicationInfo.CATEGORY_NEWS, ai.category);
            assertEquals(SettingsActivity.class.getName(), ai.manageSpaceActivityName);
            try {
                @SuppressWarnings("JavaReflectionMemberAccess")
                Field fpf = ApplicationInfo.class.getDeclaredField("privateFlags");
                fpf.setAccessible(true);
                @SuppressWarnings("ConstantConditions")
                int privateFlags = (int)fpf.get(ai);
                // 1 << 27 is ApplicationInfo.PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE
                assertFalse("allowAudioPlaybackCapture is set to true", (privateFlags & (1 << 27)) != 0);
            } catch (Exception e) {
                fail(e.toString());
            }

            assertNotNull(ai.metaData);
            Set<String> metaKeys = ai.metaData.keySet();
            assertNotNull(metaKeys);
            assertTrue(metaKeys.size() > 0);
            boolean metricsOptOutFound = false;
            boolean enableSafeBrowsingFound = false;
            for (String metaKey : metaKeys) {
                if ("android.webkit.WebView.MetricsOptOut".equals(metaKey)) {
                    metricsOptOutFound = true;
                    Object value = ai.metaData.get(metaKey);
                    assertEquals(Boolean.TRUE, value);
                } else if ("android.webkit.WebView.EnableSafeBrowsing".equals(metaKey)) {
                    enableSafeBrowsingFound = true;
                    Object value = ai.metaData.get(metaKey);
                    assertEquals(Boolean.FALSE, value);
                }
            }
            assertTrue(metricsOptOutFound);
            assertTrue(enableSafeBrowsingFound);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
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

    /**
     * Tests the {@link NewsActivity}.
     */
    @Test
    public void testNewsActivity() {
        ActivityScenario<NewsActivity> asn = ActivityScenario.launch(NewsActivity.class);
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
            View bottomVideoPauseIndicator = activity.findViewById(R.id.bottomVideoPauseIndicator);
            assertNotNull(bottomVideoPauseIndicator);
            View bottomVideoViewWrapper = activity.findViewById(R.id.bottomVideoViewWrapper);
            assertNotNull(bottomVideoViewWrapper);
            // make sure that the ExoPlayer instances exist
            if (activity.loadVideo) {
                assertNotNull(activity.exoPlayerTopVideo);
                assertNotNull(activity.exoPlayerBottomVideo);
                assertNotNull(topVideoView.getPlayer());
                assertNotNull(bottomVideoView.getPlayer());
            }
            // make sure that some Views react to clicks
            assertTrue(buttonAudio.hasOnClickListeners());
            // hasOnLongClickListeners() is available only from Android 11 (R) on
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) assertTrue(buttonAudio.hasOnLongClickListeners());
            assertTrue(textViewBottomVideoPeek.hasOnClickListeners());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) assertTrue(textViewBottomVideoPeek.hasOnLongClickListeners());
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

        ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class);
        asn.moveToState(Lifecycle.State.RESUMED);
        asn.onActivity(activity -> {
            if (background == App.BACKGROUND_DAY) assertFalse(Util.isNightMode(activity));
            else if (background == App.BACKGROUND_NIGHT) assertTrue(Util.isNightMode(activity));
            activity.finish();
        });
    }

    /**
     * Tests the creation of notification channels.
     */
    @Test
    @RequiresApi(26)
    @SmallTest
    public void testNotificationChannel() {
        assumeTrue("This test needs API 26 (O)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        App app = (App)ctx.getApplicationContext();
        assertNotNull(app.getNotificationChannel());
        assertNotNull(app.getNotificationChannelHiPri());
        assertNotNull(app.getNotificationChannelUpdates());
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
        assumeTrue("This test needs API 26 (O)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        ShortcutManager shortcutManager = (ShortcutManager)ctx.getSystemService(Context.SHORTCUT_SERVICE);
        assertNotNull(shortcutManager);
        assumeTrue("ShortcutManager does not support pinned shortcuts", shortcutManager.isRequestPinShortcutSupported());
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
     * Tests that {@link BackgroundTile} is referred to in the manifest.
     */
    @Test
    @SmallTest
    public void testTiles() {
        PackageManager pm = ctx.getPackageManager();
        try {
            ServiceInfo serviceInfo = pm.getServiceInfo(new ComponentName(ctx, BackgroundTile.class), PackageManager.GET_DISABLED_COMPONENTS);
            assertNotNull(serviceInfo);
            assertFalse(serviceInfo.enabled);
            assertTrue(serviceInfo.exported);
            assertEquals(Manifest.permission.BIND_QUICK_SETTINGS_TILE, serviceInfo.permission);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }
    }

    /**
     * Tests {@link TtfInfo}. Relies on the presence of "DroidSans.ttf" in "/system/fonts".
     */
    @Test
    @SmallTest
    public void testTtf() {
        File file = new File("/system/fonts/DroidSans.ttf");
        assumeTrue("Not a file: " + file.getAbsolutePath(), file.isFile());
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

        // test presence of UpdateJobService in manifest
        PackageManager pm = ctx.getPackageManager();
        try {
            ServiceInfo si = pm.getServiceInfo(new ComponentName(ctx, UpdateJobService.class), 0);
            assertNotNull(si);
            assertTrue(si.enabled);
            assertTrue(si.exported);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        JobInfo jobInfo = UpdateJobService.makeJobInfo(ctx);
        assertNotNull(jobInfo);
        assertTrue(jobInfo.isPeriodic());
        if (Build.VERSION.SDK_INT >= 28) assertNotNull(jobInfo.getRequiredNetwork());
        boolean night = UpdateJobService.hasNightFallenOverBerlin(prefs);
        long intervalMs = UpdateJobService.calcInterval(prefs, night);
        assertEquals(Math.max(JobInfo.getMinPeriodMillis(), intervalMs), jobInfo.getIntervalMillis());
        JobScheduler js = (JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertNotNull(js);
        int result = js.schedule(jobInfo);
        App app = (App)ctx.getApplicationContext();
        assertTrue(app.isBackgroundJobScheduled() != 0L);
        js.cancel(jobInfo.getId());
        assertEquals(result, JobScheduler.RESULT_SUCCESS);

        // parse the (presumably existing) json file for the HOME category
        File existing = new File(ctx.getFilesDir(), Source.HOME.name() + Source.FILE_SUFFIX);
        assumeTrue("To fully test the " + UpdateJobService.class + ", the HOME source must have been downloaded at least once!", existing.isFile());
        assumeTrue("Source.HOME is locked", Source.HOME.getLockHolder() == null);
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

    @Test
    @MediumTest
    public void testWidgets() {
        PackageManager pm = ctx.getPackageManager();
        // check that 'uses-feature android:name="android.software.app_widgets"' is declared
        try {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            assertNotNull(pi);
            assertNotNull(pi.reqFeatures);
            boolean appWidgetsFeatureDeclared = false;
            for (FeatureInfo fi : pi.reqFeatures) {
                if (PackageManager.FEATURE_APP_WIDGETS.equals(fi.name)) {appWidgetsFeatureDeclared = true; break;}
            }
            assertTrue("uses-feature android:name=\"android.software.app_widgets\" not found in manifest", appWidgetsFeatureDeclared);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }

        // test presence of WidgetProvider in manifest
        try {
            ActivityInfo ri = pm.getReceiverInfo(new ComponentName(ctx, WidgetProvider.class), 0);
            assertNotNull(ri);
            assertTrue(ri.enabled);
            assertFalse(ri.exported);
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }

        // test widget_info xml resource
        XmlResourceParser p = ctx.getResources().getXml(R.xml.widget_info);
        assertNotNull(p);
        try {
            boolean configureFound = false;
            int eventType = p.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    assertEquals("appwidget-provider", p.getName());
                    int nattributes = p.getAttributeCount();
                    assertTrue(nattributes > 0);
                    for (int i = 0; i < nattributes; i++) {
                        String attrName = p.getAttributeName(i);
                        String attrValue = p.getAttributeValue(i);
                        if ("configure".equals(attrName)) {
                            assertEquals(WidgetActivity.class.getName(), attrValue);
                            configureFound = true;
                        }
                    }
                }
                eventType = p.next();
            }
            p.close();
            assertTrue(configureFound);
        } catch (Exception e) {
            fail(e.toString());
        }

        // test WidgetProvider class
        AppWidgetManager aw = AppWidgetManager.getInstance(ctx);
        assertNotNull(aw);
        ComponentName provider = new ComponentName(ctx, WidgetProvider.class);
        final int[] widgetIds = aw.getAppWidgetIds(provider);
        assumeTrue("This test needs at least one existing app widget",widgetIds != null && widgetIds.length > 0);
        SparseArray<Source> widgetSources = WidgetProvider.loadWidgetSources(ctx);
        assertNotNull(widgetSources);
        assertEquals(widgetSources.size(), widgetIds.length);
        for (int widgetId : widgetIds) {
            AppWidgetProviderInfo info = aw.getAppWidgetInfo(widgetId);
            assertNotNull(info);
            assertEquals(provider, info.provider);
            assertNotNull(widgetSources.get(widgetId));
            assertTrue(WidgetProvider.fillWidget(ctx, aw, widgetId, null, new RuntimeException("TEST")));
            // the widget should display the Exception's message now
        }
        boolean textViewSetGravityRemotable = WidgetProvider.isRemotable(TextView.class,"setGravity", int.class);
        if (Build.VERSION.SDK_INT >= 31) assertTrue(textViewSetGravityRemotable); else assertFalse(textViewSetGravityRemotable);

        JobInfo ji = UpdateJobService.makeOneOffJobInfoWithDelay(ctx, 5_000L, 10_000L);
        assertNotNull(ji);
        assertFalse(ji.isPeriodic());
        assertTrue(ji.getMinLatencyMillis() > 0L);
        assertFalse(ji.isImportantWhileForeground());
    }

}
