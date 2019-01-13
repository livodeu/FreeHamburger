package de.freehamburger.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.style.ImageSpan;
import android.text.style.TypefaceSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import de.freehamburger.model.Content;

/**
 * Handles clicks on images that are embedded in a TextView.
 */
public class TextViewImageSpanClickHandler implements View.OnTouchListener {

    /** See {@link android.view.ViewConfiguration ViewConfiguration.DEFAULT_LONG_PRESS_TIMEOUT} */
    private static final long MIN_DOWN_TIME = 500L;

    private final Handler handler = new Handler();

    /**
     * Tries to find the ImageSpan that the given MotionEvent refers to.
     * @param textView TextView
     * @param event MotionEvent
     * @return ImageSpan
     */
    @Nullable
    private static ImageSpan findImageSpan(@NonNull TextView textView, @NonNull MotionEvent event) {
        // try to find the ImageSpan that the user has just tapped/clicked
        int offset = textView.getOffsetForPosition(event.getX(), event.getY());
        if (offset < 0) return null;
        CharSequence cs = textView.getText();
        if (!(cs instanceof Spannable)) return null;
        final Spannable spannable = (Spannable) cs;
        final ImageSpan[] imageSpans = spannable.getSpans(0, spannable.length(), ImageSpan.class);
        if (imageSpans != null) {
            for (ImageSpan imageSpan : imageSpans) {
                int start = spannable.getSpanStart(imageSpan);
                int end = spannable.getSpanEnd(imageSpan);
                if (offset >= start && offset <= end) {
                    return imageSpan;
                }
            }
        }
        return null;
    }

    /**
     * Handles the click.
     * @param textView TextView TextView that has been clicked
     * @param imageSpan ImageSpan in the TextView that has been clicked
     */
    private static void handle(@NonNull TextView textView, @NonNull ImageSpan imageSpan) {
        final Spannable spannable;
        try {
            spannable = (Spannable) textView.getText();
        } catch (Throwable ignored) {
            return;
        }
        // try to determine the title
        String title = null;
        TypefaceSpan[] followingspans = spannable.getSpans(spannable.getSpanEnd(imageSpan) + 1, spannable.length(), TypefaceSpan.class);
        if (followingspans != null && followingspans.length > 0) {
            // the content of TypefaceSpan.getFamily() must match the typeface given in Content for the title for TYPE_IMAGE_GALLERY
            if (Content.FONT_FACE_IMAGE_TITLE.equals(followingspans[0].getFamily())) {
                int titleStart = spannable.getSpanStart(followingspans[0]);
                int titleEnd = spannable.getSpanEnd(followingspans[0]);
                title = titleEnd > titleStart ? spannable.subSequence(titleStart, titleEnd).toString() : null;
            }
        }
        //
        Context ctx = textView.getContext();
        String source = imageSpan.getSource();
        if (source != null) {
            Util.sendUrl(ctx, source, title);
            return;
        }
        // if there is no source url, try to send the image as Bitmap
        Drawable drawable = imageSpan.getDrawable();
        if (!(drawable instanceof BitmapDrawable)) return;
        Bitmap bm = ((BitmapDrawable)drawable).getBitmap();
        Util.sendBitmap(ctx, bm, title);
    }

    /**
     * Constructor.
     */
    public TextViewImageSpanClickHandler() {
        super();
    }

    /** {@inheritDoc} */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof TextView) || event.getAction() != MotionEvent.ACTION_DOWN) return false;
        final ImageSpan imageSpan = findImageSpan((TextView)v, event);
        if (imageSpan == null) return false;
        this.handler.postDelayed(new LongClickChecker(v, handler, () -> {
            // the View is still pressed after MIN_DOWN_TIME
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            v.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            handle((TextView)v, imageSpan);
        }), MIN_DOWN_TIME);
        return false;
    }

    /**
     * Runs a {@link Runnable} when a given View is {@link View#isPressed() pressed}.
     */
    private static class LongClickChecker implements Runnable {

        private View v;
        private Runnable runMe;
        private Handler handler;

        private LongClickChecker(@NonNull View v, @NonNull Handler handler, @NonNull Runnable runMe) {
            super();
            this.v = v;
            this.handler = handler;
            this.runMe = runMe;
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            if (v.isPressed()) {
                handler.post(runMe);
            }
            v = null;
            handler = null;
            runMe = null;
        }
    }
}
