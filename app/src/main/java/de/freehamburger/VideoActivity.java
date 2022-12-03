package de.freehamburger;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Rational;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;

import java.lang.ref.WeakReference;

import de.freehamburger.exo.ExoFactory;
import de.freehamburger.exo.MediaSourceHelper;
import de.freehamburger.exo.PlayerListener;
import de.freehamburger.model.News;
import de.freehamburger.model.StreamQuality;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;
import okhttp3.Call;

/**
 * Plays a video.
 */
public class VideoActivity extends AppCompatActivity implements AudioManager.OnAudioFocusChangeListener {
    /** boolean: enable picture-in-picture playback */
    public static final String PREF_PIP_ENABLED = "pref_pip_enabled";
    public static final boolean PREF_PIP_ENABLED_DEFAULT = true;
    /** See <a href="https://developer.android.com/about/versions/oreo/android-8.0#opip"> https://developer.android.com/about/versions/oreo/android-8.0#opip</a> */
    public static final int PIP_MIN_API = Build.VERSION_CODES.O;
    static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "VideoActivity";
    /** the number of milliseconds to wait after user interaction before hiding the system UI */
    private static final long AUTO_HIDE_DELAY_MILLIS = 3_000L;
    /** Some older devices needs a small delay between UI widget updates and a change of the status and navigation bar. */
    private static final long UI_ANIMATION_DELAY = 300L;
    /** period of time for audio fadeout [ms] */
    private static final long FADEOUT = 500L;
    private static final int UI_FLAGS = View.SYSTEM_UI_FLAG_LOW_PROFILE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            ;

    private final Handler handler = new Handler();
    private final Runnable hideStatusAndNavigationBars = () -> getWindow().getDecorView().setSystemUiVisibility(UI_FLAGS);
    private final Runnable hideRunnable = this::hide;
    @NonNull
    private final AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setLegacyStreamType(App.STREAM_TYPE).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
    private final MediaSourceHelper mediaSourceHelper = new MediaSourceHelper();

    private boolean pipSupported;
    private boolean hasAudioFocus;
    private AudioFocusRequest afr;
    private int audioVolumeBeforeDucking;
    private StyledPlayerView playerView;
    private ProgressBar progressBar;
    private News news;
    @Nullable
    private ExoPlayer exoPlayerVideo;

