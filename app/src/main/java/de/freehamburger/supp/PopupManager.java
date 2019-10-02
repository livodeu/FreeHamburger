package de.freehamburger.supp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;

import de.freehamburger.R;

/**
 * This class displays tooltip-like popup windows.<br>
 * Usage:<br>
 * <pre>
 * PopupManager popupManager = new PopupManager();
 * popupManager.showPopup(anchor_view, "the message", timeout_in_ms);
 * </pre>
 */
public class PopupManager implements View.OnTouchListener {

    private final int[] anchorLocation = new int[2];
    private final Runnable dismisser = this::dismiss;
    private PopupWindow popupWindow;

    /**
     * Constructor.
     */
    public PopupManager() {
        super();
    }

    /**
     * Removes and destroys the popup window.
     */
    public void destroy() {
        dismiss();
        popupWindow = null;
    }

    /**
     * Dismisses the popup.
     */
    private void dismiss() {
        if (popupWindow == null || !popupWindow.isShowing()) return;
        popupWindow.dismiss();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent e) {
        if (popupWindow == null || !popupWindow.isShowing()) return false;
        popupWindow.dismiss();
        return true;
    }

    /**
     * Displays a popup.
     * @param anchor View to attach the popup to
     * @param txt popup contents
     * @param period optional duration after which the popup will be dismissed
     * @throws NullPointerException if {@code anchor} is {@code null}
     */
    public void showPopup(@NonNull View anchor, CharSequence txt, long period) {
        showPopup(anchor, txt, period, true);
    }

    /**
     * Displays a popup.
     * @param anchor View to attach the popup to
     * @param txt popup contents
     * @param period optional duration after which the popup will be dismissed
     * @param dismissable {@code true} if the popup should be dismissable
     * @throws NullPointerException if {@code anchor} is {@code null}
     */
    @SuppressLint({"ClickableViewAccessibility", "InflateParams"})
    public void showPopup(@NonNull View anchor, CharSequence txt, long period, boolean dismissable) {
        Context ctx = anchor.getContext();
        Handler handler = anchor.getHandler();
        if (handler != null) {
            handler.removeCallbacks(dismisser);
        }
        if (popupWindow == null) {
            popupWindow = new PopupWindow(ctx);
            // no transitions for the PopupWindow as they caused a NPE in Transition.removeListener()
            if (Build.VERSION.SDK_INT >= 23) {
                popupWindow.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
            }
            popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
            popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
            popupWindow.setContentView(LayoutInflater.from(ctx).inflate(R.layout.popup, null));
        } else if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        popupWindow.setTouchInterceptor(dismissable ? this : null);

        anchor.getLocationOnScreen(anchorLocation);

        View content = popupWindow.getContentView();
        ((TextView)content.findViewById(R.id.textView)).setText(txt);
        content.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int tooltipWidth = content.getMeasuredWidth();
        int tooltipHeight = content.getMeasuredHeight();
        // center horizontally
        int px = anchorLocation[0] + (anchor.getWidth() >> 1)  - (tooltipWidth >> 1);
        // align with the bottom
        int py = anchorLocation[1] + anchor.getHeight() - (tooltipHeight >> 1);
        //
        popupWindow.showAtLocation(anchor, Gravity.TOP, px, py);
        if (period > 0L && handler != null) {
            handler.postDelayed(dismisser, period);
        }
    }

}
