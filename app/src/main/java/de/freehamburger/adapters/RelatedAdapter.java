package de.freehamburger.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.List;

import de.freehamburger.HamburgerActivity;
import de.freehamburger.R;
import de.freehamburger.model.Related;
import de.freehamburger.views.RelatedView;

/**
 *
 */
public class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.ViewHolder> {

    @NonNull private final HamburgerActivity activity;
    private Related[] related;
    private RecyclerView recyclerView;

    /**
     * Constructor.
     * @param activity HamburgerActivity
     */
    public RelatedAdapter(@NonNull HamburgerActivity activity) {
        super();
        this.activity = activity;
        setHasStableIds(true);
    }

    /**
     * Returns the Related object at the given index.
     * @param position index
     * @return Related
     */
    @Nullable
    public Related getRelated(@IntRange(from = 0) int position) {
        return this.related != null && position >= 0 && position < this.related.length ? this.related[position] : null;
    }

    /** {@inheritDoc} */
    @Override
    public int getItemCount() {
        return this.related != null ? this.related.length : 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /** {@inheritDoc} */
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    /** {@inheritDoc} */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = null;
    }

    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RelatedView relatedView = (RelatedView) holder.itemView;
        relatedView.setRelated(this.related[position]);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (this.recyclerView != null) {
            RecyclerView.LayoutManager lm = this.recyclerView.getLayoutManager();
            if (lm instanceof GridLayoutManager || lm instanceof StaggeredGridLayoutManager) {
                RelatedView rv = (RelatedView) LayoutInflater.from(this.activity).inflate(R.layout.related_view_vert, parent, false);
                rv.init();
                return new RelatedAdapter.ViewHolder(rv);
            }
        }
        RelatedView rv = (RelatedView) LayoutInflater.from(this.activity).inflate(R.layout.related_view, parent, false);
        rv.init();
        return new RelatedAdapter.ViewHolder(rv);
    }

    /**
     * Sets the data.
     * @param related List of Related objects
     */
    @SuppressLint("NotifyDataSetChanged")
    public void setRelated(@Nullable List<Related> related) {
        if (related == null) {
            this.related = null;
        } else {
            this.related = new Related[related.size()];
            related.toArray(this.related);
        }
        notifyDataSetChanged();
    }

    /**
     *
     */
    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        /** {@inheritDoc} */
        private ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView.setOnClickListener(this);
        }

        /** {@inheritDoc} */
        @Override
        public void onClick(View v) {
            Context ctx = v.getContext();
            if (!(ctx instanceof OnRelatedClickListener)) return;
            int position = getBindingAdapterPosition();
            ((OnRelatedClickListener)ctx).onRelatedClicked(position);
        }
    }

    @FunctionalInterface
    public interface OnRelatedClickListener {
        /**
         * The user has tapped one {@link Related Related} item.
         * @param index index of the item (look at the adapter to find it)
         */
        void onRelatedClicked(int index);
    }
}