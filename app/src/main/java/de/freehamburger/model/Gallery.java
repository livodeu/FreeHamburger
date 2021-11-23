package de.freehamburger.model;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sequence of pictures.<br>
 * Each picture is represented by an instance of {@link Item}.<br>
 * Each Item is usually available in different sizes:
 * <ul>
 * <li>klein1x1                140x140</li>
 * <li>videowebs               256x144</li>
 * <li>portraetgross8x9        256x288</li>
 * <li>mittelgross1x1          420x420</li>
 * <li>videowebm               512x288</li>
 * <li>portraetgrossplus8x9    512x576</li>
 * <li>videoweb1x1l            559x559</li>
 * <li>videowebl               960x540</li>
 * </ul>
 * Not all of these will be considered - see {@link Quality}
 */
public class Gallery implements Serializable {

    @NonNull
    private final List<Item> items = new ArrayList<>();

    /**
     * Parses the given JsonReader to retrieve a Gallery element.
     * @param reader JsonReader
     * @return Gallery
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Gallery parse(@NonNull final JsonReader reader) throws IOException {
        final Gallery gallery = new Gallery();
        reader.beginArray();
        for (; reader.hasNext(); ) {
            gallery.items.add(Item.parse(reader));
        }
        reader.endArray();
        return gallery;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public List<Item> getItems() {
        return items;
    }

    /**
     * A representation of the dimensions of an {@link Item}.
     */
    enum Quality {
        /** 960x540 */
        L, 
        /** 512x288 */
        M, 
        /** 256x144 */
        S
    }

    /**
     * A representation of an individual picture within a Gallery.
     */
    public static class Item implements Serializable {

        private final Map<Quality, String> images = new HashMap<>(3);
        private String title;
        private String copyright;

        @NonNull
        static Item parse(@NonNull final JsonReader reader) throws IOException {
            Item item = new Item();
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
                if ("title".equals(name)) {
                    item.title = reader.nextString();
                } else if ("alttext".equals(name)) {
                    String alttext = reader.nextString();
                    if (TextUtils.isEmpty(item.title)) item.title = alttext;
                } else if ("copyright".equals(name)) {
                    item.copyright = reader.nextString();
                } else if ("videowebs".equals(name)) {
                    reader.beginObject();
                    reader.nextName();
                    item.addImage(Quality.S, reader.nextString());
                    reader.endObject();
                } else if ("videowebm".equals(name)) {
                    reader.beginObject();
                    reader.nextName();
                    item.addImage(Quality.M, reader.nextString());
                    reader.endObject();
                } else if ("videowebl".equals(name)) {
                    reader.beginObject();
                    reader.nextName();
                    item.addImage(Quality.L, reader.nextString());
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return item;
        }

        private void addImage(Quality quality, String url) {
            this.images.put(quality, url);
        }

        @Nullable
        String getCopyright() {
            return copyright;
        }

        @NonNull
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        Map<Quality, String> getImages() {
            return images;
        }

        String getTitle() {
            return title;
        }
    }

}
