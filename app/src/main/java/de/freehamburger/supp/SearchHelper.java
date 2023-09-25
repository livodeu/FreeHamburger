package de.freehamburger.supp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.freehamburger.BuildConfig;
import de.freehamburger.R;
import de.freehamburger.model.Content;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;

/**
 *
 */
public class SearchHelper {
    
    public static final char WORD_SOURCE_SEPARATOR = '#';
    /** see <a href="https://developer.android.com/guide/topics/search/searchable-config#searchSuggestIntentAction">searchable-config</a> */
    public static final String SEARCH_SUGGEST_ACTION = "de.freehamburger.search_suggest_intent_action";
    private static final String TAG = "SearchHelper";
    /** words excluded from the search suggestions */
    private static final Set<String> EXCLUDED_SET = new HashSet<>(Arrays.asList(
            "ab", "aber", "ach", "alle", "allem", "allen", "allenfalls", "allerdings", "allerhand", "alles", "als", "alsbald", "also", "am",
            "an", "ans", "anbetracht", "andere", "anderem", "anderen", "anderer", "anderes",
            "angesichts", "anhand",
            "anschließend", "anschließende", "anschließendem", "anschließenden", "anschließender", "anschließendes",
            "anstehend", "anstehende", "anstehendem", "anstehenden", "anstehender", "anstehendes",
            "auch", "auf", "aufgrund", "aufs", "aus", "außen", "aussen", "außer", "außerdem", "außerhalb", "ausser",
            "ausschließlich", "ausschließliche", "ausschließlichem", "ausschließlichen", "ausschließlicher", "ausschließliches",
            "bald", "baldige", "baldigem", "baldigen", "baldiger", "baldiges",
            "befand", "befände", "befanden", "befandet", "befinden", "befindest", "befindet",
            "bei", "beide", "beidem", "beiden", "beider", "beides", "beim", "beinahe", "beispielsweise", "bereit", "bereitet", "bereitete", "bereiteten", "bereits", "bevor",
            "besonders",
            "bin", "bis", "bisher", "bisherige", "bisherigem", "bisherigen", "bisheriger", "bisheriges", "bislang", "bisschen", "bist", "bleiben", "bleibt", "blieb", "blieben",
            "beziehungsweise", "bzw",
            "da", "dabei", "dafür", "daher", "dahin", "dahinter", "damals", "damalige", "damaligem", "damaligen", "damaliger", "damaliges",
            "damit", "danach", "dann", "daneben", "daran", "darauf", "daraus", "darf", "darum", "darunter", "darüber", "das", "dass", "daß", "dasselbe", "davon", "davor", "dazu",
            "dem", "demnach", "demnächst", "demselben", "den", "denen", "denn", "dennoch", "denselben", "der", "deren", "derzeit", "des", "deshalb", "dessen", "desto",
            "die", "dies", "diese", "dieselbe", "dieselben", "diesem", "diesen", "dieser", "dieses",
            "doch", "dort", "dortige", "dortigem", "dortigen", "dortiger", "dortiges", "dorthin", "drüben", "drüber", "du", "durch", "durchaus", "dürfen", "dürft", "dürfte", "dürften",
            "durchgeführt", "durchgeführte", "durchgeführtem", "durchgeführten", "durchgeführter", "durchgeführtes",
            "ehemalig", "ehemalige", "ehemaligem", "ehemaligen", "ehemaliger", "ehemaliges", "ehemals", "eher",
            "eigen", "eigene", "eigenen", "eigener", "eigenes", "eigentlich", "eigentliche", "eigentlichem", "eigentlichen", "eigentlicher", "eigentliches",
            "ein", "eine", "einem", "einen", "einer", "eines", "einige", "einst", "einstig", "einstige", "einstigem", "einstigen", "einstiger", "einstiges", "einstmals",
            "er", "erst", "erstmals", "es", "etc", "etwa", "etwaig", "etwaige", "etwaigem", "etwaigen", "etwaiger", "etwaiges", "etwas",
            "euch", "euer", "eure", "eurem", "euren", "eurer", "eures",
            "fast", "für", "fürs",
            "gab", "gäbe", "gäben", "gebe", "geben", "gegeben", "gegen", "gibt",
            "haben", "hat", "hatte", "hatten", "hätte", "hätten", "hättest", "hättet", "her", "herbei", "herein", "heraus", "hernach", "herüber", "herunter", "hervor",
            "hier", "hierbei", "hierher", "hierhin", "hin", "hinab", "hinan", "hinauf", "hinaus", "hinein", "hinüber", "hinunter",
            "ich", "ihr", "ihre", "ihrem", "ihren", "ihrer", "ihres", "im", "immer", "immerhin", "in", "indem", "inmitten", "innen", "ins", "ist",
            "ja", "je", "jede", "jedem", "jeden", "jeder", "jeher", "jemals", "jedes", "jedoch", "jemand", "jemandem", "jemanden", "jemandes", "jetzt",
            "kann", "kannst", "kaum", "kein", "keine", "keinem", "keinen", "keiner", "keines", "können", "könnt", "könnte", "könnten", "konnte", "konnten",
            "lassen", "letztlich", "ließ", "ließe", "ließen",
            "mag", "man", "manche", "manchem", "manchen", "mancher", "manches", "manchmal",
            "mehr", "mehrfach", "mehrfache", "mehrfachem", "mehrfachen", "mehrfacher", "mehrfaches",
            "mehrmalig", "mehrmaligem", "mehrmaligen", "mehrmaliger", "mehrmaliges", "mehrmals",
            "mein", "meine", "meinem", "meinen", "meiner", "meines",
            "mit", "mitunter", "möchte", "möchten", "muss", "müssen", "müsste", "müssten",
            "nach", "nachdem", "neben", "nein", "nicht", "nichts", "nie", "niemals", "noch", "nun", "nur",
            "ob", "obs", "obschon", "obwohl", "oder", "oft", "oftmalig", "oftmalige", "oftmaligem", "oftmaligen", "oftmaliger", "oftmaliges", "oftmals", "öfter", "oh", "ohne", "ohnehin",
            "plötzlich", "plötzliche", "plötzlichem", "plötzlichen", "plötzlicher", "plötzliches",
            "schließlich",
            "schon", "sehr", "seid", "sein", "seinem", "seinen", "seiner", "seines", "seit", "seitdem", "selbe", "selben", "selber", "selbige", "selbigem", "selbigen", "selbiger", "selbiges", "selbst",
            "sich", "sie", "sind",
            "so", "sobald", "sogar", "soll", "solle", "sollen", "sollte", "sollten", "somit", "sowas", "soweit", "sowie", "sowieso", "sowohl", "stets",
            "um", "ums", "und", "unter", "unterm", "über", "überm", "übers",
            "viel", "viele", "vielem", "vielen", "vieler", "vielerlei", "vieles", "vielmals",
            "vom", "von", "vor", "voran", "voraus", "vorbei", "vorher", "vormalig", "vormalige", "vormaligem", "vormaligen", "vormaliger", "vormaliges", "vormals", "vors", "vorüber",
            "wann", "war", "wäre", "wären", "warst", "wart", "warum", "was",
            "wegen", "weil", "weiter", "weitere", "weiterem", "weiteren", "weiterer", "weiteres", "wer", "werden", "weshalb", "wessen",
            "wie", "wieder", "wiederum", "wieso", "wieviel", "wieviele", "will", "wir", "wird",
            "wo", "wobei", "wofür", "wogegen", "woher", "wohin", "wohl", "wollen", "wollte", "wollten", "wonach", "woran", "worüber", "wovon", "wovor", "wozu", "würden",
            "zu", "zudem", "zuerst", "zuletzt", "zum", "zumal", "zur", "zuvor", "zuzüglich",
            "++", "+++"
    ));
    /** if true, then the {@link News#getContent() news content} will be included in search suggestions */
    private static final boolean INCLUDE_NEWS_CONTENT = true;
    private static final Object SYNC = new Object();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    /** array that contains all {@link Source Sources} */
    private static final Source[] SOURCES;
    /** array that contains the local files for all {@link Source Sources} (the files do not necessarily exist) */
    private static final File[] LOCAL_FILES;
    private static final int MIN_WORD_LENGTH = 2;
    private static final String SPLITTER = " :\"„”-!?.,()&/'#<>[]{};•";

