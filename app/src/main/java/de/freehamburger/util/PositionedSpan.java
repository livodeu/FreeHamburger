package de.freehamburger.util;

import android.content.Context;
import android.text.Spannable;
import android.text.style.CharacterStyle;
import android.text.style.TextAppearanceSpan;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.freehamburger.R;

/**
 * Wraps the information needed to call {@link Spannable#setSpan(Object, int, int, int)}.
 * Also {@link #forTag(Context, String, int) matches} a custom HTML tag to a certain Span a.k.a CharacterStyle.
 */
public class PositionedSpan {

    /** indicates very small text as defined in {@link R.style#TextAppearance_Xsm} */
    public static final String TAG_XSMALL = "xsm";
    public static final String TAG_XSMALL_OPENING = '<' + TAG_XSMALL + '>';
    public static final String TAG_XSMALL_CLOSING = "</" + TAG_XSMALL + '>';

    @NonNull
    public final CharacterStyle characterStyle;
    private final int pos;
    private int length;

    @Nullable
    public static PositionedSpan forTag(Context ctx, String tag, @IntRange(from = 0) int position) {
        if (TAG_XSMALL.equals(tag)) {
            return new PositionedSpan(new TextAppearanceSpan(ctx, R.style.TextAppearance_Xsm), position, 0);
        }
        return null;
    }

    /**
     * Constructor.
     * @param characterStyle span to apply
     * @param pos            span start
     * @param length         span length
     */
    public PositionedSpan(@NonNull CharacterStyle characterStyle, @IntRange(from = 0) int pos, @IntRange(from = 0) int length) {
        super();
        this.characterStyle = characterStyle;
        this.pos = pos;
        this.length = length;
    }

    public void applyToSpannable(@NonNull Spannable spannable) {
        spannable.setSpan(this.characterStyle, this.pos, this.pos + this.length, 0);
    }

    @IntRange(from = 0)
    public int getLength() {
        return this.length;
    }

    @IntRange(from = 0)
    public int getPos() {
        return this.pos;
    }

    public void setLength(@IntRange(from = 0) int length) {
        this.length = length;
    }
}
