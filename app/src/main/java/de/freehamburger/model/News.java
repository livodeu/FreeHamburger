package de.freehamburger.model;

import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.annotation.VisibleForTesting;
import android.text.Html;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 *
 */
public final class News implements Comparable<News>, Serializable {

    public static final String NEWS_TYPE_STORY = "story";
    public static final String NEWS_TYPE_WEBVIEW = "webview";
    public static final String NEWS_TYPE_VIDEO = "video";
    private static final String TAG = "News";
    /** Example: 2017-11-16T11:54:03.882+01:00 */
    private static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
    private static long nextid = 1L;

    private final Set<String> tags = new HashSet<>(8);
    private final Set<String> geotags = new HashSet<>(4);
    /** the streams of differenty qualities (highest number found was all 7 StreamQualities) */
    private final Map<StreamQuality, String> streams = new EnumMap<>(StreamQuality.class);
    private final Set<Region> regions = new HashSet<>(2);
    private final long id = nextid++;
    private final boolean regional;

    private boolean breakingNews;
    @Nullable private Content content;
    /** {@code true} if this News has undergone corrective processing according to the User's preferences */
    private boolean corrected;
    @Nullable private Date date;
    /** equivalent to {@link #date} */
    @IntRange(from = 0L) private long ts;
    /** a URL pointing to a json file */
    private String details;
    /** a URL pointing to a HTML file */
    private String detailsWeb;
    /** an id which is here used to check for equality */
    private String externalId;
    /** the 3rd level title */
    private String firstSentence;
    private String ressort;
    @Nullable private String shareUrl;
    private String shorttext;
    /** the 2nd level title used only in the {@link Source#HOME HOME} category */
    private String title;
    private TeaserImage teaserImage;
    /** the 1st level title */
    private String topline;
    @Nullable @NewsType private String type;
    /** the contents of {@link #topline} in lower case */
    private transient String toplineL;
    /** the contents of {@link #title} in lower case */
    private transient String titleL;
    /** the contents of {@link #firstSentence} in lower case */
    private transient String firstSentenceL;

    /**
     * Fixes the given News objects.<br>
     * This currently includes the correction of wrong (" ") quotation marks.
     * @param someNews Collection of News
     * @throws NullPointerException if {@code someNews} is {@code null}
     */
    static void correct(@NonNull final Collection<News> someNews) {
        for (News news : someNews) {
            correct(news);
        }
    }

    /**
     * Fixes the given News object.<br>
     * This currently includes the correction of wrong (" ") quotation marks.
     * @param news News
     * @throws NullPointerException if {@code news} is {@code null}
     */
    public static void correct(@NonNull final News news) {
        if (news.corrected) return;
        if (news.content != null) news.content.fixQuotationMarks();
        if (news.title != null) news.title = Util.fixQuotationMarks(news.title).toString();
        if (news.firstSentence != null) news.firstSentence = Util.fixQuotationMarks(news.firstSentence).toString();
        if (news.topline != null) news.topline = Util.fixQuotationMarks(news.topline).toString();
        news.corrected = true;
    }

