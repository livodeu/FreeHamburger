package de.freehamburger.model;

import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.MalformedJsonException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.util.PositionedSpan;
import de.freehamburger.util.Util;

/**
 *
 */
public class Content implements Serializable {

    public static final String FONT_FACE_IMAGE_TITLE = "sans-serif-condensed";
    private static final String HTML_BR = "<br>";
    /** marker for superfluous new lines to be removed (this String can be anything that is very unlikely to occur in the text naturally) */
    public static final String REMOVE_NEW_LINE = "#####";
    /** the 🔗 symbol (unicode 0x1f517) */
    static final String MARK_LINK = "\uD83D\uDD17";
    private static String colorQuotation = "#064a91";
    /** The color that a box element will be rendered in */
    private static String colorBox = "#064a91";
    /**
     * The html tag that a box element will be wrapped into ({@link #colorBox} will be applied, too)<br>
     * For usable tags see {@link Html Html.HtmlToSpannedConverter.handleStartTag()}.<br>
     * Background colors apply only to the letters, they do not extend to the edges of the screen!<br>
     * Apparently tags cannot be nested, so a background color would not be rendered if there are other tags inside this one!
     */
    private static final String TAG_BOX = "p";
    /**
     * The html tag that a box element title will be wrapped into ({@link #colorBox} will be applied, too)<br>
     * For usable tags see {@link Html Html.HtmlToSpannedConverter.handleStartTag()}<br>
     * h1 - h6 will be rendered bold with a text size factor defined in Html.HtmlToSpannedConverter.HEADING_SIZES
     */
    @Deprecated
    private static final String TAG_BOX_TITLE = "h6";
    /**
     * The html tag that a box element text will be wrapped into ({@link #colorBox} will be applied, too)<br>
     * For usable tags see {@link Html Html.HtmlToSpannedConverter.handleStartTag()} <br>
     * &lt;small&gt; will be rendered with a text size of 80% (see Html.HtmlToSpannedConverter.handleEndTag())
     */
    static final String TAG_BOX_TEXT = "small";
    /**
     * The html tag that a box element link will be wrapped into ({@link #colorBox} will be applied, too)<br>
     * For usable tags see {@link Html Html.HtmlToSpannedConverter.handleStartTag()} <br>
     * &lt;small&gt; will be rendered with a text size of 80% (see Html.HtmlToSpannedConverter.handleEndTag())
     */
    private static final String TAG_BOX_LINK = "small";
    /**
     * The html tag that a list title will be wrapped into.
     */
    private static final String TAG_LIST_TITLE = "b";
    /**
     * The html tag that a list item will start with.
     */
    private static final String TAG_LISTITEM_START = "font face=\"sans-serif-condensed\"";
    /**
     * The html tag that a list item will end with.
     */
    private static final String TAG_LISTITEM_END = "font";

    @NonNull private final List<ContentElement> elementList = new ArrayList<>(16);
    @NonNull private final List<Video> videoList = new ArrayList<>();
    @NonNull private final List<Audio> audioList = new ArrayList<>();
    @NonNull private final List<Related> relatedList = new ArrayList<>();
    /** the HTML text */
    String text;
    /** the plain text */
    String plainText;

