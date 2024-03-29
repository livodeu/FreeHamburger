package de.freehamburger.model;

import android.content.Context;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import de.freehamburger.App;
import de.freehamburger.FilterActivity;

/**
 *
 */
public class TextFilter implements Filter {

    private static final Pattern NON_WORD_CHAR_PATTERN = Pattern.compile("\\W");
    /** true if the filter will not be persisted */
    private final boolean temporary;
    /** true if the logic should be inversed (currently not persistable as it is used only during search) */
    private final boolean inverse;
    /** the filter phrase in <em>lower case</em> only*/
    @NonNull private CharSequence phrase;
    /** true if the filter should apply to word starts only (if true, "trump" filters "trumpet" but not "strumpet") */
    private boolean atStart;
    /** true if the filter should apply to word ends only (if true, "pet" filters "trumpet" but not "trumpeting") */
    private boolean atEnd;

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
    @VisibleForTesting
    public TextFilter(@NonNull CharSequence phrase, boolean atStart, boolean atEnd, boolean temporary, boolean inverse) {
        super();
        this.phrase = phrase;
        this.atStart = atStart;
        this.atEnd = atEnd;
        this.inverse = inverse;
        this.temporary = temporary;
    }

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
     * Checks whether any word in cs ends with needle.
     * @param cs CharSequence to search
     * @param needle CharSequence to look for
     * @return {@code true} if any word in cs ends with needle
     */
    private static boolean endsWith(@Nullable CharSequence cs, @NonNull final CharSequence needle) {
        if (cs == null) return false;
        final int nl = needle.length();
        if (nl == 0) return true;
        int csl = cs.length();
        if (csl < nl) return false;
        final String[] parts = NON_WORD_CHAR_PATTERN.split(cs, -1);
        for (String part : parts) {
            int pl = part.length();
            if (pl < nl) continue;
            if (TextUtils.equals(part.subSequence(pl - nl, pl), needle)) return true;
        }
        return false;
    }

    /**
     * Tests a given char.
     * @param c character to test
     * @return true if the char is not suitable, false if it is
     */
    public static boolean isInvalid(final char c) {
        return (c < 0x20 || c > 0x7e) && (c < 0xa1 || c > 0xff);
    }

    /**
     * @param phrase as stored in the preferences (possibly starting with {@link FilterActivity#C_ATSTART [} or {@link FilterActivity#C_ATEND ]})
     * @return TextFilter
     * @throws NullPointerException if {@code phrase} is {@code null}
     */
    @NonNull
    private static TextFilter parse(@NonNull final String phrase) {
        CharSequence p;
        boolean atStart = false;
        boolean atEnd = false;
        if (phrase.length() > 1) {
            char c = phrase.charAt(0);
            if (c == FilterActivity.C_ATSTART) {
                atStart = true;
                p = phrase.substring(1).toLowerCase(Locale.GERMAN);
            } else if (c == FilterActivity.C_ATEND) {
                atEnd = true;
                p = phrase.substring(1).toLowerCase(Locale.GERMAN);
            } else {
                p = phrase.toLowerCase(Locale.GERMAN);
            }
            p = sanitize(p);
        } else if (phrase.length() == 1) {
            p = phrase.toLowerCase(Locale.GERMAN);
        } else {
            p = "";
        }
        return new TextFilter(p, atStart, atEnd, false, false);
    }

