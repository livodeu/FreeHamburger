package de.freehamburger.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.R;

/**
 * The main data object which contains lists of national and regional {@link News news}.
 */
public class Blob {

    private final List<News> newsList = new ArrayList<>(16);
    private final List<News> regionalNewsList = new ArrayList<>(16);
    /** a https link to a data structure - not always present */
    @Nullable private String newStoriesCountLink;
    private Date date;
    private Source source;

    /**
     * Private constructor.
     */
    private Blob() {
        super();
    }

    /**
     * Entry point for parsing the data that has been received.<br>
     * The data consists of<ol>
     * <li>news (array)</li>
     * <li>regional (array)</li>
     * <li>newStoriesCountLink (String)</li>
     * <li>type (String)</li>
     * </ol>
     * @param ctx Context
     * @param source Source
     * @param reader JsonReader
     * @return Blob
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code ctx} or {@code reader} are {@code null}
     */
    @NonNull
    static Blob parseApi(@NonNull Context ctx, @NonNull Source source, @NonNull final JsonReader reader) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        @News.Flag int flags = 0;
        boolean htmlEmbed = prefs.getBoolean(App.PREF_SHOW_EMBEDDED_HTML_LINKS, App.PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT);
        if (htmlEmbed) flags |= News.FLAG_INCLUDE_HTMLEMBED;
        // give some current color values to the Content class
        Resources r = ctx.getResources();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Resources.Theme t = ctx.getTheme();
            Content.setColorBox("#" + Integer.toHexString(r.getColor(R.color.colorBox, t) & ~0xff000000));
            Content.setColorBoxBackground("#" + Integer.toHexString(r.getColor(R.color.colorBoxBackground, t) & ~0xff000000));
            Content.setColorHtmlEmbed("#" + Integer.toHexString(r.getColor(R.color.colorHtmlEmbed, t) & ~0xff000000));
            Content.setColorQuotation("#" + Integer.toHexString(r.getColor(R.color.colorQuotation, t) & ~0xff000000));
        } else {
            Content.setColorBox("#" + Integer.toHexString(r.getColor(R.color.colorBox) & ~0xff000000));
            Content.setColorBoxBackground("#" + Integer.toHexString(r.getColor(R.color.colorBoxBackground) & ~0xff000000));
            Content.setColorHtmlEmbed("#" + Integer.toHexString(r.getColor(R.color.colorHtmlEmbed) & ~0xff000000));
            Content.setColorQuotation("#" + Integer.toHexString(r.getColor(R.color.colorQuotation) & ~0xff000000));
        }
        //
        final Blob blob = new Blob();
        blob.source = source;
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
                blob.newsList.addAll(parseNewsList(reader, false, flags));
                continue;
            }
            if ("regional".equals(name)) {
                @Nullable final Set<String> regionIdsToInclude = prefs.getStringSet(App.PREF_REGIONS, null);
                if (regionIdsToInclude == null || regionIdsToInclude.isEmpty()) {
                    reader.skipValue();
                } else {
                    blob.regionalNewsList.addAll(parseNewsList(reader, true, flags));
                    // remove news from those regions that the user is not interested in
                    final Set<News> toRemove = new HashSet<>();
                    for (News regionalNews : blob.regionalNewsList) {
                        Set<Region> newsRegions = regionalNews.getRegions();
                        // decision: keep regional news if it has got no region attached to it
                        if (newsRegions.isEmpty()) continue;
                        //
                        boolean keep = false;
                        for (Region newsRegion : newsRegions) {
                            String newsRegionId = String.valueOf(newsRegion.getId());
                            if (regionIdsToInclude.contains(newsRegionId)) {
                                keep = true;
                                break;
                            }
                        }
                        if (!keep) toRemove.add(regionalNews);
                    }
                    blob.regionalNewsList.removeAll(toRemove);
                }
                continue;
            }
            if ("newStoriesCountLink".equals(name))  {
                blob.newStoriesCountLink = reader.nextString();
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
        // replace some text within the News instances because the Context that we have here has not been passed down
        final String[] toReplace = new String[] {Content.MARK_LINK};
        final String[] replaceWith = new String[] {ctx.getString(R.string.label_link)};
        for (News news : blob.newsList) {
            Content content = news.getContent();
            if (content != null) content.replace(toReplace, replaceWith);
        }
        for (News news : blob.regionalNewsList) {
            Content content = news.getContent();
            if (content != null) content.replace(toReplace, replaceWith);
        }
        //
        return blob;
    }

    @NonNull
    private static Collection<News> parseNewsList(@NonNull final JsonReader reader, final boolean regional, @News.Flag final int flags) throws IOException {
        try {
            final Set<News> newsList = new HashSet<>(16);
            reader.beginArray();
            for (; reader.hasNext(); ) {
                newsList.add(News.parseNews(reader, regional, flags));
            }
            reader.endArray();
            return newsList;
        } catch (MalformedJsonException e) {
            throw new InformativeJsonException(e, reader);
        }
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

    @Nullable
    public String getNewStoriesCountLink() {
        return this.newStoriesCountLink;
    }

    @VisibleForTesting
    public List<News> getRegionalNewsList() {
        return this.regionalNewsList;
    }

    @NonNull
    public Source getSource() {
        return this.source;
    }
}
