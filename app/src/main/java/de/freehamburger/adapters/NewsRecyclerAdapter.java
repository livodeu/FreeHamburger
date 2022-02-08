package de.freehamburger.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
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

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;
import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerService;
import de.freehamburger.NewsAdapterActivity;
import de.freehamburger.R;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;
import de.freehamburger.views.NewsView;
import de.freehamburger.views.NewsViewNoContent;
import de.freehamburger.views.NewsViewNoContentNoTitle;

/**
 * The adapter for the RecyclerView that contains the news list in the {@link de.freehamburger.MainActivity main activity}.
 */
public class NewsRecyclerAdapter extends RecyclerView.Adapter<NewsRecyclerAdapter.ViewHolder> implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "NewsRecyclerAdapter";
    @NonNull private final List<News> newsList = new ArrayList<>(32);
    @NonNull private final List<News> filteredNews = new ArrayList<>(32);
    @NonNull private final NewsAdapterActivity activity;
    @NonNull private final List<Filter> filters = new ArrayList<>(4);
    private final Handler handler = new Handler();
    /** a NewsView instance for each view type; used to preload News items via {@link #preloader} */
    private final SparseArray<NewsView> dummyNewsViews = new SparseArray<>(3);
    /** a ViewHolder instance for each view type */
    private final SparseArray<ViewHolder> viewholderCache = new SparseArray<>(3);
    private final Preloader preloader = new Preloader();
    @App.BackgroundSelection private int background;
    private boolean filtersEnabled;
    private Thread viewholderCreator;
    private int contextMenuIndex = -1;
    @Nullable private Typeface typeface;
    private long updated = 0L;
    private Source source;

    /**
     * Determines the appropriate layout variant,
     * <ul>
     *     <li>{@link R.layout#news_view news_view},
     *     <li>{@link R.layout#news_view_nocontent news_view_nocontent} or
     *     <li>{@link R.layout#news_view_nocontent_notitle news_view_nocontent_notitle},
     * </ul>
     * depending on the content of a News object.
     * @param news News
     * @return suitable layout resource
     */
    @LayoutRes
    public static int getViewType(@Nullable final News news) {
        if (news == null) return R.layout.news_view;
        if (News.NEWS_TYPE_VIDEO.equals(news.getType())) {
            return R.layout.news_view_nocontent_notitle;
        }
        if (news.hasTextForTextViewFirstSentence()) {
            return R.layout.news_view;
        }
        if (news.hasTextForTextViewTitle()) {
            return R.layout.news_view_nocontent;
        }
        return R.layout.news_view_nocontent_notitle;
    }

    /**
     * Checks whether the service's memory cache contains bitmaps for all the <em>visible</em> {@link NewsView views}.
     * @param activity NewsAdapterActivity
     * @return {@code true} if there is at least one missing cached image for a news item
     */
    public static boolean hasMissingImages(@NonNull NewsAdapterActivity activity) {
        HamburgerService service = activity.getHamburgerService();
        if (service == null) return true;
        // R.dimen.image_width_normal should have been given as android:maxWidth in the NewsView layout file news_view.xml
        final int imgWidth = service.getResources().getDimensionPixelSize(R.dimen.image_width_normal);
        final Set<View> kids = activity.getVisibleNewsViews();
        for (View kid : kids) {
            if (!(kid instanceof NewsView)) continue;
            NewsView nv = (NewsView)kid;
            String imageUrl = nv.getImageUrl();
            if (imageUrl == null) continue;
            if (service.getCachedBitmap(imageUrl) == null) {
                if (BuildConfig.DEBUG) Log.i(TAG, "Missing cached image for " + nv);
                return true;
            }
        }
        return false;
    }

    /**
     * Instantiates a NewsView based on the given layout.
     * @param ctx Context
     * @param viewType view layout resource
     * @return NewsView instance
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @VisibleForTesting
    @NonNull
    public static NewsView instantiateView(@NonNull Context ctx, @LayoutRes final int viewType) {
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
     * Constructor.
     * @param activity NewsAdapterActivity
     * @param typeface Typeface (optional)
     */
    public NewsRecyclerAdapter(@NonNull NewsAdapterActivity activity, @Nullable Typeface typeface) {
        super();
        this.activity = activity;
        this.background = activity.getBackground();
        this.filtersEnabled = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(App.PREF_FILTERS_APPLY, true);
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
     * Applies the {@link #typeface Typeface} to the given TextViews.
     * @param tvs TextViews (may contain null elements)
     */
    private void applyTypeface(@NonNull final TextView... tvs) {
        if (this.typeface != null) {
            for (TextView tv : tvs) {
                if (tv == null) continue;
                if (tv.getTag(R.id.original_typeface) == null) tv.setTag(R.id.original_typeface, tv.getTypeface());
                tv.setTypeface(this.typeface);
            }
        } else {
            for (TextView tv : tvs) {
                if (tv == null) continue;
                Typeface typeface = (Typeface)tv.getTag(R.id.original_typeface);
                if (typeface != null) tv.setTypeface(typeface);
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
        return this.filtersEnabled && !this.filters.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        PreferenceManager.getDefaultSharedPreferences(this.activity).registerOnSharedPreferenceChangeListener(this);
    }

    /** {@inheritDoc} */
    @Override
    @UiThread
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        this.handler.removeCallbacks(this.preloader);
        final NewsView newsView = (NewsView) holder.itemView;
        switch (this.background) {
            case App.BACKGROUND_NIGHT: newsView.setBackgroundResource(R.drawable.bg_news); break;
            case App.BACKGROUND_DAY: newsView.setBackgroundResource(R.drawable.bg_news_light); break;
            case App.BACKGROUND_AUTO: newsView.setBackgroundResource(Util.isNightMode(this.activity) ? R.drawable.bg_news : R.drawable.bg_news_light);
        }

        final Resources res = this.activity.getResources();

        // apply the desired typeface magnification
        @FloatRange(from = 0.5, to = 2.0)
        final float zoom = PreferenceManager.getDefaultSharedPreferences(this.activity).getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT) / 100f;
        TextView tvtl = newsView.getTextViewTopline();
        TextView tvda = newsView.getTextViewDate();
        TextView tvti = newsView.getTextViewTitle();
        TextView tvfs = newsView.getTextViewFirstSentence();
        if (tvtl != null) tvtl.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_topline) * zoom);
        if (tvda != null) tvda.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_date) * zoom);
        if (tvti != null) tvti.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_title) * zoom);
        if (tvfs != null) tvfs.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_firstsentence) * zoom);

        // apply the user-defined typeface to the NewsView
        applyTypeface(tvtl, tvda, tvti, tvfs);

        HamburgerService service = this.activity.getHamburgerService();
        if (isFiltered()) {
            newsView.setNews(this.filteredNews.get(position), service);
        } else {
            newsView.setNews(this.newsList.get(position), service);
        }
        // if the position is not at the end, load image at the next position into the cache (by applying the next News item to a dummy NewsView)
        if (position < getItemCount() - 1) {
            this.preloader.setPosition(position + 1);
            this.handler.postDelayed(this.preloader, 500L);
        }
    }

    /** {@inheritDoc}
     *  Returns a {@link ViewHolder} for the given view type.<br>
     *  Attempts to get one from the {@link #viewholderCache cache}; if that fails, creates a new one.<br>
     *  Finally creates another ViewHolder of the same type and puts in the cache (to be used during the next invocation of this method).<br>
     *  On a low-end (SD410) device, this method takes usually less than a ms if the ViewHolder can be retrieved from the cache and about 10 ms if not.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, final int viewType) {
        ViewHolder vh;
        synchronized (this.viewholderCache) {
            vh = this.viewholderCache.get(viewType);
        }
        if (vh == null) {
            vh = new ViewHolder(instantiateView(this.activity, viewType));
        } else {
            // remove the ViewHolder from the viewholderCache that we are about to return
            synchronized (this.viewholderCache) {
                this.viewholderCache.delete(viewType);
            }
        }
        // create another ViewHolder of the same type and store it in the viewholderCache
        if (this.viewholderCreator == null || !this.viewholderCreator.isAlive()) {
            this.viewholderCreator = new Thread() {
                @Override
                public void run() {
                    ViewHolder newOne = new ViewHolder(instantiateView(NewsRecyclerAdapter.this.activity, viewType));
                    synchronized (NewsRecyclerAdapter.this.viewholderCache) {
                        NewsRecyclerAdapter.this.viewholderCache.put(viewType, newOne);
                    }
                }
            };
            this.viewholderCreator.setPriority(Thread.NORM_PRIORITY - 1);
            this.viewholderCreator.start();
        }
        //
        return vh;
    }

    /** {@inheritDoc} */
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        PreferenceManager.getDefaultSharedPreferences(this.activity).unregisterOnSharedPreferenceChangeListener(this);
    }

    /** {@inheritDoc} */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_FILTERS_APPLY.equals(key)) {
            this.filtersEnabled = prefs.getBoolean(key, true);
        } else if (App.PREF_BACKGROUND.equals(key)) {
            // The activity receives the same info (HamburgerActivity.onSharedPreferenceChanged()) and sets its background accordingly; give it a moment to do so
            this.handler.postDelayed(() -> {
                NewsRecyclerAdapter.this.background = NewsRecyclerAdapter.this.activity.getBackground();
                notifyDataSetChanged();
            }, 500L);
        } else if (App.PREF_FONT_ZOOM.equals(key)) {
            notifyDataSetChanged();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        ((NewsView)holder.itemView).setNews(null, null);
    }

    /**
     * Attempts to apply the News at the given position to a dummy NewsView
     * in order to load the News' image into the cache.
     * @param position index
     * @param service HamburgerService
     */
    private void preload(int position, @Nullable HamburgerService service) {
        if (service == null) return;
        try {
            News follower = isFiltered() ? this.filteredNews.get(position) : this.newsList.get(position);
            int viewType = getViewType(follower);
            NewsView dummyNewsView = this.dummyNewsViews.get(viewType);
            if (dummyNewsView == null) {
                dummyNewsView = instantiateView(this.activity, viewType);
                this.dummyNewsViews.put(viewType, dummyNewsView);
            }
            dummyNewsView.setNews(follower, service);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "While trying to preload for pos. " + position + ": " + e);
        }
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

    /**
     * Sets the list of News items.<br>
     * <em>The News should be sorted because that is not done here!</em>
     * @param newsList News objects
     * @param source the News source
     */
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

    /**
     * Sets the Typeface.
     * @param typeface Typeface
     */
    public void setTypeface(@Nullable Typeface typeface) {
        boolean changed = (typeface != null && !typeface.equals(this.typeface)) || (typeface == null && this.typeface != null);
        this.typeface = typeface;
        if (changed) notifyDataSetChanged();
    }

    /**
     * Updates the {@link #filteredNews filtered news list} and calls {@link #notifyDataSetChanged()}.
     */
    private void updateFilter() {
        final List<News> oldFilteredList = isFiltered() ? new ArrayList<>(this.filteredNews) : null;
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
                    break;
                }
            }
            if (!rejected) this.filteredNews.add(news);
        }
        if (this.filteredNews.equals(oldFilteredList)) {
            return;
        }
        notifyDataSetChanged();
    }

    /**
     * To be implemented by activities that use this adapter.
     */
    public interface NewsAdapterController {

        /**
         * @return NewsRecyclerAdapter
         */
        @NonNull
        NewsRecyclerAdapter getAdapter();

        /**
         * Available (@NonNull) in {@link android.app.Activity}.
         * @return MenuInflater
         */
        MenuInflater getMenuInflater();

        /**
         * Returns the {@link NewsView news views} that are partly or completely visible.
         */
        @NonNull
        Set<View> getVisibleNewsViews();

        /**
         * The user has tapped one of the news.
         * @param news News
         * @param v the view that has been tapped
         * @param x the x coord of the event
         * @param y the y coord of the event
         */
        void onNewsClicked(@NonNull News news, @NonNull View v, float x, float y);
    }

    /**
     *
     */
    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener {

        private float xPosOfEventActionUp, yPosOfEventActionUp;

        /**
         * Throws a RuntimeException.
         * @param ctx Context
         * @throws RuntimeException (always)
         */
        private static void throwWrongContext(@Nullable Context ctx) {
            throw new RuntimeException((ctx != null ? ctx.getClass().getName() : "<null>") + " does not implement " + NewsAdapterController.class.getName());
        }

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
            if (!(ctx instanceof NewsAdapterController)) throwWrongContext(ctx);
            NewsAdapterController newsAdapterController = (NewsAdapterController)ctx;
            NewsRecyclerAdapter adapter = newsAdapterController.getAdapter();
            int position = getBindingAdapterPosition();
            try {
                News news = adapter.isFiltered() ? adapter.filteredNews.get(position) : adapter.newsList.get(position);
                if (news != null) newsAdapterController.onNewsClicked(news, v, this.xPosOfEventActionUp, this.yPosOfEventActionUp);
            } catch (IndexOutOfBoundsException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Click on " + v + " @ position " + position + " -> " + e);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            Context ctx = v.getContext();
            if (!(ctx instanceof NewsAdapterController)) throwWrongContext(ctx);
            int position = getBindingAdapterPosition();
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

    /**
     * Calls {@link #preload(int, HamburgerService)}.
     */
    private class Preloader implements Runnable {

        private int position;

        private Preloader() {
            super();
        }

        @Override
        public void run() {
            preload(this.position, NewsRecyclerAdapter.this.activity.getHamburgerService());
        }

        private void setPosition(int p) {
            this.position = p;
        }
    }
}
