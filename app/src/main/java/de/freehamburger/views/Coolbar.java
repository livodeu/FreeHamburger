package de.freehamburger.views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.MaterialToolbar;

import java.lang.reflect.Field;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;

/**
 * A modified {@link MaterialToolbar}
 */
public class Coolbar extends MaterialToolbar {

    /** period of time in milliseconds that the effects will take */
    private static final long EFFECT_PERIOD = 334L;
    private static final String TAG = "Coolbar";
    private Field fMenuView;

    /**
     * Constructor.<br>
     * For docs, just look at the marvellous documentation of {@link MaterialToolbar super…}
     * @param context Context
     */
    public Coolbar(@NonNull Context context) {
        super(context);
        init();
    }

    /**
     * Constructor.<br>
     * For docs, just look at the marvellous documentation of {@link MaterialToolbar super…}
     * @param context Context
     * @param attrs AttributeSet
     */
    public Coolbar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor.<br>
     * For docs, just look at the marvellous documentation of {@link MaterialToolbar super…}
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr def style attr hrrgh
     */
    public Coolbar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Nullable
    public ActionMenuView getActionMenuView() {
        if (this.fMenuView != null) {
            try {
                Object o = this.fMenuView.get(this);
                if (o instanceof ActionMenuView) return ((ActionMenuView) o);
            } catch (IllegalAccessException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            }
        }
        return null;
    }

    /**
     * Returns the 3-dot menu button (androidx.appcompat.widget.ActionMenuPresenter.OverflowMenuButton).
     * @return AppCompatImageView
     */
    @Nullable
    private AppCompatImageView getOverflowButton() {
        final ActionMenuView amv = getActionMenuView();
        if (amv == null) return null;
        for (int i = 0; i < amv.getChildCount(); i++) {
            View child = amv.getChildAt(i);
            if (child instanceof AppCompatImageView && "OverflowMenuButton".equals(child.getClass().getSimpleName())) return (AppCompatImageView)child;
        }
        return null;
    }

    private void init() {
        try {
            fMenuView = Toolbar.class.getDeclaredField("mMenuView");
            fMenuView.setAccessible(true);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
    }

    /**
     * Lets the action menu items identified by the given ids rotate on touch.
     * @param ids ids
     */
    public void letRotate(@IdRes final int... ids) {
        if (ids == null || ids.length == 0) return;
        final ActionMenuView amv = getActionMenuView();
        if (amv == null) return;
        for (int i = 0; i < amv.getChildCount(); i++) {
            View child = amv.getChildAt(i);
            final int childId = child.getId();
            if (childId == NO_ID) continue;
            for (int id : ids) {
                if (id == childId) {
                    child.setOnTouchListener(new RotateListener(child));
                    break;
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void stretchOverflowButton() {
        AppCompatImageView button = getOverflowButton();
        if (button == null) return;
        button.setOnTouchListener(new StretchListener(button));
    }

    static class StretchListener implements OnTouchListener {
        final Animator a;

        StretchListener(@NonNull View v) {
            super();
            a = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 1.5f, 1.0f).setDuration(EFFECT_PERIOD);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                a.start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (a.isRunning()) a.end();
            }
            return false;
        }

    }

    static class RotateListener implements OnTouchListener {
        final Animator a;

        RotateListener(@NonNull View v) {
            super();
            a = ObjectAnimator.ofFloat(v, "rotation", 0f, 360f).setDuration(EFFECT_PERIOD);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                a.start();
            }
            return false;
        }
    }
}
