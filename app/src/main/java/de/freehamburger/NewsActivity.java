package de.freehamburger;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.JsonReader;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.freehamburger.adapters.RelatedAdapter;
import de.freehamburger.model.Audio;
import de.freehamburger.model.Content;
import de.freehamburger.model.News;
import de.freehamburger.model.Related;
import de.freehamburger.model.StreamQuality;
import de.freehamburger.model.Video;
import de.freehamburger.util.Log;
import de.freehamburger.util.MediaSourceHelper;
import de.freehamburger.util.TextViewImageSpanClickHandler;
import de.freehamburger.util.Util;

/**
 * Before API 26, the two video views ({@link #topVideoView} and {@link #bottomVideoView}) must belong to <u>different</u> layers (hardware vs. software).<br>
 * The view type must also be adjusted via either <code>app:surface_type="texture_view"</code> for hardware layers
 * or <code>app:surface_type="surface_view"</code> for software layers
 * in content_news.xml respectively bottom_video.xml<br>
 * If both views had software layers, the bottom video would slide <em>under</em> the top video when expanded.<br>
 * This bug occurred under API 23 and 25.<br>
 * In API 26 both views having software layers works correctly.
 */
public class NewsActivity extends HamburgerActivity implements AudioManager.OnAudioFocusChangeListener, RelatedAdapter.OnRelatedClickListener, ServiceConnection {

