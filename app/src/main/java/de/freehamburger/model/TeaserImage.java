package de.freehamburger.model;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.AbstractMap;
import java.util.EnumMap;
import java.util.Set;

/**
 *
 */
public class TeaserImage implements Serializable {

    @Format public static final int FORMAT_LANDSCAPE = 2;
    @Format public static final int FORMAT_PORTRAIT = 1;
    @Format public static final int FORMAT_SQUARE = 3;
    /** the {@link Quality Qualities} ordered from best to worst */
    private static final Quality[] BEST_QUALITY = new Quality[]{Quality.L, Quality.P2, Quality.M, Quality.P1, Quality.S};
    final AbstractMap<Quality, String> images = new EnumMap<>(Quality.class);
    String title;
    String copyright;
    String alttext;

    /**
     * Constructor.
     */
    TeaserImage() {
        super();
    }

    /**
     * Parses the given JsonReader to retrieve a TeaserImage element.
     * @param reader JsonReader
     * @return TeaserImage
     * @throws IOException          if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static TeaserImage parse(@NonNull final JsonReader reader) throws IOException {
        final TeaserImage teaserImage = new TeaserImage();
        reader.beginObject();
        boolean videoweblFound = false;
        String original16x9 = null;
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
                teaserImage.setTitle(reader.nextString());
            } else if ("alttext".equals(name)) {
                teaserImage.setAlttext(reader.nextString());
            } else if ("copyright".equals(name)) {
                teaserImage.setCopyright(reader.nextString());
            } else if ("videowebs".equals(name)) {              // 256 x 144
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.S, reader.nextString());
                reader.endObject();
            } else if ("videowebm".equals(name)) {              // 512 x 288
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.M, reader.nextString());
                reader.endObject();
            } else if ("videowebl".equals(name)) {              // 960 x 540
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.L, reader.nextString());
                reader.endObject();
                videoweblFound = true;
            } else if ("portraetgrossplus8x9".equals(name)) {   // 512 x 576
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.P2, reader.nextString());
                reader.endObject();
            } else if ("portraetgross8x9".equals(name)) {       // 256 x 288
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.P1, reader.nextString());
                reader.endObject();
            } else if ("videoweb1x1l".equals(name)) {           // 559 x 559
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.LQ, reader.nextString());
                reader.endObject();
            } else if ("mittelgross1x1".equals(name)) {         // 420 x 420
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.MQ, reader.nextString());
                reader.endObject();
            } else if ("klein1x1".equals(name)) {               // 140 x 140
                reader.beginObject();
                reader.nextName();
                teaserImage.addImage(Quality.MQ, reader.nextString());
                reader.endObject();
            } else if ("original16x9".equals(name)) {
                reader.beginObject();
                reader.nextName();
                original16x9 = reader.nextString();
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        // "original16x9" seems to point to the same image url as "videowebl", so if "videowebl" wasn't here, use "original16x9" instead
        if (!TextUtils.isEmpty(original16x9) && !videoweblFound) {
            teaserImage.addImage(Quality.L, original16x9);
        }
        return teaserImage;
    }

    private void addImage(@NonNull Quality quality, @Nullable String url) {
        if (url == null) return;
        this.images.put(quality, url);
    }

    @Nullable
    public String getAlttext() {
        return this.alttext;
    }

    /**
     * @return the url for the best quality (highest resolution) image
     */
    @Nullable
    public String getBestImage() {
        for (Quality q : BEST_QUALITY) {
            String url = this.images.get(q);
            if (url != null) return url;
        }
        return null;
    }

    /**
     * Returns the image variant whose width is greater than the given width.
     * @param width  width in px
     * @param format requested format ({@link #FORMAT_PORTRAIT}, {@link #FORMAT_LANDSCAPE}, {@link #FORMAT_SQUARE})
     * @return MeasuredImage
     */
    @Nullable
    public MeasuredImage getBestImageForWidth(final int width, @Format int preferredFormat) {
        if (this.images.isEmpty()) return null;
        final Quality[] available;
        switch (preferredFormat) {
            case FORMAT_LANDSCAPE:
                available = Quality.valuesForLandscape();
                break;
            case FORMAT_PORTRAIT:
                available = Quality.valuesForPortrait();
                break;
            case FORMAT_SQUARE:
            default:
                available = Quality.valuesForSquare();
                break;
        }
        for (Quality q : available) {
            if (q.width < width) {
                continue;
            }
            if (this.images.containsKey(q)) {
                return new MeasuredImage(this.images.get(q), q.width, q.height);
            }
        }
        Quality q = this.images.keySet().iterator().next();
        return new MeasuredImage(this.images.get(q), q.width, q.height);
    }

    /**
     * Returns a MeasuredImage of the given width and height, or null.
     * @param width requested width
     * @param height requested height
     * @return MeasuredImage
     */
    @Nullable
    public MeasuredImage getExact(final int width, final int height) {
        final Set<Quality> qualities = this.images.keySet();
        for (Quality q : qualities) {
            if (q.width == width && q.height == height) return new MeasuredImage(this.images.get(q), width, height);
        }
        return null;
    }

    @Nullable
    public String getTitle() {
        return this.title;
    }

    public boolean hasImage() {
        return !this.images.isEmpty();
    }

    private void setAlttext(String alttext) {
        this.alttext = alttext;
    }

    private void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    private void setTitle(String title) {
        this.title = title;
    }

    /**
     * They are ordered from smallest width to biggest width, but, if same width, from biggest height to smallest height
     */
    public enum Quality {
        /** 140x140 */ SQ(140, 140),
        /** 256x288 */ P1(256, 288),
        /** 256x144 */ S(256, 144),
        /** 420x420 */ MQ(420, 420),
        /** 512x576 */ P2(512, 576),
        /** 512x288 */ M(512, 288),
        /** 559x559 */ LQ(559, 559),
        /** 960x540 */ L(960, 540);

        private static final Quality[] FOR_LANDSCAPE = new Quality[]{S, M, L};
        private static final Quality[] FOR_PORTRAIT = new Quality[]{P1, P2};
        private static final Quality[] FOR_SQUARE = new Quality[]{SQ, MQ, LQ};

        private final int width;
        private final int height;

        /**
         * Constructor.
         * @param width  width
         * @param height height
         */
        Quality(int width, int height) {
            this.width = width;
            this.height = height;
        }

        /**
         * @return Quality array ordered to prefer landscape images over portrait images
         */
        static Quality[] valuesForLandscape() {
            return FOR_LANDSCAPE;
        }

        /**
         * @return Quality array ordered to prefer portrait images over landscape images
         */
        static Quality[] valuesForPortrait() {
            return FOR_PORTRAIT;
        }

        /**
         * @return Quality array ordered to prefer square images
         */
        static Quality[] valuesForSquare() {
            return FOR_SQUARE;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FORMAT_PORTRAIT, FORMAT_LANDSCAPE, FORMAT_SQUARE})
    public @interface Format {
    }

    /**
     * Wraps an image url and the image dimensions into one object.
     */
    public static class MeasuredImage {

        public final String url;
        public final int width;
        public final int height;

        /**
         * Constructor.
         * @param url    image url
         * @param width  image width
         * @param height image height
         */
        private MeasuredImage(String url, int width, int height) {
            super();
            this.url = url;
            this.width = width;
            this.height = height;
        }

        @NonNull
        @Override
        public String toString() {
            return width + "x" + height + "-px image at " + url;
        }
    }
}
