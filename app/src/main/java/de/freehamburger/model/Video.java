package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Date;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class Video implements Serializable {

    private final AbstractMap<StreamQuality, String> streams = new EnumMap<>(StreamQuality.class);
    private String title;
    private String dateString;
    private Date date;
    private TeaserImage teaserImage;

    /**
     * Parses the given JsonReader to retrieve a Video element.
     * @param reader JsonReader
     * @return Video
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Video parseVideo(@NonNull final JsonReader reader) throws IOException {
        final Video video = new Video();
        String name = null;
        reader.beginObject();
        for (; reader.hasNext(); ) {
            JsonToken token = reader.peek();
            if (token == JsonToken.END_DOCUMENT) break;
            if (token == JsonToken.NAME) {
                name = reader.nextName();
                continue;
            }
            if (token == JsonToken.NULL) {
                reader.skipValue();
                continue;
            }
            if ("title".equals(name)) {
                video.title = reader.nextString();
            } else if ("date".equals(name)) {
                video.dateString = reader.nextString();
            } else if ("teaserImage".equals(name)) {
                video.teaserImage = TeaserImage.parse(reader);
            } else if ("streams".equals(name)) {
                reader.beginObject();
                for (; reader.hasNext(); ) {
                    String quality = reader.nextName().toUpperCase(Locale.US);
                    String url = reader.nextString();
                    if (!TextUtils.isEmpty(url)) {
                        video.streams.put(StreamQuality.valueOf(quality), url);
                    }
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return video;
    }

    @Nullable
    public Date getDate() {
        if (date != null) return date;
        if (dateString != null) {
            try {
                date = News.parseDate(dateString);
            } catch (ParseException e) {
                if (BuildConfig.DEBUG) android.util.Log.e(getClass().getSimpleName(), "While parsing date: " + e.toString());
            }
            if (date != null) return date;
        }
        return null;
    }

    @NonNull
    public Map<StreamQuality, String> getStreams() {
        return streams;
    }

    @Nullable
    public TeaserImage getTeaserImage() {
        return teaserImage;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return title != null ? title : streams.values().iterator().next();
    }
}
