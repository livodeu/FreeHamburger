package de.freehamburger.version;

import android.text.TextUtils;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import de.freehamburger.util.Util;

public final class Release {
    @Repo public static final int REPO_FDROID = 2;
    @Repo public static final int REPO_GITHUB = 1;
    @Repo public static final int REPO_NONE = 0;
    @Size(1) public static final String SEP = "#";
    private static final int STD_FIELD_COUNT = 3;
    @Size(STD_FIELD_COUNT) public static final String EMPTY = "#0#";
    private final List<Asset> assets = new ArrayList<>(4);
    private String assetsUrl;
    /** here the transient attribute is just a marker to indicate that the field is not persistent */
    private transient long checkedAt;
    /** publication timestamp, may be 0 */
    private long publishedAt;
    /** here the transient attribute is just a marker to indicate that the field is not persistent */
    @Repo private transient int repo = REPO_NONE;
    /** version tag name, e.g. "v1.4" or "1.4" */
    private String tagName;

    private Release() {
        super();
    }

    /**
     * Constructor.
     * @param repo repository id
     */
    Release(@Repo int repo) {
        this();
        this.repo = repo;
    }

    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static Release fromString(@Nullable String s) {
        final Release release = new Release();
        if (s == null) return release;
        final StringTokenizer st = new StringTokenizer(s, SEP, true);
        for (int i = 0; st.hasMoreTokens();) {
            String part = st.nextToken();
            if (SEP.equals(part)) {
                i++;
                continue;
            }
            switch (i) {
                case 0:
                    release.tagName = TextUtils.isEmpty(part) ? null : part.trim();
                    continue;
                case 1:
                    release.publishedAt = !TextUtils.isEmpty(part) ? Long.parseLong(part) : 0L;
                    continue;
                case 2:
                    release.assetsUrl = TextUtils.isEmpty(part) ? null : part.trim();
                    continue;
                default:
                    release.assets.add(Asset.fromString(part));
            }
        }
        return release;
    }

    /**
     * Adds an Asset.
     * @param asset Asset to add
     */
    void addAsset(@NonNull Asset asset) {
        this.assets.add(asset);
    }

    @NonNull
    public List<Asset> getAssets() {
        return this.assets;
    }

    @Nullable
    public String getAssetsUrl() {
        return this.assetsUrl;
    }

    @IntRange(from = 0)
    long getCheckedAt() {
        return this.checkedAt;
    }

    @Nullable
    public String getPrettyTagName() {
        return this.tagName != null ? Util.trimNumber(this.tagName) : null;
    }

    @IntRange(from = 0)
    public long getPublishedAt() {
        return this.publishedAt;
    }

    @Repo
    public int getRepo() {
        return this.repo;
    }

    @Nullable
    public String getTagName() {
        return this.tagName;
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(this.tagName) && !this.tagName.contains(SEP);
    }

    void setAssetsUrl(@Nullable String assetsUrl) {
        this.assetsUrl = assetsUrl;
    }

    /**
     * @param checkedAt timestamp
     */
    void setCheckedAt(@IntRange(from = 0) long checkedAt) {
        this.checkedAt = checkedAt;
    }

    /**
     * @param publishedAt timestamp
     */
    void setPublishedAt(@IntRange(from = 0) long publishedAt) {
        this.publishedAt = publishedAt;
    }

    /**
     * @param repo repository id
     */
    void setRepo(@Repo int repo) {
        this.repo = repo;
    }

    /**
     * @param tagName tag name
     */
    void setTagName(@Nullable String tagName) {
        this.tagName = tagName;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        final StringBuilder sb = new StringBuilder(640)
                .append(this.tagName != null ? this.tagName : "")
                .append(SEP)
                .append(this.publishedAt)
                .append(SEP)
                .append(this.assetsUrl != null ? this.assetsUrl : "")
                ;
        for (Asset asset : this.assets) {
            sb.append(SEP).append(asset.toString());
        }
        return sb.toString();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({REPO_NONE, REPO_GITHUB, REPO_FDROID})
    @interface Repo {
    }
}
