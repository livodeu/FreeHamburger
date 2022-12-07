package de.freehamburger.util;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

/**
 * Implemented by activities that use {@link CoordinatorLayout}.<br>
 * Other classes can access it to display a {@link Snackbar Snackbar}.
 */
@FunctionalInterface
public interface CoordinatorLayoutHolder {

    @Nullable CoordinatorLayout getCoordinatorLayout();
}