    /**
     * Determines whether Picture-in-picture mode is supported.
     * @param ctx Context
     * @return true / false
     */
    static boolean isPipSupported(@Nullable Context ctx) {
        return Build.VERSION.SDK_INT >= PIP_MIN_API && ctx != null && ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private void abandonAudioFocus(@Nullable AudioManager am) {
        int requestResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (this.afr == null) return;
            if (am == null) am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            requestResult = am.abandonAudioFocusRequest(this.afr);
        } else {
            if (am == null) am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            requestResult = am.abandonAudioFocus(this);
        }
        this.hasAudioFocus = (requestResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

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
        this.exoPlayerVideo = ExoFactory.makeExoPlayer(this, null);
        // assign the ExoPlayer instance to the video view
        this.playerView.setPlayer(this.exoPlayerVideo);
        View backSeconds = playerView.findViewById(R.id.exo_rew_with_amount);
        if (backSeconds != null) backSeconds.setVisibility(View.GONE);
        View fwdSeconds = playerView.findViewById(R.id.exo_ffwd_with_amount);
        if (fwdSeconds != null) fwdSeconds.setVisibility(View.GONE);
        View st = findViewById(R.id.exo_subtitles);
        if (st != null) st.setVisibility(View.GONE);
        // listen to state changes
        PlayerListener listener = new PlayerListener(this,true) {

            /** {@inheritDoc} */
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                abandonAudioFocus(null);
                super.onPlayerError(error);
                finish();
            }

            /** {@inheritDoc} */
            @Override
            public void onPlayerStateOrOnPlayWhenReadyChanged() {
                if (exoPlayerState == Player.STATE_BUFFERING) {
                    showProgressBar();
                } else {
                    hideProgressBar();
                }
                if (exoPlayerPlayWhenReady && exoPlayerState == Player.STATE_ENDED) {
                    abandonAudioFocus(null);
                    VideoActivity.this.handler.postDelayed(() -> finish(), 750L);
                }
            }
        };
        this.exoPlayerVideo.addListener(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void onAudioFocusChange(int focusChange) {
        AudioManager am;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                this.hasAudioFocus = false;
                pauseVideo(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                this.hasAudioFocus = false;
                am = (AudioManager)getSystemService(AUDIO_SERVICE);
                if (am  != null) {
                    this.audioVolumeBeforeDucking = am.getStreamVolume(App.STREAM_TYPE);
                    am.setStreamVolume(App.STREAM_TYPE, 0, 0);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                this.hasAudioFocus = true;
                am = (AudioManager)getSystemService(AUDIO_SERVICE);
                if (this.audioVolumeBeforeDucking > 0) {
                    if (am != null) am.setStreamVolume(App.STREAM_TYPE, this.audioVolumeBeforeDucking, 0);
                    this.audioVolumeBeforeDucking = 0;
                } else {
                    if (am != null) am.setStreamVolume(App.STREAM_TYPE, am.getStreamMaxVolume(App.STREAM_TYPE), 0);
                }
                break;
            default:
                if (BuildConfig.DEBUG) Log.e(TAG, "onAudioFocusChange: unhandled constant " + focusChange);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        // pause video before finishing the activity to make sure there is no audio when the activity has been finished
        long delay = pauseVideo(true);
        if (delay > 0L) {
            this.handler.postDelayed(VideoActivity.super::onBackPressed, delay);
            return;
        }
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

        // note: PIP is still flagged as supported even if the user denied the permission to use it
        this.pipSupported = isPipSupported(this);

        this.news = (News) getIntent().getSerializableExtra(EXTRA_NEWS);

        this.playerView = findViewById(R.id.playerView);
        // show the |< button only if it's not live video
        this.playerView.setShowPreviousButton(this.news != null && !this.news.isLiveStream());
        //
        this.progressBar = findViewById(R.id.progressBar);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                this.handler.removeCallbacks(this::hide);
                this.handler.postDelayed(this::hide, AUTO_HIDE_DELAY_MILLIS);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.news = (News) getIntent().getSerializableExtra(EXTRA_NEWS);
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            long delay = pauseVideo(true);
            if (delay > 0L) {
                this.handler.postDelayed(() -> NavUtils.navigateUpFromSameTask(VideoActivity.this), delay);
            } else {
                NavUtils.navigateUpFromSameTask(this);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        if (this.hasAudioFocus) abandonAudioFocus(null);
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayer();
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            this.playerView.hideController();
        } else {
            if (this.exoPlayerVideo != null && this.exoPlayerVideo.getPlaybackState() != Player.STATE_READY) this.playerView.showController();
        }
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
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

        // don't do anything if resuming from PIP mode
        if (this.exoPlayerVideo != null && this.exoPlayerVideo.getPlaybackState() == Player.STATE_READY) return;

        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT <= 23 || this.exoPlayerVideo == null) {
            initPlayer();
        }

        if (this.news == null) {
            if (this.hasAudioFocus) abandonAudioFocus(null);
            finish();
            return;
        }

        String newsVideo = StreamQuality.getStreamsUrl(this, this.news.getStreams());
        if (newsVideo != null) {
            if (this.exoPlayerVideo != null) {
                newsVideo = Util.makeHttps(newsVideo);
                MediaSource ms = this.mediaSourceHelper.buildMediaSource((Call.Factory)((App)getApplicationContext()).getOkHttpClient(), Uri.parse(newsVideo));
                requestAudioFocus();
                this.exoPlayerVideo.setMediaSource(ms, true);
                this.exoPlayerVideo.prepare();
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

    /** {@inheritDoc} */
    @Override
    protected void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < PIP_MIN_API) return;
        // https://developer.android.com/about/versions/oreo/android-8.0#opip
        // https://developer.android.com/guide/topics/ui/picture-in-picture?hl=en#java
        if (this.pipSupported
                && this.exoPlayerVideo != null
                && this.exoPlayerVideo.getPlaybackState() == Player.STATE_READY
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_PIP_ENABLED, PREF_PIP_ENABLED_DEFAULT)) {
            final PictureInPictureParams.Builder pb = new PictureInPictureParams.Builder();
            if (this.playerView != null) {
                View surface = this.playerView.getVideoSurfaceView();
                if (surface != null) {
                    Rect r = new Rect();
                    surface.getGlobalVisibleRect(r);
                    int width = r.right - r.left;
                    int height = r.bottom - r.top;
                    if (width > 0 && height > 0) pb.setSourceRectHint(r).setAspectRatio(new Rational(width, height));
                }
            }
            // PIP fails if the user denied the permission
            if (!enterPictureInPictureMode(pb.build())) this.pipSupported = false;
        }
    }

    /**
     * Pauses the video playback.
     * @return milliseconds after which the video should be paused
     */
    @IntRange(from = 0)
    private long pauseVideo(final boolean abandonFocus) {
        if (this.exoPlayerVideo == null) return 0;
        final long totalDelay = FADEOUT + 50L;
        if (this.exoPlayerVideo.getPlaybackState() == Player.STATE_READY) {
            final AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am == null) {
                this.exoPlayerVideo.setPlayWhenReady(false);
                if (abandonFocus && this.hasAudioFocus) abandonAudioFocus(null);
                return 0;
            }
            final int originalVolume = am.getStreamVolume(App.STREAM_TYPE);
            new FlexiFader(this, (float)originalVolume / (float)am.getStreamMaxVolume(App.STREAM_TYPE), 0, FADEOUT).start();
            this.handler.postDelayed(() -> {
                if (abandonFocus && VideoActivity.this.hasAudioFocus) abandonAudioFocus(am);
                if (VideoActivity.this.exoPlayerVideo != null) VideoActivity.this.exoPlayerVideo.setPlayWhenReady(false);
                am.setStreamVolume(App.STREAM_TYPE, originalVolume, 0);
            }, totalDelay);
        }
        return totalDelay;
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

    private void requestAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        int requestResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (this.afr == null) this.afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(this.aa).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(this).build();
            requestResult = am.requestAudioFocus(this.afr);
        } else {
            requestResult = am.requestAudioFocus(this, App.STREAM_TYPE, AudioManager.AUDIOFOCUS_GAIN);
        }
        this.hasAudioFocus = (requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        if (BuildConfig.DEBUG && !hasAudioFocus) Log.e(TAG, "Did not get audio focus!");
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

    /**
     * Fades between two volume values.<br>
     * If the target value is {@code 0}, {@link #stop(boolean)} is called.
     */
    static class FlexiFader extends Thread {
        @NonNull
        private final WeakReference<VideoActivity> refActivity;
        @FloatRange(from = 0, to = 1) private final float from, to;
        private final long period;

        /**
         * Constructor.
         * @param activity VideoActivity
         * @param from start level
         * @param to end lavel
         * @param period period of time in milliseconds
         */
        FlexiFader(@NonNull VideoActivity activity, @FloatRange(from = 0, to = 1) float from, @FloatRange(from = 0, to = 1) float to, @IntRange(from = 250) long period) {
            super();
            this.refActivity = new WeakReference<>(activity);
            this.from = Math.min(1f, Math.max(0f, from));
            this.to = Math.min(1f, Math.max(0f, to));
            this.period = period;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private static boolean safeSleep(long sleep) {
            try {
                Thread.sleep(sleep);
                return true;
            } catch (Throwable ignored) {
            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            VideoActivity videoActivity = this.refActivity.get();
            if (videoActivity == null || videoActivity.exoPlayerVideo == null) return;
            final long interval = this.period >= 250L ? this.period / 10 : 25L;
            final float step = this.from < this.to ? 0.1f : -0.1f;
            float vol = this.from;
            AudioManager am = (AudioManager)videoActivity.getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            final int max = am.getStreamMaxVolume(App.STREAM_TYPE);
            if (step > 0f) {
                for (; vol < this.to; vol += step) {
                    am.setStreamVolume(App.STREAM_TYPE, Math.round(vol * max), 0);
                    if (!safeSleep(interval)) break;
                }
            } else {
                for (; vol > this.to; vol += step) {
                    am.setStreamVolume(App.STREAM_TYPE, Math.round(vol * max), 0);
                    if (!safeSleep(interval)) break;
                }
            }
            if (vol != this.to) {
                am.setStreamVolume(App.STREAM_TYPE, Math.round(to * max), 0);
            }
        }
    }

}
