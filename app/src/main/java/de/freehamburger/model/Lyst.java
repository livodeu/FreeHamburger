package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class Lyst implements Serializable {


    private final List<String> urls = new ArrayList<>(8);
    private String title;

    /**
     * Parses the given JsonReader to retrieve a Lyst element.
     * @param reader JsonReader
     * @return Lyst
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Lyst parse(@NonNull final JsonReader reader) throws IOException {
        final Lyst list = new Lyst();
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
                list.title = reader.nextString();
            } else if ("items".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        String value = reader.nextString();
                        if ("url".equals(name)) {
                            list.urls.add(value);
                        }
                        // name is otherwise often/usually/always "source" with a value like "wdr" or "dw" etc.
                    }
                    reader.endObject();
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return list;
    }

    @Nullable
    public String getTitle() {
        return this.title;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public java.util.List<String> getUrls() {
        return this.urls;
    }

    boolean hasUrls() {
        return !this.urls.isEmpty();
    }
}