    /** boolean; if true, the ActionBar will not show the home arrow which would lead to MainActivity */
    private static final String EXTRA_NO_HOME_AS_UP = "extra_no_home_as_up";
    static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "NewsActivity";
    /** number of columns for {@link #recyclerViewRelated} on phones */
    private static final int RELATED_COLUMNS_PHONE = 2;
    /** number of columns for {@link #recyclerViewRelated} on tablets in portrait mode */
    private static final int RELATED_COLUMNS_TABLET_PORTRAIT = 3;
    /** number of columns for {@link #recyclerViewRelated} on tablets in landscape mode */
    private static final int RELATED_COLUMNS_TABLET_LANDSCAPE = 4;
    @NonNull
    private final AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setLegacyStreamType(App.STREAM_TYPE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final MediaSourceHelper mediaSourceHelper = new MediaSourceHelper();
    /** strong references required for image loading (PictureLoader discards Targets that are not referenced anymore!) */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Set<SpannableImageTarget> spannableImageTargets = new HashSet<>();
    private News news;
    /** <a href="https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/ui/PlayerView.html">JavaDoc</a> */
    private PlayerView topVideoView;
    private TextView textViewTitle;
    private RelativeLayout audioBlock;
    private ImageButton buttonAudio;
    private TextView textViewAudioTitle;
    private TextView textViewContent;
    private RecyclerView recyclerViewRelated;
    private FloatingActionButton fab;
    private int maxAudioVolume = -1;
    private int originalAudioVolume = -1;
    private final Runnable preferredVolumeUpdater = () -> {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        NewsActivity.this.originalAudioVolume = am.getStreamVolume(App.STREAM_TYPE);
        if (BuildConfig.DEBUG)
            Log.i(TAG, "The original audio volume has been updated to " + originalAudioVolume + " out of " + maxAudioVolume);
    };
    private TextView textViewBottomVideoPeek;
    /** <a href="https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/ui/PlayerView.html">JavaDoc</a> */
    private PlayerView bottomVideoView;
    private TextView textViewBottomVideoViewOverlay;
    private BottomSheetBehavior bottomSheetBehavior;
    private boolean loadVideo;
    @Nullable
    private ExoPlayer exoPlayerTopVideo;
    @Nullable
    private ExoPlayer exoPlayerBottomVideo;
    private int exoPlayerTopState = 0;
    private boolean exoPlayerTopPlayWhenReady = false;
    /** Listener for the top video */
    private final Player.EventListener listenerTop = new Player.EventListener() {
        /** {@inheritDoc} */
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            // just log it - not important enough to show it to the user
            if (BuildConfig.DEBUG) Log.w(TAG, "Top video onPlayerError(): " + Util.getExoPlaybackExceptionMessage(error), error);
        }

        /** {@inheritDoc} */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            NewsActivity.this.exoPlayerTopPlayWhenReady = playWhenReady;
            NewsActivity.this.exoPlayerTopState = playbackState;
        }
    };
    private int exoPlayerBottomState = 0;
    private boolean exoPlayerBottomPlayWhenReady = false;
    private ImageView bottomVideoPauseIndicator;
    /** Listener for the bottom video */
    private final Player.EventListener listenerBottom = new Player.EventListener() {

        // flag that remembers whether the bottom video has been collapsed after the playback ended
        private boolean bottomVideoHiddenAfterPlayback = false;

        /** {@inheritDoc} */
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            String msg = Util.getExoPlaybackExceptionMessage(error);
            if (BuildConfig.DEBUG) Log.e(TAG, "Bottom video onPlayerError(): " + msg, error);
            // show error message, but only if bottom sheet is expanded
            if (isBottomSheetCollapsedOrHidden()) return;
            if (msg.contains("Unable to connect to")) {
                // instead of "Unable to connect to https://lengthy.url" we show a simple message
                msg = getString(R.string.error_connection_interrupted);
            }
            collapseBottomSheet();
            View coordinatorLayout = findViewById(R.id.coordinator_layout);
            if (coordinatorLayout != null) {
                Snackbar sb = Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_LONG);
                Util.setSnackbarFont(sb, Util.CONDENSED, 14f);
                sb.show();
            } else {
                Toast.makeText(NewsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            NewsActivity.this.exoPlayerBottomPlayWhenReady = playWhenReady;
            NewsActivity.this.exoPlayerBottomState = playbackState;
            if (playWhenReady && playbackState != Player.STATE_ENDED) {
                this.bottomVideoHiddenAfterPlayback = false;
                float sx = bottomVideoView.getScaleX();
                if (sx < 1f) {
                    ObjectAnimator.ofFloat(NewsActivity.this.bottomVideoView, "scaleX", sx, 1f).setDuration(300L).start();
                }
                if (playbackState == Player.STATE_READY) NewsActivity.this.bottomVideoPauseIndicator.setVisibility(View.GONE);
            }
            // wind the tape back when finished
            if (!playWhenReady && playbackState == Player.STATE_ENDED) NewsActivity.this.exoPlayerBottomVideo.seekTo(0);
            // collapse the bottom video when finished
            if (playWhenReady && playbackState == Player.STATE_ENDED && !this.bottomVideoHiddenAfterPlayback) {
                this.bottomVideoHiddenAfterPlayback = true;
                ObjectAnimator.ofFloat(NewsActivity.this.bottomVideoView, "scaleX", 1f, 0f).setDuration(500L).start();
                NewsActivity.this.handler.postDelayed(() -> {
                    collapseBottomSheet();
                    NewsActivity.this.bottomVideoView.setScaleX(1f);
                }, 500L);
            }
            // show or hide the pause indicator (only if the bottom sheet is visible)
            if (playbackState == Player.STATE_READY && !isBottomSheetCollapsedOrHidden()) {
                NewsActivity.this.bottomVideoPauseIndicator.setVisibility(playWhenReady ? View.GONE : View.VISIBLE);
            }
        }
    };
    @Nullable
    private ExoPlayer exoPlayerAudio;
    private int exoPlayerAudioState = 0;
    private boolean exoPlayerAudioPlayWhenReady = false;
    /** Listener for the audio */
    private final Player.EventListener listenerAudio = new Player.EventListener() {
        /** {@inheritDoc} */
        @Override
        public void onPlayerError(ExoPlaybackException error) {
            String msg = Util.getExoPlaybackExceptionMessage(error);
            if (BuildConfig.DEBUG) Log.e(TAG, "Audio onPlayerError(): " + msg, error);
            if (msg.contains("Unable to connect to")) {
                // instead of "Unable to connect to https://lengthy.url" we show a simple message
                msg = getString(R.string.error_connection_interrupted);
            }
            View coordinatorLayout = findViewById(R.id.coordinator_layout);
            if (coordinatorLayout != null) {
                Snackbar sb = Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_LONG);
                Util.setSnackbarFont(sb, Util.CONDENSED, 14f);
                sb.show();
            } else {
                Toast.makeText(NewsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
            NewsActivity.this.buttonAudio.setEnabled(false);
        }

        /** {@inheritDoc} */
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            NewsActivity.this.exoPlayerAudioPlayWhenReady = playWhenReady;
            NewsActivity.this.exoPlayerAudioState = playbackState;
            NewsActivity.this.buttonAudio.setImageResource(isAudioPlaying() ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_arrow_black_24dp);
        }
    };
    @Nullable
    private TextToSpeech tts;
    private boolean ttsInitialised = false;
    private boolean ttsSpeaking = false;

    /**
     * Applies the {@link #news} to the views. No abuse. And no queues.
     */
    private void applyNews() {
        if (this.news == null) {
            this.textViewTitle.setText(null);
            this.textViewContent.setText(null);
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean fixQ = prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, false);
        // top video from news.streams
        String newsVideo = StreamQuality.getStreamsUrl(this, this.news.getStreams());
        if (newsVideo != null) {
            if (this.exoPlayerTopVideo != null) {
                MediaSource msTopVideo = this.mediaSourceHelper.buildMediaSource(Uri.parse(newsVideo));
                this.exoPlayerTopVideo.prepare(msTopVideo, true, true);
                ((SimpleExoPlayer) this.exoPlayerTopVideo).setVolume(0f);
                this.exoPlayerTopVideo.setRepeatMode(Player.REPEAT_MODE_ALL);
                this.exoPlayerTopVideo.setPlayWhenReady(true);
            }
        } else {
            this.topVideoView.setVisibility(View.GONE);
        }
        this.textViewTitle.setText(fixQ ? Util.fixQuotationMarks(this.news.getTitle()) : this.news.getTitle());
        this.textViewTitle.setSelected(true);
        //
        Content content = this.news.getContent();
        // audio from the news.content part
        Audio audio = content != null && content.hasAudio() ? content.getAudioList().get(0) : null;
        if (audio != null) {
            this.buttonAudio.setTag(audio.getStream());
            this.buttonAudio.setContentDescription(audio.getTitle());
            this.textViewAudioTitle.setText(audio.getTitle());
            this.textViewAudioTitle.setTypeface(Util.getTypefaceForTextView(this.textViewAudioTitle, audio.getTitle()));
            this.textViewAudioTitle.setSelected(true);
            this.audioBlock.setVisibility(View.VISIBLE);
            // restore the layout params for the textViewContent
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewContent.getLayoutParams();
            lp.removeRule(RelativeLayout.BELOW);
            lp.addRule(RelativeLayout.BELOW, R.id.audioBlock);
        } else {
            this.buttonAudio.setTag(null);
            this.textViewAudioTitle.setText(null);
            this.audioBlock.setVisibility(View.GONE);
            // adjust the layout params for the textViewContent to be directly below textViewTitle
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewContent.getLayoutParams();
            lp.removeRule(RelativeLayout.BELOW);
            lp.addRule(RelativeLayout.BELOW, R.id.textViewTitle);
        }
        // video(s) from the news.content part (shown in the bottom area)
        if (content != null && content.hasVideo()) {
            MediaSource msBottomVideo;
            List<Video> videoList = content.getVideoList();
            if (videoList.size() > 1) {
                // more than one content video (rare)
                StringBuilder contentVideoTitle = new StringBuilder(32);
                List<Uri> uris = new ArrayList<>(videoList.size());
                for (Video contentVideo : videoList) {
                    String url = StreamQuality.getStreamsUrl(this, contentVideo.getStreams());
                    if (url == null) continue;
                    Uri uri = Uri.parse(Util.makeHttps(url));
                    if (uri != null) {
                        uris.add(uri);
                        if (contentVideoTitle.length() > 0) contentVideoTitle.append(", ");
                        contentVideoTitle.append(contentVideo.getTitle());
                    }
                }
                if (!uris.isEmpty()) {
                    this.textViewBottomVideoPeek.setTypeface(Util.getTypefaceForTextView(this.textViewBottomVideoPeek, contentVideoTitle.toString()));
                    this.textViewBottomVideoPeek.setText(contentVideoTitle);
                    this.textViewBottomVideoViewOverlay.setText(contentVideoTitle);
                    msBottomVideo = this.mediaSourceHelper.buildMediaSource(uris);
                    if (this.exoPlayerBottomVideo != null) this.exoPlayerBottomVideo.prepare(msBottomVideo, true, true);
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    this.textViewBottomVideoPeek.setText(null);
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            } else {
                // one content video (usual case)
                Video contentVideo = videoList.get(0);
                String url = StreamQuality.getStreamsUrl(this, contentVideo.getStreams());
                if (url != null) {
                    url = Util.makeHttps(url);
                    String contentVideoTitle = contentVideo.getTitle();
                    this.textViewBottomVideoPeek.setTypeface(Util.getTypefaceForTextView(this.textViewBottomVideoPeek, contentVideoTitle));
                    this.textViewBottomVideoPeek.setText(contentVideoTitle);
                    this.textViewBottomVideoViewOverlay.setText(contentVideoTitle);
                    msBottomVideo = this.mediaSourceHelper.buildMediaSource(Uri.parse(url));
                    if (this.exoPlayerBottomVideo != null) this.exoPlayerBottomVideo.prepare(msBottomVideo, true, true);
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    this.textViewBottomVideoPeek.setText(null);
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }
        } else {
            this.textViewBottomVideoPeek.setText(null);
            this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        // if there is an audio element or a bottom video, show a warning if audio output is muted
        if (audio != null || (content != null && content.hasVideo())) {
            if (this.originalAudioVolume == 0 && prefs.getBoolean(App.PREF_WARN_MUTE, true)) {
                ImageView muteWarning = findViewById(R.id.muteWarning);
                if (muteWarning != null) {
                    AnimatorSet set = new AnimatorSet();
                    muteWarning.setAlpha(0f);
                    muteWarning.setVisibility(View.VISIBLE);
                    ObjectAnimator fadeIn = ObjectAnimator.ofFloat(muteWarning, "alpha", 0f, 1f).setDuration(1_000L);
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(muteWarning, "alpha", 1f, 0f).setDuration(1_000L);
                    fadeOut.setStartDelay(500L);
                    set.playSequentially(fadeIn, fadeOut);
                    set.start();
                    handler.postDelayed(() -> muteWarning.setVisibility(View.GONE), 2_750L);
                }
            }
        }

        View dividerRelated = findViewById(R.id.dividerRelated);
        TextView textViewRelated = findViewById(R.id.textViewRelated);
        if (content != null) {
            List<Related> relatedList = content.getRelatedList();
            if (!relatedList.isEmpty()) {
                dividerRelated.setVisibility(View.VISIBLE);
                textViewRelated.setVisibility(View.VISIBLE);
                this.recyclerViewRelated.setVisibility(View.VISIBLE);
                RelatedAdapter adapter = (RelatedAdapter) this.recyclerViewRelated.getAdapter();
                if (adapter != null) adapter.setRelated(relatedList);
            } else {
                dividerRelated.setVisibility(View.GONE);
                textViewRelated.setVisibility(View.GONE);
                this.recyclerViewRelated.setVisibility(View.GONE);
            }
        } else {
            dividerRelated.setVisibility(View.GONE);
            textViewRelated.setVisibility(View.GONE);
            this.recyclerViewRelated.setVisibility(View.GONE);
        }

        //
        String text = content != null ? content.getText() : null;
        if (TextUtils.isEmpty(text)) {
            this.textViewContent.setVisibility(View.GONE);
            this.textViewContent.setText(null);
        } else {
            Spanned spanned;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                spanned = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT, this.service, new Content.ContentTagHandler());
            } else {
                spanned = Html.fromHtml(text, this.service, new Content.ContentTagHandler());
            }
            this.textViewContent.setText(spanned, TextView.BufferType.SPANNABLE);
            this.textViewContent.setVisibility(View.VISIBLE);

            // spannable is a SpannableString; therefore the text cannot be modified easily here
            Spannable spannable = (Spannable) this.textViewContent.getText();

            // load images
            ImageSpan[] imageSpans = spannable.getSpans(0, spannable.length(), ImageSpan.class);
            if (imageSpans != null) {
                for (ImageSpan imageSpan : imageSpans) {
                    String src = imageSpan.getSource();
                    if (src == null) continue;
                    src = Util.makeHttps(src);
                    @SuppressWarnings("ObjectAllocationInLoop")
                    SpannableImageTarget target = new SpannableImageTarget(this, spannable, imageSpan, 100);
                    // store target in an (otherwise unused) instance variable to avoid garbage collection, because the service holds only a WeakReference on the target
                    this.spannableImageTargets.add(target);
                    //
                    this.service.loadImage(src, target);
                }
            }

            // if desired, replace all URLSpans linking to permitted hosts with InternalURLSpans which allows to open the urls internally
            boolean internalLinks = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_OPEN_LINKS_INTERNALLY, true);

            URLSpan[] urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
            if (urlSpans != null) {
                for (URLSpan urlSpan : urlSpans) {
                    final String url = urlSpan.getURL();
                    int start = spannable.getSpanStart(urlSpan);
                    int end = spannable.getSpanEnd(urlSpan);
                    spannable.removeSpan(urlSpan);
                    if (internalLinks) {
                        Uri uri = Uri.parse(url);
                        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : null;
                        if (App.isHostAllowed(host)) {
                            InternalURLSpan internalURLSpan = new InternalURLSpan(url, getResources().getColor(R.color.colorLinkInternal));
                            spannable.setSpan(internalURLSpan, start, end, 0);
                        } else {
                            URLSpanWChooser urlSpanWChooser = new URLSpanWChooser(url, getResources().getColor(R.color.colorLinkExternal));
                            spannable.setSpan(urlSpanWChooser, start, end, 0);
                        }
                    } else {
                        if (url.endsWith(".json")) {
                            // it does not make sense to pass json links to the browser
                            InternalURLSpan internalURLSpan = new InternalURLSpan(url, getResources().getColor(R.color.colorLinkInternal));
                            spannable.setSpan(internalURLSpan, start, end, 0);
                        } else {
                            URLSpanWChooser urlSpanWChooser = new URLSpanWChooser(url, getResources().getColor(R.color.colorLinkExternal));
                            spannable.setSpan(urlSpanWChooser, start, end, 0);
                        }
                    }
                }
            }
        }

    }

    /**
     * Collapses the bottom video.
     */
    private void collapseBottomSheet() {
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            final int kc = event.getKeyCode();
            if (kc == KeyEvent.KEYCODE_VOLUME_UP || kc == KeyEvent.KEYCODE_VOLUME_DOWN) {
                this.handler.removeCallbacks(this.preferredVolumeUpdater);
                this.handler.postDelayed(this.preferredVolumeUpdater, 500L);
            } else if (kc == KeyEvent.KEYCODE_DEL) {
                onBackPressed();
                return true;
            } else if (kc == KeyEvent.KEYCODE_L && event.isCtrlPressed()) {
                startStopReading();
                return true;
            } else if (kc == KeyEvent.KEYCODE_A && event.isCtrlPressed()) {
                playAudio(null);
                return true;
            } else if (kc == KeyEvent.KEYCODE_V && event.isCtrlPressed()) {
                toggleBottomVideo();
                return true;
            } else if (kc == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (this.news != null) {
                    Content content = this.news.getContent();
                    if (content != null) {
                        if (content.hasVideo()) {
                            toggleBottomVideo();
                            return true;
                        }
                        if (content.hasAudio()) {
                            playAudio(null);
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Ensures that a minimum audio volume is set
     * @param minVolume ]0..1]
     */
    private void ensureMinVolume(@FloatRange(from = 0.01f, to = 1f) float minVolume) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(App.STREAM_TYPE);
        if (max == 0) return;
        int value = am.getStreamVolume(App.STREAM_TYPE);
        float current = (float) value / (float) max;
        if (current < minVolume) {
            am.setStreamVolume(App.STREAM_TYPE, Math.round(minVolume * max), AudioManager.FLAG_SHOW_UI);
        }
    }

    /**
     * Expands the bottom video block.
     * @param ignored ignored View
     */
    public void expandBottomSheet(@Nullable View ignored) {
        if (!this.loadVideo) {
            Snackbar.make(this.coordinatorLayout, R.string.pref_title_pref_load_videos_over_mobile_off, Snackbar.LENGTH_SHORT).show();
            return;
        }
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    /** {@inheritDoc} */
    @Override
    int getMainLayout() {
        return R.layout.activity_news;
    }

    /** {@inheritDoc} */
    @Override
    boolean hasMenuOverflowButton() {
        return true;
    }

    /**
     * Initialises the ExoPlayers.
     */
    private void initPlayers() {
        DefaultTrackSelector dts = new DefaultTrackSelector();
        // create ExoPlayer instances
        if (this.loadVideo) {
            this.exoPlayerTopVideo = ExoPlayerFactory.newSimpleInstance(this, dts);
            this.exoPlayerBottomVideo = ExoPlayerFactory.newSimpleInstance(this, dts);
            // assign the ExoPlayer instances to their video views
            this.topVideoView.setPlayer(this.exoPlayerTopVideo);
            this.bottomVideoView.setPlayer(this.exoPlayerBottomVideo);
            // make the bottom video view scale
            ((SimpleExoPlayer) this.exoPlayerBottomVideo).setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            // tap the top video view to mute/unmute its audio
            this.topVideoView.getVideoSurfaceView().setOnClickListener(ignored -> toggleTopVideoAudio());
        } else {
            this.topVideoView.setVisibility(View.GONE);
        }
        this.exoPlayerAudio = ExoPlayerFactory.newSimpleInstance(this, dts);
        // listen to state changes
        if (this.exoPlayerTopVideo != null) this.exoPlayerTopVideo.addListener(this.listenerTop);
        if (this.exoPlayerBottomVideo != null) this.exoPlayerBottomVideo.addListener(this.listenerBottom);
        this.exoPlayerAudio.addListener(this.listenerAudio);
    }

    /**
     * Initialises TextToSpeech.
     */
    private void initTts() {
        this.ttsInitialised = false;
        this.tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) {
                NewsActivity.this.tts = null;
            }
            invalidateOptionsMenu();
            if (NewsActivity.this.tts == null) return;
            NewsActivity.this.ttsInitialised = true;
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build();
            NewsActivity.this.tts.setAudioAttributes(aa);
            NewsActivity.this.tts.setLanguage(Locale.GERMAN);
            NewsActivity.this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onDone(String utteranceId) {
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }

                @Override
                public void onError(String utteranceId) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Error: " + utteranceId);
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "Error: " + utteranceId + ", error code: " + errorCode);
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }

                @Override
                public void onStart(String utteranceId) {
                    NewsActivity.this.ttsSpeaking = true;
                    invalidateOptionsMenu();
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }
            });
        });
    }

    private boolean isAudioPaused() {
        return !this.exoPlayerAudioPlayWhenReady && this.exoPlayerAudioState == Player.STATE_READY;
    }

    private boolean isAudioPlaying() {
        return this.exoPlayerAudioPlayWhenReady && this.exoPlayerAudioState == Player.STATE_READY;
    }

    private boolean isBottomSheetCollapsedOrHidden() {
        int state = this.bottomSheetBehavior.getState();
        return state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_HIDDEN;
    }

    private boolean isBottomVideoPlaying() {
        return this.exoPlayerBottomPlayWhenReady && this.exoPlayerBottomState == Player.STATE_READY;
    }

    private boolean isTopVideoPlaying() {
        return this.exoPlayerTopPlayWhenReady && this.exoPlayerTopState == Player.STATE_READY;
    }

    private boolean isUrlCached(@Nullable String url) {
        return this.service != null && this.service.getCachedBitmap(url) != null;
    }

    /**
     * Opens the given json file in a new NewsActivity.
     * @param url link to a json file
     */
    private void newNewsActivity(@NonNull String url) {
        View progress = findViewById(R.id.progress);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        try {
            File temp = File.createTempFile("temp", ".json");
            HamburgerService service = getHamburgerService();
            if (service == null) {
                if (progress != null) progress.setVisibility(View.GONE);
                return;
            }
            this.handler.postDelayed(() -> service.loadFile(url, temp, (completed, result) -> {
                if (progress != null) progress.setVisibility(View.GONE);
                if (!completed || result == null) {
                    Util.deleteFile(temp);
                    return;
                }
                if (result.rc != 200 || result.file == null) {
                    Util.deleteFile(temp);
                    Toast.makeText(this, getString(R.string.error_download_failed, result.msg), Toast.LENGTH_SHORT).show();
                    return;
                }
                JsonReader reader = null;
                try {
                    reader = new JsonReader(new InputStreamReader(new FileInputStream(result.file), StandardCharsets.UTF_8));
                    News news = News.parseNews(reader, false);
                    Util.close(reader);
                    reader = null;
                    Intent intent = new Intent(this, NewsActivity.class);
                    intent.putExtra(NewsActivity.EXTRA_NEWS, news);
                    // prevent going "back" to MainActivity because the preceding activity is <this>, not a MainActivity
                    intent.putExtra(NewsActivity.EXTRA_NO_HOME_AS_UP, true);
                    startActivity(intent);
                    overridePendingTransition(R.anim.grow_from_bottom, R.anim.fadeout);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "While parsing \"" + url + "\": " + e.toString());
                } finally {
                    Util.close(reader);
                }
                Util.deleteFile(temp);
            }), 250L);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            if (progress != null) progress.setVisibility(View.GONE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                stopAudio();
                stopBottomVideo();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                setVolume(0f);
                break;
        }
    }

    /**
     * The user has tapped the bottom video.
     * This is used to pause or resume the video.
     * @param ignored the bottom video view which is not needed here
     */
    public void onBottomVideoTapped(@Nullable View ignored) {
        if (this.exoPlayerBottomState != Player.STATE_READY) return;
        if (this.exoPlayerBottomPlayWhenReady) {
            stopBottomVideo();
        } else {
            playBottomVideo();
        }
    }

    /** {@inheritDoc} */
    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(21)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(App.STREAM_TYPE);

        boolean mobile = Util.isNetworkMobile(this);
        this.loadVideo = !mobile || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_LOAD_VIDEOS_OVER_MOBILE, App.DEFAULT_LOAD_VIDEOS_OVER_MOBILE);

        this.fab = findViewById(R.id.fab);
        this.topVideoView = findViewById(R.id.topVideoView);
        this.textViewTitle = findViewById(R.id.textViewTitle);
        this.audioBlock = findViewById(R.id.audioBlock);
        this.buttonAudio = findViewById(R.id.buttonAudio);
        this.textViewAudioTitle = findViewById(R.id.textViewAudioTitle);
        this.textViewContent = findViewById(R.id.textViewContent);
        if (this.textViewContent != null) {
            this.textViewContent.setMovementMethod(LinkMovementMethod.getInstance());
            this.textViewContent.setFocusable(true);
            this.textViewContent.setOnTouchListener(new TextViewImageSpanClickHandler());
        }
        this.recyclerViewRelated = findViewById(R.id.recyclerViewRelated);
        if (Util.isXLargeTablet(this)) {
            this.recyclerViewRelated.setLayoutManager(new GridLayoutManager(this, getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? RELATED_COLUMNS_TABLET_LANDSCAPE : RELATED_COLUMNS_TABLET_PORTRAIT));
        } else {
            this.recyclerViewRelated.setLayoutManager(new GridLayoutManager(this, RELATED_COLUMNS_PHONE));
        }
        this.recyclerViewRelated.setAdapter(new RelatedAdapter(this));
        LinearLayout bottomVideoBlock = findViewById(R.id.bottomVideoBlock);
        this.textViewBottomVideoPeek = findViewById(R.id.textViewBottomVideoPeek);
        this.bottomVideoView = findViewById(R.id.bottomVideoView);
        this.textViewBottomVideoViewOverlay = findViewById(R.id.textViewBottomVideoViewOverlay);
        this.bottomVideoPauseIndicator = findViewById(R.id.bottomVideoPauseIndicator);
        this.buttonAudio.setOnLongClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof String)) return false;
            Util.sendBinaryData(v.getContext(), (String) tag, v.getContentDescription());
            return true;
        });

        Typeface tf = Util.loadFont(this);
        if (tf != null) {
            this.textViewContent.setTypeface(tf);
        }

        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomVideoBlock);
        this.bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {

            private boolean speakingStoppedWhenBottomSheetSlidUp = false;

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // if the text is being read aloud while the bottom sheet is being slid upwards, ...
                if (slideOffset > 0.75f) {
                    if (!this.speakingStoppedWhenBottomSheetSlidUp && NewsActivity.this.ttsSpeaking) {
                        // shut up and watch the movie
                        startStopReading();
                        this.speakingStoppedWhenBottomSheetSlidUp = true;
                    }
                } else {
                    if (this.speakingStoppedWhenBottomSheetSlidUp && !NewsActivity.this.ttsSpeaking) {
                        // ok, the movie is over, what were you saying?
                        this.speakingStoppedWhenBottomSheetSlidUp = false;
                        startStopReading();
                    }
                }
            }

            @Override
            public void onStateChanged(@NonNull View bottomSheet, final int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    // hide the textview that is shown when collapsed
                    NewsActivity.this.textViewBottomVideoPeek.setVisibility(View.GONE);
                    // display the video title at the bottom edge of the video
                    NewsActivity.this.textViewBottomVideoViewOverlay.setVisibility(View.VISIBLE);
                    NewsActivity.this.textViewBottomVideoViewOverlay.setSelected(true);
                    NewsActivity.this.handler.postDelayed(() -> NewsActivity.this.textViewBottomVideoViewOverlay.setVisibility(View.INVISIBLE), 5_000L);
                    // stop the top video
                    stopTopVideo();
                    // stop audio
                    stopAudio();
                    // play the bottom video
                    playBottomVideo();
                    // more room for the bottom video
                    Util.hideActionNavigationStatusBar(NewsActivity.this, true);
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // restore default layout
                    Util.hideActionNavigationStatusBar(NewsActivity.this, false);
                    // hide the textview that is shown when collapsed
                    NewsActivity.this.textViewBottomVideoPeek.setVisibility(View.VISIBLE);
                    // hide the textview that is shown along the bottom edge of the video
                    NewsActivity.this.textViewBottomVideoViewOverlay.setVisibility(View.INVISIBLE);
                    // stop the bottom video
                    stopBottomVideo();
                    // start the top video
                    playTopVideo();
                }
            }
        });

        if (!this.loadVideo) {
            this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            this.topVideoView.setVisibility(View.GONE);
        }

        final Intent intent = getIntent();

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            boolean noHomeAsUp = intent.getBooleanExtra(EXTRA_NO_HOME_AS_UP, false);
            if (!noHomeAsUp) ab.setDisplayHomeAsUpEnabled(true);
        }

        this.news = (News) intent.getSerializableExtra(EXTRA_NEWS);
        if (ab != null && this.news != null) {
            String topline = this.news.getTopline();
            if (TextUtils.isEmpty(topline)) topline = this.news.getTitle();
            ab.setTitle(topline);
        }

        final String newsExternalId = intent.getStringExtra(UpdateJobService.EXTRA_FROM_NOTIFICATION);
        if (newsExternalId != null) {
            intent.removeExtra(UpdateJobService.EXTRA_FROM_NOTIFICATION);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(UpdateJobService.NOTIFICATION_ID);
        }

        if (this.news == null) {
            finish();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.news_menu, menu);
        menu.setQwertyMode(true);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_share) {
            String url = this.news.getDetailsWeb();
            if (url == null) {
                return true;
            }
            String title = this.news.getTitle() != null ? this.news.getTitle() : this.news.getTopline();
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, url);
            if (title != null) sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, getString(R.string.label_select_app, "HTML")));
            return true;
        }
        if (id == R.id.action_read) {
            startStopReading();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        if (this.exoPlayerTopVideo != null) this.exoPlayerTopVideo.stop();
        if (this.exoPlayerBottomVideo != null) this.exoPlayerBottomVideo.stop();
        if (this.exoPlayerAudio != null) this.exoPlayerAudio.stop();
        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT <= 23) {
            releasePlayers();
            if (this.originalAudioVolume >= 0) {
                setVolume((float) this.originalAudioVolume / (float) this.maxAudioVolume);
            }
        }
        if (this.tts != null) {
            this.tts.stop();
            this.tts.setOnUtteranceProgressListener(null);
            this.tts.shutdown();
            this.tts = null;
            this.ttsInitialised = false;
            this.ttsSpeaking = false;
            invalidateOptionsMenu();
        }
        super.onPause();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // sharing is possible if news has its detailsWeb attribute set
        MenuItem menuItemShare = menu.findItem(R.id.action_share);
        menuItemShare.setEnabled(this.news != null && this.news.getDetailsWeb() != null);
        // reading (aloud) is possible once tts has been initialised
        MenuItem menuItemRead = menu.findItem(R.id.action_read);
        if (this.tts != null && this.ttsInitialised) {
            menuItemRead.setEnabled(true);
            menuItemRead.setIcon(this.ttsSpeaking ? R.drawable.ic_hearing_ff0000_24dp : R.drawable.ic_hearing_ededed_24dp);
            menuItemRead.setTitle(this.ttsSpeaking ? R.string.action_read_stop : R.string.action_read);
            if (this.ttsSpeaking) {
                this.fab.setImageResource(R.drawable.ic_hearing_ff0000_24dp);
                this.fab.setOnClickListener(v -> {
                    startStopReading();
                    v.setOnClickListener(null);
                    NewsActivity.this.fab.hide();
                });
                this.fab.setOnLongClickListener(v -> {
                    Toast.makeText(v.getContext(), R.string.action_read_stop, Toast.LENGTH_LONG).show();
                    v.setOnLongClickListener(null);
                    return true;
                });
                this.fab.show();
            } else {
                this.fab.hide();
            }
        } else {
            menuItemRead.setEnabled(false);
            menuItemRead.setIcon(R.drawable.ic_hearing_ededed_24dp);
            menuItemRead.setTitle(R.string.action_read);
            this.fab.hide();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void onRelatedClicked(int index) {
        RelatedAdapter adapter = (RelatedAdapter)this.recyclerViewRelated.getAdapter();
        if (adapter == null) return;
        Related related = adapter.getRelated(index);
        if (related == null) return;
        String url = related.getDetails();
        if (url == null) return;
        newNewsActivity(url);
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, HamburgerService.class), this, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);

        if (this.tts == null) {
            initTts();
        } else {
            invalidateOptionsMenu();
        }

        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT <= 23 || this.exoPlayerTopVideo == null || this.exoPlayerBottomVideo == null) {
            initPlayers();
        }

        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null) {
            this.maxAudioVolume = am.getStreamMaxVolume(App.STREAM_TYPE);
            this.originalAudioVolume = am.getStreamVolume(App.STREAM_TYPE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        applyNews();
    }

    /** {@inheritDoc} */
    @Override
    protected void onStart() {
        super.onStart();
        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT > 23) {
            initPlayers();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onStop() {
        // see https://codelabs.developers.google.com/codelabs/exoplayer-intro/#2
        if (Build.VERSION.SDK_INT > 23) {
            releasePlayers();
            if (this.originalAudioVolume >= 0) {
                setVolume((float) this.originalAudioVolume / (float) this.maxAudioVolume);
            }
        }
        super.onStop();
    }

    /**
     * Plays or pauses the audio.
     * @param ignored View
     */
    public void playAudio(@Nullable View ignored) {
        if (this.exoPlayerAudio == null) return;
        if (isAudioPlaying()) {
            this.exoPlayerAudio.setPlayWhenReady(false);
            return;
        }
        if (isAudioPaused()) {
            this.exoPlayerAudio.setPlayWhenReady(true);
            return;
        }
        Object src = this.buttonAudio.getTag();
        if (!(src instanceof String)) return;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        if (!Util.isNetworkAvailable(this)) {
            //Snackbar.make(this.coordinatorLayout, R.string.error_no_network, Snackbar.LENGTH_SHORT).show();
            showNoNetworkSnackbar();
            return;
        }
        int requestResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(this.aa).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(this).build();
            requestResult = am.requestAudioFocus(afr);
        } else {
            requestResult = am.requestAudioFocus(this, App.STREAM_TYPE, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
        if (requestResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Audio focus request denied.");
            Snackbar.make(this.coordinatorLayout, R.string.error_audio_focus_denied, Snackbar.LENGTH_SHORT).show();
            return;
        }
        am.setMode(AudioManager.MODE_NORMAL);
        MediaSource msAudio = this.mediaSourceHelper.buildMediaSource(Uri.parse((String) src));
        this.exoPlayerAudio.prepare(msAudio, true, true);
        this.exoPlayerAudio.setPlayWhenReady(true);
        if (this.originalAudioVolume > 0) {
            ensureMinVolume((float) this.originalAudioVolume / (float) this.maxAudioVolume);
        } else {
            ensureMinVolume(0.333f);
        }
    }

    /**
     * Plays the bottom (a.k.a. content) video and stops the top video.
     */
    private void playBottomVideo() {
        if (this.exoPlayerBottomVideo == null) return;
        if (!Util.isNetworkAvailable(this)) {
            //Snackbar.make(this.coordinatorLayout, R.string.error_no_network, Snackbar.LENGTH_SHORT).show();
            showNoNetworkSnackbar();
            return;
        }
        // play the video
        if (this.exoPlayerTopVideo != null) this.exoPlayerTopVideo.setPlayWhenReady(false);
        if (this.originalAudioVolume >= 0) {
            setVolume((float) this.originalAudioVolume / (float) this.maxAudioVolume);
        } else {
            ensureMinVolume(0.5f);
        }
        this.exoPlayerBottomVideo.setPlayWhenReady(true);
    }

    /**
     * Plays the top (a.k.a. news) video and stops the bottom video.
     */
    private void playTopVideo() {
        if (this.exoPlayerTopVideo == null) return;
        if (this.exoPlayerBottomVideo != null) this.exoPlayerBottomVideo.setPlayWhenReady(false);
        this.exoPlayerTopVideo.setPlayWhenReady(true);
    }

    /**
     * Releases the ExoPlayers.
     */
    private void releasePlayers() {
        if (this.exoPlayerTopVideo != null) {
            this.exoPlayerTopVideo.removeListener(this.listenerTop);
            //playbackPosition = exoPlayerTopVideo.getCurrentPosition();
            //currentWindow = exoPlayerTopVideo.getCurrentWindowIndex();
            //playWhenReady = exoPlayerTopVideo.getPlayWhenReady();
            this.exoPlayerTopVideo.release();
            this.exoPlayerTopVideo = null;
        }
        if (this.exoPlayerBottomVideo != null) {
            this.exoPlayerBottomVideo.removeListener(this.listenerBottom);
            //playbackPosition = exoPlayerBottomVideo.getCurrentPosition();
            //currentWindow = exoPlayerBottomVideo.getCurrentWindowIndex();
            //playWhenReady = exoPlayerBottomVideo.getPlayWhenReady();
            this.exoPlayerBottomVideo.release();
            this.exoPlayerBottomVideo = null;
        }
        if (this.exoPlayerAudio != null) {
            this.exoPlayerAudio.removeListener(this.listenerAudio);
            this.exoPlayerAudio.release();
            this.exoPlayerAudio = null;
        }
    }

    /**
     * Sets the audio volume.
     * @param value [0..1]
     */
    private void setVolume(@FloatRange(from = 0f, to = 1f) float value) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        int max = am.getStreamMaxVolume(App.STREAM_TYPE);
        am.setStreamVolume(App.STREAM_TYPE, Math.round(value * max), 0);
    }

    /**
     * Reads the article to the user or stops reading it.
     */
    private void startStopReading() {
        if (this.tts == null) {
            invalidateOptionsMenu();
            return;
        }
        if (this.ttsSpeaking) {
            this.tts.stop();
            invalidateOptionsMenu();
            return;
        }
        Bundle extras = new Bundle();
        extras.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.667f);

        CharSequence txt = this.textViewContent.getText();
        int max = TextToSpeech.getMaxSpeechInputLength();
        List<String> toSpeak = Util.splitString(txt.toString(), max - 1);
        int counter = 1;
        for (String partToSpeak : toSpeak) {
            int result = this.tts.speak(partToSpeak, counter == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, extras, this.news.getTitle() + '_' + counter);
            if (result != TextToSpeech.SUCCESS) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Tts failed with error " + result);
                @StringRes int msg;
                switch (result) {
                    case TextToSpeech.ERROR_NOT_INSTALLED_YET: msg = R.string.error_tts_fail_not_downloaded; break;
                    case TextToSpeech.ERROR_NETWORK:
                    case TextToSpeech.ERROR_NETWORK_TIMEOUT: msg = R.string.error_tts_fail_network; break;
                    default: msg = R.string.error_tts_fail;
                }
                Snackbar.make(this.coordinatorLayout, msg, Snackbar.LENGTH_LONG).show();
                break;
            }
            counter++;
        }
        invalidateOptionsMenu();
    }

    private void stopAudio() {
        if (this.exoPlayerAudio == null) return;
        if (isAudioPlaying()) {
            this.exoPlayerAudio.setPlayWhenReady(false);
        }
    }

    /**
     * Stops playing the bottom (a.k.a. content) video.
     */
    private void stopBottomVideo() {
        if (this.exoPlayerBottomVideo == null) return;
        if (isBottomVideoPlaying()) {
            this.exoPlayerBottomVideo.setPlayWhenReady(false);
        }
    }

    /**
     * Stops playing the top (a.k.a. news) video.
     */
    private void stopTopVideo() {
        if (this.exoPlayerTopVideo == null) return;
        if (isTopVideoPlaying()) {
            this.exoPlayerTopVideo.setPlayWhenReady(false);
        }
    }

    /**
     * Expands or collapses the bottom sheet.
     */
    private void toggleBottomVideo() {
        if (this.news == null) return;
        Content content = this.news.getContent();
        if (content == null) return;
        if (content.hasVideo()) {
            if (isBottomSheetCollapsedOrHidden()) {
                expandBottomSheet(null);
            } else {
                collapseBottomSheet();
            }
        }
    }

    /**
     * Switches audio for the top video on or off.
     */
    private void toggleTopVideoAudio() {
        if (this.exoPlayerTopVideo == null) return;
        float vol = ((SimpleExoPlayer) this.exoPlayerTopVideo).getVolume();
        if (vol <= 0.01f) {
            ((SimpleExoPlayer) this.exoPlayerTopVideo).setVolume(1f);
        } else {
            ((SimpleExoPlayer) this.exoPlayerTopVideo).setVolume(0f);
        }
    }

    /**
     * Picasso {@link Target} that can be used to replace an {@link ImageSpan} which is embedded within a {@link Spannable}.
     */
    private static class SpannableImageTarget implements Target {

        private final Reference<NewsActivity> refActivity;
        private final String source;
        private Spannable spannable;
        private ImageSpan toReplace;
        private final int percentWidth;

        /**
         * Constructor.
         * @param activity NewsActivity
         * @param spannable Spannable
         * @param toReplace ImageSpan
         */
        SpannableImageTarget(@NonNull NewsActivity activity, @NonNull Spannable spannable, @NonNull ImageSpan toReplace, @IntRange(from = 1, to = 100) int percentWidth) {
            super();
            this.refActivity = new WeakReference<>(activity);
            this.spannable = spannable;
            this.toReplace = toReplace;
            this.source = toReplace.getSource();
            this.percentWidth = percentWidth;
        }

        /** {@inheritDoc} */
        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
            //if (BuildConfig.DEBUG) Log.i(TAG, "Failed to load " + this.source + (e != null ? " - " + e.toString() : ""));
            this.spannable.removeSpan(this.toReplace);
            NewsActivity activity = this.refActivity.get();
            if (activity != null) activity.spannableImageTargets.remove(this);
            this.spannable = null;
            this.toReplace = null;
        }

        /** {@inheritDoc} */
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            NewsActivity activity = this.refActivity.get();
            if (activity == null) {
                return;
            }
            //TODO why is the bitmap not put into the Picasso cache by Picasso itself?!
            String url = this.toReplace.getSource();
            if (url != null && !activity.isUrlCached(url)) {
                activity.service.addToCache(url, bitmap);
            }
            int availableWidth = activity.textViewContent.getWidth() - activity.textViewContent.getPaddingStart() - activity.textViewContent.getPaddingEnd();
            if (this.percentWidth > 0 && this.percentWidth <= 100) availableWidth = Math.round(availableWidth * this.percentWidth / 100f);
            float factor = (float) bitmap.getHeight() / (float) bitmap.getWidth();
            bitmap = Bitmap.createScaledBitmap(bitmap, availableWidth, Math.round(availableWidth * factor), false);
            int start = this.spannable.getSpanStart(this.toReplace);
            int end = this.spannable.getSpanEnd(this.toReplace);
            this.spannable.removeSpan(this.toReplace);
            Drawable d = new BitmapDrawable(activity.getResources(), bitmap);
            d.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            ImageSpan newImageSpan = new ImageSpan(d, this.source); // pass original source to the new ImageSpan so its url can later be shared (see TextViewImageSpanClickHandler)
            this.spannable.setSpan(newImageSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            activity.spannableImageTargets.remove(this);
            this.spannable = null;
            this.toReplace = null;
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    }

    /**
     *
     */
    private static class URLSpanWChooser extends URLSpan {
        @ColorInt
        private final int linkColor;

        /**
         * Constructor
         * @param url URL
         * @param linkColor link color
         */
        private URLSpanWChooser(@NonNull String url, @IntRange(from = 1) @ColorInt int linkColor) {
            super(Util.makeHttps(url));
            this.linkColor = linkColor;
        }

        /** {@inheritDoc} */
        @Override
        public void onClick(View widget) {
            Uri uri = Uri.parse(getURL());
            String lps = uri.getLastPathSegment();
            String tag = lps != null ? lps.substring(lps.lastIndexOf('.') + 1) : null;
            String mime = tag != null ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(tag) : null;
            Context context = widget.getContext();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooserIntent = Intent.createChooser(intent, context.getString(R.string.label_select_app, lps != null ? lps : getURL()));
            try {
                context.startActivity(chooserIntent);
            } catch (ActivityNotFoundException e) {
                if (BuildConfig.DEBUG) Log.w(getClass().getSimpleName(), "Actvity was not found for intent, " + intent.toString());
            }
        }

        /** {@inheritDoc} */
        @Override
        @NonNull
        public String toString() {
            return getClass().getSimpleName() + " to \"" + getURL() + '\"';
        }

        /** {@inheritDoc} */
        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.linkColor = this.linkColor;
            ds.setTextSkewX(-0.05f);
            super.updateDrawState(ds);
        }
    }

    /**
     * A replacement for URLSpan that opens links internally instead of posting an Intent.ACTION_VIEW action.
     */
    private static class InternalURLSpan extends URLSpan {

        @ColorInt
        private final int linkColor;

        /**
         * Constructor.
         * @param url URL to open (its host must end with one of those in {@link App#PERMITTED_HOSTS})
         * @param linkColor link color
         */
        InternalURLSpan(@NonNull String url, @IntRange(from = 1) @ColorInt int linkColor) {
            super(Util.makeHttps(url));
            this.linkColor = linkColor;
        }

        /** {@inheritDoc} */
        @Override
        public void onClick(View widget) {
            Context context = widget.getContext();
            String url = getURL();
            if (url == null) return;
            // links to json files will be opened in a new NewsActivity
            if (url.toLowerCase(Locale.US).endsWith(".json")) {
                if (context instanceof NewsActivity) {
                    ((NewsActivity)context).newNewsActivity(url);
                }
                return;
            }
            // links to html files will be opened in a WebViewActivity
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra(WebViewActivity.EXTRA_URL, url);
            // prevent going "back" to MainActivity because we did not arrive from that
            intent.putExtra(WebViewActivity.EXTRA_NO_HOME_AS_UP, true);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                if (BuildConfig.DEBUG)
                    Log.e(getClass().getSimpleName(), WebViewActivity.class.getName() + " was not found!");
            }
        }

        /** {@inheritDoc} */
        @Override
        @NonNull
        public String toString() {
            return "InternalURLSpan \"" + getURL() + "\"";
        }

        /** {@inheritDoc} */
        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.linkColor = this.linkColor;
            super.updateDrawState(ds);
        }
    }

}
