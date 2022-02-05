package de.freehamburger;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Special version of WebViewActivity for teletext display that can create pinned shortcuts to a teletext page (from API 26 on).
 */
public class TeletextActivity extends WebViewActivity {

    /**
     * Modifies the teletext page after it has been loaded by
     * <ul>
     * <li>applying dark mode,</li>
     * <li>expanding the page horizontally,</li>
     * <li>adjusting the font size</li>
     * </ul>
     */
    private final PageFinishedListener pfl = (url) -> {
        Configuration c = getResources().getConfiguration();

        // apply night mode
        boolean nightMode = (c.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setDarkMode(nightMode);

        // expand the page horizontally
        /*
         examples of screen widths:
         4.0" phone  960x 540px hdpi   densityDpi=240: vertically 360dp, horizontally 592dp
         5.0" phone 1280x 720px xhdpi  densityDpi=320: vertically 360dp, horizontally 552dp (sic!)
         5.2" phone 1920x1080px xxhdpi densityDpi=480: vertically 360dp, horizontally 592dp
         7"  tablet 1024x 600px mdpi   densityDpi=160: vertically 600dp, horizontally 1024dp
         10" tablet 1920x1080px hdpi   densityDpi=240: vertically 720dp, horizontally 1280dp
         */
        int displayWidth = c.screenWidthDp;

        // apply the font zoom factor as set in the preferences
        int zoom = PreferenceManager.getDefaultSharedPreferences(TeletextActivity.this).getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT);
        //
        if (displayWidth > 0) {
            if (zoom > 0 && zoom != App.PREF_FONT_ZOOM_DEFAULT) setPageWidthAndFontSize(displayWidth, zoom);
            else setPageWidth(displayWidth);
        } else if (zoom > 0 && zoom != App.PREF_FONT_ZOOM_DEFAULT) {
            setFontSize(zoom);
        }

        // the menu item for creating a shortcut should be hidden if the shortcut already exists
        invalidateOptionsMenu();
    };

    /**
     * Determines whether the given pinned shortcut already exists.
     * @param sm ShortcutManager
     * @param id shortcut id
     * @return true / false
     * @throws NullPointerException if {@code sm} is {@code null}
     */
    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    private static boolean hasShortcut(@NonNull ShortcutManager sm, @NonNull final String id) {
        final List<ShortcutInfo> allExistingShortcuts = sm.getPinnedShortcuts();
        for (ShortcutInfo existingShortcut : allExistingShortcuts) {
            if (existingShortcut.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    PageFinishedListener getPageFinishedListener() {
        return this.pfl;
    }

    /**
     * Creates the shortcut id (and label) for the currently displayed url.
     * @return id / label
     */
    @NonNull
    private String makeShortcutIdForCurrentUrl() {
        Uri currentUri = Uri.parse(this.webView.getUrl());
        String label = getString(R.string.action_teletext_short);
        String lps = currentUri.getLastPathSegment();
        if (!TextUtils.isEmpty(lps)) {
            label = label + " " + lps;
        }
        return label;
    }

    /** {@inheritDoc} */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // reload so that the page width can be set again - simply calling setPageWidth() here does not work
        this.webView.reload();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ttext_menu, menu);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_shortcut_create) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager sm = (ShortcutManager)getSystemService(SHORTCUT_SERVICE);
                if (sm == null || !sm.isRequestPinShortcutSupported()) {
                    Snackbar.make(this.webView, R.string.error_shortcut_fail, Snackbar.LENGTH_LONG).show();
                    return true;
                }
                String label = makeShortcutIdForCurrentUrl();
                if (hasShortcut(sm, label)) {
                    Snackbar.make(this.webView, R.string.msg_shortcut_exists, Snackbar.LENGTH_LONG).show();
                    return true;
                }
                //
                Intent intent = new Intent(this, TeletextActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(EXTRA_URL, Uri.parse(this.webView.getUrl()).toString());
                intent.putExtra(EXTRA_NO_HOME_AS_UP, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(this, label)
                        .setActivity(new ComponentName(this, MainActivity.class))
                        .setShortLabel(label)
                        .setLongLabel(getString(R.string.action_teletext))
                        .setIcon(Icon.createWithResource(this, R.mipmap.ic_vt))
                        .setIntent(intent)
                        .build();
                if (!sm.requestPinShortcut(pinShortcutInfo, null)) {
                    Snackbar.make(this.webView, R.string.error_shortcut_fail, Snackbar.LENGTH_LONG).show();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem itemShortcut = menu.findItem(R.id.action_shortcut_create);
        if (itemShortcut != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                itemShortcut.setVisible(false);
            } else {
                ShortcutManager sm = (ShortcutManager) getSystemService(SHORTCUT_SERVICE);
                itemShortcut.setVisible(sm != null && sm.isRequestPinShortcutSupported() && !hasShortcut(sm, makeShortcutIdForCurrentUrl()));
            }
        }
        return true;
    }

    /**
     * Enables/disables dark mode.<br>
     * Relies on the javascript code found in
     * <a href="https://www.ard-text.de/mobil/">https://www.ard-text.de/mobil/</a>.
     * @param dark true / false
     */
    private void setDarkMode(boolean dark) {
        String cmd = "document.body.className = '" + (dark ? "dark" : "light") + "';";
        this.webView.evaluateJavascript(cmd, null);
    }

    /**
     * Sets the font size in percent of the original size.
     * @param fontSizePercent font size in percent
     */
    private void setFontSize(int fontSizePercent) {
        String cmd = "var pagewrapper = document.body.getElementsByTagName('div')[0]; if (pagewrapper) pagewrapper.style.fontSize='" + fontSizePercent + "%'";
        this.webView.evaluateJavascript(cmd, null);
    }

    /**
     * Sets the page width. Expects the first &lt;div&gt; in the page to be the relevant one.<br>
     * The &lt;div&gt;'s width was set to 425px by the <a href="https://www.ard-text.de/stylesheets/mobil/general.css">default css file</a>.
     * @param pageWidthPx page width to set
     */
    private void setPageWidth(@IntRange(from = 1) int pageWidthPx) {
        String cmd = "var pagewrapper = document.body.getElementsByTagName('div')[0]; if (pagewrapper) pagewrapper.style.width='" + pageWidthPx + "px'";
        this.webView.evaluateJavascript(cmd, null);
    }

    /**
     * Sets the page width and font size.
     * @param pageWidthPx page width to set
     * @param fontSizePercent font size in percent
     */
    private void setPageWidthAndFontSize(@IntRange(from = 1) int pageWidthPx, int fontSizePercent) {
        String cmd = "var pagewrapper = document.body.getElementsByTagName('div')[0]; if (pagewrapper) { pagewrapper.style.width='" + pageWidthPx + "px';pagewrapper.style.fontSize='" + fontSizePercent + "%' }";
        this.webView.evaluateJavascript(cmd, null);
    }
}
