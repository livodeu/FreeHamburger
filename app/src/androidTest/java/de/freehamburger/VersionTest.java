package de.freehamburger;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import de.freehamburger.util.Util;
import de.freehamburger.version.Asset;
import de.freehamburger.version.Release;
import de.freehamburger.version.ReleaseChecker;
import de.freehamburger.version.VersionUtil;

/**
 * Tests for code in package de.freehamburger.version
 */
public class VersionTest {

    private static Context ctx;

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
        assertTrue(ctx instanceof App);
    }

    @Test
    @SmallTest
    public void testAsHex() {
        byte[] b = "ABC".getBytes();
        CharSequence cs = VersionUtil.asHex(b);
        assertNotNull(cs);
        assertEquals(b.length << 1, cs.length());
        assertEquals("414243", cs.toString());
    }

    @Test
    @SmallTest
    public void testCertDigest() {
        Assume.assumeTrue(de.freehamburger.test.BuildConfig.DEBUG);
        final String sha256 = ReleaseChecker.getCertDigest(ctx.getPackageManager(), ctx.getPackageName());
        assertNotNull(sha256);
        assertEquals(64, sha256.length());
        assertTrue(sha256.indexOf('g') < 0);
        assertTrue(sha256.indexOf('A') < 0);
        assertEquals(ReleaseChecker.DIGEST_SHA256_ANDROID_DEBUG, sha256);
    }

    @Test
    @SmallTest
    public void testIntents() {
        Intent intentFdroid = ReleaseChecker.makeBrowseFdroidIntent(ctx);
        assertNotNull(intentFdroid);
        assertEquals(Intent.ACTION_CHOOSER, intentFdroid.getAction());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Parcelable[] excluded = intentFdroid.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS);
            assertNotNull(excluded);
            assertTrue(excluded.length > 0);
        }
        Release githubRelease = Release.fromString("v1.4#1675082928000#https://api.github.com/repos/livodeu/FreeHamburger/releases/90684900/assets");
        assertTrue(githubRelease.isValid());
        Intent intentGithub = ReleaseChecker.makeBrowseGithubIntent(githubRelease);
        assertNotNull(intentGithub);
        assertEquals(Intent.ACTION_VIEW, intentGithub.getAction());
        Uri uri = intentGithub.getData();
        assertNotNull(uri);
        assertEquals("github.com", uri.getHost());
    }

    @Test
    @SmallTest
    public void testNewerVersion() {
        String thisVersion = Util.trimNumber(BuildConfig.VERSION_NAME);
        Assume.assumeTrue("Unexpected version name in BuildConfig: " + BuildConfig.VERSION_NAME, thisVersion.startsWith("1."));
        Release same = Release.fromString(thisVersion + "#0#");
        Release v09 = Release.fromString("0.9#0#");
        Release v20 = Release.fromString("2.0#0#");
        Release vv09 = Release.fromString("v0.9#0#");
        Release vv20 = Release.fromString("v2.0#0#");
        assertFalse(ReleaseChecker.isNewerReleaseAvailable(same));
        assertFalse(ReleaseChecker.isNewerReleaseAvailable(v09));
        assertTrue(ReleaseChecker.isNewerReleaseAvailable(v20));
        assertFalse(ReleaseChecker.isNewerReleaseAvailable(vv09));
        assertTrue(ReleaseChecker.isNewerReleaseAvailable(vv20));
    }

    /**
     * Tests parsing the github output.<br>
     * Needs a json file (according to <a href="https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28">Github API for releases</a>)
     * in R.raw.github.
     */
    @Test
    @MediumTest
    public void testParseGithub() {
        InputStream inputStream = null;
        try {
            inputStream = ctx.getResources().openRawResource(R.raw.github);
        } catch (Resources.NotFoundException e) {
        }
        Assume.assumeNotNull("R.raw.github (see ReleaseChecker.URL_GITHUB_API) not found - expected in debug res", inputStream);
        try {
            Release release = ReleaseChecker.parseGithub(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            assertNotNull(release);
            assertTrue(release.isValid());
        } catch (Exception e) {
            fail(e.toString());
        } finally {
            Util.close(inputStream);
        }
    }

    @Test
    @SmallTest
    public void testToAndFromString() {
        // it must all go wrong it the seps are the same
        assertNotNull(Release.SEP);
        assertNotNull(Asset.SEP);
        assertEquals(1, Release.SEP.length());
        assertEquals(1, Asset.SEP.length());
        assertNotEquals("The Release SEP is the same as the Asset SEP", Release.SEP, Asset.SEP);
        //
        final String[] originals = new String[] {
                "v1.4#1675082928000#https://api.github.com/repos/livodeu/FreeHamburger/releases/90684900/assets#app-arm64-v8a-release.apk§https://github.com/livodeu/FreeHamburger/releases/download/v1.4/app-arm64-v8a-release.apk#app-armeabi-v7a-release.apk§https://github.com/livodeu/FreeHamburger/releases/download/v1.4/app-armeabi-v7a-release.apk#app-x86-release.apk§https://github.com/livodeu/FreeHamburger/releases/download/v1.4/app-x86-release.apk#app-x86_64-release.apk§https://github.com/livodeu/FreeHamburger/releases/download/v1.4/app-x86_64-release.apk",
                "#0#",
                "#12345#",
                "1.4#0#",
                Release.EMPTY
        };
        final boolean[] valid = new boolean[] {
                true, false, false, true, false
        };
        final int[] assetCounts = new int[] {
                4, 0, 0, 0, 0
        };
        assertEquals(originals.length, valid.length);
        assertEquals(originals.length, assetCounts.length);
        int i = 0;
        for (String original : originals) {
            final Release release = Release.fromString(original);
            assertNotNull(release);
            assertEquals(valid[i], release.isValid());
            assertEquals(assetCounts[i], release.getAssets().size());
            for (Asset asset : release.getAssets()) {
                assertNotNull(asset);
                assertTrue(asset.isValid());
            }
            String copy = release.toString();
            assertEquals(copy, original);
            i++;
        }
    }
}
