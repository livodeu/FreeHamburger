package de.freehamburger;

import android.os.Build;
import android.os.Bundle;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * See
 * <ul>
 * <li><a href="https://developer.android.com/about/versions/13/features/predictive-back-gesture">https://developer.android.com/about/versions/13/features/predictive-back-gesture</a></li>
 * <li><a href="https://developer.android.com/about/versions/14/features/predictive-back">https://developer.android.com/about/versions/14/features/predictive-back</a></li>
 * <li><a href="https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#update-default">https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#update-default</a></li>
 * <li><a href="https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back?hl=en">https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back?hl=en</a></li>
 * <li><a href="https://github.com/material-components/material-components-android/blob/master/docs/foundations/PredictiveBack.md">https://github.com/material-components/material-components-android/blob/master/docs/foundations/PredictiveBack.md</a></li>
 * </ul>
 */
abstract class BackhandActivity extends AppCompatActivity {

    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
            getWindow().getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
    }
}
