package de.freehamburger.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.IntRange;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerActivity;
import de.freehamburger.MainActivity;
import de.freehamburger.R;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.util.Log;
import de.freehamburger.views.NewsView;
import de.freehamburger.views.NewsViewNoContent;
import de.freehamburger.views.NewsViewNoContentNoTitle;

/**
 *
 */
public class NewsRecyclerAdapter extends RecyclerView.Adapter<NewsRecyclerAdapter.ViewHolder> {

    private static final String TAG = "NewsRecyclerAdapter";
    /** see <a href="https://developer.android.com/training/articles/perf-tips.html#PackageInner">https://developer.android.com/training/articles/perf-tips.html#PackageInner</a> */
    @NonNull private final List<News> newsList = new ArrayList<>(32);
    /** see <a href="https://developer.android.com/training/articles/perf-tips.html#PackageInner">https://developer.android.com/training/articles/perf-tips.html#PackageInner</a> */
    @NonNull private final List<News> filteredNews = new ArrayList<>(32);
    @NonNull private final MainActivity mainActivity;
    @NonNull private final List<Filter> filters = new ArrayList<>(4);
    @App.BackgroundSelection private final int background;
    private int contextMenuIndex = -1;
    @Nullable private Typeface typeface;
    /** original Typeface used in {@link R.id#textViewFirstSentence} before a user-supplied typeface was applied */
    @Nullable private Typeface originalTypefaceTextViewFirstSentence;

    private long updated = 0L;

    private Source source;

    /**
     * Determines the appropriate layout variant ({@link R.layout#news_view news_view}, {@link R.layout#news_view_nocontent news_view_nocontent} or {@link R.layout#news_view_nocontent_notitle news_view_nocontent_notitle})
     * depending on the content of a News object.
     * @param news News
     * @return suitable layout resource
     */
    @LayoutRes
    public static int getViewType(@Nullable final News news) {
        if (news == null) return R.layout.news_view;
        @News.NewsType String type = news.getType();
        if (News.NEWS_TYPE_VIDEO.equals(type)) {
            return R.layout.news_view_nocontent_notitle;
        }
        boolean hasTitle = !TextUtils.isEmpty(news.getTitle());
        boolean hasFirstSentence = !TextUtils.isEmpty(news.getFirstSentence());
        boolean hasShorttext= !TextUtils.isEmpty(news.getShorttext());
        boolean hasPlainText = news.getContent() != null && !TextUtils.isEmpty(news.getContent().getPlainText());
        boolean hasTextFor3rdView = hasFirstSentence || hasShorttext || hasPlainText;
        if (hasTitle && !hasTextFor3rdView) {
            return R.layout.news_view_nocontent;
        }
        if (!hasTitle && !hasTextFor3rdView) {
            return R.layout.news_view_nocontent_notitle;
        }
        return R.layout.news_view;
    }

    @VisibleForTesting
    @NonNull
    public static NewsView selectView(Context ctx, final int viewType) {
        NewsView v;
        if (viewType == R.layout.news_view_nocontent_notitle) {
            v = new NewsViewNoContentNoTitle(ctx);
        } else if (viewType == R.layout.news_view_nocontent) {
            v = new NewsViewNoContent(ctx);
        } else {
            v = new NewsView(ctx);
        }
        return v;
    }

    /**
     * Throws a RuntimeException.
     * @param ctx Context
     * @throws RuntimeException (always)
     */
    private static void throwWrongContext(Context ctx) {
        throw new RuntimeException((ctx != null ? ctx.getClass().getName() : "<null>") + " does not implement " + NewsAdapterController.class.getName());
    }

    /**
     * Constructor.
     * @param mainActivity Context <em>that implements {@link NewsAdapterController}</em>
     * @param typeface Typeface
     * @throws RuntimeException if {@code ctx} does not implement {@code NewsAdapterController}
     */
    public NewsRecyclerAdapter(@NonNull MainActivity mainActivity, @Nullable Typeface typeface) {
        super();
        this.mainActivity = mainActivity;
        this.background = HamburgerActivity.resolveBackground(mainActivity, null);
        setHasStableIds(true);
        setTypeface(typeface);
    }

    /**
     * Adds one filter.
     * @param f Filter to add
      */
    public void addFilter(@Nullable Filter f) {
        if (f == null || this.filters.contains(f)) return;
        this.filters.add(f);
        updateFilter();
    }

