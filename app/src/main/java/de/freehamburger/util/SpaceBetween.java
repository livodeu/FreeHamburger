package de.freehamburger.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import de.freehamburger.R;

/**
 *
 */
public class SpaceBetween extends RecyclerView.ItemDecoration {

    private static final String DEKO_LEFT = "❦";
    private static final String DEKO_RITE = "❦";
    private final int space;
    private final Paint paint = new Paint();
    private final Rect leftTextRect = new Rect();
    private final Rect riteTextRect = new Rect();
    private final Rect childRect = new Rect();
    private final Drawable leftLine;
    private final Drawable riteLine;

    /**
     * Constructor.
     * @param distance distance in px
     */
    public SpaceBetween(Context ctx, int distance) {
        this.space = distance;
        this.paint.setColor(0xaa1599e6);
        this.paint.setTextSize(space);
        this.paint.getTextBounds(DEKO_LEFT, 0, DEKO_LEFT.length(), this.leftTextRect);
        this.paint.getTextBounds(DEKO_RITE, 0, DEKO_RITE.length(), this.riteTextRect);
        this.leftLine = ctx.getDrawable(R.drawable.nav_deco_left);
        this.riteLine = ctx.getDrawable(R.drawable.nav_deco_right);
    }

    /** {@inheritDoc} */
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        // https://stackoverflow.com/questions/24618829/how-to-add-dividers-and-spaces-between-items-in-recyclerview/27037230
        outRect.bottom = this.space;
        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = this.space;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onDrawOver(@NonNull final Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (parent.getLayoutManager() == null || parent.getChildCount() < 1) return;
        View kid0 = parent.getChildAt(0);
        parent.getDecoratedBoundsWithMargins(kid0, this.childRect);
        int leftTopY = Math.round(this.childRect.bottom - this.leftTextRect.bottom);
        int riteTopY = Math.round(this.childRect.bottom - this.riteTextRect.bottom);
        c.drawText(DEKO_LEFT, 0, leftTopY, this.paint);
        c.drawText(DEKO_RITE, c.getWidth() - this.riteTextRect.right, riteTopY, paint);
        c.drawText(DEKO_LEFT, 0, c.getHeight() - this.leftTextRect.bottom, paint);
        c.drawText(DEKO_RITE, c.getWidth() - this.riteTextRect.right, c.getHeight() - this.riteTextRect.bottom, paint);

        int topLineTop = leftTopY - (this.space >> 1);
        int topLineBottom = topLineTop + (this.space >> 1);
        this.leftLine.setBounds(leftTextRect.right + this.space, topLineTop, c.getWidth() / 3, topLineBottom);
        this.leftLine.draw(c);
        this.riteLine.setBounds(c.getWidth() * 2 / 3, topLineTop, c.getWidth() - riteTextRect.right - this.space, topLineBottom);
        this.riteLine.draw(c);
    }
}