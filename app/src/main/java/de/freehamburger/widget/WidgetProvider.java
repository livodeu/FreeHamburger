package de.freehamburger.widget;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SizeF;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.MainActivity;
import de.freehamburger.R;
import de.freehamburger.UpdateJobService;
import de.freehamburger.WidgetActivity;
import de.freehamburger.model.Blob;
import de.freehamburger.model.BlobParser;
import de.freehamburger.model.Filter;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.model.TextFilter;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class WidgetProvider extends AppWidgetProvider {

    @WidgetLayout public static final int WIDGET_LAYOUT_REGULAR = 0;
    @WidgetLayout public static final int WIDGET_LAYOUT_SMALL = 1;
    @WidgetLayout public static final int WIDGET_LAYOUT_WIDE = 2;
    /** int: one of the @WidgetLayout values */
    public static final String PREF_WIDGETS_LAYOUT_PRE_S = "pref_widgets_layout_pre_s";
    @WidgetLayout public static final int PREF_WIDGETS_LAYOUT_PRE_S_DEFAULT = WIDGET_LAYOUT_REGULAR;
    public static final String ACTION_WIDGET_TAPPED = BuildConfig.APPLICATION_ID + ".action.widgettapped";
    public static final String EXTRA_FROM_WIDGET = BuildConfig.APPLICATION_ID + ".extra.fromwidget";
    private static final String TAG = "WidgetProvider";
    /** the file where mappings like &lt;widgetId&gt;=&lt;SOURCE&gt; are stored */
    private static final String WIDGET_SOURCES = "widgets.txt";
    /** the separator char for {@link #WIDGET_SOURCES} */
    private static final char SEP = '=';
    /** the default Source for each widget as long as the user has not selected something else */
    private static final Source DEFAULT_SOURCE = Source.HOME;
    /** TextView.setGravity() has been made "remotable" apparently for Android 12 */
    private static Boolean setGravityMayBeUsed = null;

    /**
     * Fills the given RemoteViews with either a News or an error message.
     * @param ctx Context
     * @param appWidgetId widget id
     * @param news News to display
     * @param error error to announce
     * @param rv RemoteViews (one or more)
     */
    @SuppressWarnings("ConstantConditions")
    private static void fillRemoteViews(@NonNull final Context ctx, final int appWidgetId, @Nullable News news, @Nullable final Throwable error, @NonNull final RemoteViews... rv) {
        if (setGravityMayBeUsed == null) {
            // this seems to be true from API 31 on
            setGravityMayBeUsed = isRemotable(TextView.class,"setGravity", int.class);
        }
        for (final RemoteViews remoteViews : rv) {
            if (remoteViews == null) continue;
            // show either error message or News, but not both
            setViewsVisibility(error != null, remoteViews);
            //
            if (error != null) {
                // case 1: Hamburg, we have a problem!
                String msg;
                if (error instanceof java.io.EOFException) {
                    msg = ctx.getString(R.string.error_parsing);
                } else {
                    msg = error.getMessage();
                    if (msg == null) msg = error.toString();
                }
                remoteViews.setTextViewText(R.id.textError, msg);
                PendingIntent pi = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class), Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(android.R.id.background, pi);
            } else if (news == null) {
                // case 2: for whatever reason, we don't have anything to report
                remoteViews.setTextViewText(R.id.textViewTopline, null);
                remoteViews.setTextViewText(R.id.textViewDate, null);
                remoteViews.setViewVisibility(R.id.textViewTopline, View.GONE);
                remoteViews.setViewVisibility(R.id.textViewDate, View.GONE);
                // textViewFirstSentence will show the text set as "android:hint"
                remoteViews.setTextViewText(R.id.textViewFirstSentence, null);
                if (setGravityMayBeUsed) remoteViews.setInt(R.id.textViewFirstSentence, "setGravity", Gravity.CENTER_HORIZONTAL | Gravity.FILL_VERTICAL);
                remoteViews.setOnClickPendingIntent(android.R.id.background, makeConfigureIntent(ctx, appWidgetId));
            } else {
                // case 3: all things normal, carry on…
                remoteViews.setViewVisibility(R.id.textViewTopline, View.VISIBLE);
                remoteViews.setViewVisibility(R.id.textViewDate, View.VISIBLE);
                if (Build.VERSION.SDK_INT >= 31) remoteViews.setInt(R.id.textViewFirstSentence, "setGravity", Gravity.START | Gravity.FILL_VERTICAL);

                // 3rd line
                String firstSentence = news.getTextForTextViewFirstSentence();
                boolean hasFirstSentence = !TextUtils.isEmpty(firstSentence);
                boolean showTextViewFirstSentence = hasFirstSentence;
                remoteViews.setTextViewText(R.id.textViewFirstSentence, firstSentence);

                // 1st line
                if (hasFirstSentence) {
                    if (!TextUtils.isEmpty(news.getTitle())) {
                        remoteViews.setTextViewText(R.id.textViewTopline, news.getTitle().trim());
                    } else if (!TextUtils.isEmpty(news.getTopline())) {
                        // topline is usually a very short label, like "Baden-Württemberg" or similar
                        remoteViews.setTextViewText(R.id.textViewTopline, news.getTopline().trim());
                    } else {
                        remoteViews.setTextViewText(R.id.textViewTopline, null);
                    }
                } else {
                    // if we don't have content for textViewFirstSentence, then move other content there
                    if (!TextUtils.isEmpty(news.getTitle())) {
                        showTextViewFirstSentence = true;
                        remoteViews.setTextViewText(R.id.textViewTopline, news.getTopline().trim());
                        remoteViews.setTextViewText(R.id.textViewFirstSentence, news.getTitle().trim());
                    } else  {
                        showTextViewFirstSentence = false;
                    }
                }
                remoteViews.setViewVisibility(R.id.textViewFirstSentence, showTextViewFirstSentence ? View.VISIBLE : View.GONE);

                // 2nd line - the date
                java.util.Date date = news.getDate();
                String dates = date != null
                        ? (System.currentTimeMillis() - date.getTime() > 86_400_000L
                        ? DateFormat.getDateInstance(DateFormat.SHORT).format(date)
                        : DateFormat.getTimeInstance(DateFormat.SHORT).format(date))
                        : null;
                remoteViews.setTextViewText(R.id.textViewDate, dates);

                // show News upon click; pass to MainActivity which will then, in turn, pass it to NewsActivity
                Intent intent = new Intent(ctx, MainActivity.class);
                intent.setAction(MainActivity.ACTION_SHOW_NEWS);
                intent.putExtra(MainActivity.EXTRA_NEWS, news);
                intent.putExtra(EXTRA_FROM_WIDGET, appWidgetId);
                // if we have more than one widget, passing the widget id as request code makes the PendingIntents different from each other (which is important)
                PendingIntent pi = PendingIntent.getActivity(ctx, appWidgetId, intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                remoteViews.setOnClickPendingIntent(android.R.id.background, pi);
            }

            // we have a "configure" button in Android versions below 12 which opens the WidgetActivity
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                remoteViews.setOnClickPendingIntent(R.id.buttonConfigure, makeConfigureIntent(ctx, appWidgetId));
            }
        }
    }

    /**
     * Fills a particular widget identified by the given id with the given News.
     * @param ctx Context
     * @param appWidgetManager AppWidgetManager
     * @param appWidgetId widget id
     * @param news News to display
     * @param error optional error
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @VisibleForTesting
    public static boolean fillWidget(@NonNull Context ctx, @NonNull AppWidgetManager appWidgetManager, final int appWidgetId, @Nullable final News news, @Nullable Throwable error) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false;

        String packageName = ctx.getPackageName();
        final RemoteViews viewsRegular;
        final RemoteViews viewsSmall;
        final RemoteViews viewsWide;
        final RemoteViews viewsPreS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewsPreS = null;
            viewsSmall = new RemoteViews(packageName, R.layout.news_view_widget_small);
            viewsRegular = new RemoteViews(packageName, R.layout.news_view_widget);
            viewsWide = new RemoteViews(packageName, R.layout.news_view_widget_wide);
            fillRemoteViews(ctx, appWidgetId, news, error, viewsRegular, viewsSmall, viewsWide);
        } else {
            viewsSmall = null;
            viewsRegular = null;
            viewsWide = null;
            @WidgetLayout int widgetLayout = PreferenceManager.getDefaultSharedPreferences(ctx).getInt(PREF_WIDGETS_LAYOUT_PRE_S, PREF_WIDGETS_LAYOUT_PRE_S_DEFAULT);
            switch (widgetLayout) {
                case WIDGET_LAYOUT_SMALL: viewsPreS = new RemoteViews(packageName, R.layout.news_view_widget_small); break;
                case WIDGET_LAYOUT_WIDE: viewsPreS = new RemoteViews(packageName, R.layout.news_view_widget_wide); break;
                case WIDGET_LAYOUT_REGULAR:
                default: viewsPreS = new RemoteViews(packageName, R.layout.news_view_widget); break;
            }
            fillRemoteViews(ctx, appWidgetId, news, error, viewsPreS);
        }

        try {
            // https://developer.android.com/guide/topics/appwidgets/layouts?hl=en#java
            final RemoteViews assembledRemoteViews;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Map<SizeF, RemoteViews> viewMapping = new ArrayMap<>(3);
                viewMapping.put(new SizeF(150f, 0f), viewsSmall);
                viewMapping.put(new SizeF(500f, 0f), viewsWide);
                viewMapping.put(new SizeF(150f, 150f), viewsRegular);
                assembledRemoteViews = new RemoteViews(viewMapping);
            } else {
                assembledRemoteViews = viewsPreS;
            }
            appWidgetManager.updateAppWidget(appWidgetId, assembledRemoteViews);
            return true;
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While updating widget " + appWidgetId + ": " + e);
        }
        return false;
    }

    /**
     * Determines whether the given method carries the "android.view.RemotableViewMethod" annotation.
     * @param clazz Class
     * @param method method name
     * @param params the method's parameter types
     * @return true / false
     */
    @VisibleForTesting
    public static boolean isRemotable(Class<? extends View> clazz, String method, Class<?>... params) {
        boolean yesitis = false;
        try {
            Method setGravity = clazz.getDeclaredMethod(method, params);
            @SuppressWarnings("unchecked") @SuppressLint("PrivateApi")
            Annotation a = setGravity.getAnnotation((Class <? extends Annotation>)Class.forName("android.view.RemotableViewMethod"));
            yesitis = (a != null);
        } catch (Throwable e) {
            if (BuildConfig.DEBUG) Log.w(TAG, e.toString());
        }
        return yesitis;
    }

    /**
     * Loads the Sources to use for the widgets.
     * @param ctx Context
     * @return SparseArray with widget ids as keys
     */
    @NonNull
    public static SparseArray<Source> loadWidgetSources(@NonNull Context ctx) {
        AppWidgetManager aw = AppWidgetManager.getInstance(ctx);
        final int[] widgetIds = aw.getAppWidgetIds(new ComponentName(ctx.getApplicationContext(), WidgetProvider.class));
        final SparseArray<Source> widgetSources = new SparseArray<>(widgetIds != null ? widgetIds.length : 0);
        // initialise each widget with the default Source
        if (widgetIds != null && widgetIds.length > 0) {
            for (int widgetId : widgetIds) {
                widgetSources.put(widgetId, DEFAULT_SOURCE);
            }
        }
        //
        synchronized (WIDGET_SOURCES) {
            File file = new File(ctx.getFilesDir(), WIDGET_SOURCES);
            if (!file.isFile()) return widgetSources;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                for (; ; ) {
                    String line = reader.readLine();
                    if (line == null) break;
                    int sep = line.indexOf(SEP);
                    int widgetId = Integer.parseInt(line.substring(0, sep));
                    Source source = Source.valueOf(line.substring(sep + 1));
                    widgetSources.put(widgetId, source);
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "While loading widget sources: " + e);
            } finally {
                Util.close(reader);
            }
        }
        return widgetSources;
    }

    /**
     * Builds a PendingIntent that can be used to configure the app widget identified by the given id.
     * @param ctx Context
     * @param appWidgetId app widget id
     * @return PendingIntent
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    private static PendingIntent makeConfigureIntent(@NonNull Context ctx, int appWidgetId) {
        Intent configure = new Intent(ctx, WidgetActivity.class);
        configure.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getActivity(ctx, 0, configure, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Sets an app widget's views visibility, depending on whether we have an error condition.<br>
     * In that case, only the error will be shown while the fields that usually contain the News will be hidden.
     * @param error true / false
     * @param rv one or more RemoteViews
     */
    private static void setViewsVisibility(final boolean error, @NonNull final RemoteViews... rv) {
        for (RemoteViews r : rv) {
            r.setViewVisibility(R.id.textError, error ? View.VISIBLE : View.GONE);
            r.setViewVisibility(R.id.textViewTopline, error ? View.INVISIBLE : View.VISIBLE);
            r.setViewVisibility(R.id.textViewDate, error ? View.INVISIBLE : View.VISIBLE);
            r.setViewVisibility(R.id.textViewFirstSentence, error ? View.INVISIBLE : View.VISIBLE);
            r.setViewVisibility(R.id.buttonConfigure, error ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * Stores the Sources to use for each widget.
     * @param ctx Context
     * @param widgetSources SparseArray with widget ids as keys
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static void storeWidgetSources(@NonNull Context ctx, @NonNull final SparseArray<Source> widgetSources) {
        synchronized (WIDGET_SOURCES) {
            final File file = new File(ctx.getFilesDir(), WIDGET_SOURCES);
            BufferedWriter writer = null;
            try {
                File temp = File.createTempFile("widgets", null);
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8));
                final int n = widgetSources.size();
                for (int i = 0; i < n; i++) {
                    int widgetId = widgetSources.keyAt(i);
                    Source source = widgetSources.get(widgetId);
                    String line = String.valueOf(widgetId) + SEP + source.toString();
                    writer.write(line);
                    writer.newLine();
                }
                Util.close(writer);
                writer = null;
                if (!temp.renameTo(file)) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Failed to rename " + temp + " to " + file);
                    Util.deleteFile(temp);
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            } finally {
                Util.close(writer);
            }
        }
    }

    /**
     * Updates all widgets based on the data as it is found on "disk".
     * <i>No downloads are performed here!</i>
     * @param ctx Context
     * @param widgetSources SparseArray with widget ids as keys
     * @throws NullPointerException if any parameter is {@code null}
     */
    @AnyThread
    public static void updateWidgets(@NonNull Context ctx, @NonNull SparseArray<Source> widgetSources) {
        if (BuildConfig.DEBUG) Log.i(TAG, "updateWidgets(…, " + widgetSources + ")");
        final App app = (App)ctx.getApplicationContext();
        final AppWidgetManager aw = AppWidgetManager.getInstance(app);
        final int[] widgetIds = aw.getAppWidgetIds(new ComponentName(app, WidgetProvider.class));
        if (widgetIds == null || widgetIds.length == 0) return;
        final List<Filter> filters = TextFilter.createTextFiltersFromPreferences(ctx);
        final Map<Source, Blob> blobsCache = new HashMap<>();
        final AtomicBoolean needsNetworkRefresh = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        for (int widgetId : widgetIds) {
            Source source = widgetSources.indexOfKey(widgetId) >= 0 ? widgetSources.get(widgetId) : DEFAULT_SOURCE;
            Blob blob = blobsCache.get(source);
            if (blob != null) {
                // the source has been parsed before (for a different widget)
                final List<News> sortedJointList = blob.getAllNews();
                News latest = null;
                for (News news : sortedJointList) {
                    // skip all News that the user does not want to see
                    if (Filter.refusedByAny(filters, news)) continue;
                    // skip all News that do not contain text
                    if (!News.NEWS_TYPE_STORY.equals(news.getType())) continue;
                    //
                    latest = news;
                    break;
                }
                // if there is no 'latest' News, we leave the previous content as it is
                if (latest == null) {
                    continue;
                }
                //
                fillWidget(app, aw, widgetId, latest, null);
            } else {
                File file = app.getLocalFile(source);
                if (!file.isFile()) {
                    needsNetworkRefresh.set(true);
                    if (BuildConfig.DEBUG) Log.i(TAG, "Local file for " + source + " for widget " + widgetId + " does not exist - clearing that widget");
                    fillWidget(app, aw, widgetId, null, null);
                    continue;
                }
                BlobParser blobParser = new BlobParser(ctx, (parsedBlob, ok, oops) -> {
                    if (parsedBlob == null || !ok || oops != null) {
                        if (BuildConfig.DEBUG && oops != null) Log.e(TAG, "While updating widget " + widgetId + ": " + oops);
                        if (oops != null) fillWidget(app, aw, widgetId, null, oops);
                        if (oops instanceof java.io.EOFException) needsNetworkRefresh.set(true);
                        return;
                    }
                    blobsCache.put(source, parsedBlob);
                    final List<News> sortedJointList = parsedBlob.getAllNews();
                    News latest = null;
                    for (News news : sortedJointList) {
                        // skip all News that the user does not want to see
                        if (Filter.refusedByAny(filters, news)) continue;
                        // skip all News that do not contain text
                        if (!News.NEWS_TYPE_STORY.equals(news.getType())) continue;
                        //
                        latest = news;
                        break;
                    }
                    // if there is no 'latest' News, we leave the previous content as it is
                    if (latest == null) {
                        if (BuildConfig.DEBUG) Log.i(TAG, "No suitable News for widget " + widgetId + " - not updating that widget (b)");
                        return;
                    }
                    fillWidget(app, aw, widgetId, latest, null);
                });
                handler.post(() -> blobParser.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, file));
            }
        }
        if (needsNetworkRefresh.get()) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Initiating one-off job because we're lacking data…");
            JobScheduler js = (JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            // once there was some kind of endless loop when there wasn't a delay, so…
            if (js != null) js.schedule(UpdateJobService.makeOneOffJobInfoWithDelay(ctx, 2_000L, 10_000L));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onDeleted(Context ctx, final int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        final SparseArray<Source> widgetSources = loadWidgetSources(ctx);
        boolean modified = false;
        for (int widgetId : appWidgetIds) {
            if (widgetSources.indexOfKey(widgetId) < 0) continue;
            widgetSources.delete(widgetId);
            modified = true;
        }
        if (!modified) return;
        storeWidgetSources(ctx, widgetSources);
    }

    /** {@inheritDoc} */
    @Override
    public void onRestored(Context ctx, int[] oldWidgetIds, int[] newWidgetIds) {
        if (oldWidgetIds == null || newWidgetIds == null || oldWidgetIds.length == 0 || newWidgetIds.length != oldWidgetIds.length) return;
        boolean modified = false;
        final SparseArray<Source> widgetSources = loadWidgetSources(ctx);
        final int n = oldWidgetIds.length;
        for (int i = 0; i < n; i++) {
            if (newWidgetIds[i] == oldWidgetIds[i]) continue;
            Source source = widgetSources.get(oldWidgetIds[i], DEFAULT_SOURCE);
            widgetSources.remove(oldWidgetIds[i]);
            widgetSources.put(newWidgetIds[i], source);
            modified = true;
        }
        if (modified) storeWidgetSources(ctx, widgetSources);
    }

    /** {@inheritDoc}
     * <hr>
     * This is called by the system every &lt;updatePeriodMillis&gt; ms as specified in the widget info xml */
    @Override
    public void onUpdate(final Context ctx, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        updateWidgets(ctx, loadWidgetSources(ctx));
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WIDGET_LAYOUT_REGULAR, WIDGET_LAYOUT_SMALL, WIDGET_LAYOUT_WIDE})
    public @interface WidgetLayout {}
}
