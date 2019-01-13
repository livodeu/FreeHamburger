package de.freehamburger.util;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 *
 */
public class BitmapTarget implements Target {

    @NonNull private final String source;
    private Bitmap bitmap;

    /**
     * Constructor.
     * @param source url
     */
    public BitmapTarget(@NonNull String source) {
        super();
        this.source = source;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BitmapTarget that = (BitmapTarget) o;

        return bitmap != null ? bitmap.equals(that.bitmap) : that.bitmap == null;

    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return this.bitmap != null ? this.bitmap.hashCode() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        this.bitmap = null;
    }

    /** {@inheritDoc} */
    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        this.bitmap = bitmap;
    }

    /** {@inheritDoc} */
    @Override
    public void onPrepareLoad(@Nullable Drawable placeHolderDrawable) {
        this.bitmap = null;
    }

    @Override
    public String toString() {
        return "BitmapTarget for source \"" + this.source + "\"";
    }
}
