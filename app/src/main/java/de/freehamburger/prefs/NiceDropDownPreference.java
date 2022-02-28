package de.freehamburger.prefs;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DropDownPreference;

import de.freehamburger.R;

/**
 * A DropDownPreference with a different layout for the individual options.
 */
public class NiceDropDownPreference extends DropDownPreference {

    public NiceDropDownPreference(@NonNull Context context) {
        super(context);
    }

    public NiceDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NiceDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public NiceDropDownPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @NonNull
    @Override
    protected ArrayAdapter<?> createAdapter() {
        return new ArrayAdapter<>(getContext(), R.layout.spinner_item);
    }
}