    static {
        SOURCES = Source.values();
        LOCAL_FILES = new File[SOURCES.length];
    }

    /**
     * Adds search suggestions to the database.<br>
     * Does not do anything if source is either {@link Source#VIDEO} or {@link Source#CHANNELS}.<br>
     * This is because TextFilters never match videos
     * (if TextFilters were applied to videos, then it might happen that if you created a filter containing "trumpet"
     * because their looks and sound make you sick, the video might show a trumpet nevertheless because the video's textual description does not contain that word).
     * @param ctx Context
     * @param source Source that the list of News belongs to
     * @param newsList list of News objects
     * @param testonly {@code true} only for testing,
     * @throws NullPointerException if any parameter is {@code null}
     */
    @Nullable
    public static Collection<Inserter> createSearchSuggestions(@NonNull Context ctx, @NonNull Source source, @NonNull final List<News> newsList, boolean testonly) {
        if (newsList.isEmpty()) return null;
        // no suggestions for videos - see TextFilter.internalAccept(News)
        if (Source.VIDEO == source || Source.CHANNELS == source) return null;

        long now = System.currentTimeMillis();

        setCreationTime(ctx, source, now);
        final int count = newsList.size();
        // max. number of threads to distribute the work to
        final int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        // don't process less than 16 news items per thread
        final int newsPerThread = Math.max(16, count / threads);
        final Collection<Inserter> inserters = new ArrayList<>(threads);
        for (int i = 0, remaining = count; i < count;) {
            int n = Math.min(remaining, newsPerThread);
            inserters.add(new Inserter(ctx, source, newsList.subList(i, i + n), now, testonly));
            remaining -= n;
            i += n;
        }
        for (Inserter inserter : inserters) inserter.start();
        return inserters;
    }

