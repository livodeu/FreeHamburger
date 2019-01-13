package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.freehamburger.BuildConfig;

public class Related implements Serializable, Comparable<Related> {

    @Nullable private TeaserImage teaserImage;
    @Nullable private Date date;
    @Nullable private String dateString;
    @Nullable private String details;
    @Nullable private String title;
    @Nullable private String type;

    /**
     * Parses the given JsonReader to retrieve an array of Related elements.
     * @param reader JsonReader
     * @return array of Related elements
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @SuppressWarnings("ObjectAllocationInLoop")
    @NonNull
    static Related[] parse(@NonNull final JsonReader reader) throws IOException {
        final List<Related> relatedList = new ArrayList<>();
        reader.beginArray();
        for (; reader.hasNext(); ) {
            reader.beginObject();
            String name = null;
            Related related = new Related();
            for (; reader.hasNext(); ) {
                JsonToken next = reader.peek();
                if (next == JsonToken.NAME) {
                    name = reader.nextName();
                    continue;
                }
                if (next == JsonToken.NULL) {
                    reader.skipValue();
                    continue;
                }
                if ("teaserImage".equals(name)) {
                    related.teaserImage = TeaserImage.parse(reader);
                } else if ("details".equals(name)) {
                    related.details = reader.nextString();
                } else if ("title".equals(name)) {
                    related.title = reader.nextString();
                } else if ("date".equals(name)) {
                    related.dateString = reader.nextString();
                } else if ("type".equals(name)) {
                    related.type = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            // it makes only sense to the app if the element has got a) a picture, b) a title and c) a link to the details json file
            if (related.teaserImage != null && related.teaserImage.hasImage() && !TextUtils.isEmpty(related.title) && !TextUtils.isEmpty(related.details)) {
                relatedList.add(related);
            }
            reader.endObject();
        }
        reader.endArray();
        Collections.sort(relatedList);
        Related[] relatedArray = new Related[relatedList.size()];
        relatedList.toArray(relatedArray);
        return relatedArray;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(@NonNull Related o) {
        if (dateString == null) {
            return o.dateString == null ? 0 : 1;
        }
        Date d = o.getDate();
        if (d == null) return -1;
        return d.compareTo(getDate());
    }

    /**
     * @return the date
     */
    @Nullable
    public Date getDate() {
        if (date != null) return date;
        if (dateString != null) {
            try {
                //date = News.DF.parse(dateString);
                //date = News.getDateFormat().parse(dateString);
                date = News.parseDate(dateString);
            } catch (Exception e) {
                if (BuildConfig.DEBUG) de.freehamburger.util.Log.e(getClass().getSimpleName(), "Failed to parse \"" + dateString + "\"", e);
            }
            return date;
        }
        return null;
    }

    /**
     * @return an address of a json file
     */
    @Nullable
    public String getDetails() {
        return details;
    }

    @Nullable
    public TeaserImage getTeaserImage() {
        return teaserImage;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Related{" + "teaserImage=" + (teaserImage != null ? "yes" : "no") + ", dateString='" + dateString + '\'' + ", details='" + details + '\'' + ", title='" + title + '\'' + ", type='" + type + '\'' + '}';
    }
}
