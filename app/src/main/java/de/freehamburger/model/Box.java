package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <pre>
 * "box" : {
 * "title" : "Rätsel um verschwundenen Interpol-Chef",
 * "subtitle" : "Auf China-Reise",
 * "images" : {
 *  "title" : "Interpol-Präsident Meng Hongwei.",
 *  "copyright" : "AP",
 *  "alttext" : "Interpol-Präsident Meng Hongwei",
 *  "preferredVariants" : "16x9",
 *  "type" : "image",
 *  "videowebl" : {
 *      "imageurl" : "https://www.tagesschau.de/ausland/hongwei-101~_v-videowebl.jpg"
 *  }
 *
 * "box": {
 *  "title": "Untersuchungsanlage ",
 *  "subtitle": "Untersuchungsanlage ",
 *  "text": "<strong>Grundgesamtheit:</strong> Wahlberechtigte in Deutschland<br/><strong>Stichprobe:</strong> Repräsentative Zufallsauswahl / Dual Frame (Festnetz- und Mobilfunkstichprobe)<br/><strong>Erhebungsverfahren:</strong> Telefoninterviews (CATI) <br/><strong>Fallzahl: </strong> 1.035 Wahlberechtigte<br/><strong>Erhebungszeitraum:  </strong>17. bis 19. September 2018<br/><strong>Gewichtung:</strong> nach soziodemographischen Merkmalen<br/><strong>Fehlertoleranz: </strong>1,4* bis 3,1** Prozentpunkte<br/><strong>Durchführendes Institut:</strong> Infratest dimap<br/><br/>* bei einem Anteilswert von 5 Prozent ** bei einem Anteilswert von 50 Prozent",
 *  "source": ""
 * }
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
    @NonNull
    static Box parse(@NonNull final JsonReader reader) throws IOException {
        final Box box = new Box();
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
            } else if ("images".equals(name)) {
                box.image = Image.parse(reader);
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();
        return box;
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
        return "Box \"" + title + "\" \"" + text + "\"";
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
                } else if ("videowebl".equals(name)) {
                    reader.beginObject();
                    String imageurl = reader.nextName();
                    if ("imageurl".equals(imageurl)) {
                        image.url = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
            return image;
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