    /**
     * Deletes all search suggestions.
     * @param ctx Context
     * @return {@code true} if the operation finished without error
     */
    public static boolean deleteAllSearchSuggestions(@NonNull Context ctx) {
        try {
            Uri uri = Uri.parse("content://" + ctx.getString(R.string.app_search_auth));
            ContentResolver cr = ctx.getContentResolver();
            synchronized (SYNC) {
                cr.delete(uri, null, null);
                setCreationTime(ctx, null, 0L);
            }
            return true;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to delete search suggestions: " + e);
        }
        return false;
    }

    /**
     * Deletes the search suggestions for the given Source.
     * @param ctx Context
     * @param source Source
     */
    public static void deleteSearchSuggestionsForSource(@NonNull Context ctx, @NonNull Source source) {
        try {
            Uri uri = Uri.parse("content://" + ctx.getString(R.string.app_search_auth));
            ContentResolver cr = ctx.getContentResolver();
            synchronized (SYNC) {
                cr.delete(uri, SearchContentProvider.COLUMN_DISPLAY2 + " = '" + ctx.getString(source.getLabel()) + "'", null);
                setCreationTime(ctx, source, 0L);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to delete search suggestion: " + e);
        }
    }

    /**
     * Returns the latest creation/modification time of search suggestions for the given Source.
     * @param ctx Context
     * @param source Source
     * @return timestamp (or 0L if no such search suggestions exist)
     * @throws NullPointerException if any parameter is {@code null}
     */
    @IntRange(from = 0)
    public static long getCreationTime(@NonNull Context ctx, @NonNull Source source) {
        return PreferenceManager.getDefaultSharedPreferences(ctx).getLong(SearchContentProvider.PREF_PREFIX_SEARCHSUGGESTIONS + source.name(), 0L);
    }

    /**
     * Sets the latest creation/modification time of search suggestions for the given Source.
     * @param ctx Context
     * @param source Source ({@code null} to apply the timestamp to all sources)
     * @param ts timestamp
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    private static void setCreationTime(@NonNull Context ctx, @Nullable Source source, @IntRange(from = 0) long ts) {
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
        if (source != null) {
            if (ts > 0L) ed.putLong(SearchContentProvider.PREF_PREFIX_SEARCHSUGGESTIONS + source.name(), ts);
            else ed.remove(SearchContentProvider.PREF_PREFIX_SEARCHSUGGESTIONS + source.name());
        } else {
            for (Source s : SOURCES) {
                if (ts > 0L) ed.putLong(SearchContentProvider.PREF_PREFIX_SEARCHSUGGESTIONS + s.name(), ts);
                else ed.remove(SearchContentProvider.PREF_PREFIX_SEARCHSUGGESTIONS + s.name());
            }
        }
        ed.apply();
    }

    /**
     * Extracts the words from a phrase and puts them into a given Collection of Strings.<br>
     * Words that<ol>
     * <li>are on the exclusion list</li>
     * <li>are shorter than {@link #MIN_WORD_LENGTH} chars</li>
     * <li>start with a digit or a whitespace</li>
     * </ol>
     * are excluded.<br>
     * Note: Splitting via {@code pattern = Pattern.compile("[ :\"„”\\-!?.,()&/'#<>\\[\\]{};•]"); String[] s = pattern.split(…);} has proved to be <i>much</i> slower.
     * @param phrase phrase to split
     * @param addHere Collection of Strings to add the words to
     * @throws NullPointerException if any parameter is {@code null}
     */
    @WorkerThread
    @VisibleForTesting
    public static void splitSentence(@NonNull final String phrase, @NonNull final Collection<String> addHere) {
        final StringTokenizer st = new StringTokenizer(phrase, SPLITTER, false);
        while (st.hasMoreTokens()) {
            final String token = st.nextToken();
            if (token.length() < MIN_WORD_LENGTH) continue;
            final String l = token.toLowerCase(Locale.GERMAN);
            if (EXCLUDED_SET.contains(l)) continue;
            char c = l.charAt(0);
            if (Character.isWhitespace(c) || Character.isDigit(c)) continue;
            addHere.add(l);
        }
    }

    /**
     * Inserts search suggestions into the database.
     */
    @VisibleForTesting
    public static class Inserter extends Thread {

        private final @NonNull Context ctx;
        private final @NonNull Source source;
        private final Collection<News> newsList;
        private final long date;
        private final boolean testonly;
        @VisibleForTesting() public ContentValues[] cv;

        /**
         * Constructor.
         * @param ctx Context
         * @param source Source that the list of News belongs to
         * @param newsList Collection of News objects
         * @param ts timestamp
         * @param testonly true if this is just a test and the database should not be touched
         */
        private Inserter(@NonNull Context ctx, @NonNull Source source, @NonNull final Collection<News> newsList, long ts, boolean testonly) {
            super();
            this.ctx = ctx;
            this.source = source;
            this.newsList = newsList;
            this.date = ts;
            this.testonly = testonly;
            setPriority(Thread.NORM_PRIORITY - 1);
        }

        /** {@inheritDoc} */
        @SuppressWarnings("ConstantConditions")
        @Override
        public void run() {
            final ContentResolver cr = ctx.getContentResolver();
            final String sourceLabel = ctx.getString(source.getLabel());
            final Set<String> words = new HashSet<>(newsList.size() * 9);
                /*
                In the News list, the user sees these News attributes:
                - topline (if empty: title)             // this is the small print in the top-left corner
                - title                                 // this is the enlarged text next to the photo
                - firstSentence (if empty: shorttext)   // this is the longer text below the title
                 */
            for (News news : newsList) {
                String topline = news.getTopline();
                String title = news.getTitle();
                String fs = news.getFirstSentence();
                if (TextUtils.isEmpty(topline)) topline = title;
                if (TextUtils.isEmpty(fs)) fs = news.getShorttext();
                if (!TextUtils.isEmpty(topline)) splitSentence(topline, words);
                if (!TextUtils.isEmpty(title) && !title.equals(topline)) splitSentence(title, words);
                if (!TextUtils.isEmpty(fs)) splitSentence(fs, words);
                if (INCLUDE_NEWS_CONTENT) {
                    Content content = news.getContent();
                    if (content != null && !TextUtils.isEmpty(content.getPlainText())) splitSentence(content.getPlainText(), words);
                }
            }
            cv = new ContentValues[words.size()];
            final String symbol = "android.resource://" + BuildConfig.APPLICATION_ID + '/' + source.getIconSearch();
            final String querySuffix = WORD_SOURCE_SEPARATOR + source.name();
            int i = 0;
            for (String word : words) {
                cv[i] = new ContentValues(5);
                cv[i].put(SearchContentProvider.COLUMN_DATE, date);
                cv[i].put(SearchContentProvider.COLUMN_DISPLAY1, word);
                cv[i].put(SearchContentProvider.COLUMN_DISPLAY2, sourceLabel);
                cv[i].put(SearchContentProvider.COLUMN_QUERY, word + querySuffix);
                cv[i].put(SearchContentProvider.COLUMN_SYMBOL, symbol);
                i++;
            }
            if (testonly) return;
            Uri uri = Uri.parse("content://" + ctx.getString(R.string.app_search_auth));
            synchronized (SYNC) {
                cr.delete(uri, SearchContentProvider.COLUMN_DISPLAY2 + " = '" + sourceLabel + "'", null);
                cr.bulkInsert(uri, cv);
            }
            Arrays.fill(cv, null);
        }
    }

}
