package de.freehamburger.adapters;

import android.content.Context;
import android.os.Build;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import de.freehamburger.R;
import de.freehamburger.model.Region;

/**
 *
 */
public class RegionsAdapter extends BaseAdapter {

    private final List<Region> regions;
    private final Context ctx;
    private final boolean[] checked;
    @ColorInt private final int colorAccent;
    @ColorInt private final int colorText;

    /**
     * Constructor.
     * @param ctx Context
     * @param regions list of Regions
     * @param checked checked Regions
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public RegionsAdapter(@NonNull Context ctx, @Nullable List<Region> regions, @Nullable boolean[] checked) {
        this.ctx = ctx;
        this.regions = regions != null ? regions : Region.getValidRegions();
        this.checked = checked != null ? checked : new boolean[this.regions.size()];
        this.colorText = ctx.getResources().getColor(R.color.color_onBackground);
        this.colorAccent = ctx.getResources().getColor(R.color.colorAccent);
    }

    @NonNull
    public boolean[] getChecked() {
        return this.checked;
    }

    /** {@inheritDoc} */
    @Override
    public int getCount() {
        return this.regions.size();
    }

    /** {@inheritDoc} */
    @Override
    public Object getItem(int position) {
        return this.regions.get(position);
    }

    /** {@inheritDoc} */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /** {@inheritDoc} */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv;
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            tv = new TextView(this.ctx);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tv.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
            } else {
                tv.setTextAppearance(this.ctx, R.style.TextAppearance_Material3_BodyMedium);
            }
            tv.setMaxLines(1);
            tv.setPadding(32, 4, 8, 4);
            tv.setFocusable(false);
        }
        tv.setText(getItem(position).toString());
        tv.setTextColor(this.checked[position] ? this.colorAccent : this.colorText);
        return tv;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    public void toggle(@IntRange(from = 0) int index) {
        this.checked[index] = !this.checked[index];
        notifyDataSetChanged();
    }
}