    /**
     * Parses the given JsonReader to retrieve a Content element.
     * @param reader JsonReader
     * @param flags flags
     * @return Content
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    static Content parseContent(@NonNull final JsonReader reader, @News.Flag final int flags) throws IOException {
        final Content content = new Content();
        reader.beginArray();
        // attach a sequential id to each element so that we can restore the original order any time
        int order = ContentElement.MIN_ORDER;
        //
        for (; reader.hasNext(); ) {
            ContentElement ce = ContentElement.parseContentElement(reader);
            ce.setOrder(order++);
            final String type = ce.getType();
            // we currently ignore ContentElements of type "webview"
            if (ContentElement.TYPE_TEXT.equals(type)) {
                String value = ce.getValue();
                /*
                    At one point (2020-03-06), this was found:
                    {
                        "value": "!function(){\"use strict\";window.addEventListener(\"message\",function(a){if(void 0!==a.data[\"datawrapper-height\"])for(var e in a.data[\"datawrapper-height\"]){var t=document.getElementById(\"datawrapper-chart-\"+e)||document.querySelector(\"iframe[src*='\"+e+\"']\");t&&(t.style.height=a.data[\"datawrapper-height\"][e]+\"px\")}})}(); ",
                        "type": "text"
                    },

                    BTW, the value's content was also displayed literally in the official app that day…
                 */
                if (value != null && !value.startsWith("!")) {
                    content.elementList.add(ce);
                }
            } else if (ContentElement.TYPE_HEADLINE.equals(type)
                    || ContentElement.TYPE_QUOTATION.equals(type)
                    || ContentElement.TYPE_IMAGE_GALLERY.equals(type)
                    || ContentElement.TYPE_BOX.equals(type)
                    || (ContentElement.TYPE_HTMLEMBED.equals(type) && (flags & News.FLAG_INCLUDE_HTMLEMBED) > 0)
                    || ContentElement.TYPE_LIST.equals(type)
                    || ContentElement.TYPE_VIDEO.equals(type)
                    || ContentElement.TYPE_AUDIO.equals(type)
                    || ContentElement.TYPE_RELATED.equals(type)) {
                content.elementList.add(ce);
            }
        }
        reader.endArray();
        // build the text from the relevant content elements, textBuilder will usually end up with a 4-digit length, few are less, some are beyond 20K chars
        final StringBuilder htmlTextBuilder = new StringBuilder(2048);
        final StringBuilder plainTextBuilder = new StringBuilder(768);
        for (ContentElement ce : content.elementList) {
            final String type = ce.getType();
            if (ContentElement.TYPE_TEXT.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    // the value has to be pre-processed because Html.fromHtml() which is used in NewsActivity.applyNews() is not perfect…
                    CharSequence cs = Util.replaceAll(value, new CharSequence [] {"<br />", "\t", "\r\n\r\n", "&nbsp;"}, new CharSequence[] {"\n", " ", "\n", " "});
                    StringBuilder sb = Util.removeHtmlLists(cs);
                    // create html text
                    htmlTextBuilder.append(sb).append("<br><br>");
                    // create plain text
                    Spanned spannedPlainText = Util.fromHtml(null, Util.removeLinks(sb).toString(), null);
                    plainTextBuilder.append(spannedPlainText).append('\n');
                }
            } else if (ContentElement.TYPE_HEADLINE.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    htmlTextBuilder.append(Util.replaceAll(value, new CharSequence[] {"<h2>", "</h2>"}, new CharSequence[] {"<h4>", "</h4>"}));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // trailing <br> looks better if FROM_HTML_MODE_COMPACT is used
                        htmlTextBuilder.append(HTML_BR);
                    }
                }
            } else if (ContentElement.TYPE_QUOTATION.equals(type)) {
                String value = ce.getValue();
                if (value != null) {
                    htmlTextBuilder.append("<i><blockquote><font color=\"").append(colorQuotation).append("\">❠&nbsp;&nbsp;").append(value).append("</font></blockquote></i><br><br>");
                    plainTextBuilder.append(value);
                }
            } else if (ContentElement.TYPE_LIST.equals(type)) {
                Lyst list = ce.getList();
                if (list != null && list.hasUrls()) {
                    String title = list.getTitle();
                    if (!TextUtils.isEmpty(title)) {
                        htmlTextBuilder.append('<').append(TAG_LIST_TITLE).append('>').append(title).append("</").append(TAG_LIST_TITLE).append("><br>");
                    }
                    htmlTextBuilder.append("<ul>\n");
                    for (String url : list.getUrls()) {
                        // url is not just a url but a whole <a href="...">url</a> element
                        htmlTextBuilder.append("<li>&nbsp;&nbsp;<").append(TAG_LISTITEM_START).append('>').append(url).append("</").append(TAG_LISTITEM_END).append("></li>\n");
                    }
                    htmlTextBuilder.append("</ul>\n<br>\n");
                }
            } else if (ContentElement.TYPE_HTMLEMBED.equals(type)) {
                HtmlEmbed htmlEmbed = ce.htmlEmbed;
                // check for valid urls, especially skip those that start with App.URL_PREFIX
                if (htmlEmbed != null && htmlEmbed.getUrl() != null && !htmlEmbed.getUrl().startsWith(App.URL_PREFIX)) {
                    String label = null;
                    try {label = Uri.parse(htmlEmbed.getUrl()).getHost(); } catch (Exception ignored) {}
                    if (TextUtils.isEmpty(label)) label = htmlEmbed.getService();
                    if (!TextUtils.isEmpty(label)) {
                        // the 🔗 symbol will be replaced later in Blob.parseApi()
                        // the <xsm></xsm> tag will be resolved in Util.fromHtml()
                        htmlTextBuilder.append("<p style=\"background-color:lightgray\">")
                                .append("<a href=\"")
                                .append(htmlEmbed.getUrl())
                                .append("\">↗&nbsp;")
                                .append(label)
                                .append("</a><br>")
                                .append(PositionedSpan.TAG_XSMALL_OPENING)
                                .append(MARK_LINK)
                                .append(PositionedSpan.TAG_XSMALL_CLOSING)
                                .append("</p><br>\n");
                    }
                }
            } else if (ContentElement.TYPE_BOX.equals(type)) {
                Box box = ce.getBox();
                if (box != null) {
                    // add the box, followed by a <br>
                    htmlTextBuilder.append("<").append(TAG_BOX).append(" style=\"background-color:lightgray\"").append('>');
                    String boxTitle = box.getTitle();
                    if (!TextUtils.isEmpty(boxTitle)) {
                        // add the box title
                        //FIXME apparently, a <h6> causes the background color not being applied because in Html.setSpanFromMark() 'where' equals 'len'…
                        htmlTextBuilder.append("<font color=\"").append(colorBox).append("\">").append(boxTitle).append("</font>");
                    }
                    Box.Image boxImage = box.getImage();
                    if (boxImage != null && !TextUtils.isEmpty(boxImage.getUrl())) {
                        // add the box image, followed by a <br>
                        htmlTextBuilder.append("<br><img src=\"").append(boxImage.getUrl()).append("\"/><br>");
                    }
                    String boxText = box.getText();
                    if (!TextUtils.isEmpty(boxText)) {
                        htmlTextBuilder
                                .append("<font color=\"").append(colorBox).append("\"><").append(TAG_BOX_TEXT).append('>')
                                .append(boxText);
                        String imageCopyright = boxImage != null ? boxImage.getCopyright() : null;
                        if (!TextUtils.isEmpty(imageCopyright)) {
                            htmlTextBuilder.append(" (&copy; ").append(imageCopyright).append(")");
                        }
                        // appending a <br> directly after boxText and before TAG_BOX_TEXT avoids a strange vertical gap before the last line
                        htmlTextBuilder.append("<br></").append(TAG_BOX_TEXT).append("></font>");
                    }
                    String boxLink = box.getLink();
                    if (!TextUtils.isEmpty(boxLink)) {
                        htmlTextBuilder.append("<font color=\"").append(colorBox).append("\"><").append(TAG_BOX_LINK).append('>')
                        // e.g.: "link": "<a href=\"https://www.server.nl/api7/buitenland/guldenvlies.json\" type=\"intern\">meer</a>",
                        .append(boxLink).append("</").append(TAG_BOX_LINK).append("></font>");
                    }
                    htmlTextBuilder.append("</").append(TAG_BOX).append(">");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) htmlTextBuilder.append("<br>");
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
                            htmlTextBuilder.append("<img src=\"").append(url).append("\"/>");
                            if (!TextUtils.isEmpty(item.getTitle())) {
                                htmlTextBuilder.append("<p>");
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) htmlTextBuilder.append(REMOVE_NEW_LINE);
                                // do not change the font face without adjusting TextViewImageSpanClickHandler
                                // the <br> between </font> and </small> (sometimes) avoids an apparent bug that there is excess vertical space before the last line
                                htmlTextBuilder.append("<small><font face=\"").append(FONT_FACE_IMAGE_TITLE).append("\">");
                                htmlTextBuilder.append(item.getTitle());
                                if (!TextUtils.isEmpty(item.getCopyright())) {
                                    htmlTextBuilder.append(" (&copy; ").append(item.getCopyright()).append(")");
                                }
                                htmlTextBuilder.append("</font><br></small></p>");
                            }
                            // for Html.FROM_HTML_MODE_COMPACT, add a new line
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) htmlTextBuilder.append(HTML_BR);
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
        content.text = htmlTextBuilder.toString();
        content.plainText = plainTextBuilder.toString();
        // cut off trailing <br> elements
        while (content.text.endsWith(HTML_BR)) {
            content.text = content.text.substring(0, content.text.length() - HTML_BR.length());
        }
        while (content.plainText.endsWith("\n")) {
            content.plainText = content.plainText.substring(0, content.plainText.length() - 1);
        }
        return content;
    }

    public static void setColorBox(@NonNull String color) {
        colorBox = color;
    }

    public static void setColorQuotation(@NonNull String color) {
        colorQuotation = color;
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
    public String getHtmlText() {
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

    /**
     * Replaces occurrences of {@code what} in {@link #text} with {@code with}.
     * @param what to replace
     * @param with replacements
     */
    public void replace(final String[] what, final String[] with) {
        if (this.text == null || what == null || with == null) return;
        final int n = what.length;
        if (with.length != n) return;
        for (int i = 0; i < n; i++) {
            if (what[i] == null || with[i] == null) continue;
            this.text = this.text.replace(what[i], with[i]);
        }
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
        static final String TYPE_HTMLEMBED = "htmlEmbed";
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
        @Nullable private HtmlEmbed htmlEmbed;

        /**
         * @param reader JsonReader
         * @return ContentElement
         * @throws IOException if an I/O error occurs
         */
        @NonNull
        private static ContentElement parseContentElement(@NonNull final JsonReader reader) throws IOException {
            final ContentElement ce = new ContentElement();
            String name = null;
            try {
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
                    } else if ("htmlEmbed".equals(name)) {
                        ce.htmlEmbed = HtmlEmbed.parse(reader);
                    } else if ("quotation".equals(name)) {
                        reader.beginObject();
                        reader.nextName();
                        ce.value = reader.nextString();
                        reader.endObject();
                    } else if ("related".equals(name)) {
                        ce.related = Related.parse(reader);
                    } else {
                        // known elements that wil be ignored: "tracking", "social"
                        if (BuildConfig.DEBUG && !"tracking".equals(name) && !"social".equals(name))
                            android.util.Log.w("Content.ContentElement", "Skipping content element '" + name + "'");
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } catch (MalformedJsonException mje) {
                if (BuildConfig.DEBUG) android.util.Log.e(Content.class.getSimpleName(), mje.toString());
                throw new InformativeJsonException(mje, reader);
            }
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
        public HtmlEmbed getHtmlEmbed() {
            return htmlEmbed;
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
        @StringDef({TYPE_TEXT, TYPE_HEADLINE, TYPE_IMAGE_GALLERY, TYPE_VIDEO, TYPE_AUDIO, TYPE_BOX, TYPE_HTMLEMBED, TYPE_LIST, TYPE_RELATED, TYPE_QUOTATION, TYPE_SOCIALMEDIA, TYPE_WEBVIEW})
        @interface ContentType {}
    }
}
