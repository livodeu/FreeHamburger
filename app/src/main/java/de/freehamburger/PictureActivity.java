package de.freehamburger;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.squareup.picasso.Picasso;

import de.freehamburger.util.BitmapTarget;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class PictureActivity extends HamburgerActivity {

    private static final String TAG = "PictureActivity";
    private Uri uri;
    private ImageView pictureView;

    @Override int getMainLayout() {
        return R.layout.activity_picture;
    }

    @Override boolean hasMenuOverflowButton() {
        return false;
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        this.uri = uri;
        setContentView(getMainLayout());
        this.pictureView = findViewById(R.id.pictureView);
        Util.goFullScreen(this);
    }

    @Override public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        if (this.service != null) {
            this.service.loadImage(this.uri.toString(), new BitmapTarget(this.uri.toString()) {
                @Override public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    super.onBitmapLoaded(bitmap, from);
                    pictureView.setImageBitmap(bitmap);
                }

                @Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                    super.onBitmapFailed(e, errorDrawable);
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                    Toast.makeText(PictureActivity.this, R.string.error_download_failed2, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }
    }
}
