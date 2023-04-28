package de.freehamburger.model;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.freehamburger.BuildConfig;

/**
 * <pre>
 * "box" : {
 *  "image" : {
 *   "title" : "",
 *   "copyright" : "BUBU",
 *   "alttext" : "Ein Flugzeug.",
 *   "imageVariants" : {
 *           "1x1-144" : "https://images.tagesschau.de/image/…/1x1-144.jpg",
 *           …
 *           "16x9-1920" : "https://images.tagesschau.de/image/…/16x9-1920.jpg"
 *   },
 *   "type" : "image"
 *  },
 * "link" : "<a href=\"https://www.tagesschau.de/api2u/wirtschaft/flugzeug-101.json\" type=\"intern\">mehr",
 * "subtitle" : "Wirtschaftsförderung",
 * "text" : "Die Maßnahmen werden von Airlines gelobt, stoßen aber auf Widerstand bei Umweltschützern.",
 * "title" : "Flughafen will Flugzeuge"
 * </pre>
 */
public class Box implements Serializable {

    /** matches multiple white spaces */
    private static final Pattern MULTIPLE_WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

    private Image image;
    private String title;
    private String subtitle;
    private String text;
    private String link;

    /**
     * Parses the given JsonReader to retrieve a Box element.
     * @param reader JsonReader
     * @return Box
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @Nullable
    static Box parse(@NonNull final JsonReader reader) throws IOException {
        final Box box = new Box();
        boolean returnNull = false;
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
                box.title = reader.nextString();
            } else if ("subtitle".equals(name)) {
                box.subtitle = reader.nextString();
            } else if ("text".equals(name)) {
                String txt = reader.nextString().replace('\n', ' ');
                // for some mysterious reason, the authors like to insert a mix of 160s and 32s here...
                Matcher m = MULTIPLE_WHITE_SPACE_PATTERN.matcher(txt);
                box.text = m.replaceAll(" ");
                //
                int textLinkStart = box.text.indexOf("<a");
                if (textLinkStart > -1) {
                    int textLinkEnd = box.text.indexOf("</a>", textLinkStart + 2);
                    if (textLinkEnd > textLinkStart) {
                        String link = box.text.substring(textLinkStart, textLinkEnd);
                        int hrefStart = link.indexOf("href=\"");
                        int hrefEnd = hrefStart > -1 ? link.indexOf("\"", hrefStart + 6) : -1;
                        String href = hrefStart > -1 && hrefEnd > hrefStart ? link.substring(hrefStart + 6, hrefEnd) : null;
                        if (TextUtils.isEmpty(href)) {
                            /*
                             This would be link like this:
                             <a href="" externalId="tagesschau_fm-link-generated-href-hash-97617a9dc2732ed9f55507f84c4287af">mehr</a>
                             <a href="" externalId="tagesschau_fm-link-generated-href-hash-987654321abcdef1234567890abcdef0">mehr</a>
                             In the end, the user would see a non-working link with some blah-blah teaser text around it
                             so it seems better so skip this element altogether
                             */
                            returnNull = true;
                        }
                        if (link.contains("type=\"intern\"")) {
                            // remove internal links
                            box.text = box.text.substring(0, textLinkStart) + box.text.substring(textLinkEnd + 4);
                        } else {
                            // insert a <small> into external links because box text will have that, too (see handling of the Box type in Content.parseContent())
                            int bracket1 = box.text.indexOf('>', textLinkStart + 2);
                            String linkText = box.text.substring(bracket1 + 1, textLinkEnd);
                            box.text = box.text.substring(0, bracket1) + '<' + Content.TAG_BOX_TEXT + '>' + linkText + "</" + Content.TAG_BOX_TEXT + '>' + box.text.substring(textLinkEnd);
                        }
                    }
                }
            } else if ("link".equals(name)) {
                box.link = reader.nextString();
            } else if ("image".equals(name)) {
                box.image = Image.parse(reader);
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();
        if (BuildConfig.DEBUG && returnNull) {
            android.util.Log.w(Box.class.getSimpleName(), "Skipping " + box);
        }
        return returnNull ? null : box;
    }

    @Nullable
    public Image getImage() {
        return image;
    }

    @Nullable
    public String getLink() {
        return link;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return "Box \"" + title + "\"" + (text != null ? " \"" + text + "\"" : " with no text") + (image != null ? ", image \"" + image + "\"" : ", with no image");
    }

    /**
     *
     */
    public static class Image implements Serializable {

        private String title;
        private String alttext;
        private String copyright;
        private String url;

        @NonNull
        private static Image parse(@NonNull final JsonReader reader) throws IOException {
            final Image image = new Image();
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
                    image.title = reader.nextString();
                } else if ("alttext".equals(name)) {
                    image.alttext = reader.nextString();
                } else if ("copyright".equals(name)) {
                    image.copyright = reader.nextString();
                } else if ("imageVariants".equals(name)) {
                    parseImageVariants(image, reader);
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
            return image;
        }

        /**
         * Parses the "imageVariants" block.<br>
         * Consists of a list of urls with its keys having the format "&lt;imageformat&gt;-&lt;width&gt;".
         * @param image Image object to set the image url in.
         * @param reader JsonReader to read from.
         * @throws IOException if they messed up the data
         */
        private static void parseImageVariants(@NonNull final Image image, @NonNull final JsonReader reader) throws IOException {
            reader.beginObject();
            for (; reader.hasNext(); ) {
                final String name = reader.nextName();
                JsonToken next = reader.peek();
                if (next == JsonToken.NAME) {
                    continue;
                }
                if (next == JsonToken.NULL) {
                    reader.skipValue();
                    continue;
                }
                if ("16x9-256".equals(name)) {                      // 256 x 144
                    if (TextUtils.isEmpty(image.url)) image.url = reader.nextString(); else reader.skipValue();
                } else if ("16x9-512".equals(name)) {               // 512 x 288
                    if (TextUtils.isEmpty(image.url)) image.url = reader.nextString(); else reader.skipValue();
                } else if ("16x9-960".equals(name)) {               // 960 x 540
                    image.url = reader.nextString();
                } else if ("1x1-640".equals(name)) {                // 640 x 640
                    if (TextUtils.isEmpty(image.url)) image.url = reader.nextString(); else reader.skipValue();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        @Nullable
        public String getAlttext() {
            return alttext;
        }

        @Nullable
        public String getCopyright() { return copyright; }

        @Nullable
        public String getTitle() {
            return title;
        }

        @Nullable
        public String getUrl() {
            return url;
        }

        /** {@inheritDoc} */
        @Override
        @NonNull
        public String toString() {
            return "Box.Image \"" + title + "\" @ " + url;
        }
    }
}
