package de.freehamburger.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import de.freehamburger.App;
import de.freehamburger.R;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.util.Util;

/**
 *
 */
public class NewsView extends RelativeLayout {

    private static final DateFormat DF = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final List<String> REGION_LABELS = Region.getValidLabels();
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE) @Nullable
    public TextView textViewTitle;
    @VisibleForTesting
    public TextView textViewTopline;
    @VisibleForTesting
    public TextView textViewDate;
    @VisibleForTesting @Nullable
    public ImageView imageView;
    @VisibleForTesting @Nullable
    public TextView textViewFirstSentence;

    /**
     * Returns a textual representation of a time difference, e.g. "2¼ hours ago".
     * @param ctx Context
     * @param time Date
     * @param basedOn Date (set to {@code null} to compare time to current time)
     * @return relative time
     * @throws NullPointerException if {@code ctx} is {@code null} and {@code time} is not {@code null}
     */
    @NonNull
    public static String getRelativeTime(@NonNull Context ctx, @Nullable Date time, @Nullable Date basedOn) {
        if (time == null) return "";
        final long delta;
        if (basedOn == null) {
            delta = Math.abs(time.getTime() - System.currentTimeMillis());
        } else {
            delta = Math.abs(time.getTime() - basedOn.getTime());
        }
        if (delta < 60_000L) {
            int seconds = (int)(delta / 1_000L);
            return ctx.getResources().getQuantityString(R.plurals.label_time_rel_seconds, seconds, seconds);
        }
        if (delta < 60 * 60_000L) {
            int minutes = (int)(delta / 60_000L);
            return ctx.getResources().getQuantityString(R.plurals.label_time_rel_minutes, minutes, minutes);
        }
        if (delta < 24 * 60 * 60_000L) {
            double dhours = delta / 3_600_000.;
            int hours = (int)dhours;
            final double frac = dhours - (double)hours;
            if (frac >= 0.125 && frac < 0.375) {
                return ctx.getResources().getQuantityString(R.plurals.label_time_rel_hours1, hours, hours);
            }
            if (frac >= 0.375 && frac < 0.625) {
                return ctx.getResources().getQuantityString(R.plurals.label_time_rel_hours2, hours, hours);
            }
            if (frac >= 0.625 && frac < 0.875) {
                return ctx.getResources().getQuantityString(R.plurals.label_time_rel_hours3, hours, hours);
            }
            if (frac >= 0.875) hours++;
            return ctx.getResources().getQuantityString(R.plurals.label_time_rel_hours, hours, hours);
        }
        int days = (int)(delta / 86_400_000L);
        return ctx.getResources().getQuantityString(R.plurals.label_time_rel_days, days, days);
    }

    /**
     * Constructor.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public NewsView(@NonNull Context ctx) {
        super(ctx, null, 0, 0);
        init(ctx);
    }

    /**
     * Returns the url of the image to be displayed.
     * @return image url
     */
    @Nullable
    public String getImageUrl() {
        if (this.imageView == null) return null;
        Object tag = this.imageView.getTag();
        return tag instanceof String ? (String)tag : null;
    }

    /**
     * Returns the layout resource for this View.
     * @return layout resource id
     */
    @LayoutRes
    int getLid() {
        return R.layout.news_view;
    }

    /**
     * Initialisation.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    private void init(@NonNull Context ctx) {
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return;
        inflater.inflate(getLid(), this);
        this.textViewTopline = findViewById(R.id.textViewTopline);
        this.textViewTitle = findViewById(R.id.textViewTitle);
        this.textViewDate = findViewById(R.id.textViewDate);
        this.imageView = findViewById(R.id.imageView);
        this.textViewFirstSentence = findViewById(R.id.textViewFirstSentence);
    }

    /**
     * Sets this view's contents.
     * @param news News
     * @param bitmapGetter BitmapGetter implementation
     */
    @CallSuper
    @UiThread
    public void setNews(@Nullable final News news, @Nullable BitmapGetter bitmapGetter) {
        if (news == null) {
            this.textViewTopline.setText(null);
            if (this.textViewTitle != null) this.textViewTitle.setText(null);
            this.textViewDate.setText(null);
            if (this.textViewFirstSentence != null) this.textViewFirstSentence.setText(null);
            if (this.imageView != null) this.imageView.setImageDrawable(null);
            return;
        }
        Context ctx = getContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final boolean fixQ = prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, false);
        // the original order is: topline, title, firstSentence

        /*
               news_view.xml
        |-------------------------------------------|
        | textViewTopline                           |
        | textViewDate      textViewTitle           |
        | imageView         textViewFirstSentence   |
        |                                           |
        |                                           |
        |                                           |
        |-------------------------------------------|

               news_view_nocontent.xml
        |-------------------------------------------|
        | textViewTopline                           |
        | textViewDate      textViewTitle           |
        | imageView                                 |
        |                                           |
        |                                           |
        |                                           |
        |-------------------------------------------|

               news_view_nocontent_notitle.xml
        |-------------------------------------------|
        | textViewTopline                           |
        | textViewDate      imageView               |
        |                                           |
        |                                           |
        |                                           |
        |                                           |
        |-------------------------------------------|

         */

        // topline (only the news items from Source.HOME have toplines)
        String newsTopline = news.getTopline();
        boolean hasTopline = !TextUtils.isEmpty(newsTopline);
        boolean titleReplacesTopline = !hasTopline;
        if (!hasTopline) newsTopline = news.getTitle();
        if (newsTopline != null) newsTopline = newsTopline.trim();
        this.textViewTopline.setTypeface(Util.getTypefaceForTextView(this.textViewTopline, newsTopline));
        this.textViewTopline.setText(fixQ ? Util.fixQuotationMarks(newsTopline) : newsTopline);
        if (news.isBreakingNews()) {
            this.textViewTopline.setTextColor(getResources().getColor(R.color.colorBreakingNews));
        } else if (REGION_LABELS.contains(newsTopline)) {
            this.textViewTopline.setTextColor(getResources().getColor(R.color.colorRegionalNews));
        } else {
            this.textViewTopline.setTextColor(Color.BLACK);
        }

        // title
        if (this.textViewTitle != null) {
            if (!titleReplacesTopline) {
                String title = news.getTitle(); if (title != null) title = title.trim();
                this.textViewTitle.setText(fixQ ? Util.fixQuotationMarks(title) : title);
                this.textViewTitle.setVisibility(View.VISIBLE);
                if (this.textViewFirstSentence != null) {
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewFirstSentence.getLayoutParams();
                    lp.removeRule(RelativeLayout.BELOW);
                    lp.addRule(RelativeLayout.BELOW, R.id.textViewTitle);
                }
            } else {
                this.textViewTitle.setVisibility(View.GONE);
                if (this.textViewFirstSentence != null) {
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewFirstSentence.getLayoutParams();
                    lp.removeRule(RelativeLayout.BELOW);
                    lp.addRule(RelativeLayout.BELOW, R.id.textViewTopline);
                }
            }
        }

        // date
        Date date = news.getDate();
        if (date != null) {
            boolean timeMode = prefs.getBoolean(App.PREF_TIME_MODE_RELATIVE, App.PREF_TIME_MODE_RELATIVE_DEFAULT);
            this.textViewDate.setText(timeMode ? getRelativeTime(ctx, date, null) : DF.format(date));
        } else {
            this.textViewDate.setText(null);
        }

        // firstSentence or shorttext or content.plainText
        if (this.textViewFirstSentence != null) {
            String fs = news.getTextForTextViewFirstSentence();
            if (!TextUtils.isEmpty(fs)) {
                this.textViewFirstSentence.setText(fixQ ? Util.fixQuotationMarks(fs.trim()) : fs.trim());
                this.textViewFirstSentence.setVisibility(View.VISIBLE);
            } else {
                this.textViewFirstSentence.setVisibility(View.GONE);
                this.textViewFirstSentence.setText(null);
             }
        }

        // as the last step, load the image
        if (this.imageView == null) return;
        TeaserImage image = news.getTeaserImage();
        if (image == null) {
            // it is perfectly normal to have no TeaserImage
            this.imageView.setImageDrawable(null);
            this.imageView.setTag(null);
            this.imageView.setVisibility(View.GONE);
            // let textViewTitle start to the end of textViewDate instead of imageView
            if (this.textViewTitle != null) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewTitle.getLayoutParams();
                lp.removeRule(RelativeLayout.END_OF);
                lp.addRule(RelativeLayout.END_OF, R.id.textViewDate);
            }
            // let textViewFirstSentence start to the end of textViewDate instead of imageView
            if (this.textViewFirstSentence != null) {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewFirstSentence.getLayoutParams();
                lp.removeRule(RelativeLayout.END_OF);
                lp.addRule(RelativeLayout.END_OF, R.id.textViewDate);
            }
            return;
        }
        // restore imageView, in case it had been removed previously
        this.imageView.setVisibility(View.VISIBLE);
        // restore layout parameters of textViewTitle
        if (this.textViewTitle != null) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewTitle.getLayoutParams();
            lp.removeRule(RelativeLayout.END_OF);
            lp.addRule(RelativeLayout.END_OF, R.id.imageView);
        }
        // restore layout parameters of textViewFirstSentence
        if (this.textViewFirstSentence != null) {
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewFirstSentence.getLayoutParams();
            lp.removeRule(RelativeLayout.END_OF);
            lp.addRule(RelativeLayout.END_OF, R.id.imageView);
        }
        //
        int imageViewMaxWidth;
        if (this.imageView.getMaxWidth() > 0) {
            imageViewMaxWidth = this.imageView.getMaxWidth();   // should be the same as getResources().getDimensionPixelSize(R.dimen.image_width_normal)
        } else {
            imageViewMaxWidth = Util.getDisplaySize(ctx).x;
        }
        // get the image url; if this NewsView was inflated from news_view_nocontent_notitle (no textViewTitle and no textViewFirstSentence), then preferrably in landscape orientation
        boolean landscapePreferred = this.textViewTitle == null && this.textViewFirstSentence == null;
        //
        final TeaserImage.MeasuredImage measuredImage = image.getBestImageForWidth(imageViewMaxWidth, landscapePreferred);
        this.imageView.setTag(measuredImage != null ? measuredImage.url : null);
        if (!TextUtils.isEmpty(image.getTitle())) {
            this.imageView.setContentDescription(image.getTitle());
        } else if (!TextUtils.isEmpty(image.getAlttext())) {
            this.imageView.setContentDescription(image.getAlttext());
        } else {
            this.imageView.setContentDescription(null);
        }
        if (measuredImage == null || measuredImage.url == null) {
            this.imageView.setImageBitmap(null);
            return;
        }
        if (bitmapGetter == null) {
            this.imageView.setImageBitmap(null);
            return;
        }
        Bitmap bitmap = bitmapGetter.getCachedBitmap(measuredImage.url);
        if (bitmap != null) {
            this.imageView.setImageBitmap(bitmap);
            return;
        }
        // clear the image before loading it
        this.imageView.setImageBitmap(null);
        //
        bitmapGetter.loadImageIntoImageView(measuredImage.url, this.imageView, measuredImage.width, measuredImage.height);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return "NewsView \"" + (this.textViewTitle != null ? this.textViewTitle.getText().toString() : "<null>") + "\"";
    }

    /**
     * Retrieves a bitmap either synchronously from a cache or asynchronously from a remote resource.
     */
    public interface BitmapGetter {

        /**
         * Returns a bitmap from the memory cache.
         * @param url image uri
         * @return Bitmap
         */
        @Nullable
        Bitmap getCachedBitmap(@NonNull String url);

        /**
         * Loads a picture into the given ImageView.
         * @param url image uri
         * @param imageView ImageView to load the bitmap into
         * @param imageWidth expected width of the image
         * @param imageHeight expected height of the image
         */
        void loadImageIntoImageView(@NonNull String url, @Nullable ImageView imageView, int imageWidth, int imageHeight);
    }
}
