package de.freehamburger.prefs;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

/**
 * A SwitchPreference with nice title hyphenation for API &gt;= 23.<br>
 * In the xml file, "app:singleLineTitle" must be set to "false" for this to have any effect.
 */
@TargetApi(Build.VERSION_CODES.M)
public class NiceSwitchPreference extends SwitchPreferenceCompat {

    public NiceSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NiceSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NiceSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NiceSwitchPreference(Context context) {
        super(context);
    }

    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        View titleView = holder.findViewById(android.R.id.title);
        if (!(titleView instanceof TextView)) return;
        ((TextView)titleView).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
    }
}
