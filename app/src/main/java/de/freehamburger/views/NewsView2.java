package de.freehamburger.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.preference.PreferenceManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import de.freehamburger.App;
import de.freehamburger.R;
import de.freehamburger.model.News;
import de.freehamburger.model.Region;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.util.ResourceUtil;
import de.freehamburger.util.Util;

/**
 *
 */
public class NewsView2 extends ConstraintLayout {

    private static final DateFormat DF = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    /** List of labels of all valid Regions */
    private static final List<String> REGION_LABELS = Region.getValidLabels();

    /** <em>this does not always exist!</em> */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE) @Nullable
    public TextView textViewTitle;
    /** Seriously, the topmost line within the View; this always exists */
    @VisibleForTesting
    public TextView textViewTopline;
    /** Located beneath the {@link #textViewTopline top line}; this always exists */
    @VisibleForTesting
    public TextView textViewDate;
    /** Located beneath the {@link #textViewDate date line}; this always exists */
    @VisibleForTesting
    public ImageView imageView;
    /** <em>this does not always exist!</em> */
    @VisibleForTesting @Nullable
    public TextView textViewFirstSentence;

    /**
     * Constructor.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public NewsView2(@NonNull Context ctx) {
        super(ctx, null, 0, 0);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public NewsView2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr int
     */
    public NewsView2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

    public TextView getTextViewDate() {
        return this.textViewDate;
    }

    @Nullable
    public TextView getTextViewFirstSentence() {
        return this.textViewFirstSentence;
    }

    @Nullable
    public TextView getTextViewTitle() {
        return this.textViewTitle;
    }

    public TextView getTextViewTopline() {
        return this.textViewTopline;
    }

    /**
     * Initialisation.
     */
    public final void init() {
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
     * @param prefs SharedPreferences (optional)
     * @return size of the bitmap being loaded into the {@link #imageView ImageView}
     */
    @UiThread
    @Nullable
    public final Size setNews(@Nullable final News news, @Nullable BitmapGetter bitmapGetter, @Nullable SharedPreferences prefs) {
        if (news == null) {
            this.textViewTopline.setText(null);
            this.textViewTopline.setTag(R.id.original_typeface, null);
            this.textViewDate.setText(null);
            this.textViewDate.setTag(R.id.original_typeface, null);
            if (this.textViewTitle != null) {
                this.textViewTitle.setText(null);
                this.textViewTitle.setTag(R.id.original_typeface, null);
            }
            if (this.textViewFirstSentence != null) {
                this.textViewFirstSentence.setText(null);
                this.textViewFirstSentence.setTag(R.id.original_typeface, null);
            }
            this.imageView.setImageDrawable(null);
            this.imageView.setElevation(0f);
            this.imageView.setTag(null);
            return null;
        }

        final Context ctx = getContext();

        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        // the original order is: topline, title, firstSentence

        /*
               news_view2.xml
        |-------------------------------------------|
        | textViewTopline                           |
        | textViewDate      textViewTitle           |
        | imageView         textViewFirstSentence   |
        |                                           |
        |                                           |
        |                                           |
        |-------------------------------------------|

               news_view_nocontent_notitle2.xml
        |-------------------------------------------|
        | textViewTopline                           |
        | textViewDate      imageView               |
        |                                           |
        |                                           |
        |                                           |
        |                                           |
        |-------------------------------------------|

         */

        boolean titleWentIntoTopline = false;

        // topline
        String contentForTopline = news.getTopline();
        if (TextUtils.isEmpty(contentForTopline)) {
            contentForTopline = news.getTitle();
            titleWentIntoTopline = true;
        }
        if (contentForTopline != null) contentForTopline = contentForTopline.trim();
        this.textViewTopline.setText(contentForTopline);
        if (prefs.getBoolean(App.PREF_TOPLINE_MARQUEE, false)) {
            this.textViewTopline.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            this.textViewTopline.setMarqueeRepeatLimit(-1);
            this.textViewTopline.setSelected(true);
        } else {
            this.textViewTopline.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (news.isBreakingNews()) {
            this.textViewTopline.setTextColor(ResourceUtil.getColor(ctx, R.color.colorBreakingNews));
        } else if (REGION_LABELS.contains(contentForTopline)) {
            this.textViewTopline.setTextColor(ResourceUtil.getColor(ctx, R.color.colorRegionalNews));
        } else {
            this.textViewTopline.setTextColor(ResourceUtil.getColor(ctx, R.color.colorContent));
        }

        // title
        if (this.textViewTitle != null) {
            if (titleWentIntoTopline) {
                this.textViewTitle.setText(null);
            } else {
                String title = news.getTitle();
                if (title != null) title = title.trim();
                this.textViewTitle.setText(title);
            }
        }

        // date
        Date date = news.getDate();
        if (date != null) {
            boolean relativeTime = prefs.getBoolean(App.PREF_TIME_MODE_RELATIVE, App.PREF_TIME_MODE_RELATIVE_DEFAULT);
            if (relativeTime) {
                String longDate = Util.getRelativeTime(ctx, date.getTime(), null, false);
                String shortDate = Util.getRelativeTime(ctx, date.getTime(), null, true);
                Util.fitText(this.textViewDate, "…", longDate, shortDate);
            } else {
                this.textViewDate.setText(DF.format(date));
            }
        } else {
            this.textViewDate.setText(null);
        }

        // firstSentence or shorttext or content.plainText
        if (this.textViewFirstSentence != null) {
            String fs = news.getTextForTextViewFirstSentence();
            if (!TextUtils.isEmpty(fs)) {
                this.textViewFirstSentence.setText(fs.trim());
                this.textViewFirstSentence.setVisibility(View.VISIBLE);
            } else {
                this.textViewFirstSentence.setVisibility(View.GONE);
                this.textViewFirstSentence.setText(null);
             }
        }

        // as the last step, load the image
        TeaserImage image = news.getTeaserImage();
        if (image == null) {
            // it is perfectly normal to have no TeaserImage
            this.imageView.setImageDrawable(null);
            this.imageView.setElevation(0f);
            this.imageView.setTag(null);
            this.imageView.setVisibility(View.INVISIBLE);
            return null;
        }
        this.imageView.setVisibility(View.VISIBLE);
        //
        int imageViewMaxWidth;
        if (this.imageView.getMaxWidth() > 0) {
            imageViewMaxWidth = this.imageView.getMaxWidth();   // should be the same as getResources().getDimensionPixelSize(R.dimen.image_width_normal)
        } else {
            imageViewMaxWidth = Util.getDisplaySize(ctx).x;
        }
        // get the image url; if there is no text in the right-hand part of the view (title or firstSentence), then preferrably in landscape orientation
        boolean landscapePreferred = this.textViewTitle == null && this.textViewFirstSentence == null;
        final TeaserImage.MeasuredImage measuredImage = image.getBestImageForWidth(imageViewMaxWidth, landscapePreferred ? TeaserImage.FORMAT_LANDSCAPE : TeaserImage.FORMAT_PORTRAIT);
        if (measuredImage == null || measuredImage.url == null || bitmapGetter == null) {
            this.imageView.setImageBitmap(null);
            this.imageView.setElevation(0f);
            return null;
        }
        this.imageView.setTag(measuredImage.url);
        if (!TextUtils.isEmpty(image.getTitle())) {
            this.imageView.setContentDescription(image.getTitle());
        } else if (!TextUtils.isEmpty(image.getAlttext())) {
            this.imageView.setContentDescription(image.getAlttext());
        } else {
            this.imageView.setContentDescription(null);
        }
        // clear the image before loading it
        this.imageView.setImageBitmap(null);
        this.imageView.setElevation(0f);
        //
        bitmapGetter.loadImageIntoImageView(measuredImage.url, this.imageView, measuredImage.width, measuredImage.height);
        return new Size(measuredImage.width, measuredImage.height);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String toString() {
        return getClass().getSimpleName() + " \"" + (this.textViewTitle != null ? this.textViewTitle.getText().toString() : "<null>") + "\"";
    }

    /**
     * Retrieves a bitmap asynchronously from a remote resource.
     */
    @FunctionalInterface
    public interface BitmapGetter {

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