    /**
     * Adds some filters.
     * @param filters Filters to add
     */
    private void addFilters(@NonNull final Collection<Filter> filters) {
        boolean modified = false;
        for (Filter f : filters) {
            if (this.filters.contains(f)) continue;
            this.filters.add(f);
            modified = true;
        }
        if (modified) updateFilter();
    }

    /**
     * Applies the {@link #typeface Typeface}.
     * @param v (News-)View
     */
    private void applyTypeface(@NonNull View v) {
        TextView textViewFirstSentence = v.findViewById(R.id.textViewFirstSentence);
        if (textViewFirstSentence != null) {
            if (this.typeface != null) {
                if (this.originalTypefaceTextViewFirstSentence == null) {
                    this.originalTypefaceTextViewFirstSentence = textViewFirstSentence.getTypeface();
                }
                textViewFirstSentence.setTypeface(this.typeface);
            } else {
                textViewFirstSentence.setTypeface(this.originalTypefaceTextViewFirstSentence);
            }
        }
    }

    /**
     * Removes all temporary filters.
     */
    public void clearTemporaryFilters() {
        final Set<Filter> toRemove = new HashSet<>(1);
        for (Filter f : this.filters) {
            if (f.isTemporary()) toRemove.add(f);
        }
        if (!toRemove.isEmpty()) {
            this.filters.removeAll(toRemove);
            updateFilter();
        }
    }

    /**
     * Attempts to find a News by its {@link News#getExternalId() externalId} field.
     * @param newsExternalId externalId field of News to find
     * @return index or -1
     */
    @IntRange(from = -1)
    public int findNews(@NonNull final String newsExternalId) {
        final int n = getItemCount();
        for (int i = 0; i < n; i++) {
            if (newsExternalId.equals(getItem(i).getExternalId())) return i;
        }
        return -1;
    }

    /**
     * @return the index that the context menu will be shown for now
     */
    public int getContextMenuIndex() {
        return this.contextMenuIndex;
    }

    /**
     * Returns the News at a given position.
     * @param position news position
     * @return News
     * @throws IndexOutOfBoundsException if the position is out of range (<tt>position &lt; 0 || position &gt;= getItemCount()</tt>)
     */
    public News getItem(@IntRange(from = 0) int position) {
        return isFiltered() ? this.filteredNews.get(position) : this.newsList.get(position);
    }

    /** {@inheritDoc} */
    @Override
    public int getItemCount() {
        return isFiltered() ? this.filteredNews.size() : this.newsList.size();
    }

    /** {@inheritDoc} */
    @Override
    public long getItemId(int position) {
        return isFiltered() ? this.filteredNews.get(position).getId() : this.newsList.get(position).getId();
    }

    /** {@inheritDoc} */
    @Override
    public int getItemViewType(int position) {
        News news = isFiltered() ? this.filteredNews.get(position) : this.newsList.get(position);
        return getViewType(news);
    }

    /**
     * @return the Source that provided the data
     */
    @Nullable
    public Source getSource() {
        return this.source;
    }

    /**
     * @return timestamp when the data was set
     */
    public long getUpdated() {
        return this.updated;
    }

    /**
     * @return {@code true} if among the filters there is one that is temporary
     */
    public boolean hasTemporaryFilter() {
        for (Filter filter : this.filters) {
            if (filter.isTemporary()) return true;
        }
        return false;
    }

