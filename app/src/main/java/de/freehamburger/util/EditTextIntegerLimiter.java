package de.freehamburger.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * Limits integer/long values that may be entered into an EditText.
 */
public class EditTextIntegerLimiter implements TextWatcher {

    private static final String GREATER = "≧ ";
    private static final String SMALLER = "≦ ";
    private final Reference<EditText> ref;
    private final long maximum;
    private final long minimum;

    /**
     * Constructor.
     * At least one of the limits must be set to a value other than {@link Long#MIN_VALUE} resp. {@link Long#MAX_VALUE}.
     * @param editText EditText
     * @param minimum min. value
     * @param maximum max. value
     * @throws IllegalArgumentException if neither minimum nor maximum are set or if minimum &gt; maximum
     */
    public EditTextIntegerLimiter(@NonNull EditText editText, long minimum, long maximum) {
        super();
        this.ref = new WeakReference<>(editText);
        this.minimum = minimum;
        this.maximum = maximum;
        if (minimum == Long.MIN_VALUE && maximum == Long.MAX_VALUE) throw new IllegalArgumentException("No limit given!");
        if (minimum > maximum) throw new IllegalArgumentException("Mixed up limits!");
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override public void afterTextChanged(Editable s) {
        final EditText editText = this.ref.get();
        if (editText == null) return;
        try {
            long value = Long.parseLong(s.toString());
            boolean toolow = value < this.minimum;
            boolean toohigh = value > this.maximum;
            if (toolow) {
                if (toohigh) editText.setError(GREATER + this.minimum + ", " + SMALLER + this.maximum);
                else editText.setError(GREATER + this.minimum);
            } else if (toohigh) {
                editText.setError(SMALLER + this.maximum);
            } else {
                editText.setError(null);
            }
        } catch (Exception e) {
            if (this.minimum > Long.MIN_VALUE) {
                if (this.maximum < Long.MAX_VALUE) editText.setError(GREATER + this.minimum + ", " + SMALLER + this.maximum);
                else editText.setError(GREATER + this.minimum);
            } else {
                editText.setError(SMALLER + this.maximum);
            }
        }
    }
}
