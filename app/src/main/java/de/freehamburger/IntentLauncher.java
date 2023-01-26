package de.freehamburger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import de.freehamburger.util.Log;

/**
 * Launches Intents with a given {@code action} and {@code data} given as<br>
 * <pre>launcher://launcher/&lt;action&gt;?data=&lt;data&gt;</pre>
 */
public class IntentLauncher extends Activity {

    @VisibleForTesting static final String HOST = "launcher";
    @VisibleForTesting static final String SCHEME = "launcher";

    @Override protected void onResume() {
        super.onResume();
        final Uri data = getIntent().getData();
        if (data != null && SCHEME.equals(data.getScheme()) && HOST.equals(data.getHost())) {
            String action = data.getPath();
            if (action == null || action.length() <= 1) {
                finish();
                return;
            }
            if (action.charAt(0) == '/') action = action.substring(1);
            final Intent launchIntent = new Intent(action);
            String dataParameter = data.getQueryParameter("data");
            if (!TextUtils.isEmpty(dataParameter)) launchIntent.setData(Uri.parse(dataParameter));
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launchIntent);
            } catch (ActivityNotFoundException e) {
                if (BuildConfig.DEBUG) Log.e(getClass().getSimpleName(), e.toString());
                Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }
}