    /**
     * @return {@code true} if a filter has been set
     */
    private boolean isFiltered() {
        return !this.filters.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    @UiThread
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NewsView newsView = (NewsView) holder.itemView;
        newsView.setBackgroundResource(this.background == App.BACKGROUND_LIGHT ? R.drawable.bg_news_light : R.drawable.bg_news);
        applyTypeface(newsView);
        if (isFiltered()) {
            newsView.setNews(this.filteredNews.get(position), this.mainActivity.getHamburgerService());
        } else {
            newsView.setNews(this.newsList.get(position), this.mainActivity.getHamburgerService());
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, final int viewType) {
        return new ViewHolder(selectView(this.mainActivity, viewType));
    }

    /**
     * Sets the Filters.
     * @param filters Filters to set
     * @return {@code true} if among the filters there is one that is temporary
     */
    public boolean setFilters(@Nullable final Collection<Filter> filters) {
        boolean justClearAllFilters = filters == null || filters.isEmpty();
        if (justClearAllFilters && this.filters.isEmpty()) return false;
        this.filters.clear();
        if (justClearAllFilters) {
            updateFilter();
            return false;
        }
        addFilters(filters);
        return hasTemporaryFilter();
    }

    public void setNewsList(@Nullable final Collection<News> newsList, @NonNull Source source) {
        this.source = source;
        if (this.newsList.isEmpty() && (newsList == null || newsList.isEmpty())) {
            return;
        }
        this.updated = System.currentTimeMillis();
        this.newsList.clear();
        if (newsList != null) {
            this.newsList.addAll(newsList);
        }
        updateFilter();
    }

    public void setTypeface(@Nullable Typeface typeface) {
        boolean changed = (typeface != null && !typeface.equals(this.typeface)) || (typeface == null && this.typeface != null);
        this.typeface = typeface;
        if (changed) notifyDataSetChanged();
    }

    private void updateFilter() {
        this.filteredNews.clear();
        if (!isFiltered()) {
            notifyDataSetChanged();
            return;
        }
        for (News news : this.newsList) {
            boolean rejected = false;
            for (Filter filter : this.filters) {
                if (!filter.accept(news)) {
                    rejected = true;
                    //if (BuildConfig.DEBUG) Log.w(TAG, "Rejected \"" + news.getTitle() + "\" by " + filter);
                    break;
                }
            }
            if (!rejected) this.filteredNews.add(news);
        }
        notifyDataSetChanged();
    }

    public interface NewsAdapterController {

        /**
         * @return NewsRecyclerAdapter
         */
        NewsRecyclerAdapter getAdapter();

        MenuInflater getMenuInflater();

        /**
         * The user has clicked one of the news.
         * @param news News
         */
        void onNewsClicked(@NonNull News news, View v, float x, float y);
    }

    /**
     *
     */
    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener {

        private float xPosOfEventActionUp, yPosOfEventActionUp;

        /**
         * Constructor.
         * @param v View
         */
        @SuppressLint("ClickableViewAccessibility")
        private ViewHolder(@NonNull View v) {
            super(v);
            this.itemView.setOnClickListener(this);
            this.itemView.setOnCreateContextMenuListener(this);
            // record x and y coordinates
            this.itemView.setOnTouchListener((v1, event) -> {
                if (event.getAction() != MotionEvent.ACTION_UP) return false;
                ViewHolder.this.xPosOfEventActionUp = event.getX();
                ViewHolder.this.yPosOfEventActionUp = event.getY();
                return false;
            });
        }

        /**
         * {@inheritDoc}
         * @throws RuntimeException if the View's Context does not implement {@link NewsAdapterController}
         */
        @Override
        public void onClick(View v) {
            Context ctx = v.getContext();
            if (!(ctx instanceof NewsAdapterController)) {
                throwWrongContext(ctx);
            }
            NewsAdapterController newsAdapterController = (NewsAdapterController)ctx;
            NewsRecyclerAdapter adapter = newsAdapterController.getAdapter();
            int position = getAdapterPosition();
            try {
                News news = adapter.isFiltered() ? adapter.filteredNews.get(position) : adapter.newsList.get(position);

                if (news != null) newsAdapterController.onNewsClicked(news, v, xPosOfEventActionUp, yPosOfEventActionUp);
            } catch (IndexOutOfBoundsException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Click on " + v + " @ position " + position + " -> " + e.toString());
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            Context ctx = v.getContext();
            if (!(ctx instanceof NewsAdapterController)) {
                throwWrongContext(ctx);
            }
            int position = getAdapterPosition();
            if (position < 0) return;
            NewsAdapterController ma = (NewsAdapterController) ctx;
            ma.getMenuInflater().inflate(R.menu.list_item_menu, menu);
            NewsRecyclerAdapter adapter = ma.getAdapter();
            adapter.contextMenuIndex = position;
            //
            News news = adapter.getItem(position);
            MenuItem menuItemShareNews = menu.findItem(R.id.action_share_news);
            menuItemShareNews.setEnabled(news != null && !TextUtils.isEmpty(news.getDetailsWeb()));
            MenuItem menuItemShareVideo = menu.findItem(R.id.action_share_video);
            menuItemShareVideo.setEnabled(news != null && news.getContent() != null && news.getContent().hasVideo());
            boolean hasImage = news != null && news.getTeaserImage() != null && news.getTeaserImage().hasImage();
            MenuItem menuItemShareImage = menu.findItem(R.id.action_share_image);
            menuItemShareImage.setEnabled(hasImage);
            MenuItem menuItemViewImage = menu.findItem(R.id.action_view_picture);
            menuItemViewImage.setEnabled(hasImage);
        }
    }
}
