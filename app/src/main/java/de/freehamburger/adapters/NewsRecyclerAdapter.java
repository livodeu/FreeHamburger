package de.freehamburger.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Size;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiContext;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerService;
import de.freehamburger.NewsAdapterActivity;
import de.freehamburger.R;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.util.Log;
import de.freehamburger.views.NewsView2;

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
    private final SparseArray<NewsView2> dummyNewsViews = new SparseArray<>(2);
    /** a ViewHolder instance for each view type */
    private final SparseArray<ViewHolder> viewholderCache = new SparseArray<>(2);
    private final SharedPreferences prefs;
    private boolean preloading = false;
    @App.BackgroundSelection private int background;
    private boolean filtersEnabled;
    @FloatRange(from = 0.5, to = 2.0)
    private float zoom;
    private boolean zoomModified;
    private Thread viewholderCreator;
    private int contextMenuIndex = -1;
    @Nullable private Typeface typeface;
    private long updated = 0L;
    private Source source;

    /**
     * Constructor.
     * @param activity NewsAdapterActivity
     * @param typeface Typeface (optional)
     */
    public NewsRecyclerAdapter(@NonNull NewsAdapterActivity activity, @Nullable Typeface typeface) {
        super();
        this.activity = activity;
        this.background = activity.getBackground();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        this.filtersEnabled = this.prefs.getBoolean(App.PREF_FILTERS_APPLY, App.PREF_FILTERS_APPLY_DEFAULT);
        this.zoom = this.prefs.getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT) / 100f;
        this.zoomModified = Math.abs(this.zoom - 1f) > 0.001;
        setHasStableIds(true);
        setTypeface(typeface);
    }

    /**
     * Determines the appropriate layout variant,
     * depending on the content of a News object.
     * @param news News
     * @return suitable layout resource
     */
    @LayoutRes
    public static int getViewType(@Nullable final News news) {
        if (news == null) return R.layout.news_view2;
        if (News.NEWS_TYPE_VIDEO.equals(news.getType())) {
            return R.layout.news_view_nocontent_notitle2;
        }
        return R.layout.news_view2;
    }

    /**
     * Instantiates a NewsView based on the given layout.
     * @param activity Activity
     * @param parent ViewGroup
     * @param viewType view layout resource
     * @return NewsView instance
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    @VisibleForTesting
    @NonNull
    public static NewsView2 instantiateView(@UiContext @NonNull Context activity, @Nullable ViewGroup parent, @LayoutRes int viewType) {
        NewsView2 v = (NewsView2)LayoutInflater.from(activity).inflate(viewType, parent, false);
        v.init();
        return v;
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
     * @throws NullPointerException if {@code newsExternalId} is {@code null}
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
    public synchronized int getItemCount() {
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
     * @return timestamp telling when the data was set
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
        this.prefs.registerOnSharedPreferenceChangeListener(this);
    }

    /** {@inheritDoc} */
    @Override
    @UiThread
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final NewsView2 newsView2 = (NewsView2) holder.itemView;

        TextView tvtl = newsView2.getTextViewTopline();
        TextView tvda = newsView2.getTextViewDate();
        TextView tvti = newsView2.getTextViewTitle();
        TextView tvfs = newsView2.getTextViewFirstSentence();

        if (this.zoomModified) {
            // apply the desired typeface magnification
            final Resources res = this.activity.getResources();
            tvtl.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_topline) * this.zoom);
            tvda.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_date) * this.zoom);
            if (tvti != null) tvti.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_title) * this.zoom);
            if (tvfs != null) tvfs.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.text_size_list_news_firstsentence) * this.zoom);
        }

        // apply the user-defined typeface to the NewsView
        applyTypeface(tvtl, tvda, tvti, tvfs);

        HamburgerService service = this.activity.getHamburgerService();
        final Size bitmapSize;
        if (isFiltered()) {
            bitmapSize = newsView2.setNews(this.filteredNews.get(position), service, this.prefs);
        } else {
            bitmapSize = newsView2.setNews(this.newsList.get(position), service, this.prefs);
        }
        if (service != null && bitmapSize != null && !this.preloading) {
            this.preloading = true;
            final int n = getItemCount();
            final List<String> cacheUs = new ArrayList<>(n - 1);
            for (int i = 0; i < n; i++) {
                if (i == position) continue;
                TeaserImage image = this.newsList.get(i).getTeaserImage();
                if (image == null) continue;
                TeaserImage.MeasuredImage measuredImage = image.getExact(bitmapSize.getWidth(), bitmapSize.getHeight());
                if (measuredImage == null || TextUtils.isEmpty(measuredImage.url)) continue;
                cacheUs.add(measuredImage.url);
            }
            service.loadIntoCache(cacheUs);
        }
    }

    /** {@inheritDoc}
     * <hr>
     *  Returns a {@link ViewHolder} for the given view type.<br>
     *  Attempts to get one from the {@link #viewholderCache cache}; if that fails, creates a new one.<br>
     *  Finally creates another ViewHolder of the same type and puts in the cache (to be used during the next invocation of this method).<br>
     *  Has been proven to improve speed on low-end (SD410) and newer (SD778) devices, though admittedly only in the lower ms range.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        ViewHolder vh;
        synchronized (this.viewholderCache) {
            vh = this.viewholderCache.get(viewType);
        }
        if (vh == null) {
            vh = new ViewHolder(instantiateView(this.activity, parent, viewType));
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
                    ViewHolder newOne = new ViewHolder(instantiateView(NewsRecyclerAdapter.this.activity, parent, viewType));
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
        this.prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    /** {@inheritDoc} */
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (App.PREF_FILTERS_APPLY.equals(key)) {
            this.filtersEnabled = prefs.getBoolean(key, App.PREF_FILTERS_APPLY_DEFAULT);
        } else if (App.PREF_BACKGROUND.equals(key)) {
            // The activity receives the same info (HamburgerActivity.onSharedPreferenceChanged()) and sets its background accordingly; give it a moment to do so
            this.handler.postDelayed(() -> {
                NewsRecyclerAdapter.this.background = NewsRecyclerAdapter.this.activity.getBackground();
                notifyDataSetChanged();
            }, 500L);
        } else if (App.PREF_FONT_ZOOM.equals(key)) {
            this.zoom = prefs.getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT) / 100f;
            this.zoomModified = true;
            notifyDataSetChanged();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        ((NewsView2)holder.itemView).setNews(null, null, null);
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
    @SuppressLint("NotifyDataSetChanged")
    public void setTypeface(@Nullable Typeface typeface) {
        boolean changed = (typeface != null && !typeface.equals(this.typeface)) || (typeface == null && this.typeface != null);
        this.typeface = typeface;
        if (changed) notifyDataSetChanged();
    }

    /**
     * Updates the {@link #filteredNews filtered news list} and calls {@link #notifyDataSetChanged()}.
     */
    @SuppressLint("NotifyDataSetChanged")
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
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnCreateContextMenuListener {

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
         * Throws a RuntimeException.
         * @param ctx Context
         * @throws RuntimeException (always)
         */
        private static void throwWrongContext(@Nullable Context ctx) {
            throw new RuntimeException((ctx != null ? ctx.getClass().getName() : "<null>") + " does not implement " + NewsAdapterController.class.getName());
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) menu.setGroupDividerEnabled(true);
            NewsRecyclerAdapter adapter = ma.getAdapter();
            adapter.contextMenuIndex = position;
            //
            final News news = adapter.getItem(position);
            boolean hasWebLink = news != null && !TextUtils.isEmpty(news.getDetailsWeb());
            // allow sharing videos if a) there is one in the content part of STORY-type news or b) if there is one in the streams of VIDEO-type news
            boolean hasVideo = news != null && (news.hasBottomVideo() || (News.NEWS_TYPE_VIDEO.equals(news.getType()) && !news.getStreams().isEmpty()));
            boolean hasImage = news != null && news.getTeaserImage() != null && news.getTeaserImage().hasImage();
            MenuItem menuItemViewInBrowser = menu.findItem(R.id.action_view_in_browser);
            menuItemViewInBrowser.setEnabled(hasWebLink);
            MenuItem menuItemShareNews = menu.findItem(R.id.action_share_news);
            menuItemShareNews.setEnabled(hasWebLink);
            MenuItem menuItemShareVideo = menu.findItem(R.id.action_share_video);
            menuItemShareVideo.setEnabled(hasVideo);
            MenuItem menuItemShareImage = menu.findItem(R.id.action_share_image);
            menuItemShareImage.setEnabled(hasImage);
            MenuItem menuItemViewImage = menu.findItem(R.id.action_view_picture);
            menuItemViewImage.setEnabled(hasImage);
            MenuItem menuItemPrintImage = menu.findItem(R.id.action_print_picture);
            menuItemPrintImage.setEnabled(hasImage);
        }
    }
}