    /**
     * Removes invalid chars from the given input.<br>
     * This shouldn't end up in the filter:
     * <pre>
     *   ┓   ┏
     *   ╭◜≋◝╮
     *  ⋃(⊙ ⊙)⋃
     *    │⚇│
     *    ╰⊍╯</pre>
     * @param input possibly bad chars
     * @return clean chars
     * @throws NullPointerException if {@code input} is {@code null}
     */
    @NonNull
    private static CharSequence sanitize(@NonNull final CharSequence input) {
        final int n = input.length();
        final StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (isInvalid(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb;
    }

    /**
     * Checks whether any word in cs starts with needle.
     * @param cs CharSequence to search
     * @param needle CharSequence to look for
     * @return {@code true} if any word in cs starts with needle
     */
    private static boolean startsWith(@Nullable CharSequence cs, @NonNull final CharSequence needle) {
        if (cs == null) return false;
        final int nl = needle.length();
        if (nl == 0) return true;
        int csl = cs.length();
        if (csl < nl) return false;
        final String[] parts = NON_WORD_CHAR_PATTERN.split(cs, -1);
        for (String part : parts) {
            int pl = part.length();
            if (pl < nl) continue;
            if (TextUtils.equals(part.subSequence(0, nl), needle)) return true;
        }
        return false;
    }

    public final boolean accept(@NonNull String txt) {
        if (this.atStart) return !txt.startsWith(this.phrase.toString());
        else if (this.atEnd) return !txt.endsWith(this.phrase.toString());
        else return !txt.contains(this.phrase);
    }

    /** {@inheritDoc} */
    @Override
    public final boolean accept(@Nullable final News news) {
        if (inverse) return !internalAccept(news);
        return internalAccept(news);
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

    /**
     * Searches the given News'
     * <ul>
     * <li>{@link News#getFirstSentenceLowerCase() first sentence}</li>
     * <li>{@link News#getToplineLowerCase() top line}</li>
     * <li>{@link News#getTitleLowerCase() title}</li>
     * <li>{@link News#getContent() content}</li>
     * <li>{@link News#getTags() tags}</li>
     * <li>{@link News#getGeotags() geo tags}</li>
     * </ul>
     * for occurrences of {@link #phrase}
     * @param news News
     * @return true if this Filter accepts the News
     */
    private boolean internalAccept(@Nullable News news) {
        if (news == null) return false;
        // we do not filter videos by text because my momma always said, "Video was like a box of chocolates. You never know what you're gonna get."
        if (News.NEWS_TYPE_VIDEO.equals(news.getType())) return true;
        if (atStart) {
            if (startsWith(news.getFirstSentenceLowerCase(), phrase)) return false;
            if (startsWith(news.getToplineLowerCase(), phrase)) return false;
            if (startsWith(news.getTitleLowerCase(), phrase)) return false;
            if (news.getContent() != null && startsWith(news.getContent().getPlainText().toLowerCase(Locale.GERMAN), phrase)) return false;
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
            if (news.getContent() != null && endsWith(news.getContent().getPlainText().toLowerCase(Locale.GERMAN), phrase)) return false;
            for (String tag : news.getTags()) {
                if (endsWith(tag.toLowerCase(Locale.GERMAN), phrase)) return false;
            }
            for (String tag : news.getGeotags()) {
                if (endsWith(tag.toLowerCase(), phrase)) return false;
            }
        } else {
            if (contains(news.getFirstSentenceLowerCase(), phrase)) return false;
            if (contains(news.getToplineLowerCase(), phrase)) return false;
            if (contains(news.getTitleLowerCase(), phrase)) return false;
            if (news.getContent() != null && contains(news.getContent().getPlainText().toLowerCase(Locale.GERMAN), phrase)) return false;
            for (String tag : news.getTags()) {
                if (contains(tag.toLowerCase(Locale.GERMAN), phrase)) return false;
            }
            for (String tag : news.getGeotags()) {
                if (contains(tag.toLowerCase(), phrase)) return false;
            }
        }
        return true;
    }

    /**
     * Returns true if a given haystack contains a given needle.
     * @param haystack haystack to search
     * @param needle needle to find
     * @return true / false
     */
    private static boolean contains(@Nullable String haystack, @NonNull CharSequence needle) {
        return haystack != null && haystack.contains(needle);
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
    @NonNull
    public String toString() {
        return "TextFilter \"" + phrase + "\"" + (atStart ? " at start" : "") + (atEnd ? " at end" : "") + (temporary ? " (T)" : "")+ (inverse ? " (I)" : "");
    }
}
