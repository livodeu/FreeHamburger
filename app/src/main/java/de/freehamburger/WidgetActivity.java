package de.freehamburger;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.freehamburger.model.Source;
import de.freehamburger.widget.WidgetProvider;

/**
 * Configures app widgets.
 */
public class WidgetActivity extends AppCompatActivity {

    /** the {@link Source sources} that a user may not select as widget sources */
    private static final List<Source> FORBIDDEN = Arrays.asList(Source.WEATHER, Source.VIDEO, Source.CHANNELS);
    private static final int UI_FLAGS = View.SYSTEM_UI_FLAG_LOW_PROFILE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    /** the {@link Source sources} that a user may select */
    private final List<Source> availableSources = new ArrayList<>(8);
    private final Handler handler = new Handler();
    private final Runnable hideStatusBar = () -> getWindow().getDecorView().setSystemUiVisibility(UI_FLAGS);
    /** the id of the app widget that is being configured here */
    private int widgetId;
    private RecyclerView recyclerView;
    /** maps widget ids to Sources */
    private SparseArray<Source> widgetSources;
    private Source originalSource;

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        HamburgerActivity.applyTheme(this, null, false);
        super.onCreate(savedInstanceState);
        getDelegate().setContentView(R.layout.activity_widget);
        for (Source source : Source.values()) {
            // skip sources without meaningful textual content
            if (FORBIDDEN.contains(source)) continue;
            this.availableSources.add(source);
        }
        this.widgetSources = WidgetProvider.loadWidgetSources(this);
        Intent intent = getIntent();
        this.widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction()) || this.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        this.originalSource = this.widgetSources.get(this.widgetId);
        Toolbar toolbar = getDelegate().findViewById(R.id.toolbar);
        if (toolbar != null) setSupportActionBar(toolbar);
        this.recyclerView = getDelegate().findViewById(R.id.recyclerView);
        assert this.recyclerView != null;
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.recyclerView.setHasFixedSize(true);
        this.recyclerView.setAdapter(new RecyclerView.Adapter<WidgetActivity.ViewHolder>() {

            @ColorInt final int colorAccent = getResources().getColor(R.color.colorAccent);

            @Override
            public int getItemCount() {
                return WidgetActivity.this.availableSources.size();
            }

            @Override
            public void onBindViewHolder(@NonNull WidgetActivity.ViewHolder holder, int position) {
                final TextView tv = (TextView)holder.itemView;
                Source source = WidgetActivity.this.availableSources.get(position);
                boolean selected = WidgetActivity.this.widgetSources.get(WidgetActivity.this.widgetId) == source;
                tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(source.getIcon()), null, null, null);
                tv.setText(getString(source.getLabel()));
                tv.setContentDescription(getString(R.string.hint_widget_source, tv.getText()));
                tv.setBackgroundColor(selected ? this.colorAccent : Color.TRANSPARENT);
                tv.setElevation(selected ? 20f : 0f);
            }

            @NonNull
            @Override
            public WidgetActivity.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                final TextView tv = new TextView(WidgetActivity.this);
                tv.setSingleLine();
                tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                tv.setPadding(24, 8, 24, 8);
                tv.setHapticFeedbackEnabled(true);
                return new WidgetActivity.ViewHolder(tv);
            }
        });
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, this.widgetId));
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        // save the change that has been made here
        WidgetProvider.storeWidgetSources(this, this.widgetSources);
        // update the widget
        WidgetProvider.updateWidgets(this, this.widgetSources);
        // display a Toast indicating the result
        if (this.originalSource != null) {
            Source s = this.widgetSources.get(this.widgetId);
            if (s != null) {
                if (this.originalSource.equals(s)) Toast.makeText(this, R.string.msg_widget_source_notchanged, Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, getString(R.string.msg_widget_source_changed, getString(s.getLabel())), Toast.LENGTH_SHORT).show();
            }
        }
        //
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.handler.post(this.hideStatusBar);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView.setOnClickListener(this);
        }

        @SuppressWarnings("ConstantConditions")
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (position < 0 || position >= WidgetActivity.this.availableSources.size()) return;
            Source source = WidgetActivity.this.availableSources.get(position);
            WidgetActivity.this.widgetSources.put(WidgetActivity.this.widgetId, source);
            WidgetActivity.this.recyclerView.getAdapter().notifyDataSetChanged();
        }
    }
}