    /**
     * Removes HTML elements from a String.
     * @param input input text possibly containing HTML elements
     * @return text without HTML elements
     */
    @Nullable
    private static String fixHtml(@Nullable final String input) {
        if (input == null) return null;
        if (input.length() == 0) return "";
        String output;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            output = Html.fromHtml(input, Html.FROM_HTML_MODE_COMPACT, null, null).toString();
        } else {
            output = Html.fromHtml(input).toString();
        }
        if (BuildConfig.DEBUG) {
            if (!output.equals(input)) Log.i(TAG, "fixHtml(\"" + input + "\") -> \"" + output + "\"");
        }
        return output;
    }

    /**
     * Parses the data read from the given JsonReader and fills the given News object.
     * @param reader JsonReader
     * @param news News
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if either parameter is {@code null}
     */
    private static void loop(@NonNull final JsonReader reader, @NonNull final News news) throws IOException {
        String name = null;
        final Set<TeaserImage> images = new HashSet<>(1);
        reader.beginObject();
        while (reader.hasNext()) {
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
                news.title = fixHtml(reader.nextString());
            } else if ("topline".equals(name)) {
                news.topline = fixHtml(reader.nextString());
            } else if ("firstSentence".equals(name)) {
                news.firstSentence = fixHtml(reader.nextString());
            } else if ("shareURL".equals(name)) {
                news.shareUrl = reader.nextString();
            } else if ("shorttext".equals(name)) {
                news.shorttext = fixHtml(reader.nextString());
            } else if ("externalId".equals(name)) {
                news.externalId = reader.nextString();
            } else if ("content".equals(name)) {
                news.content = Content.parseContent(reader);
            } else if ("date".equals(name)) {
                String dateString = reader.nextString();
                try {
                    news.date = parseDate(dateString);
                    if (news.date != null) news.ts = news.date.getTime();
                } catch (Exception ignored) {
                }
            } else if ("details".equals(name)) {
                news.details = reader.nextString();
            } else if ("detailsweb".equals(name)) {
                news.detailsWeb = reader.nextString();
            } else if ("ressort".equals(name)) {
                news.ressort = reader.nextString();
            } else if ("breakingNews".equals(name)) {
                news.breakingNews = reader.nextBoolean();
            } else if ("regionIds".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    news.regions.add(Region.getById(reader.nextInt()));
                }
                reader.endArray();
            } else if ("streams".equals(name)) {
                reader.beginObject();
                while (reader.hasNext()) {
                    try {
                        String q = reader.nextName().toUpperCase(Locale.US);
                        String url = reader.nextString();
                        if (!TextUtils.isEmpty(url)) {
                            news.streams.put(StreamQuality.valueOf(q), url);
                        }
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "While parsing 'streams': " + e.toString());
                    }
                }
                reader.endObject();
            } else if ("geotags".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    name = reader.nextName();
                    String tag = reader.nextString();
                    news.geotags.add(tag);
                    reader.endObject();
                }
                reader.endArray();
            } else if ("tags".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    reader.beginObject();
                    name = reader.nextName();
                    String tag = reader.nextString();
                    news.tags.add(tag);
                    reader.endObject();
                }
                reader.endArray();
            } else if ("teaserImage".equals(name)) {
                news.teaserImage = TeaserImage.parse(reader);
            } else if ("images".equals(name)) {
                // as of May 2019, the images element apparently always contains only 1 image
                reader.beginArray();
                while (reader.hasNext()) {
                    TeaserImage anotherTeaserImage = TeaserImage.parse(reader);
                    images.add(anotherTeaserImage);
                }
                reader.endArray();
            } else if ("type".equals(name)) {
                // type is usually "story", it often is "webview" for sports, "video" for videos, and <null> for weather
                news.type = reader.nextString();
            } else if (reader.hasNext()) {
                reader.skipValue();
            }
        }
        reader.endObject();

        // if there is no 'teaserImage' but the 'images' set is not empty, pick one of those and set it as 'teaserImage'
        if (news.teaserImage == null && !images.isEmpty()) {
            //if (BuildConfig.DEBUG) Log.i(TAG, "Using element of 'images' as 'teaserImage' in '" + news.getTitle() + "'");
            news.teaserImage = images.iterator().next();
        }
    }

    /**
     * See <a href="https://docs.oracle.com/javase/6/docs/api/java/text/SimpleDateFormat.html#synchronization">here</a>.
     * @param dateString "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
     * @return Date
     * @throws ParseException if the date could not be parsed
     */
    @Nullable
    synchronized static Date parseDate(String dateString) throws ParseException {
        return DF.parse(dateString);
    }

    /**
     * Parses the given JsonReader to retrieve a News element.
     * @param reader JsonReader
     * @param regional {@code true} if the News originates in the "regional" part of the json data
     * @return News
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if {@code reader} is {@code null}
     */
    @NonNull
    public static News parseNews(@NonNull final JsonReader reader, boolean regional) throws IOException {
        final News news = new News(regional);
        loop(reader, news);
        return news;
    }

    /**
     * Constructor.
     * @param regional {@code true} if the News originates in the "regional" part of the json data
     */
    private News(boolean regional) {
        super();
        this.regional = regional;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(@NonNull News o) {
        if (type == null) {
            return o.type == null ? 0 : 1;
        }
        //
        if (o.date == null) {
            // live streams do not have a date; this way they appear at the top of lists
            return date == null ? 0 : 1;
        }
        if (date == null) {
            return -1;
        }
        return Long.compare(o.ts, ts);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof News)) return false;
        News news = (News) o;
        return Objects.equals(externalId, news.externalId);
    }

    /**
     * Returns the Content element.<br>
     * May be null.
     * @return Content
     */
    @Nullable
    public Content getContent() {
        return this.content;
    }

    /**
     * @return the date
     */
    @Nullable
    public Date getDate() {
        return this.date;
    }

    /**
     * @return URL pointing to a json file
     */
    @Nullable
    public String getDetails() {
        return this.details;
    }

    /**
     * @return URL pointing to a HTML file
     */
    @Nullable
    public String getDetailsWeb() {
        return this.detailsWeb;
    }

    @Nullable
    public String getExternalId() {
        return this.externalId;
    }

    @Nullable
    public String getFirstSentence() {
        return this.firstSentence;
    }

    /**
     * @return firstSentence in lower case
     */
    @Nullable
    String getFirstSentenceLowerCase() {
        if (this.firstSentenceL == null && this.firstSentence != null) {
            this.firstSentenceL = this.firstSentence.toLowerCase(Locale.GERMAN);
        }
        return this.firstSentenceL;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Set<String> getGeotags() {
        return this.geotags;
    }

    public long getId() {
        return this.id;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Set<Region> getRegions() {
        return this.regions;
    }

    @Nullable
    public String getRessort() {
        return this.ressort;
    }

    @Nullable
    public String getShareUrl() {
        return this.shareUrl;
    }

    @Nullable
    public String getShorttext() {
        return this.shorttext;
    }

    @NonNull
    public Map<StreamQuality, String> getStreams() {
        return this.streams;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Set<String> getTags() {
        return this.tags;
    }

    @Nullable
    public TeaserImage getTeaserImage() {
        return this.teaserImage;
    }

    /**
     * @return the text that goes into the {@link de.freehamburger.views.NewsView#textViewFirstSentence large TextView} within a {@link de.freehamburger.views.NewsView NewsView}
     */
    @Nullable
    public String getTextForTextViewFirstSentence() {
        if (!TextUtils.isEmpty(this.firstSentence)) {
            return this.firstSentence;
        }
        if (!TextUtils.isEmpty(this.shorttext) && !this.shorttext.equals(getTextForTextViewTitle())) {
            return this.shorttext;
        }
        if (this.content != null && !TextUtils.isEmpty(this.content.getPlainText())) {
            return this.content.getPlainText();
        }
        return null;
    }

    @Nullable
    private String getTextForTextViewTitle() {
        if (!TextUtils.isEmpty(this.title) && !TextUtils.isEmpty(this.topline)) return this.title;
        return null;
    }

    @Nullable
    public String getTitle() {
        return this.title;
    }

    /**
     * @return title in lower case
     */
    @Nullable
    String getTitleLowerCase() {
        if (this.titleL == null && this.title != null) {
            this.titleL = this.title.toLowerCase(Locale.GERMAN);
        }
        return this.titleL;
    }

    @Nullable
    public String getTopline() {
        return this.topline;
    }

    /**
     * @return topline in lower case
     */
    @Nullable
    String getToplineLowerCase() {
        if (this.toplineL == null && this.topline != null) {
            this.toplineL = this.topline.toLowerCase(Locale.GERMAN);
        }
        return this.toplineL;
    }

    @Nullable
    @NewsType
    public final String getType() {
        return this.type;
    }

    /**
     * Determines whether this News provides a text that goes into the 3rd level text view next to the image.
     * @return {@code true} if this News has got text to fill the {@link de.freehamburger.views.NewsView#textViewFirstSentence large TextView} within a {@link de.freehamburger.views.NewsView NewsView}
     */
    public boolean hasTextForTextViewFirstSentence() {
        return !TextUtils.isEmpty(this.firstSentence)
                || (!TextUtils.isEmpty(this.shorttext) && !this.shorttext.equals(getTextForTextViewTitle()))
                || (this.content != null && !TextUtils.isEmpty(this.content.getPlainText()));
    }

    /**
     * Determines whether this News provides a text that goes into the 2nd level text view next to the date.
     * @return {@code true} if this News has got a text that goes into the {@link de.freehamburger.views.NewsView#textViewTitle title view} within a {@link de.freehamburger.views.NewsView NewsView}
     */
    public boolean hasTextForTextViewTitle() {
        return !TextUtils.isEmpty(this.title) && !TextUtils.isEmpty(this.topline);
    }

    /**
     * @return {@code true} if this News has got a title
     */
    public boolean hasTitle() {
        return !TextUtils.isEmpty(this.title);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.externalId);
    }

    /**
     * @return {@code true} if this News is considered to be "breaking".
     */
    public boolean isBreakingNews() {
        return this.breakingNews;
    }

    /**
     * @return {@code true} if the News originates in the "regional" part of the json data
     */
    public boolean isRegional() {
        return this.regional;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return "News (" + id
                + ", Title: \"" + title
                + "\", Type: \"" + type
                + "\", Date: " + date
                + ", Topline: \"" + topline
                + "\", Ressort: \"" + ressort
                + "\", Ext.Id: \"" + externalId
                + "\", Shorttext: " + (shorttext != null ? "\"" + shorttext + "\"" : "<null>")
                + ", Regional: " + regional
                + ", Tags:" + tags.toString()
                + ", Content: \"" + content + "\")";
    }

    /**
     * See <a href="https://developer.android.com/studio/write/annotations#enum-annotations">here</a>
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({NEWS_TYPE_STORY, NEWS_TYPE_WEBVIEW, NEWS_TYPE_VIDEO})
    public @interface NewsType {}

}