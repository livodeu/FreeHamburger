package de.freehamburger.model;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.R;
import de.freehamburger.adapters.RegionsAdapter;
import de.freehamburger.util.Util;

/**
 *
 */
public enum Region {

    UNKNOWN(0, "Unbekannt"),
    BW(1, "Baden-Württemberg"),
    BAYERN(2, "Bayern"),
    BERLIN(3, "Berlin"),
    BB(4, "Brandenburg"),
    BREMEN(5, "Bremen"),
    HAMBURG(6, "Hamburg"),
    HESSEN(7, "Hessen"),
    MV(8, "Mecklenburg-Vorpommern"),
    NS(9, "Niedersachsen"),
    NRW(10, "Nordrhein-Westfalen"),
    RP(11, "Rheinland-Pfalz"),
    SAARLAND(12, "Saarland"),
    SACHSEN(13, "Sachsen"),
    SA(14, "Sachsen-Anhalt"),
    SH(15, "Schleswig-Holstein"),
    THUERINGEN(16, "Thüringen")
    ;

    private final int id;
    private final String label;

    @Nullable
    public static Region getById(@IntRange(from = 0) final int id) {
        final Region[] values = Region.values();
        for (Region r : values) {
            if (r.id == id) return r;
        }
        return null;
    }

    /**
     * @return List of labels of all valid Regions
     */
    public static List<String> getValidLabels() {
        final Region[] regions = values();
        final List<String> list = new ArrayList<>(regions.length - 1);
        for (Region region : regions) {
            if (region.id > 0) list.add(region.label);
        }
        return list;
    }

    /**
     * @return List of valid Regions (all except {@link #UNKNOWN})
     */
    @NonNull
    public static List<Region> getValidRegions() {
        final Region[] regions = values();
        final List<Region> list = new ArrayList<>(regions.length - 1);
        for (Region region : regions) {
            if (region.id > 0) list.add(region);
        }
        return list;
    }

    /**
     * Displays a dialog to select interesting regions.
     * @param activity Activity
     * @return AlertDialog
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    @NonNull
    public static AlertDialog selectRegions(@NonNull Activity activity) {
        final App app = (App)activity.getApplicationContext();
        final List<Region> regions = getValidRegions();
        final int n = regions.size();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final Set<String> regionSet = prefs.getStringSet(App.PREF_REGIONS, new HashSet<>(0));
        boolean[] checked = new boolean[n];
        for (String regionId : regionSet) {
            int id = Integer.parseInt(regionId);
            for (int i = 0; i < n; i++) {
                if (regions.get(i).getId() == id) {
                    checked[i] = true;
                    break;
                }
            }
        }
        final RegionsAdapter adapter = new RegionsAdapter(activity, regions, checked);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.label_regions)
                .setSingleChoiceItems(adapter, -1, (dialog, which) -> adapter.toggle(which))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final Set<String> newRegionSet = new HashSet<>();
                    boolean[] newChecked = adapter.getChecked();
                    for (int i = 0; i < n; i++) {
                        if (newChecked[i]) {
                            newRegionSet.add(String.valueOf(regions.get(i).getId()));
                        }
                    }
                    dialog.dismiss();
                    if (newRegionSet.equals(regionSet)) return;
                    SharedPreferences.Editor ed = prefs.edit();
                    ed.putStringSet(App.PREF_REGIONS, newRegionSet);
                    ed.apply();
                    // delete cached file for Source.REGIONAL to force refresh
                    File f = app.getLocalFile(Source.REGIONAL);
                    if (f.isFile()) {
                        Util.deleteFile(f);
                    }
                })
                ;
        AlertDialog ad = builder.create();
        Window w = ad.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(R.drawable.bg_dialog);
        }
        ad.supportRequestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS);
        ad.show();
        return ad;
    }

    /**
     * Constructor.
     * @param id id
     * @param label label
     */
    Region(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return label;
    }

}
