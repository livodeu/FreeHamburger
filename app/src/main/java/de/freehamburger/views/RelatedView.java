package de.freehamburger.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.text.DateFormat;

import de.freehamburger.HamburgerActivity;
import de.freehamburger.HamburgerService;
import de.freehamburger.R;
import de.freehamburger.model.Related;
import de.freehamburger.model.TeaserImage;

/**
 *
 */
public class RelatedView extends RelativeLayout {

    private static final DateFormat DF = DateFormat.getDateInstance(DateFormat.MEDIUM);
    private ImageView imageViewRelated;
    private ImageView imageViewType;
    private TextView textViewDate;
    private TextView textViewTitle;
    @Nullable
    private Related related;

    /**
     * Constructor.
     * @param context Context
     */
    public RelatedView(Context context) {
        super(context);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public RelatedView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr int
     */
    public RelatedView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Nullable
    public Related getRelated() {
        return related;
    }

    public final void init() {
        imageViewRelated = findViewById(R.id.imageViewRelated);
        imageViewType = findViewById(R.id.imageViewType);
        imageViewRelated.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        textViewDate = findViewById(R.id.textViewDate);
        textViewTitle = findViewById(R.id.textViewTitle);
    }

    public void setRelated(@Nullable Related r) {
        this.related = r;
        if (r == null) {
            imageViewRelated.setImageDrawable(null);
            imageViewType.setVisibility(View.GONE);
            textViewTitle.setText(null);
            textViewDate.setText(null);
        } else {
            String title = r.getTitle();
            textViewTitle.setText(!TextUtils.isEmpty(title) ? title : "-");
            java.util.Date date = r.getDate();
            textViewDate.setText(date != null ? DF.format(date) : null);
            TeaserImage teaserImage = r.getTeaserImage();
            if (teaserImage == null) return;
            String url = teaserImage.getBestImage();
            if (url == null) return;
            if ("video".equals(related.getType())) {
                imageViewType.setVisibility(View.VISIBLE);
                imageViewType.setImageResource(R.drawable.ic_videocam_content_24dp);
            } else {
                // the type is usually "story", sometimes "webview"
                imageViewType.setVisibility(View.GONE);
            }
            Context ctx = getContext();
            if (ctx instanceof HamburgerActivity) {
                HamburgerService service = ((HamburgerActivity) ctx).getHamburgerService();
                if (service != null) {
                    service.loadImageIntoImageView(url, imageViewRelated, -1, -1);
                }
            }
        }
    }
}
