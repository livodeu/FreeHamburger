package de.freehamburger;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
    private static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "VideoActivity";
    /** Whether or not the system UI should be auto-hidden after {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds. */
    private static final boolean AUTO_HIDE = true;
    /** If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after user interaction before hiding the system UI. */
    private static final long AUTO_HIDE_DELAY_MILLIS = 3_000L;
    /** Some older devices needs a small delay between UI widget updates and a change of the status and navigation bar. */
    private static final long UI_ANIMATION_DELAY = 300L;
    private final Handler handler = new Handler();
    private ViewGroup fullscreenContent;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @Override
        public void run() {
            fullscreenContent.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            //| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            //| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private PlayerView playerView;
    private ProgressBar progressBar;
    private View controlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            controlsView.setVisibility(View.VISIBLE);
        }
    };
    private final Runnable hideRunnable = this::hide;
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the system UI.
     * This is to prevent the jarring behavior of controls going away while interacting with activity UI.
     */
    @SuppressLint("ClickableViewAccessibility")
    private final View.OnTouchListener mDelayHideTouchListener = (view, motionEvent) -> {
        if (AUTO_HIDE) {
            delayedHide(AUTO_HIDE_DELAY_MILLIS);
        }
        return false;
    };
    private final MediaSourceHelper mediaSourceHelper = new MediaSourceHelper();
    private News news;
    @Nullable
    private ExoPlayer exoPlayerVideo;
    //private int exoPlayerState = 0;
    //private boolean exoPlayerPlayWhenReady = false;

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any previously scheduled calls.
     */
    private void delayedHide(@IntRange(from = 0) long delayMillis) {
        this.handler.removeCallbacks(this.hideRunnable);
        this.handler.postDelayed(this.hideRunnable, delayMillis);
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        this.controlsView.setVisibility(View.GONE);
        // Schedule a runnable to remove the status and navigation bar after a delay
        this.handler.removeCallbacks(mShowPart2Runnable);
        this.handler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Initialises the ExoPlayer.
     */
    private void initPlayer() {
        // create ExoPlayer instance
        this.exoPlayerVideo = ExoPlayerFactory.newSimpleInstance(this, new DefaultTrackSelector());
        // assign the ExoPlayer instance to the video view
        this.playerView.setPlayer(this.exoPlayerVideo);
        // listen to state changes
        Player.EventListener listener = new Player.EventListener() {
            /** {@inheritDoc} */
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                String msg = Util.getExoPlaybackExceptionMessage(error);
                if (BuildConfig.DEBUG) Log.e(TAG, "Video player error: " + msg, error);
                Toast.makeText(VideoActivity.this, msg, Toast.LENGTH_LONG)
                        .show();
                finish();
            }

            /** {@inheritDoc} */
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    VideoActivity.this.progressBar.setVisibility(View.VISIBLE);
                } else {
                    VideoActivity.this.progressBar.setVisibility(View.GONE);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setVolumeControlStream(App.STREAM_TYPE);

        this.news = (News) getIntent().getSerializableExtra(EXTRA_NEWS);

        this.controlsView = findViewById(R.id.fullscreen_content_controls);
        this.fullscreenContent = findViewById(R.id.fullscreen_content);
        this.playerView = findViewById(R.id.playerView);
        this.progressBar = findViewById(R.id.progressBar);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(this.mDelayHideTouchListener);

        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            /** {@inheritDoc} */
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    handler.postDelayed(VideoActivity.this::hide, 3_000L);
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
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
        // Trigger the initial hide() shortly after the activity has been created, to briefly hint to the user that UI controls are available.
        delayedHide(100);
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
                MediaSource ms = this.mediaSourceHelper.buildMediaSource(Uri.parse(newsVideo));
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

    /*
     *
    public void playVideo(@Nullable View ignored) {
        if (this.exoPlayerVideo == null) return;
        if (BuildConfig.DEBUG) Log.i(TAG, "playVideo()");
        if (isVideoPlaying()) {
            // pause the video
            this.exoPlayerVideo.setPlayWhenReady(false);
        } else {
            this.exoPlayerVideo.setPlayWhenReady(true);
        }
    }*/

    private void releasePlayer() {
        if (this.exoPlayerVideo != null) {
            this.exoPlayerVideo.release();
            this.exoPlayerVideo = null;
        }
    }

    /*
    private void show() {
        // Show the system bar
        this.fullscreenContent.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        // Schedule a runnable to display UI elements after a delay
        this.handler.removeCallbacks(this.mHidePart2Runnable);
        this.handler.postDelayed(this.mShowPart2Runnable, UI_ANIMATION_DELAY);
    }
    */

    /*private void toggle() {
        if (BuildConfig.DEBUG) Log.i(TAG, "toggle()");
        if (this.visible) {
            hide();
        } else {
            show();
        }
    }*/
}
