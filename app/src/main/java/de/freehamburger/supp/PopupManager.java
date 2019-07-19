package de.freehamburger.supp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.transition.Fade;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;

import de.freehamburger.R;

/**
 * See http://androidsrc.net/android-popupwindow-show-tooltip/
 */
public class PopupManager implements PopupWindow.OnDismissListener {

    private final int[] sp = new int[2];
    private final Runnable dismisser = this::dismiss;
    private PopupWindow popupWindow;

    /**
     * Constructor.
     */
    public PopupManager() {
        super();
    }

    public void destroy() {
        dismiss();
        popupWindow = null;
    }

    /**
     * Dismisses the popup.
     */
    private void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.getContentView().playSoundEffect(SoundEffectConstants.CLICK);
            popupWindow.dismiss();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onDismiss() {
    }

    /**
     * Displays a popup.
     * @param anchor View to attach the popup to
     * @param txt popup contents
     * @param period optional duration after which the popup will be dismissed
     * @throws NullPointerException if {@code anchor} is {@code null}
     */
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    public void showPopup(@NonNull View anchor, CharSequence txt, long period) {
        Context ctx = anchor.getContext();
        Handler handler = anchor.getHandler();
        if (handler != null) {
            handler.removeCallbacks(dismisser);
        }
        if (popupWindow == null) {
            popupWindow = new PopupWindow(ctx);
            popupWindow.setOnDismissListener(this);
            if (Build.VERSION.SDK_INT >= 23) {
                popupWindow.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            }
            popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setOutsideTouchable(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Fade enterFade = new Fade(ctx, null);
                enterFade.setDuration(750L);
                enterFade.setInterpolator(new DecelerateInterpolator(2f));
                popupWindow.setEnterTransition(enterFade);
                Fade exitFade = new Fade(ctx, null);
                exitFade.setDuration(750L);
                exitFade.setInterpolator(new AccelerateInterpolator());
                popupWindow.setExitTransition(exitFade);
            }
            popupWindow.setTouchInterceptor((v, event) -> {
                popupWindow.dismiss();
                return true;
            });
            popupWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
            popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
            popupWindow.setContentView(LayoutInflater.from(ctx).inflate(R.layout.popup, null));
        } else if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        anchor.getLocationOnScreen(sp);
        Rect anchorRect = new Rect(sp[0], sp[1], sp[0] + anchor.getWidth(), sp[1] + anchor.getHeight());

        View content = popupWindow.getContentView();
        ((TextView)content.findViewById(R.id.textView)).setText(txt);
        content.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int tooltipWidth = content.getMeasuredWidth();
        int tooltipHeight = content.getMeasuredHeight();
        int px = anchorRect.centerX() - (tooltipWidth >> 1);
        int py = anchorRect.bottom - (tooltipHeight >> 1);
        popupWindow.setElevation(anchor.getElevation() + 10);
        popupWindow.showAtLocation(anchor, Gravity.START | Gravity.TOP, px, py);
        if (period > 0L && handler != null) {
            handler.postDelayed(dismisser, period);
        }
    }

}
