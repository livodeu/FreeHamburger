package de.freehamburger.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * News item that has been stored on the device as its json representation.
 */
public class ArchivedNews implements Comparable<ArchivedNews> {

    @NonNull private final File file;
    private final boolean regional;

    /**
     * Constructor.
     * @param file json file
     * @param regional flag
     */
    public ArchivedNews(@NonNull File file, boolean regional) {
        super();
        this.file = file;
        this.regional = regional;
    }

    /** {@inheritDoc} <hr> Newer articles should appear before older articles */
    @Override public int compareTo(ArchivedNews other) {
        return -Long.compare(this.file.lastModified(), other.file.lastModified());
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchivedNews that = (ArchivedNews) o;
        return this.file.equals(that.file);
    }

    @NonNull
    public String getDisplayName() {
        String fileName = this.file.getName();
        int dot = fileName.lastIndexOf('.');
        return (dot < 0) ? fileName : fileName.substring(0, dot);
    }

    public long getTimestamp() {
        return this.file.lastModified();
    }

    @NonNull public File getFile() {
        return this.file;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(this.file);
    }

    public boolean isRegional() {
        return this.regional;
    }

    /** {@inheritDoc} */
    @Override @NonNull public String toString() {
        return this.file.getName();
    }
}
