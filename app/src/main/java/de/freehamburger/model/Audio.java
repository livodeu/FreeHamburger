package de.freehamburger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Date;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class Audio implements Serializable {

    private final String title;
    private final String dateString;
    @NonNull private final String stream;
    private Date date;

    /**
     * Constructor.
     * @param title  title
     * @param stream audio stream url
     * @param date   date, formatted as "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
     */
    Audio(String title, @NonNull String stream, String date) {
        super();
        this.title = title;
        this.stream = stream;
        this.dateString = date;
    }

    @Nullable
    public Date getDate() {
        if (date != null) return date;
        if (dateString != null) {
            try {
                date = News.parseDate(dateString);
            } catch (ParseException e) {
                if (BuildConfig.DEBUG) android.util.Log.e(getClass().getSimpleName(), "While parsing date: " + e);
            }
            if (date != null) return date;
        }
        return null;
    }

    @NonNull
    public String getStream() {
        return this.stream;
    }

    @NonNull
    public String getTitle() {
        return this.title != null && this.title.length() > 0 ? this.title : this.stream;
    }

    /** {@inheritDoc} */
    @NonNull @Override public String toString() {
        return getTitle();
    }
}
