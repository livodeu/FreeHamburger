package de.freehamburger.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Arrays;
import java.util.List;

import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerService;
import de.freehamburger.R;
import de.freehamburger.model.News;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.util.ResourceUtil;

public class RecommendationsAdapter extends BaseAdapter {

    private static final String TAG = "RecommendationsAdapter";
    /** max. number of text lines for each view */
    private static final int MAX_LINES = 3;

    @NonNull private final List<News> news;
    private final boolean[] picLoading;
    private final BitmapDrawable[] pics;
    private final Target[] targets;
    private final int iconSize;
    private final int iconPadding;
    /** the vertical padding */
    private final int padding;
    private final Drawable placeholder;

    private HamburgerService service;

    /**
     * Constructor.
     * @param news recommended News
     * @param service HamburgerService
     * @throws NullPointerException if any parameter is null
     */
    public RecommendationsAdapter(@NonNull List<News> news, @NonNull HamburgerService service) {
        super();
        this.news = news;
        this.picLoading = new boolean[this.news.size()];
        this.pics = new BitmapDrawable[this.news.size()];
        this.targets = new Target[this.news.size()];
        Resources r = service.getResources();
        this.padding = r.getDimensionPixelSize(R.dimen.recommendation_padding);
        this.iconSize = r.getDimensionPixelSize(R.dimen.recommendation_icon_width);
        this.iconPadding = r.getDimensionPixelSize(R.dimen.recommendation_icon_padding);
        this.service = service;
        Bitmap bm = Bitmap.createBitmap(this.iconSize, this.iconSize, Bitmap.Config.ARGB_8888);
        bm.eraseColor(ResourceUtil.getColor(service, R.color.colorPrimarySemiTrans));
        this.placeholder = new BitmapDrawable(r, bm);
        this.placeholder.setBounds(0, 0, this.iconSize, this.iconSize);
    }

    public void cleanup() {
        this.news.clear();
        Arrays.fill(this.targets, null);
        this.service = null;
    }

    /** {@inheritDoc} */
    @Override protected void finalize() {
        if (BuildConfig.DEBUG && this.service != null) android.util.Log.e(TAG, "cleanup() not called!");
    }

    /** {@inheritDoc} */
    @Override public int getCount() {
        return this.news.size();
    }

    /** {@inheritDoc} */
    @Override public Object getItem(int position) {
        return this.news.get(position);
    }

    /** {@inheritDoc} */
    @Override public long getItemId(int position) {
        return position;
    }

    /** {@inheritDoc} */
    @Override public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {
        final TextView textView;
        final Context ctx = parent.getContext();
        if (convertView instanceof TextView) {
            textView = (TextView) convertView;
        } else {
            textView = (TextView) LayoutInflater.from(ctx).inflate(android.R.layout.simple_list_item_1, parent, false);
            textView.setMinimumHeight(iconSize + padding);
            textView.setPadding(padding << 1, padding, padding << 1, padding);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(MAX_LINES);
            textView.setCompoundDrawablePadding(this.iconPadding);
            textView.setBackgroundResource(R.drawable.bg_recommendation);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                textView.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
                textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL);
            } else {
                // the TextAppearance in the >= M block above has got this text size
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            }
        }
        News news = (News)getItem(position);
        textView.setText(news.getTitle());
        if (this.pics[position] != null) {
            textView.setCompoundDrawables(this.pics[position], null, null, null);
        } else if (this.service != null && !this.picLoading[position]) {
            String imageUrl;
            if (news.getTeaserImage() != null) {
                TeaserImage.MeasuredImage mi = news.getTeaserImage().getBestImageForWidth(this.iconSize, TeaserImage.FORMAT_SQUARE);
                imageUrl = mi != null ? mi.url : null;
            } else {
                imageUrl = null;
            }
            if (imageUrl != null) {
                this.picLoading[position] = true;
                this.targets[position] = new Target() {
                    @Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "While loading \"" + imageUrl + "\": " + e, e);
                        View kid = parent.getChildAt(position);
                        if (kid instanceof TextView) ((TextView)kid).setCompoundDrawables(null, null, null, null);
                    }

                    @Override public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        if (bitmap == null) return;
                        try {
                            float ratio = (float)bitmap.getWidth() / (float)bitmap.getHeight();
                            bitmap = Bitmap.createScaledBitmap(bitmap, RecommendationsAdapter.this.iconSize, Math.round(RecommendationsAdapter.this.iconSize / ratio), true);
                            BitmapDrawable d = new BitmapDrawable(ctx.getResources(), bitmap);
                            d.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            RecommendationsAdapter.this.pics[position] = d;
                            View kid = parent.getChildAt(position);
                            if (kid instanceof TextView) ((TextView)kid).setCompoundDrawables(d, null, null, null);
                        } catch (Exception e) {
                            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                        }
                    }

                    @Override public void onPrepareLoad(Drawable placeHolderDrawable) {
                        View kid = parent.getChildAt(position);
                        if (kid instanceof TextView) ((TextView)kid).setCompoundDrawables(placeHolderDrawable, null, null, null);
                    }
                };
                this.service.loadImage(imageUrl, this.targets[position], this.placeholder);
            } else if (BuildConfig.DEBUG) Log.w(TAG, "No image url for  \"" + news.getTitle() + "\"");
        } else if (service == null && !this.picLoading[position]) {
            if (BuildConfig.DEBUG) Log.w(TAG, "No service! Cannot load pic for pos. " + position);
        }
        return textView;
    }

    /** {@inheritDoc} */
    @Override public boolean hasStableIds() {
        return true;
    }
}
