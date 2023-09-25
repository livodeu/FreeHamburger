package de.freehamburger;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.freehamburger.adapters.FilterAdapter;
import de.freehamburger.model.Filter;
import de.freehamburger.model.TextFilter;
import de.freehamburger.util.CoordinatorLayoutHolder;
import de.freehamburger.util.Util;
import de.freehamburger.views.FilterView;

public class FilterActivity extends StyledActivity implements CoordinatorLayoutHolder {

    /** the toggle animation must be run via a Runnable (for whatever reason); this value is an offset in ms for its start */
    private static final long TOGGLE_ANIMATION_OFFSET = 50L;
    /** denotes a filter that applies to word starts */
    public static final char C_ATSTART = '[';
    /** denotes a filter that applies to word ends */
    public static final char C_ATEND = ']';
    private final Handler handler = new Handler();
    private CoordinatorLayout coordinatorLayout;
    private RecyclerView recyclerView;
    private Snackbar sb;
    /** number of filters when this activity starts */
    private int filterCountOnResume;
    /** true if the user has just switched the filters on or off */
    private boolean filterChanging;

    /**
     * Adds a filter with an empty phrase.
     * @return {@code true} if the filter has been added
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean addFilter() {
        FilterAdapter adapter = (FilterAdapter)this.recyclerView.getAdapter();
        if (adapter == null) return false;
        boolean added = adapter.addFilter(new TextFilter(""));
        invalidateOptionsMenu();
        if (added) {
            this.handler.postDelayed(() -> this.recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1), 500L);
        }
        return added;
    }

    /** {@inheritDoc} */
    @Override
    public CoordinatorLayout getCoordinatorLayout() {
        return this.coordinatorLayout;
    }

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        this.coordinatorLayout = findViewById(R.id.coordinator_layout);
        this.recyclerView = findViewById(R.id.recyclerView);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Filter> filters = TextFilter.createTextFiltersFromPreferences(this);
        FilterAdapter adapter = new FilterAdapter(this);
        adapter.setFilters(filters);
        this.recyclerView.setAdapter(adapter);

