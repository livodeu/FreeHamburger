package de.freehamburger.views;

import android.content.Context;
import androidx.annotation.LayoutRes;
import android.util.AttributeSet;

import de.freehamburger.R;

/**
 *
 */
public class RelatedViewVertical extends RelatedView {


    public RelatedViewVertical(Context context) {
        super(context);
    }

    public RelatedViewVertical(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RelatedViewVertical(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @LayoutRes
    int getLayoutId() {
        return R.layout.related_view_vert;
    }

}
