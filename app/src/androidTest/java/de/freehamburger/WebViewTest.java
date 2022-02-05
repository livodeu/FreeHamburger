package de.freehamburger;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.test.filters.MediumTest;

import org.junit.Test;

@MediumTest
public class WebViewTest {

    @Test
    public void testDownloadableRes() {
        final String[] urls = new String[] {
                "https://www.google.com/file.iso",
                "https://www.google.com/file.htm",
                "https://www.google.com"
        };
        final boolean[] expected = new boolean[] {
                true, false, false
        };
        final int n = urls.length;
        assertEquals(n, expected.length);
        for (int i = 0; i < n; i++) {
            assertEquals(expected[i], WebViewActivity.HamburgerWebViewClient.isDownloadableResource(urls[i]));
        }
    }

    @Test
    public void testShouldBlock() {
        final String[] urls = new String[] {
                "https://www.tagesschau.de/home.htm",
                "http://www.tagesschau.de/home.htm",
                "ftp://www.tagesschau.de/home.htm",
                "mailto://karlheinz@tagesschau.de",
                "https://www.tagessau.de/file.htm",
                "https://www.tagessau.de"
        };
        final boolean[] expected = new boolean[] {
                false, false, true, true, true, true
        };
        final int n = urls.length;
        assertEquals(n, expected.length);
        for (int i = 0; i < n; i++) {
            assertEquals(expected[i], WebViewActivity.HamburgerWebViewClient.shouldBlock(Uri.parse(urls[i])));
        }

    }

}
