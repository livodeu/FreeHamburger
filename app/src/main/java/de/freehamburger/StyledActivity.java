package de.freehamburger;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import de.freehamburger.util.Util;

public abstract class StyledActivity extends AppCompatActivity {

    /** white during daytime, black at night */
    @BackgroundVariant static final int BACKGROUND_VARIANT_BLACKWHITE = 1;
    /** nearly white during daytime, deep blue at night (day: S 7%, B 100% / night: S 81%, B 30%) */
    @BackgroundVariant static final int BACKGROUND_VARIANT_BLUEWHITE = 4;
    /** light blue during daytime, medium blue at night (the original look) */
    @BackgroundVariant static final int BACKGROUND_VARIANT_NORMAL = 0;
    /** light turquoise during daytime, dark turquoise at night (day: S 7%, B 100% / night: S 81%, B 30%) */
    @BackgroundVariant static final int BACKGROUND_VARIANT_TURQUOISE = 3;
    /** light viola during daytime, dark viola at night (day: S 7%, B 100% / night: S 81%, B 30%) */
    @BackgroundVariant static final int BACKGROUND_VARIANT_VIOLA = 2;

    /**
     * Applies one of the bg_news_xxx background drawables (e.g. {@link R.drawable#bg_news R.drawable.bg_news}) to the given View.<br>
     * Depends on {@link Prefs#PREF_BACKGROUND_VARIANT_INDEX}.
     * @param view View to modify
     * @throws NullPointerException if {@code view} is {@code null}
     */
    private static void setViewBackground(@NonNull final View view) {
        @BackgroundVariant final int backgroundVariantIndex = PreferenceManager.getDefaultSharedPreferences(view.getContext()).getInt(App.PREF_BACKGROUND_VARIANT_INDEX, App.PREF_BACKGROUND_VARIANT_INDEX_DEFAULT);
        switch (backgroundVariantIndex) {
            case BACKGROUND_VARIANT_BLACKWHITE: view.setBackgroundResource(R.drawable.bg_news_blackwhite); break;
            case BACKGROUND_VARIANT_TURQUOISE: view.setBackgroundResource(R.drawable.bg_news_turquoise); break;
            case BACKGROUND_VARIANT_VIOLA: view.setBackgroundResource(R.drawable.bg_news_viola); break;
            case BACKGROUND_VARIANT_BLUEWHITE: view.setBackgroundResource(R.drawable.bg_news_bluewhite); break;
            case BACKGROUND_VARIANT_NORMAL:
            default: view.setBackgroundResource(R.drawable.bg_news);
        }
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setWindowBackground(null);
        super.onCreate(savedInstanceState);
    }

    /**
     * Sets the window background color according to the preferences.
     * @param prefs SharedPreferences (optional)
     */
    protected final void setWindowBackground(@Nullable SharedPreferences prefs) {
        if (isDestroyed()) return;
        final android.view.Window window = getWindow();
        if (window == null) return;
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(this);
        @BackgroundVariant final int backgroundVariantIndex = prefs.getInt(App.PREF_BACKGROUND_VARIANT_INDEX, App.PREF_BACKGROUND_VARIANT_INDEX_DEFAULT);
        @ColorRes final int colorRes;
        switch (backgroundVariantIndex) {
            case BACKGROUND_VARIANT_BLACKWHITE: colorRes = R.color.colorBgNewsBlackWhite; break;
            case BACKGROUND_VARIANT_TURQUOISE: colorRes = R.color.colorBgNewsTurquoise; break;
            case BACKGROUND_VARIANT_VIOLA: colorRes = R.color.colorBgNewsViola; break;
            case BACKGROUND_VARIANT_BLUEWHITE: colorRes = R.color.colorBgNewsBlueWhite; break;
            case BACKGROUND_VARIANT_NORMAL:
            default: colorRes = R.color.colorWindowBackground;
        }
        window.setBackgroundDrawable(new ColorDrawable(Util.getColor(this, colorRes)));
    }

    /**
     * The possible values of the {@link #PREF_BACKGROUND_VARIANT_INDEX background variant preference}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({BACKGROUND_VARIANT_NORMAL, BACKGROUND_VARIANT_BLACKWHITE, BACKGROUND_VARIANT_TURQUOISE, BACKGROUND_VARIANT_VIOLA, BACKGROUND_VARIANT_BLUEWHITE})
    @interface BackgroundVariant {}

    /**
     * An adapter for RecyclerViews that sets the background of its children.
     * @param <V> ViewHolder
     */
    public abstract static class StyledAdapter<V extends ViewHolder> extends RecyclerView.Adapter<V> {

        /** {@inheritDoc}
         * <hr>
         * Sets the background according to the preferences.
         */
        @CallSuper
        @Override
        public void onBindViewHolder(@NonNull V holder, int position) {
            setViewBackground(holder.itemView);
        }
    }
}
