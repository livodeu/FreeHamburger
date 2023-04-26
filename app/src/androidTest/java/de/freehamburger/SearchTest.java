package de.freehamburger;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import de.freehamburger.supp.SearchHelper;

/**
 * There is also {@link DataAndGuiTest#testSearchSuggestions()} which needs some data created over there; thus it cannot be here, unfortunately.
 */
@MediumTest
public class SearchTest {

    private static Context ctx;

    @BeforeClass
    public static void init() {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        assertNotNull(ctx);
    }

    /**
     * Tests that the MainActivity handles {@link SearchHelper#SEARCH_SUGGEST_ACTION}.
     */
    @Test
    @SmallTest
    public void testHandleSearchSuggestAction() {
        // The default intent action to be used when a user taps on a custom search suggestion
        Intent intent = new Intent(SearchHelper.SEARCH_SUGGEST_ACTION);
        intent.setData(Uri.parse("content://de.freehamburger.debug/sid/karneval#HOME"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_DEBUG_LOG_RESOLUTION);
        try {
            List<ResolveInfo> as = ctx.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            assertNotNull("Not resolved: " + intent, as);
            boolean found = false;
            String mainActivityClass = MainActivity.class.getName();
            for (ResolveInfo ri : as) {
                if (ri.activityInfo == null || ri.activityInfo.name == null) continue;
                if (ri.activityInfo.name.equals(mainActivityClass)) {
                    found = true;
                    break;
                }
            }
            assertTrue(mainActivityClass + " does not handle " + SearchHelper.SEARCH_SUGGEST_ACTION, found);
        } catch (Exception e) {
            fail("No activity for " + intent + ": " + e);
        }
    }

    /**
     * Tests that the MainActivity handles {@link Intent#ACTION_SEARCH}.
     */
    @Test
    @SmallTest
    public void testHandlesSearchAction() {
        Intent intent = new Intent(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, ".");
        List<ResolveInfo> as = ctx.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(as);
        boolean found = false;
        String mainActivityClass = MainActivity.class.getName();
        for (ResolveInfo ri : as) {
            if (ri.activityInfo == null || ri.activityInfo.name == null) continue;
            if (ri.activityInfo.name.equals(mainActivityClass)) {
                found = true;
                break;
            }
        }
        assertTrue(mainActivityClass + " does not handle Intent.ACTION_SEARCH", found);
    }

}
