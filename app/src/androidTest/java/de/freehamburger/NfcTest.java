package de.freehamburger;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.MediumTest;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.model.Source;
import de.freehamburger.supp.NfcHelper;

public class NfcTest {

    private static final String MAINACTIVITY_ALIAS_CLASS = "de.freehamburger.MainActivityNfc";
    private static Context ctx;

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
    }

    /**
     * Tests the manifest if NFC usage is disabled via preferences.
     */
    private static void testManifestDisabled() {
        Assume.assumeFalse("NFC usage is on.", PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT));
        PackageManager pm = ctx.getPackageManager();
        try {
            boolean mainActivityAliasClassFound = false;
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            final ActivityInfo[] ais = pi.activities;
            for (ActivityInfo ai : ais) {
                if (MAINACTIVITY_ALIAS_CLASS.equals(ai.name)) {
                    mainActivityAliasClassFound = true;
                    break;
                }
            }
            assertFalse("Enabled: " + MAINACTIVITY_ALIAS_CLASS, mainActivityAliasClassFound);
            int ces = pm.getComponentEnabledSetting(new ComponentName(ctx, MAINACTIVITY_ALIAS_CLASS));
            assertNotEquals("Enabled: " + MAINACTIVITY_ALIAS_CLASS, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, ces);
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    /**
     * Tests the manifest if NFC usage is enabled via preferences.
     */
    private static void testManifestEnabled() {
        Assume.assumeTrue("NFC usage is off.", PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT));
        PackageManager pm = ctx.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_PERMISSIONS);
            assertNotNull(pi);
            assertNotNull(pi.permissions);
            boolean permNfc = false;
            for (String permission : pi.requestedPermissions) {
                if (Manifest.permission.NFC.equals(permission)) {
                    permNfc = true;
                    break;
                }
            }
            assertTrue("Not in manifest: " + Manifest.permission.NFC, permNfc);

            final String url = "https://" + ctx.getString(R.string.viewable_host_1);

            boolean mainActivityAliasClassFound = false;
            pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
            final ActivityInfo[] ais = pi.activities;
            for (ActivityInfo ai : ais) {
                if (MAINACTIVITY_ALIAS_CLASS.equals(ai.name)) {
                    mainActivityAliasClassFound = true;
                    break;
                }
            }
            assertTrue("Not available or enabled: " + MAINACTIVITY_ALIAS_CLASS, mainActivityAliasClassFound);
            int ces = pm.getComponentEnabledSetting(new ComponentName(ctx, MAINACTIVITY_ALIAS_CLASS));
            assertEquals("Not enabled: " + MAINACTIVITY_ALIAS_CLASS, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, ces);

            Set<Intent> intentsForMain = new HashSet<>(3);
            Intent intentTagDiscovered = new Intent(NfcAdapter.ACTION_TAG_DISCOVERED);
            intentsForMain.add(intentTagDiscovered);
            Intent intentNdefDiscovered = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
            intentNdefDiscovered.setData(Uri.parse(url));
            intentsForMain.add(intentNdefDiscovered);
            Intent intentNdefDiscovered2 = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
            intentNdefDiscovered2.setData(Uri.parse(NfcHelper.SCHEME_H + "://" + Source.HOME.name()));
            intentsForMain.add(intentNdefDiscovered2);
            assertEquals(3, intentsForMain.size());

            for (Intent intentForMain : intentsForMain) {
                boolean found = false;
                List<ResolveInfo> as = ctx.getPackageManager().queryIntentActivities(intentForMain, PackageManager.MATCH_DEFAULT_ONLY);
                assertNotNull("Not resolved: " + intentForMain, as);
                for (ResolveInfo ri : as) {
                    if (ri.activityInfo == null || ri.activityInfo.name == null) continue;
                    if (ri.activityInfo.name.equals(MAINACTIVITY_ALIAS_CLASS)) {
                        found = true;
                        break;
                    }
                }
                assertTrue(MAINACTIVITY_ALIAS_CLASS + " does not handle " + intentForMain, found);
            }

        } catch (Exception e) {
            fail(e.toString());
        }
    }

    @Test
    public void testForegroundDispatchEnabled() {
        Assume.assumeTrue("NFC usage is off.", PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT));
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        Assume.assumeFalse("Device is locked.", km.isDeviceLocked());
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(ctx);
        Assume.assumeTrue("This test needs an enabled NfcAdapter",nfcAdapter != null && nfcAdapter.isEnabled());
        try (ActivityScenario<MainActivity> asn = ActivityScenario.launch(MainActivity.class)) {
            asn.moveToState(Lifecycle.State.RESUMED);
            asn.onActivity(activity -> {
                assertTrue(NfcHelper.isForegroundDispatchEnabled(activity));
                activity.finish();
            });
        }
    }

    /**
     * Tests the manifest for NFC-related settings.
     */
    @Test
    @MediumTest
    public void testManifest() {
        if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_NFC_USE, App.PREF_NFC_USE_DEFAULT)) {
            testManifestEnabled();
        } else {
            testManifestDisabled();
        }
    }

    @Test
    public void testMisc() {
        // test sourceFromUri
        assertEquals(Source.SPORT, NfcHelper.sourceFromUri(Uri.parse(NfcHelper.SCHEME_H + "://" + Source.SPORT.name())));
        // test extractNfcUrl
        final String url = "https://" + ctx.getString(R.string.viewable_host_1);
        Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        NdefMessage msg0 = new NdefMessage(NdefRecord.createApplicationRecord(ctx.getPackageName()));
        NdefMessage msg1 = new NdefMessage(NdefRecord.createUri(Uri.parse(url)));
        intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new Parcelable[] {msg0, msg1});
        Uri uri = NfcHelper.extraxtNfcUrl(intent);
        assertNotNull(uri);
        assertEquals(url, uri.toString());
    }
}
