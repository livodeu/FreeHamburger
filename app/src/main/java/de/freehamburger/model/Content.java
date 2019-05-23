package de.freehamburger.model;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.annotation.VisibleForTesting;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.freehamburger.util.Util;

/**
 *
 */
public class Content implements Serializable {

    public static final String FONT_FACE_IMAGE_TITLE = "sans-serif-condensed";
    private static final String[] H_REPLACE_ME = new String[] {"<h2>", "</h2>"};
    private static final CharSequence[] H_REPLACE_WITH = new CharSequence[] {"<h4>", "</h4>"};

    @NonNull private final List<ContentElement> elementList = new ArrayList<>(16);
    @NonNull private final List<Video> videoList = new ArrayList<>();
    @NonNull private final List<Audio> audioList = new ArrayList<>();
    @NonNull private final List<Related> relatedList = new ArrayList<>();
    private String text;
    private String plainText;

    /**
     * Parses the given JsonReader to retrieve a Content element.
     * @param reader JsonReader
     * @return Content
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Content parseContent(@NonNull final JsonReader reader) throws IOException {
        final Content content = new Content();
        reader.beginArray();
        // attach a sequential id to each element so that we can restore the original order any time
        int order = ContentElement.MIN_ORDER;
        //
        for (; reader.hasNext(); ) {
            ContentElement ce = ContentElement.parseContentElement(reader);
            ce.setOrder(order++);
            final String type = ce.getType();
            //TODO we currently ignore "webview"
            if (ContentElement.TYPE_TEXT.equals(type)
                    || ContentElement.TYPE_HEADLINE.equals(type)
                    || ContentElement.TYPE_QUOTATION.equals(type)
                    || ContentElement.TYPE_IMAGE_GALLERY.equals(type)
                    || ContentElement.TYPE_BOX.equals(type)
                    || ContentElement.TYPE_LIST.equals(type)
                    || ContentElement.TYPE_VIDEO.equals(type)
                    || ContentElement.TYPE_AUDIO.equals(type)
                    || ContentElement.TYPE_RELATED.equals(type)) {
                content.elementList.add(ce);
            }
        }
        reader.endArray();
        // build the text from the relevant content elements, textBuilder will usually end up with a 4-digit length, few are less, some are beyond 20K chars
        final StringBuilder textBuilder = new StringBuilder(1024);
        final StringBuilder plainTextBuilder = new StringBuilder(768);
        for (ContentElement ce : content.elementList) {
            String type = ce.getType();
            if (ContentElement.TYPE_TEXT.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    StringBuilder cs = Util.removeUlliAndOlli(value.replace('\t', ' ').replace("<br />", "\n").replace("\r\n\r\n", "\n"));
                    textBuilder.append(cs).append("<br><br>");
                    plainTextBuilder.append(Util.removeLinks(cs)).append("\n");
                }
            } else if (ContentElement.TYPE_HEADLINE.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    textBuilder.append(TextUtils.replace(value, H_REPLACE_ME, H_REPLACE_WITH)).append("<br>");  // trailing <br> looks better if FROM_HTML_MODE_COMPACT is used
                }
            } else if (ContentElement.TYPE_QUOTATION.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    textBuilder.append("<i><blockquote><font color=\"#064a91\">❠&nbsp;&nbsp;").append(value).append("</font></blockquote></i><br><br>");
                    plainTextBuilder.append(value);
                }
            } else if (ContentElement.TYPE_LIST.equals(type)) {
                Lyst list = ce.getList();
                if (list != null && list.hasUrls()) {
                    String title = list.getTitle();
                    if (!TextUtils.isEmpty(title)) textBuilder.append("<b>").append(title).append("</b><br>");
                    textBuilder.append("<ul>\n");
                    for (String url : list.getUrls()) {
                        textBuilder.append("<li>&nbsp;&nbsp;").append(url).append("</li>\n");
                    }
                    textBuilder.append("</ul>\n<br>\n");
                }
            } else if (ContentElement.TYPE_BOX.equals(type)) {
                Box box = ce.getBox();
                if (box != null) {
                    textBuilder.append("<p>");
                    String boxTitle = box.getTitle();
                    if (!TextUtils.isEmpty(boxTitle)) {
                        textBuilder.append("<h6><font color=\"#064a91\">").append(boxTitle).append("</font></h6>");
                    }
                    Box.Image boxImage = box.getImage();
                    if (boxImage != null && !TextUtils.isEmpty(boxImage.getUrl())) {
                        textBuilder.append("<img src=\"").append(boxImage.getUrl()).append("\"/><br>");
                    }
                    String boxText = box.getText();
                    if (!TextUtils.isEmpty(boxText)) {
                        textBuilder.append("<font color=\"#064a91\"><small>").append(boxText).append("</small></font>");
                    }
                    textBuilder.append("</p><br><br>");
                }
            } else if (ContentElement.TYPE_IMAGE_GALLERY.equals(type)) {
                Gallery gallery = ce.getGallery();
                if (gallery != null) {
                    for (Gallery.Item item : gallery.getItems()) {
                        Map<Gallery.Quality, String> pics = item.getImages();
                        String url;
                        if (pics.containsKey(Gallery.Quality.M)) {
                            url = pics.get(Gallery.Quality.M);
                        } else if (pics.containsKey(Gallery.Quality.L)) {
                            url = pics.get(Gallery.Quality.L);
                        } else if (pics.containsKey(Gallery.Quality.S)) {
                            url = pics.get(Gallery.Quality.S);
                        } else {
                            url = null;
                        }
                        if (url != null) {
                            textBuilder.append("<br><img src=\"").append(url).append("\"/>");
                            if (!TextUtils.isEmpty(item.getTitle())) {
                                // do not change the font face without adjusting TextViewImageSpanClickHandler
                                // the <br> between </font> and </small> (sometimes) avoids an apparent bug that there is excess vertical space before the last line
                                textBuilder.append("<p><small><font face=\"").append(FONT_FACE_IMAGE_TITLE).append("\">").append(item.getTitle()).append("</font><br></small></p>");
                            }
                            textBuilder.append("<br>");
                        }
                    }
                }
            } else if (ContentElement.TYPE_RELATED.equals(type)) {
                Related[] relatedArray = ce.getRelated();
                if (relatedArray != null && relatedArray.length > 0) {
                    content.relatedList.addAll(Arrays.asList(relatedArray));
                }
            } else if (ContentElement.TYPE_VIDEO.equals(type)) {
                Video video = ce.getVideo();
                if (video != null) content.videoList.add(video);
            } else if (ContentElement.TYPE_AUDIO.equals(type)) {
                Audio audio = ce.getAudio();
                if (audio != null) content.audioList.add(audio);
            }
        }
        content.text = textBuilder.toString();
        content.plainText = plainTextBuilder.toString();
        // cut off trailing <br> elements
        while (content.text.endsWith("<br>")) {
            content.text = content.text.substring(0, content.text.length() - 4);
        }
        while (content.plainText.endsWith("\n")) {
            content.plainText = content.plainText.substring(0, content.plainText.length() - 1);
        }
        return content;
    }

    /**
     * Fixes wrong (" ") quotation marks in {@link #text} and {@link #plainText}.
     */
    void fixQuotationMarks() {
        this.text = Util.fixQuotationMarks(this.text).toString();
        this.plainText = Util.fixQuotationMarks(this.plainText).toString();
    }

