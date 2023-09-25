package de.freehamburger.supp;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentCallbacks2;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import java.util.Locale;

import de.freehamburger.BuildConfig;
import de.freehamburger.R;
import de.freehamburger.model.Source;

/**
 *
 */
public class SearchContentProvider extends ContentProvider {

    /** may contain a timestamp of the point in time when search suggestions had to be deleted due to a locale change */
    public static final String PREF_SEARCHSUGGESTIONS_DELETED_LOCALE_CHANGE = "pref_searchsuggestions_deleted_locale_change";
    /** boolean */
    public static final String PREF_SEARCH_HINT_RESET_SHOWN = "pref_search_hint_reset_shown";
    /** long (timestamp) */
    static final String PREF_PREFIX_SEARCHSUGGESTIONS = "search_sugg_";
    /** date contains the timestamp of the creation/update of that particular search suggestion */
    static final String COLUMN_DATE = "date";
    /** display1 contains the word that the user can search for */
    static final String COLUMN_DISPLAY1 = "display1";
    /** display2 contains the <em>localised</em> {@link Source#getLabel() source label}<br>Therefore the database will be erased when the Locale changes! */
    static final String COLUMN_DISPLAY2 = "display2";
    /** query consists of: {@link #COLUMN_DISPLAY1 display1} + {@link SearchHelper#WORD_SOURCE_SEPARATOR} + source.name() */
    static final String COLUMN_QUERY = "query";
    /** symbol contains the {@link Source#getIcon()} source icon */
    static final String COLUMN_SYMBOL = "symbol";
    static final String COLUMN_INTENTDATA = "intentdata";
    /** database table name */
    private static final String SUGGESTIONS = "suggestions";
    /** text for exceptions */
    private static final String UNKNOWN_URI = "Unknown Uri ";
    private static final String TAG = "SearchContentProvider";
    /** database name */
    private static final String SUGGESTIONS_DB = "suggestions.db";
    /** search suggestions are ordered by {@link #COLUMN_DISPLAY1 display1} and {@link #COLUMN_DISPLAY2 display2} */
    private static final String ORDER_BY = COLUMN_DISPLAY1 + ',' + COLUMN_DISPLAY2;
    private static final String NULL_COLUMN = COLUMN_QUERY;
    @IntRange(from = 1)
    private static final int DATABASE_VERSION = 1;
    private static final String INSERT_STMT = "INSERT OR REPLACE INTO "
            + SUGGESTIONS
            + '(' + COLUMN_DATE
            + ',' + COLUMN_SYMBOL
            + ',' + COLUMN_DISPLAY1
            + ',' + COLUMN_DISPLAY2
            + ',' + COLUMN_QUERY
            + ") VALUES (?,?,?,?,?)";
    private static final String[] SUGGESTION_PROJECTION = new String[] {
            "0 AS " + SearchManager.SUGGEST_COLUMN_FORMAT,
            COLUMN_SYMBOL + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1,
            "NULL" + " AS " + SearchManager.SUGGEST_COLUMN_ICON_2,
            COLUMN_DISPLAY1 + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1,
            COLUMN_DISPLAY2 + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_2,
            COLUMN_QUERY + " AS " + SearchManager.SUGGEST_COLUMN_QUERY,
            COLUMN_DATE + " AS " + SearchManager.SUGGEST_COLUMN_LAST_ACCESS_HINT,
            COLUMN_QUERY + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
            "_id"
    };
    private static final int URI_MATCH_SUGGEST = 1;

    private Uri uri;
    private UriMatcher uriMatcher;
    private DatabaseHelper databaseHelper;
    private Locale locale;

    /** {@inheritDoc} */
    @Override
    @AnyThread
    public int bulkInsert(@NonNull Uri uri, @NonNull final ContentValues[] values) {
        if (values.length == 0) return 0;
        Context ctx = getContext();
        if (ctx == null) return 0;

        final SQLiteDatabase db = this.databaseHelper.getWritableDatabase();
        int counter = 0;

        try {
            db.beginTransaction();
            final SQLiteStatement stmt = db.compileStatement(INSERT_STMT);
            long rowId;
            for (ContentValues value : values) {
                stmt.bindLong(1, value.getAsLong(COLUMN_DATE));
                stmt.bindString(2, value.getAsString(COLUMN_SYMBOL));
                stmt.bindString(3, value.getAsString(COLUMN_DISPLAY1));
                stmt.bindString(4, value.getAsString(COLUMN_DISPLAY2));
                stmt.bindString(5, value.getAsString(COLUMN_QUERY));
                rowId = stmt.executeInsert();
                stmt.clearBindings();
                if (rowId > 0) counter++;
            }
            if (counter > 0) {
                db.setTransactionSuccessful();
                ContentResolver cr = ctx.getContentResolver();
                if (cr != null) cr.notifyChange(this.uri, null, false);
            }
        } catch (SQLiteException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
        } finally {
            db.endTransaction();
        }
        return counter;
    }

