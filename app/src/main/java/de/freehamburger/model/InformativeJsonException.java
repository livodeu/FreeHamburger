package de.freehamburger.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import java.io.IOException;

/**
 * Wraps a MalformedJsonException and the state of the JsonReader that caused it.
 */
class InformativeJsonException extends IOException {

    @Nullable private final String readerState;

    /**
     * Constructor.
     * @param base MalformedJsonException that has been thrown
     * @param reader JsonReader
     */
    InformativeJsonException(MalformedJsonException base, @Nullable JsonReader reader) {
        super(base.getMessage());
        this.readerState = reader != null ? reader.toString() : null;
    }

    @Nullable
    public String getReaderState() {
        return this.readerState;
    }

    @NonNull
    @Override
    public String toString() {
        if (this.readerState != null) {
            return super.toString() + " - " + this.readerState;
        }
        return super.toString();
    }
}
