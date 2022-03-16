package de.freehamburger;

import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

/**
 * Lets the user toggle background updates.
 * Note: there seems to be a memory leak in the super class.<br>
 * See <a href="https://github.com/square/leakcanary/issues/2207">https://github.com/square/leakcanary/issues/2207</a>.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class BackgroundTile extends TileService implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** {@inheritDoc} */
    @Override
    public void onClick() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(App.PREF_POLL, !prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT));
        ed.apply();
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_POLL.equals(key)) {
            update(prefs);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onStartListening() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        update(prefs);
    }

    /** {@inheritDoc} */
    @Override
    public void onStopListening() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void update(@NonNull SharedPreferences prefs) {
        final boolean poll = prefs.getBoolean(App.PREF_POLL, App.PREF_POLL_DEFAULT);
        final Tile tile = getQsTile();
        tile.setState(poll ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(poll ? getString(R.string.pref_title_poll_on) : getString(R.string.pref_title_poll_off));
        }
        tile.setContentDescription(getString(R.string.pref_hint_poll_quicksettings));
        tile.updateTile();
    }
}