    /** {@inheritDoc} */
    @Override
    @AnyThread
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        try {
            SQLiteDatabase db = this.databaseHelper.getWritableDatabase();
            count = db.delete(SUGGESTIONS, selection, selectionArgs);
            Context ctx = getContext();
            if (ctx != null) ctx.getContentResolver().notifyChange(uri, null);
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public String getType(@NonNull Uri uri) {
        if (this.uriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            return SearchManager.SUGGEST_MIME_TYPE;
        }
        java.util.List<String> path = uri.getPathSegments();
        int length = path.size();
        if (length >= 1) {
            String base = path.get(0);
            if (SUGGESTIONS.equals(base)) {
                if (length == 1) {
                    return "vnd.android.cursor.dir/suggestion";
                } else if (length == 2) {
                    return "vnd.android.cursor.item/suggestion";
                }
            }
        }
        throw new IllegalArgumentException(UNKNOWN_URI + uri);
    }

    /** {@inheritDoc} */
    @Override
    @AnyThread
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        Context ctx = getContext();
        if (ctx == null) return null;

        SQLiteDatabase db = this.databaseHelper.getWritableDatabase();
        Uri newUri;
        long rowid = db.insert(SUGGESTIONS, NULL_COLUMN, values);
        if (rowid > 0) {
            newUri = Uri.withAppendedPath(this.uri, String.valueOf(rowid));
        } else {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to insert: " + values);
            newUri = null;
        }
        if (newUri != null) ctx.getContentResolver().notifyChange(newUri, null);
        return newUri;
    }

    /** {@inheritDoc} */
    @Override
    public void onConfigurationChanged(Configuration c) {
        // if the Locale has changed, the suggestions must be deleted because the display2 column contains localised text
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = c.getLocales().get(0);
        } else {
            locale = c.locale;
        }
        boolean changed = !this.locale.equals(locale);
        this.locale = locale;
        if (!changed) return;
        Context ctx = getContext();
        if (ctx == null) return;
        if (SearchHelper.deleteAllSearchSuggestions(ctx)) {
            SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
            ed.putLong(PREF_SEARCHSUGGESTIONS_DELETED_LOCALE_CHANGE, System.currentTimeMillis());
            ed.apply();
        }
    }

    /** {@inheritDoc} */
    @Override
    @MainThread
    public boolean onCreate() {
        Context ctx = getContext();
        if (ctx == null) return false;
        String authority = ctx.getString(R.string.app_search_auth);
        this.uri = Uri.parse("content://" + authority);
        this.uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        this.uriMatcher.addURI(authority, SearchManager.SUGGEST_URI_PATH_QUERY, URI_MATCH_SUGGEST);
        // ctx is most likely the App instance
        this.databaseHelper = new DatabaseHelper(ctx);
        Configuration c = ctx.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.locale = c.getLocales().get(0);
        } else {
            this.locale = c.locale;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void onTrimMemory(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            try {
                this.databaseHelper.close();
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * {@inheritDoc}
     * <hr>
     * This is called from {@link SearchManager#getSuggestions(SearchableInfo, String, int)} whenever the user modifies the search phrase.<br>
     * {@code projection} will be {@code null}<br>
     * {@code selection} will be " ?"<br>
     * {@code selectionArgs} will contain the text (as one String) that the user has entered into the search textview.<br>
     * {@code sortOrder} will be {@code null}.
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) return null;

        SQLiteDatabase db = this.databaseHelper.getReadableDatabase();

        // special case for actual suggestions (from search manager)
        if (this.uriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            String suggestSelection;
            String[] myArgs;
            if (TextUtils.isEmpty(selectionArgs[0])) {
                suggestSelection = null;
                myArgs = null;
            } else {
                String like = "%" + selectionArgs[0] + "%";
                myArgs = new String[] {like, like};
                suggestSelection = COLUMN_DISPLAY1 + " LIKE ? OR " + COLUMN_DISPLAY2 + " LIKE ?";
            }
            Cursor c = db.query(SUGGESTIONS, SUGGESTION_PROJECTION, suggestSelection, myArgs, null, null, ORDER_BY, null);
            c.setNotificationUri(ctx.getContentResolver(), uri);
            return c;
        }

        // otherwise process arguments and perform a standard query
        java.util.List<String> path = uri.getPathSegments();
        int length = path.size();
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException(UNKNOWN_URI + uri);
        }

        String base = path.get(0);
        if (!SUGGESTIONS.equals(base)) {
            throw new IllegalArgumentException(UNKNOWN_URI + uri);
        }

        String[] useProjection = null;
        if (projection != null && projection.length > 0) {
            useProjection = new String[projection.length + 1];
            System.arraycopy(projection, 0, useProjection, 0, projection.length);
            useProjection[projection.length] = "_id AS _id";
        }

        StringBuilder whereClause = new StringBuilder(256);
        if (length == 2) {
            whereClause.append("(_id = ").append(path.get(1)).append(")");
        }

        // Tack on the user's selection, if present
        if (selection != null && selection.length() > 0) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }

            whereClause.append('(');
            whereClause.append(selection);
            whereClause.append(')');
        }

        // And perform the generic query as requested
        if (BuildConfig.DEBUG) Log.i(TAG, "Query WHERE clause \"" + whereClause + "\"");
        Cursor c = db.query(base, useProjection, whereClause.toString(), selectionArgs, null, null, sortOrder, null);
        c.setNotificationUri(ctx.getContentResolver(), uri);
        return c;
    }

    /** {@inheritDoc} */
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * See {@link SQLiteOpenHelper}.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        /**
         * Constructor.
         * @param context Context
         */
        DatabaseHelper(@NonNull Context context) {
            super(context, SUGGESTIONS_DB, null, DATABASE_VERSION);
        }

        static void delete(@NonNull SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + SUGGESTIONS);
        }

        /** {@inheritDoc} */
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + SUGGESTIONS + " (_id INTEGER PRIMARY KEY,display1 TEXT,display2 TEXT,query TEXT,date LONG,symbol TEXT);");
        }

        /** {@inheritDoc} */
        @Override
        public void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            onVersionChange(db);
        }

        /** {@inheritDoc} */
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            onVersionChange(db);
        }

        private void onVersionChange(@NonNull SQLiteDatabase db) {
            delete(db);
            onCreate(db);
        }

    }
}
