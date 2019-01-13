package de.freehamburger;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.freehamburger.adapters.FilterAdapter;
import de.freehamburger.model.Filter;
import de.freehamburger.model.TextFilter;
import de.freehamburger.util.CoordinatorLayoutHolder;
import de.freehamburger.views.FilterView;

public class FilterActivity extends AppCompatActivity implements CoordinatorLayoutHolder {

    private final Handler handler = new Handler();
    private CoordinatorLayout coordinatorLayout;
    private RecyclerView recyclerView;
    /** number of filters when this activity starts */
    private int filterCountOnResume;

    /**
     * Adds a filter
     * @return {@code true} if the filter has been added
     */
    private boolean addFilter() {
        FilterAdapter adapter = (FilterAdapter)this.recyclerView.getAdapter();
        if (adapter == null) return false;
        Filter filter = new TextFilter("");
        boolean added = adapter.addFilter(filter);
        invalidateOptionsMenu();
        if (added) {
            this.handler.postDelayed(() -> FilterActivity.this.recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1), 500);
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

    /**
     * The clear/delete button has been clicked.
     * @param button the 'clear/delete' button next to the filter phrase
     */
    public void onDeleteClicked(View button) {
        ViewParent parent = button.getParent().getParent();
        if (!(parent instanceof FilterView)) return;
        FilterView filterView = (FilterView)parent;
        EditText editTextPhrase = filterView.findViewById(R.id.editTextPhrase);
        if (editTextPhrase != null && !TextUtils.isEmpty(editTextPhrase.getText())) {
            editTextPhrase.setText(null);
            invalidateOptionsMenu();
            return;
        }
        int pos = this.recyclerView.getChildAdapterPosition(filterView);
        if (pos == RecyclerView.NO_POSITION) return;
        FilterAdapter filterAdapter = (FilterAdapter)this.recyclerView.getAdapter();
        if (filterAdapter != null) filterAdapter.removeFilter(pos);
        invalidateOptionsMenu();
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
                    preferredFilters.add('[' + s.toLowerCase(Locale.GERMAN));
                } else if (((TextFilter) filter).isAtEnd()) {
                    preferredFilters.add(']' + s.toLowerCase(Locale.GERMAN));
                } else {
                    preferredFilters.add(s.toLowerCase(Locale.GERMAN));
                }
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor ed = prefs.edit();
            int n = preferredFilters.size();
            if (n == 0) {
                ed.remove(App.PREF_FILTERS);
            } else {
                ed.putStringSet(App.PREF_FILTERS, preferredFilters);
            }
            ed.apply();
            if (n == 0) {
                if (this.filterCountOnResume > 0) Toast.makeText(getApplicationContext(), getString(R.string.msg_filters_removed), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), getResources().getQuantityString(R.plurals.msg_filters_stored, n, n), Toast.LENGTH_SHORT).show();
            }
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter_menu, menu);
        menu.setQwertyMode(true);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        FilterAdapter adapter = (FilterAdapter)this.recyclerView.getAdapter();
        MenuItem menuItemAdd = menu.findItem(R.id.action_add_filter);
        menuItemAdd.setVisible(adapter != null && !adapter.hasFilterWithNoText());
        return super.onPrepareOptionsMenu(menu);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_filter) {
            if (!addFilter()) {
                Snackbar.make(this.coordinatorLayout, R.string.error_filter_not_added, Snackbar.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        @App.BackgroundSelection int background = HamburgerActivity.pickTheme(this, null, false);
        this.coordinatorLayout.setBackgroundResource(background == App.BACKGROUND_LIGHT ? R.drawable.bg_news_light :R.drawable.bg_news);
        RecyclerView.Adapter adapter = this.recyclerView.getAdapter();
        this.filterCountOnResume = adapter != null ? adapter.getItemCount() : 0;
    }
}
