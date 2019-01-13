package de.freehamburger.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;

/**
 *
 */
public class TextFilter implements Filter {

    private static final Pattern NON_WORD_CHAR_PATTERN = Pattern.compile("\\W");

    /** the filter phrase in <em>lower case</em> only*/
    @NonNull private CharSequence phrase;
    /** true if the filter will not be persisted */
    private final boolean temporary;
    /** true if the filter should apply to word starts only (if true, "trump" filters "trumpet" but not "strumpet") */
    private boolean atStart;
    /** true if the filter should apply to word ends only (if true, "pet" filters "trumpet" but not "trumpeting") */
    private boolean atEnd;
    /** true if the logic should be inversed (currently not persistable as it is used only during search) */
    private final boolean inverse;

    /**
     * Loads the preferred filters from the preferences.
     * @param ctx Context
     * @return List of Filters
     */
    @NonNull
    public static List<Filter> createTextFiltersFromPreferences(@NonNull Context ctx) {
        Set<String> preferredFilters = PreferenceManager.getDefaultSharedPreferences(ctx).getStringSet(App.PREF_FILTERS, new HashSet<>());
        final List<Filter> filters = new ArrayList<>(preferredFilters.size());
        for (String pf : preferredFilters) {
            pf = pf.trim();
            if (pf.length() == 0) continue;
            filters.add(TextFilter.parse(pf));
        }
        Collections.sort(filters);
        return filters;
    }

    /**
     * @param phrase as stored in the preferences (possibly starting with [ or ])
     * @return TextFilter
     */
    @NonNull
    private static TextFilter parse(@NonNull final String phrase) {
        final CharSequence p;
        boolean atStart = false;
        boolean atEnd = false;
        if (phrase.length() > 1) {
            char c = phrase.charAt(0);
            if (c == '[') {
                atStart = true;
                p = phrase.substring(1).toLowerCase(Locale.GERMAN);
            } else if (c == ']') {
                atEnd = true;
                p = phrase.substring(1).toLowerCase(Locale.GERMAN);
            } else {
                p = phrase.toLowerCase(Locale.GERMAN);
            }
        } else if (phrase.length() == 1) {
            p = phrase.toLowerCase(Locale.GERMAN);
        } else {
            p = "";
        }
        return new TextFilter(p, atStart, atEnd, false, false);
    }

    private static boolean endsWith(@Nullable CharSequence cs, @NonNull final CharSequence needle) {
        if (cs == null) return false;
        final int nl = needle.length();
        if (nl == 0) return true;
        int csl = cs.length();
        if (csl < nl) return false;
        final String[] parts = NON_WORD_CHAR_PATTERN.split(cs, -1);
        if (BuildConfig.DEBUG) android.util.Log.i(TextFilter.class.getSimpleName(), Arrays.toString(parts));
        for (String part : parts) {
            int pl = part.length();
            if (pl < nl) continue;
            if (TextUtils.equals(part.subSequence(pl - nl, pl), needle)) return true;
        }
        return false;
    }

    private static boolean startsWith(@Nullable CharSequence cs, @NonNull final CharSequence needle) {
        if (cs == null) return false;
        final int nl = needle.length();
        if (nl == 0) return true;
        int csl = cs.length();
        if (csl < nl) return false;
        final String[] parts = NON_WORD_CHAR_PATTERN.split(cs, -1);
        if (BuildConfig.DEBUG) android.util.Log.i(TextFilter.class.getSimpleName(), Arrays.toString(parts));
        for (String part : parts) {
            int pl = part.length();
            if (pl < nl) continue;
            if (TextUtils.equals(part.subSequence(0, nl), needle)) return true;
        }
        return false;
    }

    /**
     * Constructor.
     * @param phrase filter phrase
     */
    public TextFilter(@NonNull String phrase) {
        this(phrase, false, false, false, false);
    }

    /**
     * Constructor.
     * @param phrase filter phrase
     * @param temporary true / false
     * @param inverse true if the logic should be inverted
     */
    public TextFilter(@NonNull String phrase, boolean temporary, boolean inverse) {
        this(phrase, false, false, temporary, inverse);
    }