    /**
     * @return List of Audio elements contained in the Content element.
     */
    @NonNull
    public List<Audio> getAudioList() {
        return audioList;
    }

    @NonNull
    @VisibleForTesting()
    public List<ContentElement> getElementList() {
        return elementList;
    }

    public String getPlainText() {
        return plainText;
    }

    @NonNull
    public List<Related> getRelatedList() {
        return relatedList;
    }

    /**
     * @return HTML text
     */
    public String getText() {
        return text;
    }

    /**
     * @return List of Video elements contained in the Content element.
     */
    @NonNull
    public List<Video> getVideoList() {
        return videoList;
    }

    /**
     * @return {@code true} if the audio list is not empty
     */
    public boolean hasAudio() {
        return !audioList.isEmpty();
    }

    /**
     * @return {@code true} if the video list is not empty
     */
    public boolean hasVideo() {
        return !videoList.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return text != null ? text : "<no content>";
    }

    /**
     * A sub-element of {@link Content}
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static class ContentElement implements Comparable<ContentElement>, Serializable {

        static final int MIN_ORDER = 1;
        static final String TYPE_TEXT = "text";
        static final String TYPE_HEADLINE = "headline";
        static final String TYPE_IMAGE_GALLERY = "image_gallery";
        static final String TYPE_AUDIO = "audio";
        static final String TYPE_VIDEO = "video";
        static final String TYPE_RELATED = "related";
        static final String TYPE_BOX = "box";
        static final String TYPE_LIST = "list";
        static final String TYPE_QUOTATION = "quotation";
        static final String TYPE_SOCIALMEDIA = "socialmedia";
        static final String TYPE_WEBVIEW = "webview";

        /** the order by which the ContentElements originally appeared in the Content element */
        @IntRange(from = MIN_ORDER) private int order;
        @Nullable @ContentType
        private String type;
        @Nullable private String value;
        @Nullable private Video video;
        @Nullable private Audio audio;
        @Nullable private Gallery gallery;
        @Nullable private Box box;
        @Nullable private Lyst list;
        @Nullable private Related[] related;

