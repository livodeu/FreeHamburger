package de.freehamburger.adapters;

import android.content.Context;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import de.freehamburger.HamburgerActivity;
import de.freehamburger.model.Related;
import de.freehamburger.views.RelatedView;
import de.freehamburger.views.RelatedViewVertical;

/**
 *
 */
public class RelatedAdapter extends RecyclerView.Adapter<RelatedAdapter.ViewHolder> {

    @NonNull private final HamburgerActivity activity;
    private Related[] related;
    private RecyclerView rv;

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
        this.rv = recyclerView;
    }

    /** {@inheritDoc} */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        this.rv = null;
    }

    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int i) {
        RelatedView relatedView = (RelatedView) holder.itemView;
        relatedView.setRelated(this.related[i]);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        if (this.rv != null) {
            RecyclerView.LayoutManager lm = this.rv.getLayoutManager();
            if (lm instanceof GridLayoutManager || lm instanceof StaggeredGridLayoutManager) {
                return new RelatedAdapter.ViewHolder(new RelatedViewVertical(this.activity));
            }
        }
        return new RelatedAdapter.ViewHolder(new RelatedView(this.activity));
    }

    /**
     * Sets the data.
     * @param related List of Related objects
     */
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
            if (ctx instanceof OnRelatedClickListener) {
                int position = getAdapterPosition();
                ((OnRelatedClickListener)ctx).onRelatedClicked(position);
            }
        }
    }

    public interface OnRelatedClickListener {
        /**
         * The user has tapped one {@link Related Related} item.
         * @param index index of the item (look at the adapter to find it)
         */
        void onRelatedClicked(int index);
    }
}