    /**
     * Constructor.
     * @param phrase filter phrase
     * @param atStart true / false
     * @param atEnd true / false
     * @param temporary true / false
     * @param inverse true if the logic should be inverted
     */
    private TextFilter(@NonNull CharSequence phrase, boolean atStart, boolean atEnd, boolean temporary, boolean inverse) {
        super();
        this.phrase = phrase;
        this.atStart = atStart;
        this.atEnd = atEnd;
        this.inverse = inverse;
        this.temporary = temporary;
    }

    /** {@inheritDoc} */
    @Override
    public final boolean accept(@Nullable final News news) {
        if (inverse) return !internalAccept(news);
        return internalAccept(news);
    }

    /**
     * @param news News
     * @return true if this Filter accepts the News
     */
    private boolean internalAccept(@Nullable News news) {
        if (news == null) return false;
        // we do not filter videos by text because my momma always said, "Video was like a box of chocolates. You never know what you're gonna get."
        if (News.NEWS_TYPE_VIDEO.equals(news.getType())) return true;
        //TODO remove HTML content before checking
        if (atStart) {
            if (startsWith(news.getFirstSentenceLowerCase(), phrase)) return false;
            if (startsWith(news.getToplineLowerCase(), phrase)) return false;
            if (startsWith(news.getTitleLowerCase(), phrase)) return false;
            if (news.getContent() != null && startsWith(news.getContent().getText().toLowerCase(Locale.GERMAN), phrase)) return false;
            for (String tag : news.getTags()) {
                if (startsWith(tag.toLowerCase(Locale.GERMAN), phrase)) return false;
            }
            for (String tag : news.getGeotags()) {
                if (startsWith(tag.toLowerCase(), phrase)) return false;
            }
        } else if (atEnd) {
            if (endsWith(news.getFirstSentenceLowerCase(), phrase)) return false;
            if (endsWith(news.getToplineLowerCase(), phrase)) return false;
            if (endsWith(news.getTitleLowerCase(), phrase)) return false;
            if (news.getContent() != null && endsWith(news.getContent().getText().toLowerCase(Locale.GERMAN), phrase)) return false;
            for (String tag : news.getTags()) {
                if (endsWith(tag.toLowerCase(Locale.GERMAN), phrase)) return false;
            }
            for (String tag : news.getGeotags()) {
                if (endsWith(tag.toLowerCase(), phrase)) return false;
            }
        } else {
            if (news.getFirstSentenceLowerCase() != null && news.getFirstSentenceLowerCase().contains(phrase)) return false;
            if (news.getToplineLowerCase() != null && news.getToplineLowerCase().contains(phrase)) return false;
            if (news.getTitleLowerCase() != null && news.getTitleLowerCase().contains(phrase)) return false;
            if (news.getContent() != null && news.getContent().getText().toLowerCase(Locale.GERMAN).contains(phrase)) return false;
            for (String tag : news.getTags()) {
                if (tag.toLowerCase(Locale.GERMAN).contains(phrase)) return false;
            }
            for (String tag : news.getGeotags()) {
                if (tag.toLowerCase().contains(phrase)) return false;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(@NonNull Filter o) {
        return getText().toString().compareTo(o.getText().toString());
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextFilter)) return false;
        TextFilter that = (TextFilter) o;
        return temporary == that.temporary && atStart == that.atStart && atEnd == that.atEnd && inverse == that.inverse && Objects.equals(phrase, that.phrase);
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getText() {
        return phrase;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(phrase, temporary, atStart, atEnd, inverse);
    }

    public boolean isAtEnd() {
        return atEnd;
    }

    public boolean isAtStart() {
        return atStart;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEditable() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTemporary() {
        return temporary;
    }

    public void setAtStartAndAtAend(boolean atStart, boolean atEnd) {
        this.atStart = atStart;
        this.atEnd = atEnd;
    }

    public void setPhrase(@NonNull CharSequence phrase) {
        this.phrase = phrase;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "TextFilter \"" + phrase + "\"" + (atStart ? " at start" : "") + (atEnd ? " at end" : "") + (temporary ? " (T)" : "")+ (inverse ? " (I)" : "");
    }
}
