package de.freehamburger.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import androidx.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import de.freehamburger.Archive;
import de.freehamburger.BuildConfig;
import de.freehamburger.App;
import de.freehamburger.R;
import de.freehamburger.StyledActivity;
import de.freehamburger.model.ArchivedNews;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class ArchivedNewsAdapter extends StyledActivity.StyledAdapter<ArchivedNewsAdapter.ViewHolder> {

    private static final String TAG = "ArchivedNewsAdapter";
    private final List<ArchivedNews> list = new ArrayList<>();
    private DateFormat df;
    private SharedPreferences prefs;
    @Nullable private Typeface typeface;

    /**
     * Constructor.
     */
    public ArchivedNewsAdapter() {
        super();
        setHasStableIds(true);
    }

    /** {@inheritDoc} */
    @Override public int getItemCount() {
        return this.list.size();
    }

    /** {@inheritDoc} */
    @Override public long getItemId(int position) {
        return this.list.get(position).hashCode();
    }

    /** {@inheritDoc} */
    @Override public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        ArchivedNews an = this.list.get(position);

        super.onBindViewHolder(holder, position);

        if (an == null) {
            holder.textViewDate.setText(null);
            holder.textViewTitle.setText(null);
            holder.buttonDelete.setVisibility(View.GONE);
            return;
        }
        final Context ctx = holder.itemView.getContext();
        final Resources res = ctx.getResources();
        if (this.prefs == null) this.prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        boolean timeMode = this.prefs.getBoolean(App.PREF_TIME_MODE_RELATIVE, App.PREF_TIME_MODE_RELATIVE_DEFAULT);
        if (timeMode) {
            holder.textViewDate.setText(Util.getRelativeTime(ctx, an.getTimestamp(), null));
        } else {
            if (this.df == null) this.df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            holder.textViewDate.setText(this.df.format(new Date(an.getTimestamp())));
        }

        holder.textViewTitle.setText(an.getDisplayName());
        holder.textViewTitle.setSelected(true);

        holder.buttonDelete.setVisibility(View.VISIBLE);

        // apply the desired typeface magnification
        @FloatRange(from = 0.5, to = 2.0)
        final float zoom = this.prefs.getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT) / 100f;
        holder.textViewDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_archive_date) * zoom);
        holder.textViewTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_archive_title) * zoom);
    }

    /** {@inheritDoc} */
    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewGroup v = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.archived_news, parent, false);
        return new ViewHolder(v, this.typeface);
    }

    /**
     * Removes the item at the given index.
     * @param index index
     */
    public void remove(@IntRange(from = 0) int index) {
        this.list.remove(index);
    }

    @SuppressLint("NotifyDataSetChanged")
    @UiThread
    public void setItems(@Nullable Collection<ArchivedNews> archivedNews) {
        this.list.clear();
        if (archivedNews != null) this.list.addAll(archivedNews);
        notifyDataSetChanged();
    }

    /**
     * Sets the Typeface to use.
     * @param typeface Typeface
     */
    public void setTypeface(@Nullable Typeface typeface) {
        this.typeface = typeface;
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView textViewDate;
        private final TextView textViewTitle;
        private final ImageButton buttonDelete;

        /**
         * Constructor.
         * @param v View
         * @param typeface Typeface to use
         */
        private ViewHolder(@NonNull final View v, @Nullable final Typeface typeface) {
            super(v);
            this.textViewDate = v.findViewById(R.id.textViewDate);
            this.textViewTitle = v.findViewById(R.id.textViewTitle);
            this.buttonDelete = v.findViewById(R.id.buttonDelete);
            v.setOnClickListener(this);
            this.buttonDelete.setOnClickListener(this);
            if (typeface != null) {
                this.textViewDate.setTypeface(typeface);
                this.textViewTitle.setTypeface(typeface);
            }
        }

        /** {@inheritDoc} */
        @Override public void onClick(final View v) {
            final ArchivedNewsAdapter adapter = (ArchivedNewsAdapter) getBindingAdapter();
            if (adapter == null) return;
            final int position = getBindingAdapterPosition();
            if (position < 0 || position >= adapter.getItemCount()) return;
            final Context ctx = v.getContext();
            if (!(ctx instanceof Archive)) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Bad Context!");
                return;
            }
            Archive archive = (Archive)ctx;
            final ArchivedNews archivedNews = adapter.list.get(position);
            if (!archivedNews.getFile().isFile()) {
                Util.makeSnackbar(archive, R.string.error_archived_retrieval, Snackbar.LENGTH_SHORT).show();
                return;
            }
            if (v == this.buttonDelete) {
                archive.deleteWithConfirmation(archivedNews, position);
            } else {
                archive.showArchivedNews(archivedNews, v);
            }
        }
    }
}
