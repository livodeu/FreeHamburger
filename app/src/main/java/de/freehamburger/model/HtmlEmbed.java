package de.freehamburger.model;

import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public class HtmlEmbed implements Serializable {

    @Nullable private String service;
    @Nullable private String url;

    /**
     * Parses the output of the given JsonReader to retrieve an HtmlEmbed element.
     * @param reader JsonReader
     * @return HtmlEmbed element
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static HtmlEmbed parse(@NonNull final JsonReader reader) throws IOException {
        final HtmlEmbed htmlEmbed = new HtmlEmbed();
        reader.beginObject();
        for (; reader.hasNext(); ) {
            String name = reader.nextName();
            JsonToken next = reader.peek();
            if (next == JsonToken.NAME) {
                continue;
            }
            if (next == JsonToken.NULL) {
                reader.skipValue();
                continue;
            }
            if ("service".equals(name)) {
                htmlEmbed.service = reader.nextString();
            } else if ("url".equals(name)) {
                htmlEmbed.url = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return htmlEmbed;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HtmlEmbed htmlEmbed = (HtmlEmbed) o;
        return Objects.equals(this.url, htmlEmbed.url);
    }

    @Nullable
    String getService() {
        return this.service;
    }

    @Nullable
    String getUrl() {
        return this.url;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.url);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return "HtmlEmbed{" +
                "service='" + this.service + '\'' +
                ", url='" + this.url + '\'' +
                '}';
    }
}