        ActionBar ab = getSupportActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter_menu, menu);
        menu.setQwertyMode(true);
        return true;
    }

    /**
     * The clear/delete button has been clicked.
     * @param button the 'clear/delete' button next to the filter phrase
     */
    public void onDeleteClicked(View button) {
        ViewParent parent = button.getParent();
        if (!(parent instanceof FilterView)) return;
        FilterView filterView = (FilterView)parent;
        EditText editTextPhrase = filterView.findViewById(R.id.editTextPhrase);
        if (editTextPhrase != null && !TextUtils.isEmpty(editTextPhrase.getText())) {
            // the filter phrase was not empty -> clear the filter phrase
            editTextPhrase.setText(null);
            invalidateOptionsMenu();
            return;
        }
        // the filter phrase was empty -> delete the filter
        int pos = this.recyclerView.getChildAdapterPosition(filterView);
        if (pos == RecyclerView.NO_POSITION) return;
        FilterAdapter filterAdapter = (FilterAdapter)this.recyclerView.getAdapter();
        if (filterAdapter != null) filterAdapter.removeFilter(pos);
        invalidateOptionsMenu();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (this.sb != null && this.sb.isShown()) this.sb.dismiss();
        int id = item.getItemId();
        if (id == R.id.action_add_filter) {
            if (!addFilter()) {
                this.sb = Snackbar.make(this.coordinatorLayout, R.string.error_filter_not_added, Snackbar.LENGTH_SHORT);
                this.sb.show();
            }
            return true;
        }
        if (id == R.id.action_enable_filters) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean wasEnabled = prefs.getBoolean(App.PREF_FILTERS_APPLY, App.PREF_FILTERS_APPLY_DEFAULT);
            boolean nowEnabled = !wasEnabled;
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(App.PREF_FILTERS_APPLY, nowEnabled);
            ed.apply();
            this.filterChanging = true;
            invalidateOptionsMenu();
            this.handler.postDelayed(() -> {
                this.sb = Snackbar.make(coordinatorLayout, nowEnabled ? R.string.msg_filters_enabled : R.string.msg_filters_disabled, Snackbar.LENGTH_SHORT);
                this.sb.show();
            }, TOGGLE_ANIMATION_OFFSET + getResources().getInteger(R.integer.toggle_animation_step) * 10L);
        }
        if (id == R.id.action_cats_filters) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean filtersWereAppliedToCats = prefs.getBoolean(App.PREF_APPLY_FILTERS_TO_CATEGORIES, App.PREF_APPLY_FILTERS_TO_CATEGORIES_DEFAULT);
            boolean filtersAreAppliedToCats = !filtersWereAppliedToCats;
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(App.PREF_APPLY_FILTERS_TO_CATEGORIES, filtersAreAppliedToCats);
            ed.apply();
            invalidateOptionsMenu();
            return true;
        }
        if (id == R.id.action_help_filters) {
            WebView webViewForHelp = new WebView(this);
            WebSettings ws = webViewForHelp.getSettings();
            ws.setBlockNetworkLoads(true);
            ws.setAllowContentAccess(false);
            ws.setGeolocationEnabled(false);
            webViewForHelp.setNetworkAvailable(false);
            webViewForHelp.setBackgroundColor(getResources().getColor(R.color.colorPrimarySemiTrans));
            Util.showHelp(this, R.raw.help_filters_de, webViewForHelp);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        FilterAdapter adapter = (FilterAdapter)this.recyclerView.getAdapter();
        if (adapter != null) {
            final List<Filter> filters = adapter.getFilters();
            final Set<String> preferredFilters = new HashSet<>(filters.size());
            for (Filter filter : filters) {
                if (filter.isTemporary() || !(filter instanceof TextFilter)) continue;
                CharSequence phrase = filter.getText();
                if (TextUtils.isEmpty(phrase)) continue;
                String s = phrase.toString().trim();
                if (s.length() == 0) continue;
                if (((TextFilter) filter).isAtStart()) {
                    preferredFilters.add(C_ATSTART + s.toLowerCase(Locale.GERMAN));
                } else if (((TextFilter) filter).isAtEnd()) {
                    preferredFilters.add(C_ATEND + s.toLowerCase(Locale.GERMAN));
                } else {
                    preferredFilters.add(s.toLowerCase(Locale.GERMAN));
                }
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = prefs.getBoolean(App.PREF_FILTERS_APPLY, App.PREF_FILTERS_APPLY_DEFAULT);
            SharedPreferences.Editor ed = prefs.edit();
            int n = preferredFilters.size();
            if (n == 0) {
                ed.remove(App.PREF_FILTERS);
            } else {
                ed.putStringSet(App.PREF_FILTERS, preferredFilters);
            }
            ed.apply();
            if (isFinishing()) {
                if (!enabled) {
                    Toast.makeText(this, R.string.msg_filters_disabled, Toast.LENGTH_SHORT).show();
                } else {
                    if (n == 0) {
                        if (this.filterCountOnResume > 0) Toast.makeText(this, getString(R.string.msg_filters_removed), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getResources().getQuantityString(R.plurals.msg_filters_stored, n, n), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //
        FilterAdapter adapter = (FilterAdapter)this.recyclerView.getAdapter();
        MenuItem menuItemAdd = menu.findItem(R.id.action_add_filter);
        menuItemAdd.setVisible(adapter != null && !adapter.hasFilterWithNoText());
        //
        MenuItem menuItemEnable = menu.findItem(R.id.action_enable_filters);
        boolean filtersEnabled = prefs.getBoolean(App.PREF_FILTERS_APPLY, App.PREF_FILTERS_APPLY_DEFAULT);
        menuItemEnable.setTitle(filtersEnabled ? R.string.action_filters_enabled : R.string.action_filters_disabled);
        if (this.filterChanging) {
            // set the icon for the start situation (yes, red if enabled and green if disabled)
            menuItemEnable.setIcon(filtersEnabled ? R.drawable.ic_toggle_0: R.drawable.ic_toggle_9);
            // the animated drawable cannot be set here but must apparently be set via Handler.post() - reason unknown
            Drawable d = getDrawable(filtersEnabled ? R.drawable.toggling_on : R.drawable.toggling_off);
            this.handler.postDelayed(() -> {
                menuItemEnable.setIcon(d);
                if (d instanceof Animatable) ((Animatable)d).start();
            }, TOGGLE_ANIMATION_OFFSET);
            this.filterChanging = false;
            // call the else branch below after the animation is complete
            this.handler.postDelayed(this::invalidateOptionsMenu, TOGGLE_ANIMATION_OFFSET + getResources().getInteger(R.integer.toggle_animation_step) * 10L);
        } else {
            menuItemEnable.setIcon(filtersEnabled ? R.drawable.ic_toggle_9: R.drawable.ic_toggle_0);
        }
        //
        MenuItem menuItemApplyFiltersToCats = menu.findItem(R.id.action_cats_filters);
        menuItemApplyFiltersToCats.setChecked(prefs.getBoolean(App.PREF_APPLY_FILTERS_TO_CATEGORIES, App.PREF_APPLY_FILTERS_TO_CATEGORIES_DEFAULT));
        //
        return super.onPrepareOptionsMenu(menu);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        HamburgerActivity.applyTheme(this, null, false);
        @SuppressWarnings("rawtypes") RecyclerView.Adapter adapter = this.recyclerView.getAdapter();
        if (adapter != null) {
            this.filterCountOnResume = adapter.getItemCount();
        } else {
            this.filterCountOnResume = 0;
        }
        if (this.filterCountOnResume == 0) {
            this.sb = Snackbar.make(this.coordinatorLayout, R.string.hint_filter_add, Snackbar.LENGTH_INDEFINITE);
            this.sb.setActionTextColor(getResources().getColor(R.color.colorToolbarText));
            // the "+" action text corresponds to the + icon ic_add_toolbartext_24dp in the menu - they should look the same so that the snackbar text refers to both
            this.sb.setAction("+", v -> {
                if (!addFilter()) Snackbar.make(this.coordinatorLayout, R.string.error_filter_not_added, Snackbar.LENGTH_SHORT).show();
            });
            Util.setSnackbarActionFont(this.sb, Typeface.MONOSPACE, getResources().getInteger(R.integer.snackbar_action_font_size));
            this.sb.show();
        }
    }
}
