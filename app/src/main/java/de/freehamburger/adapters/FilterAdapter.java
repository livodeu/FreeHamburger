package de.freehamburger.adapters;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import de.freehamburger.App;
import de.freehamburger.R;
import de.freehamburger.model.Filter;
import de.freehamburger.model.TextFilter;
import de.freehamburger.views.FilterView;

/**
 *
 */
public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {

    private final List<Filter> filters = new ArrayList<>(4);
    @NonNull private final Context context;
    @App.BackgroundSelection private int background;
    @Nullable private Filter focusMe;

    /**
     * Constructor.
     * @param ctx Context
     */
    public FilterAdapter(@NonNull Context ctx) {
        super();
        this.context = ctx;
    }

    /**
     * Adds a filter.
     * @param filter Filter to add
     * @return {@code true} if the filter has been added
     */
    public boolean addFilter(@Nullable Filter filter) {
        if (filter == null || this.filters.contains(filter)) return false;
        this.focusMe = filter;
        this.filters.add(filter);
        notifyDataSetChanged();
        return true;
    }

    @NonNull
    public List<Filter> getFilters() {
        return this.filters;
    }

    /** {@inheritDoc} */
    @Override
    public int getItemCount() {
        return this.filters.size();
    }

    /**
     * @return {@code true} if at least one of the filters has got no text
     */
    public boolean hasFilterWithNoText() {
        for (Filter f : this.filters) {
            String s = f.getText().toString().trim();
            if (s.length() == 0) return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterView filterView = (FilterView) holder.itemView;
        filterView.setBackgroundResource(this.background == App.BACKGROUND_LIGHT ? R.drawable.bg_news_light : R.drawable.bg_news);
        Filter filter = this.filters.get(position);
        filterView.setFilter(filter);
        filterView.setListener(new FilterView.Listener() {
            @Override
            public void anywhere() {
                int pos = holder.getBindingAdapterPosition();
                Filter filter = FilterAdapter.this.filters.get(pos);
                if (!(filter instanceof TextFilter)) return;
                ((TextFilter)filter).setAtStartAndAtAend(false, false);
            }

            @Override
            public void atEnd() {
                int pos = holder.getBindingAdapterPosition();
                Filter filter = FilterAdapter.this.filters.get(pos);
                if (!(filter instanceof TextFilter)) return;
                ((TextFilter)filter).setAtStartAndAtAend(false, true);
            }

            @Override
            public void atStart() {
                int pos = holder.getBindingAdapterPosition();
                Filter filter = FilterAdapter.this.filters.get(pos);
                if (!(filter instanceof TextFilter)) return;
                ((TextFilter)filter).setAtStartAndAtAend(true, false);
            }

            @Override
            public void textChanged(Editable s) {
                int pos = holder.getBindingAdapterPosition();
                Filter filter = filters.get(pos);
                if (!(filter instanceof TextFilter)) return;
                ((TextFilter)filter).setPhrase(s);
                Context ctx = holder.itemView.getContext();
                if (ctx instanceof Activity) ((Activity)ctx).invalidateOptionsMenu();
              }
        });
        if (filter.equals(this.focusMe)) {
            filterView.focusEditText();
            this.focusMe = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = new FilterView(this.context);
        return new FilterAdapter.ViewHolder(v);
    }

    public void removeFilter(@IntRange(from = 0) int position) {
        if (position < 0 || position >= this.filters.size()) return;
        this.filters.remove(position);
        notifyItemRemoved(position);
    }

    public void setBackground(@App.BackgroundSelection int background) {
        this.background = background;
    }

    public void setFilters(@Nullable List<Filter> filters) {
        this.filters.clear();
        if (filters != null) {
            this.filters.addAll(filters);
        }
        notifyDataSetChanged();
    }

    /**
     *
     */
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        /**
         * Constructor.
         * @param v View
         */
        private ViewHolder(@NonNull View v) {
            super(v);
            this.itemView.setOnClickListener(this);
        }

        /** {@inheritDoc} */
        @Override
        public void onClick(View v) {
            ((FilterView)this.itemView).focusEditText();
        }
    }

}
