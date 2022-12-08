package de.freehamburger;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.squareup.picasso.Picasso;

import de.freehamburger.util.BitmapTarget;
import de.freehamburger.util.Util;

public class PictureActivity extends HamburgerActivity {

    private Uri uri;
    private ImageView pictureView;

    /** {@inheritDoc} */
    @Override int getMainLayout() {
        return R.layout.activity_picture;
    }

    /** {@inheritDoc} */
    @Override boolean hasMenuOverflowButton() {
        return false;
    }

    /** {@inheritDoc} */
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        if (uri == null || !App.isHostAllowed(uri.getHost())) {
            finish();
            return;
        }
        uri = Uri.parse(Util.makeHttps(uri.toString()));
        if (!App.isSchemeAllowed(uri.getScheme())) {
            finish();
            return;
        }
        this.uri = uri;
        setContentView(getMainLayout());
        this.pictureView = getDelegate().findViewById(R.id.pictureView);
        Util.goFullScreen(this);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
    }

    /** {@inheritDoc} */
    @Override public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        if (this.uri == null) {
            finish();
            return;
        }
        if (this.service == null) return;
        String u = this.uri.toString();
        this.pictureView.setScaleX(0.1f);
        this.pictureView.setScaleY(0.1f);
        this.service.loadImage(u, new BitmapTarget(u) {
            @Override public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                super.onBitmapLoaded(bitmap, from);
                PictureActivity.this.pictureView.setImageBitmap(bitmap);
                Animator ax = ObjectAnimator.ofFloat(PictureActivity.this.pictureView, "scaleX", 0.1f, 1f);
                Animator ay = ObjectAnimator.ofFloat(PictureActivity.this.pictureView, "scaleY", 0.1f, 1f);
                AnimatorSet as = new AnimatorSet().setDuration(200L);
                as.setInterpolator(new AnticipateOvershootInterpolator());
                as.playTogether(ax,ay);
                as.start();
            }

            @Override public void onBitmapFailed(Exception e, @Nullable Drawable errorDrawable) {
                super.onBitmapFailed(e, errorDrawable);
                Toast.makeText(PictureActivity.this, R.string.error_download_failed2, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
