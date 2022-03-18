package de.freehamburger.supp;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import de.freehamburger.App;

/**
 * See <a href="https://developer.android.com/training/sharing/send#share-interaction-data">https://developer.android.com/training/sharing/send#share-interaction-data</a>
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class ShareReceiver extends BroadcastReceiver {

    /** {@inheritDoc} */
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ComponentName chosenComponent = intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT);
        if (chosenComponent == null) return;
        ((App) ctx.getApplicationContext()).setLatestShare(chosenComponent);
    }
}
