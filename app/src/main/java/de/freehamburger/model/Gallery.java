package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
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
    public static Gallery parse(@NonNull final JsonReader reader) throws IOException {
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

    public enum Quality {
        L, M, S
    }

    /**
     *
     */
    public static class Item implements Serializable {

        private final Map<Quality, String> images = new HashMap<>(3);
        private String title;

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
                    if (!TextUtils.isEmpty(alttext)) item.title = alttext;
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
                    //if (BuildConfig.DEBUG) android.util.Log.i(Gallery.Item.class.getSimpleName(), "Skipping " + name);
                    reader.skipValue();
                }
            }
            reader.endObject();
            return item;
        }

        private void addImage(Quality quality, String url) {
            this.images.put(quality, url);
        }

        @NonNull
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public Map<Quality, String> getImages() {
            return images;
        }

        public String getTitle() {
            return title;
        }
    }

}
