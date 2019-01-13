package de.freehamburger.util;

import android.content.Context;
import android.preference.EditTextPreference;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.util.AttributeSet;

/**
 * An extension of EditTextPreference
 * which shows the value as summary.<br>
 * If the value is empty, the original summary text (as set in the xml file) is displayed.
 */
public class SummarizingEditTextPreference extends EditTextPreference {

    @StringRes private int stringRes;

    public SummarizingEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SummarizingEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SummarizingEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SummarizingEditTextPreference(Context context) {
        super(context);
    }

    /** {@inheritDoc} */
    @Override
    public CharSequence getSummary() {
        String txt = getText();
        if (TextUtils.isEmpty(txt)) {
            return super.getSummary();
        }
        if (this.stringRes != 0) {
            return getContext().getString(this.stringRes, txt);
        }
        return txt;
    }

    /**
     * Sets a string resource to be used in conjunction with the value.<br>
     * <b>The string must contain a "%1$s"!</b>
     * @param stringRes string resource id
     */
    public void setStringRes(@StringRes int stringRes) {
        this.stringRes = stringRes;
    }
}
