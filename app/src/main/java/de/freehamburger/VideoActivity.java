package de.freehamburger;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;

import de.freehamburger.model.News;
import de.freehamburger.model.StreamQuality;
import de.freehamburger.util.Log;
import de.freehamburger.util.MediaSourceHelper;
import de.freehamburger.util.Util;

/**
 * Plays a video.
 */
public class VideoActivity extends AppCompatActivity {
    static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "VideoActivity";
    /** the number of milliseconds to wait after user interaction before hiding the system UI */
    private static final long AUTO_HIDE_DELAY_MILLIS = 3_000L;
    /** Some older devices needs a small delay between UI widget updates and a change of the status and navigation bar. */
    private static final long UI_ANIMATION_DELAY = 300L;
    private static final int UI_FLAGS = View.SYSTEM_UI_FLAG_LOW_PROFILE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            //| View.SYSTEM_UI_FLAG_IMMERSIVE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
    private final Handler handler = new Handler();
    private final Runnable hideStatusAndNavigationBars = () -> getWindow().getDecorView().setSystemUiVisibility(UI_FLAGS);
    private final Runnable hideRunnable = this::hide;
    private final MediaSourceHelper mediaSourceHelper = new MediaSourceHelper();
    private PlayerView playerView;
    private ProgressBar progressBar;
    private News news;
    @Nullable
    private ExoPlayer exoPlayerVideo;

    /**
     * Hides ActionBar, status and navigation bar.
     */
    private void hide() {
        // hide ActionBar first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();
        // hide status and navigation bars after a short delay
        this.handler.postDelayed(this.hideStatusAndNavigationBars, UI_ANIMATION_DELAY);
    }

    /**
     * Hides the {@link #progressBar progress spinner}.
     */
    private void hideProgressBar() {
        this.progressBar.animate()
                .alpha(0f)
                .setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        VideoActivity.this.progressBar.setVisibility(View.GONE);
                        VideoActivity.this.progressBar.setAlpha(1f);
                    }
                });

    }

    /**
     * Initialises the ExoPlayer.
     */
    private void initPlayer() {
        // create ExoPlayer instance
        this.exoPlayerVideo = ExoPlayerFactory.newSimpleInstance(this, new DefaultTrackSelector());
        // assign the ExoPlayer instance to the video view
        this.playerView.setPlayer(this.exoPlayerVideo);
        View st = findViewById(R.id.exo_subtitles);
        if (st != null) st.setVisibility(View.GONE);
        // listen to state changes
        Player.EventListener listener = new Player.EventListener() {
            /** {@inheritDoc} */
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                String msg = Util.getExoPlaybackExceptionMessage(error);
                if (BuildConfig.DEBUG) Log.e(TAG, "Video player error: " + msg, error);
                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }

            /** {@inheritDoc} */
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    showProgressBar();
                } else {
                    hideProgressBar();
                }
                if (playWhenReady && playbackState == Player.STATE_ENDED) {
                    VideoActivity.this.handler.postDelayed(() -> finish(), 750L);
                }
            }
        };
        this.exoPlayerVideo.addListener(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        // pause video before finishing the activity to make sure there is no audio when the activity has been finished
        pauseVideo();
        super.onBackPressed();
    }

    /** {@inheritDoc} */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        setVolumeControlStream(App.STREAM_TYPE);

        this.news = (News) getIntent().getSerializableExtra(EXTRA_NEWS);

        this.playerView = findViewById(R.id.playerView);
        this.progressBar = findViewById(R.id.progressBar);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            /** {@inheritDoc} */
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    VideoActivity.this.handler.removeCallbacks(VideoActivity.this::hide);
                    VideoActivity.this.handler.postDelayed(VideoActivity.this::hide, AUTO_HIDE_DELAY_MILLIS);
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            pauseVideo();
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer();
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        this.handler.removeCallbacks(this.hideRunnable);
        this.handler.postDelayed(this.hideRunnable, 100);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT <= 23 || this.exoPlayerVideo == null) {
            initPlayer();
        }

        if (this.news == null) {
            finish();
            return;
        }

        String newsVideo = StreamQuality.getStreamsUrl(this, this.news.getStreams());
        if (newsVideo != null) {
            if (this.exoPlayerVideo != null) {
                newsVideo = Util.makeHttps(newsVideo);
                MediaSource ms = this.mediaSourceHelper.buildMediaSource(((App)getApplicationContext()).getOkHttpClient(), Uri.parse(newsVideo));
                this.exoPlayerVideo.prepare(ms, true, true);
                this.exoPlayerVideo.setPlayWhenReady(true);
            }
        } else {
            this.playerView.setVisibility(View.GONE);
        }


    }

    /** {@inheritDoc} */
    @Override
    protected void onStart() {
        super.onStart();
        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT > 23) {
            initPlayer();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onStop() {
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayer();
        }
        super.onStop();
    }

    /**
     * Pauses the video playback.
     */
    private void pauseVideo() {
        if (this.exoPlayerVideo == null) return;
        this.exoPlayerVideo.setPlayWhenReady(false);
    }

    /**
     * Releases the {@link #exoPlayerVideo ExoPlayer}.
     */
    private void releasePlayer() {
        if (this.exoPlayerVideo != null) {
            this.exoPlayerVideo.release();
            this.exoPlayerVideo = null;
        }
    }

    /**
     * Shows the {@link #progressBar progress spinner}.
     */
    private void showProgressBar() {
        this.progressBar.setAlpha(0f);
        this.progressBar.setVisibility(View.VISIBLE);
        this.progressBar.animate()
                .alpha(1f)
                .setDuration(getResources().getInteger(android.R.integer.config_longAnimTime))
                ;
    }
}
