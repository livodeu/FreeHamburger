package de.freehamburger.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;

/**
 * The main data object which contains lists of national and regional {@link News news}.
 */
public class Blob {

    private final List<News> newsList = new ArrayList<>(16);
    private final List<News> regionalNewsList = new ArrayList<>(16);
    private Date date;

    /**
     * Entry point for parsing the data that has been received.<br>
     * The data consists of<ol>
     * <li>news (array)</li>
     * <li>regional (array)</li>
     * <li>newStoriesCountLink (String)</li>
     * <li>type (String)</li>
     * </ol>
     * @param ctx Context
     * @param reader JsonReader
     * @return Blob
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code ctx} or {@code reader} are {@code null}
     */
    @NonNull
    static Blob parseApi(@NonNull Context ctx, @NonNull final JsonReader reader) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        //
        final Blob blob = new Blob();
        String name = null;
        reader.beginObject();
        for (; reader.hasNext(); ) {
            JsonToken token = reader.peek();
            if (token == JsonToken.END_DOCUMENT) break;
            if (token == JsonToken.NAME) {
                name = reader.nextName();
                continue;
            }
            if ("news".equals(name) || "channels".equals(name)) {
                blob.newsList.addAll(parseNewsList(reader, false));
                continue;
            }
            if ("regional".equals(name)) {
                blob.regionalNewsList.addAll(parseNewsList(reader, true));
                // remove news from those regions that the user is not interested in
                final Set<String> regionIdsToInclude = prefs.getStringSet(App.PREF_REGIONS, new HashSet<>(0));
                final Set<News> toRemove = new HashSet<>();
                for (News regionalNews : blob.regionalNewsList) {
                    Set<Region> newsRegions = regionalNews.getRegions();
                    // decision: keep regional news if it has got no region attached to it
                    if (newsRegions.isEmpty()) {
                        if (BuildConfig.DEBUG) Log.w(Blob.class.getSimpleName(), "Regional news without region: \"" + regionalNews.getTitle() + "\"!");
                        continue;
                    }
                    //
                    boolean keep = false;
                    for (Region newsRegion : newsRegions) {
                        String newsRegionId = String.valueOf(newsRegion.getId());
                        if (regionIdsToInclude.contains(newsRegionId)) {
                            keep = true;
                            break;
                        }
                    }
                    if (!keep) {
                        toRemove.add(regionalNews);
                    }
                }
                blob.regionalNewsList.removeAll(toRemove);
                continue;
            }
            if (reader.hasNext()) {
                reader.skipValue();
            }
        }
        reader.endObject();
        //
        if (prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT)) {
            News.correct(blob.newsList);
            News.correct(blob.regionalNewsList);
        }
        //
        return blob;
    }

    @NonNull
    private static Collection<News> parseNewsList(@NonNull final JsonReader reader, final boolean regional) throws IOException {
        final Set<News> newsList = new HashSet<>(16);
        reader.beginArray();
        for (; reader.hasNext(); ) {
            newsList.add(News.parseNews(reader, regional));
        }
        reader.endArray();
        return newsList;
    }

    /**
     * Private constructor.
     */
    private Blob() {
        super();
    }

    /**
     * Returns the date of the newest/youngest News item.
     * @return Date
     */
    @Nullable
    public Date getDate() {
        if (this.date != null) {
            return this.date;
        }
        for (News news : this.newsList) {
            Date newsDate = news.getDate();
            if (newsDate == null) continue;
            if (this.date == null || newsDate.after(this.date)) {
                this.date = newsDate;
            }
        }
        for (News news : this.regionalNewsList) {
            Date newsDate = news.getDate();
            if (newsDate == null) continue;
            if (this.date == null || newsDate.after(this.date)) {
                this.date = newsDate;
            }
        }
        return this.date;
    }

    @VisibleForTesting
    public List<News> getRegionalNewsList() {
        return regionalNewsList;
    }

    /**
     * @return Sorted list containing both national news and regional news
     */
    @NonNull
    public List<News> getAllNews() {
        final List<News> jointList = new ArrayList<>(this.newsList.size() + this.regionalNewsList.size());
        jointList.addAll(this.newsList);
        for (News rn : this.regionalNewsList) {
            if (!jointList.contains(rn)) jointList.add(rn);
        }
        Collections.sort(jointList);
        return jointList;
    }
}
