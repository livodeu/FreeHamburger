package de.freehamburger.version;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class Asset {
    @Size(1) public static final String SEP = "§";
    String name;
    String url;

    Asset() {
        super();
    }

    @NonNull
    static Asset fromString(@Nullable String s) {
        final Asset asset = new Asset();
        if (s == null) return asset;
        int sep = s.indexOf(SEP);
        if (sep <= 1) return asset;
        asset.name = s.substring(0, sep);
        String substring = s.substring(sep + 1);
        asset.url = substring;
        if (substring.length() == 0 || asset.url.contains(SEP)) asset.url = null;
        return asset;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Asset asset = (Asset) o;
        return Objects.equals(this.name, asset.name) && Objects.equals(this.url, asset.url);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Objects.hash(this.name, this.url);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public boolean isValid() {
        return this.name != null
                && this.name.length() > 0
                && !this.name.contains(SEP)
                && this.url != null
                && this.url.length() > 0
                && !this.url.contains(SEP);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return (this.name != null ? this.name.trim() : "")
                + SEP
                + (this.url != null ? this.url.trim() : "");
    }
}
