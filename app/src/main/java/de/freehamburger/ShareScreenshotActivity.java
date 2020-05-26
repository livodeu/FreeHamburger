package de.freehamburger;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.io.File;

import androidx.core.content.FileProvider;

/**
 * Shares (sends) a jpeg file that is located in the app's export directory.
 */
public class ShareScreenshotActivity extends Activity {

    /** absolute path to a file containing a screenshot */
    static final String EXTRA_SCREENSHOT_FILE = BuildConfig.APPLICATION_ID + ".screenshotfile";
    static final Bitmap.CompressFormat COMPRESSFORMAT = Bitmap.CompressFormat.JPEG;
    static final String FILETAG = ".jpg";
    private static final String MIME = "image/jpeg";

    private Uri screenShotUri;

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String screenshotFile = getIntent().getStringExtra(EXTRA_SCREENSHOT_FILE);
        this.screenShotUri = screenshotFile != null ? FileProvider.getUriForFile(this, App.getFileProvider(), new File(screenshotFile)) : null;
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        if (this.screenShotUri != null) {
            Intent shareScreenshot = new Intent(Intent.ACTION_SEND);
            shareScreenshot.setType(MIME);
            shareScreenshot.putExtra(Intent.EXTRA_STREAM, this.screenShotUri);
            shareScreenshot.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (getPackageManager().resolveActivity(shareScreenshot, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                ActivityOptions o = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    o = ActivityOptions.makeClipRevealAnimation(getWindow().getDecorView(), 0, 0, 16, 16);
                }
                startActivity(shareScreenshot, o != null ? o.toBundle() : null);
            }
        }
        finish();
    }
}
