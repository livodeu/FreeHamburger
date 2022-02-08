package de.freehamburger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class Audio implements Serializable {

    private String title;
    private String dateString;
    private Date date;
    private String stream;

    /**
     * Parses the given JsonReader to retrieve an Audio element.
     * @param reader JsonReader
     * @return Audio
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Audio parseAudio(@NonNull final JsonReader reader) throws IOException {
        final Audio audio = new Audio();
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
                audio.title = reader.nextString();
            } else if ("stream".equals(name)) {
                audio.stream = reader.nextString();
            } else if ("date".equals(name)) {
                audio.dateString = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return audio;
    }

    @Nullable
    public Date getDate() {
        if (date != null) return date;
        if (dateString != null) {
            try {
                date = News.parseDate(dateString);
            } catch (ParseException e) {
                if (BuildConfig.DEBUG) android.util.Log.e(getClass().getSimpleName(), "While parsing date: " + e);
            }
            if (date != null) return date;
        }
        return null;
    }

    @Nullable
    public String getStream() {
        return stream;
    }

    @Nullable
    public String getTitle() {
        return title;
    }
}
