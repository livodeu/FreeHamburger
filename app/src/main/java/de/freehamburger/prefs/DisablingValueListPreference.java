package de.freehamburger.prefs;

import android.content.Context;
import androidx.preference.ListPreference;
import android.util.AttributeSet;

/**
 * Disables dependants when a particular value is selected.
 */
public class DisablingValueListPreference extends ListPreference {

    private String selectionToDisableDependents = null;

    public DisablingValueListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DisablingValueListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DisablingValueListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DisablingValueListPreference(Context context) {
        super(context);
    }

    /** {@inheritDoc} */
    @Override
    protected void notifyChanged() {
        super.notifyChanged();
        super.notifyDependencyChange(shouldDisableDependents());
    }

    public void setSelectionToDisableDependents(String selectionToDisableDependents) {
        this.selectionToDisableDependents = selectionToDisableDependents;
    }

    /** {@inheritDoc} */
    @Override
    public boolean shouldDisableDependents() {
        if (this.selectionToDisableDependents != null) {
            if (this.selectionToDisableDependents.equals(getValue())) return true;
        }
        return super.shouldDisableDependents();
    }

}