        /**
         * @param reader JsonReader
         * @return ContentElement
         * @throws IOException if an I/O error occurs
         */
        @NonNull
        private static ContentElement parseContentElement(@NonNull final JsonReader reader) throws IOException {
            final ContentElement ce = new ContentElement();
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
                if ("type".equals(name)) {
                    ce.type = reader.nextString();
                } else if ("value".equals(name)) {
                    ce.value = reader.nextString();
                } else if ("gallery".equals(name)) {
                    ce.gallery = Gallery.parse(reader);
                } else if ("video".equals(name)) {
                    ce.video = Video.parseVideo(reader);
                } else if ("audio".equals(name)) {
                    ce.audio = Audio.parseAudio(reader);
                } else if ("box".equals(name)) {
                    ce.box = Box.parse(reader);
                } else if ("list".equals(name)) {
                    ce.list = Lyst.parse(reader);
                } else if ("quotation".equals(name)) {
                    reader.beginObject();
                    reader.nextName();
                    ce.value = reader.nextString();
                    reader.endObject();
                } else if ("related".equals(name)) {
                    ce.related = Related.parse(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return ce;
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(@NonNull ContentElement o) {
            return Integer.compare(order, o.order);
        }

        @Nullable
        Audio getAudio() {
            return audio;
        }

        @Nullable
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public Box getBox() {
            return box;
        }

        /*
         * @return the order by which the ContentElement originally appeared in the parent Content element
         *
        @IntRange(from = MIN_ORDER)
        public int getOrder() {
            return order;
        }*/

        @Nullable
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public Gallery getGallery() {
            return gallery;
        }

        @Nullable
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public Lyst getList() {
            return list;
        }

        @Nullable
        Related[] getRelated() {
            return related;
        }

        @ContentType
        @Nullable
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public String getType() {
            return type;
        }

        @Nullable
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        public String getValue() {
            return value;
        }

        @Nullable
        Video getVideo() {
            return video;
        }

        void setOrder(@IntRange(from = MIN_ORDER) int order) {
            this.order = order;
        }

        /** {@inheritDoc} */
        @Override
        @NonNull
        public String toString() {
            return type + " " + value;
        }

        /**
         * See <a href="https://developer.android.com/studio/write/annotations#enum-annotations">here</a>
         */
        @Retention(RetentionPolicy.SOURCE)
        @StringDef({TYPE_TEXT, TYPE_HEADLINE, TYPE_IMAGE_GALLERY, TYPE_VIDEO, TYPE_AUDIO, TYPE_BOX, TYPE_LIST, TYPE_RELATED, TYPE_QUOTATION, TYPE_SOCIALMEDIA, TYPE_WEBVIEW})
        @interface ContentType {}
    }

    /**
     *
     */
    public static class ContentTagHandler implements Html.TagHandler {

        public ContentTagHandler() {
            super();
        }

        /** {@inheritDoc} */
        @Override
        public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
            char lastChar = output.length() > 0 ? output.charAt(output.length() - 1) : 0;
            if (opening) {
                switch (tag) {
                    case "p":
                    case "div":
                        if (lastChar != '\n') output.append('\n');
                        break;
                    case "ul":
                        output.append('\n');
                        break;
                    case "li":
                        output.append(lastChar == '\n' ? " •  " : "\n •  ");
                        break;
                }

            }
        }
    }
}
