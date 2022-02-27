package de.freehamburger.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

import de.freehamburger.R;

/**
 *
 */
public class SpaceBetween extends RecyclerView.ItemDecoration {

    private static final String DEKO_LEFT = "❦";
    private static final String DEKO_RITE = "❦";

    private final int space;
    private final int halfSpace;
    private final Paint paint = new Paint();
    private final Rect leftTextRect = new Rect();
    private final Rect riteTextRect = new Rect();
    private final Rect childRect = new Rect();
    private final Drawable leftLine;
    private final Drawable riteLine;

    /**
     * Constructor.
     * @param ctx Context
     * @param distance distance in px
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public SpaceBetween(@NonNull Context ctx, int distance) {
        this.space = Math.abs(distance);
        this.halfSpace = this.space >> 1;
        this.paint.setColor(ctx.getResources().getColor(R.color.colorSpaceBetween));
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
        final int cw = c.getWidth();
        final int ch = c.getHeight();
        View kid0 = parent.getChildAt(0);
        parent.getDecoratedBoundsWithMargins(kid0, this.childRect);
        int leftTopY = this.childRect.bottom - this.leftTextRect.bottom;
        int riteTopY = this.childRect.bottom - this.riteTextRect.bottom;
        c.drawText(DEKO_LEFT, 0, leftTopY, this.paint);
        c.drawText(DEKO_RITE, cw - this.riteTextRect.right, riteTopY, paint);
        c.drawText(DEKO_LEFT, 0, ch - this.leftTextRect.bottom, paint);
        c.drawText(DEKO_RITE, cw - this.riteTextRect.right, ch - this.riteTextRect.bottom, paint);

        int topLineTop = leftTopY - this.halfSpace;
        int topLineBottom = topLineTop + this.halfSpace;
        this.leftLine.setBounds(leftTextRect.right + this.space, topLineTop, cw / 3, topLineBottom);
        this.leftLine.draw(c);
        this.riteLine.setBounds(cw * 2 / 3, topLineTop, cw - riteTextRect.right - this.space, topLineBottom);
        this.riteLine.draw(c);
    }
}