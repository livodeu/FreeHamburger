package de.freehamburger;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import java.util.List;

/**
 * Special version of WebViewActivity for teletext display that can create pinned shortcuts to a teletext page (from API 26 on).
 */
public class TeletextActivity extends WebViewActivity {

    /**
     * Determines whether the given pinned shortcut already exists.
     * @param sm ShortcutManager
     * @param id shortcut id
     * @return true / false
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
                String currentUrl = this.webView.getUrl();
                Uri currentUri = Uri.parse(currentUrl);
                String labelLong = getString(R.string.action_teletext);
                String label = getString(R.string.action_teletext_short);
                String lps = currentUri.getLastPathSegment();
                if (!TextUtils.isEmpty(lps)) {
                    label = label + " " + lps;
                    labelLong = labelLong + " " + lps;
                }
                if (hasShortcut(sm, label)) {
                    Snackbar.make(this.webView, R.string.msg_shortcut_exists, Snackbar.LENGTH_LONG).show();
                    return true;
                }
                //
                Intent intent = new Intent(this, TeletextActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(EXTRA_URL, currentUri.toString());
                intent.putExtra(EXTRA_NO_HOME_AS_UP, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(this, label)
                        .setActivity(new ComponentName(this, MainActivity.class))
                        .setShortLabel(label)
                        .setLongLabel(labelLong)
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
                itemShortcut.setVisible(sm != null && sm.isRequestPinShortcutSupported());
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

}
