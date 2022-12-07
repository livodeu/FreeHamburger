package de.freehamburger;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
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
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.util.JsonReader;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.freehamburger.adapters.RelatedAdapter;
import de.freehamburger.exo.ExoFactory;
import de.freehamburger.exo.MediaSourceHelper;
import de.freehamburger.exo.PlayerListener;
import de.freehamburger.model.Audio;
import de.freehamburger.model.Content;
import de.freehamburger.model.Dictionary;
import de.freehamburger.model.News;
import de.freehamburger.model.Related;
import de.freehamburger.model.StreamQuality;
import de.freehamburger.model.Video;
import de.freehamburger.supp.PopupManager;
import de.freehamburger.util.Log;
import de.freehamburger.util.PositionedSpan;
import de.freehamburger.util.PrintUtil;
import de.freehamburger.util.TextViewImageSpanClickHandler;
import de.freehamburger.util.Util;
import okhttp3.Call;

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

    static final String EXTRA_NEWS = BuildConfig.APPLICATION_ID + ".extra.news";
    static final String EXTRA_JSON = BuildConfig.APPLICATION_ID + ".extra.json";
    /** boolean; if true, the ActionBar will not show the home arrow which would lead the user to the MainActivity */
    static final String EXTRA_NO_HOME_AS_UP = "extra_no_home_as_up";
    /** pictures are scaled to this percentage of the available width */
    private static final int SCALE_PICTURES_TO_PERCENT = 90;
    private static final String TAG = "NewsActivity";
    @NonNull
    private final AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setLegacyStreamType(App.STREAM_TYPE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final MediaSourceHelper mediaSourceHelper = new MediaSourceHelper();
    /** strong references required for image loading (PictureLoader discards Targets that are not referenced anymore!) */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Set<SpannableImageTarget> spannableImageTargets = new HashSet<>();
    /** Listener for the top video */
    private final PlayerListener listenerTop = new PlayerListener(this,false);
    @VisibleForTesting BottomSheetBehavior<? extends LinearLayout> bottomSheetBehavior;
    @VisibleForTesting boolean loadVideo;
    @Nullable @VisibleForTesting ExoPlayer exoPlayerTopVideo;
    @Nullable @VisibleForTesting ExoPlayer exoPlayerBottomVideo;
    /** passed with the Intent as extra {@link #EXTRA_NEWS} */
    private News news;
    private String json;
    /** <a href="https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/ui/StyledPlayerView.html">JavaDoc</a> */
    private StyledPlayerView topVideoView;
    /** Listener for the audio */
    private final PlayerListener listenerAudio = new PlayerListener(this,true) {

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            super.onPlayerError(error);
            NewsActivity.this.buttonAudio.setEnabled(false);
        }

        @Override
        public void onPlayerStateOrOnPlayWhenReadyChanged() {
            NewsActivity.this.buttonAudio.setImageResource(isAudioPlaying() ? R.drawable.ic_pause_black_24dp : R.drawable.ic_play_arrow_black_24dp);
        }
    };
    private TextView textViewTitle;
    private ViewGroup /*RelativeLayout*/ audioBlock;
    private ImageButton buttonAudio;
    private TextView textViewAudioTitle;
    private TextView textViewContent;
    private RecyclerView recyclerViewRelated;
    private FloatingActionButton fab;
    private AudioFocusRequest afr;
    private int maxAudioVolume = -1;
    private int originalAudioVolume = -1;
    private final Runnable preferredVolumeUpdater = () -> {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        NewsActivity.this.originalAudioVolume = am.getStreamVolume(App.STREAM_TYPE);
        if (BuildConfig.DEBUG)
            Log.i(TAG, "The original audio volume has been updated to " + originalAudioVolume + " out of " + maxAudioVolume);
    };
    // located at the bottom edge, contains the video title
    private TextView textViewBottomVideoPeek;
    /** <a href="https://google.github.io/ExoPlayer/doc/reference/com/google/android/exoplayer2/ui/StyledPlayerView.html">JavaDoc</a> */
    private StyledPlayerView bottomVideoView;
    private TextView textViewBottomVideoViewOverlay;
    /** Listener for the bottom video */
    private final PlayerListener listenerBottom = new PlayerListener(this,true) {

        // flag that remembers whether the bottom video has been collapsed after the playback ended
        private boolean bottomVideoHiddenAfterPlayback = false;

        /** {@inheritDoc} */
        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            // if bottom sheet is collapsed, hide it completely
            if (isBottomSheetCollapsed()) {
                hideBottomSheet();
                return;
            }
            if (isBottomSheetHidden()) return;
            collapseBottomSheet();
            // show error message because bottom sheet was expanded
            super.onPlayerError(error);
        }

        @Override
        public void onPlayerStateOrOnPlayWhenReadyChanged() {
            if (exoPlayerPlayWhenReady && exoPlayerState != Player.STATE_ENDED) {
                this.bottomVideoHiddenAfterPlayback = false;
                float sx = bottomVideoView.getScaleX();
                if (sx < 1f) ObjectAnimator.ofFloat(NewsActivity.this.bottomVideoView, "scaleX", sx, 1f).setDuration(300L).start();
            }
            // wind the tape back when finished
            if (!exoPlayerPlayWhenReady && exoPlayerState == Player.STATE_ENDED) NewsActivity.this.exoPlayerBottomVideo.seekTo(0);
            // collapse the bottom video when finished
            if (exoPlayerPlayWhenReady && exoPlayerState == Player.STATE_ENDED && !this.bottomVideoHiddenAfterPlayback) {
                this.bottomVideoHiddenAfterPlayback = true;
                ObjectAnimator.ofFloat(NewsActivity.this.bottomVideoView, "scaleX", 1f, 0f).setDuration(500L).start();
                NewsActivity.this.handler.postDelayed(() -> {
                    collapseBottomSheet();
                    NewsActivity.this.bottomVideoView.setScaleX(1f);
                }, 500L);
            }
        }
    };
    @VisibleForTesting @Nullable
    ExoPlayer exoPlayerAudio;
    @Nullable
    private TextToSpeech tts;
    private boolean ttsInitialised = false;
    private boolean ttsSpeaking = false;

    /**
     * Abandons the audio focus.
     * @param am AudioManager
     */
    private void abandonAudioFocus(@Nullable AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (this.afr == null) return;
            if (am == null) am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            am.abandonAudioFocusRequest(this.afr);
        } else {
            if (am == null) am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            am.abandonAudioFocus(this);
        }
    }

    /**
     * Applies the {@link #news} to the views. If you find a rhyme you may keep it.
     */
    private void applyNews() {
        if (this.news == null) {
            this.textViewTitle.setText(null);
            this.textViewContent.setText(null);
            this.textViewContent.setTextIsSelectable(false);
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean validAudio = false;
        boolean validTopVideo = false;
        boolean validBottomVideo = false;
        // top video from news.streams
        if (prefs.getBoolean(App.PREF_SHOW_TOP_VIDEO, getResources().getBoolean(R.bool.pref_show_topvideo_default))) {
            String newsVideo = StreamQuality.getStreamsUrl(this, this.news.getStreams());
            if (newsVideo != null && this.exoPlayerTopVideo != null) {
                Uri videoUri = Uri.parse(Util.makeHttps(newsVideo));
                if (App.isHostAllowed(videoUri.getHost())) {
                    validTopVideo = true;
                    MediaSource msTopVideo = this.mediaSourceHelper.buildMediaSource((Call.Factory) ((App) getApplicationContext()).getOkHttpClient(), videoUri);
                    this.exoPlayerTopVideo.setMediaSource(msTopVideo, true);
                    this.exoPlayerTopVideo.prepare();
                    this.exoPlayerTopVideo.setVolume(0f);
                    this.exoPlayerTopVideo.setRepeatMode(Player.REPEAT_MODE_ALL);
                    this.exoPlayerTopVideo.setPlayWhenReady(true);
                }
            }
        }
        if (!validTopVideo) {
            this.topVideoView.setVisibility(View.GONE);
        }
        this.textViewTitle.setText(this.news.getTitle());
        this.textViewTitle.setSelected(true);
        //
        Content content = this.news.getContent();
        // audio from the news.content part
        Audio audio = content != null && content.hasAudio() ? content.getAudioList().get(0) : null;
        if (audio != null) {
            String audioUrl = audio.getStream();
            Uri audioUri = audioUrl != null ? Uri.parse(Util.makeHttps(audioUrl)) : null;
            if (audioUri != null && App.isHostAllowed(audioUri.getHost())) {
                validAudio = true;
                this.buttonAudio.setTag(audio.getStream());
                this.buttonAudio.setContentDescription(audio.getTitle());
                this.textViewAudioTitle.setText(audio.getTitle());
                this.textViewAudioTitle.setSelected(true);
                this.audioBlock.setVisibility(View.VISIBLE);
                this.listenerAudio.setCurrentResource(audioUrl);
                // restore the layout params for the textViewContent
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) this.textViewContent.getLayoutParams();
                lp.removeRule(RelativeLayout.BELOW);
                lp.addRule(RelativeLayout.BELOW, R.id.audioBlock);
            }
        }
        if (!validAudio) {
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
                    if (uri != null && App.isHostAllowed(uri.getHost())) {
                        uris.add(uri);
                        if (contentVideoTitle.length() > 0) contentVideoTitle.append(", ");
                        contentVideoTitle.append(contentVideo.getTitle());
                    }
                }
                if (!uris.isEmpty()) {
                    validBottomVideo = true;
                    this.textViewBottomVideoPeek.setTypeface(Util.getTypefaceForTextView(this.textViewBottomVideoPeek, contentVideoTitle.toString()));
                    this.textViewBottomVideoPeek.setText(contentVideoTitle);
                    this.textViewBottomVideoViewOverlay.setText(contentVideoTitle);
                    msBottomVideo = this.mediaSourceHelper.buildMediaSource((Call.Factory) ((App)getApplicationContext()).getOkHttpClient(), uris);
                    if (this.exoPlayerBottomVideo != null) {
                        this.exoPlayerBottomVideo.setMediaSource(msBottomVideo, true);
                        this.exoPlayerBottomVideo.prepare();
                    }
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            } else {
                // one content video (usual case)
                Video contentVideo = videoList.get(0);
                String url = StreamQuality.getStreamsUrl(this, contentVideo.getStreams());
                Uri uri = url != null ? Uri.parse(Util.makeHttps(url)) : null;
                if (uri != null && App.isHostAllowed(uri.getHost())) {
                    validBottomVideo = true;
                    String contentVideoTitle = contentVideo.getTitle();
                    this.textViewBottomVideoPeek.setTypeface(Util.getTypefaceForTextView(this.textViewBottomVideoPeek, contentVideoTitle));
                    this.textViewBottomVideoPeek.setText(contentVideoTitle);
                    this.textViewBottomVideoViewOverlay.setText(contentVideoTitle);
                    msBottomVideo = this.mediaSourceHelper.buildMediaSource((Call.Factory) ((App) getApplicationContext()).getOkHttpClient(), uri);
                    if (this.exoPlayerBottomVideo != null) {
                        this.exoPlayerBottomVideo.setMediaSource(msBottomVideo, true);
                        this.exoPlayerBottomVideo.prepare();
                        this.listenerBottom.setCurrentResource(uri.toString());
                    }
                    this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            }
        }
        if (!validBottomVideo) {
            this.textViewBottomVideoPeek.setText(null);
            this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        // if there is an audio element or a bottom video, show a warning if audio output is muted
        if (validAudio || validBottomVideo) {
            if (this.originalAudioVolume == 0 && prefs.getBoolean(App.PREF_WARN_MUTE, App.PREF_WARN_MUTE_DEFAULT)) {
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
                    this.handler.postDelayed(() -> muteWarning.setVisibility(View.GONE), 2_750L);
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
        String htmlText = content != null ? content.getHtmlText() : null;
        if (TextUtils.isEmpty(htmlText)) {
            this.textViewContent.setVisibility(View.GONE);
            this.textViewContent.setText(null);
        } else {
            List<PositionedSpan> additionalSpans = new ArrayList<>();
            assert htmlText != null;

            Spanned spanned = Util.fromHtml(this, htmlText, this.service);

            final String xsmallStart = "<xsm>";
            final String xsmallEnd = "</xsm>";
            for (int pos = 0; ; ) {
                int start = TextUtils.indexOf(spanned, xsmallStart, pos);
                if (start < 0) break;
                int end = TextUtils.indexOf(spanned, xsmallEnd, start + 1);
                if (end < 0) break;
                htmlText = htmlText.substring(0, start) + htmlText.substring(end + xsmallEnd.length());
                additionalSpans.add(new PositionedSpan(new TextAppearanceSpan(this, android.R.style.TextAppearance_DeviceDefault_Small), start, end - start));
                pos = end + xsmallEnd.length();
            }

            //final boolean textSelectable = this.textViewContent.isTextSelectable();
            this.textViewContent.setTextIsSelectable(false);
            this.textViewContent.setText(spanned, TextView.BufferType.SPANNABLE);
            this.textViewContent.setVisibility(View.VISIBLE);

            // spannable is a SpannableString; therefore the text cannot be modified easily here
            CharSequence text = this.textViewContent.getText();
            Spannable spannable = (Spannable) this.textViewContent.getText();

            for (PositionedSpan positionedSpan : additionalSpans) {
                spannable.setSpan(positionedSpan.characterStyle, positionedSpan.getPos(), positionedSpan.getPos() + positionedSpan.getLength(), 0);
            }

            // load images
            final ImageSpan[] imageSpans = spannable.getSpans(0, spannable.length(), ImageSpan.class);
            if (imageSpans != null && imageSpans.length > 0) {
                // Collect Targets and sources and actually start loading only after all targets have been put into this.spannableImageTargets.
                // This is to make sure that loading of all images has finished once this.spannableImageTargets is empty.
                // See SpannableImageTarget: it calls enableTextSelection() when this.spannableImageTargets is empty.
                final Map<SpannableImageTarget, String> targetsAndUris = new HashMap<>(imageSpans.length);
                //
                for (ImageSpan imageSpan : imageSpans) {
                    String src = imageSpan.getSource();
                    if (src == null || src.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE)) continue;
                    src = Util.makeHttps(src);
                    SpannableImageTarget target = new SpannableImageTarget(this, spannable, imageSpan, SCALE_PICTURES_TO_PERCENT);
                    targetsAndUris.put(target, src);
                    // store target in an (otherwise unused) instance variable to avoid garbage collection, because the service holds only a WeakReference on the target
                    this.spannableImageTargets.add(target);
                }
                // now start loading
                Set<Map.Entry<SpannableImageTarget, String>> entries = targetsAndUris.entrySet();
                for (Map.Entry<SpannableImageTarget, String> entry : entries) {
                    this.service.loadImage(entry.getValue(), entry.getKey());
                }
            } else {
                // it seems now to be safe to enable text selection as we don't have pictures to load
                enableTextSelection(prefs);
            }

            // convert BackgroundColorSpans to BackgroundSpans
            // Note: there won't be any BackgroundColorSpans in API < 24 because Html.fromHtml() does not generate them
            BackgroundColorSpan[] bs = spannable.getSpans(0, spannable.length(), BackgroundColorSpan.class);
            if (bs != null) {
                final int backgroundColor = getResources().getColor(R.color.colorBoxBackground);
                for (BackgroundColorSpan b : bs) {
                    int start = spannable.getSpanStart(b);
                    int end = spannable.getSpanEnd(b);
                    spannable.removeSpan(b);
                    spannable.setSpan(new BackgroundSpan(backgroundColor), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                }
            }

            // if desired, replace all URLSpans linking to permitted hosts with InternalURLSpans which allows to open the urls internally
            boolean internalLinks = prefs.getBoolean(App.PREF_OPEN_LINKS_INTERNALLY, App.PREF_OPEN_LINKS_INTERNALLY_DEFAULT);

            URLSpan[] urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
            if (urlSpans != null) {
                @ColorInt final int colorInternal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? getResources().getColor(R.color.colorLinkInternal, getTheme()) : getResources().getColor(R.color.colorLinkInternal);
                @ColorInt final int colorExternal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? getResources().getColor(R.color.colorLinkExternal, getTheme()) : getResources().getColor(R.color.colorLinkExternal);
                for (URLSpan urlSpan : urlSpans) {
                    final String url = urlSpan.getURL();
                    int start = spannable.getSpanStart(urlSpan);
                    int end = spannable.getSpanEnd(urlSpan);
                    spannable.removeSpan(urlSpan);
                    if (internalLinks) {
                        Uri uri = Uri.parse(url);
                        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.US) : null;
                        if (App.isHostAllowed(host) && !App.isHostRestrictedToNonScript(host)) {
                            // links identified as 'internal' will be opened within this app
                            InternalURLSpan internalURLSpan = new InternalURLSpan(url, colorInternal);
                            spannable.setSpan(internalURLSpan, start, end, 0);
                        } else {
                            // other links go to the browser
                            URLSpanWChooser urlSpanWChooser = new URLSpanWChooser(url, colorExternal, "text/html");
                            spannable.setSpan(urlSpanWChooser, start, end, 0);
                        }
                    } else {
                        if (url.endsWith(".json")) {
                            // it does not make sense to pass json links to the browser
                            InternalURLSpan internalURLSpan = new InternalURLSpan(url, colorInternal);
                            spannable.setSpan(internalURLSpan, start, end, 0);
                        } else {
                            // non-json links go to the browser
                            URLSpanWChooser urlSpanWChooser = new URLSpanWChooser(url, colorExternal, "text/html");
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
     * Enables or disables text selection according to the preferences.
     * @param prefs SharedPreferences
     */
    @MainThread
    private void enableTextSelection(@Nullable SharedPreferences prefs) {
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableTextSelection = prefs.getBoolean(App.PREF_TEXT_SELECTION, App.PREF_TEXT_SELECTION_DEFAULT);
        this.textViewContent.setTextIsSelectable(enableTextSelection);
        if (enableTextSelection) {
            Dictionary.enable(this.textViewContent);
            // setting the MovementMethod here allows BOTH selection and clickable links
            this.textViewContent.setMovementMethod(LinkMovementMethod.getInstance());
            // workaround for weird behaviour: scrollViewNews scrolls downwards by about 300-400 px on first text selection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ScrollView sv = findViewById(R.id.scrollViewNews);
                sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    boolean firstScroll = true;
                    @Override
                    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                        if (this.firstScroll) {
                            v.scrollTo(0,0);
                            this.firstScroll = false;
                        }
                    }
                });
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ScrollView sv = findViewById(R.id.scrollViewNews);
                sv.setOnScrollChangeListener(null);
            }
        }
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
     * Hides the bottom video.
     */
    private void hideBottomSheet() {
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    /**
     * Initialises the ExoPlayers.
     */
    private void initPlayers() {
        DefaultTrackSelector dts = new DefaultTrackSelector(this);
        // create ExoPlayer instances
        if (this.loadVideo) {
            this.exoPlayerTopVideo = ExoFactory.makeExoPlayer(this, dts);
            this.exoPlayerBottomVideo = ExoFactory.makeExoPlayer(this, dts);
            //
            View backSeconds = this.bottomVideoView.findViewById(R.id.exo_rew_with_amount);
            if (backSeconds != null) backSeconds.setVisibility(View.GONE);
            View fwdSeconds = this.bottomVideoView.findViewById(R.id.exo_ffwd_with_amount);
            if (fwdSeconds != null) fwdSeconds.setVisibility(View.GONE);
            View st = findViewById(R.id.exo_subtitles);
            if (st != null) st.setVisibility(View.GONE);
            // assign the ExoPlayer instances to their video views
            this.topVideoView.setPlayer(this.exoPlayerTopVideo);
            this.bottomVideoView.setPlayer(this.exoPlayerBottomVideo);
            // make the bottom video view scale
            this.exoPlayerBottomVideo.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            // tap the top video view to mute/unmute its audio
            try {
                //noinspection ConstantConditions
                this.topVideoView.getVideoSurfaceView().setOnClickListener(ignored -> toggleTopVideoAudio());
            } catch (NullPointerException ignored) {
                // this never occurred but lint thinks it might…
            }
        } else {
            this.topVideoView.setVisibility(View.GONE);
        }
        this.exoPlayerAudio = ExoFactory.makeExoPlayer(this, dts);
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
            if (isDestroyed()) return;
            if (status != TextToSpeech.SUCCESS) {
                NewsActivity.this.tts = null;
            }
            invalidateOptionsMenu();
            if (NewsActivity.this.tts == null) return;
            NewsActivity.this.ttsInitialised = true;
            AudioAttributes.Builder aab = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    ;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // https://android-developers.googleblog.com/2019/07/capturing-audio-in-android-q.html
                aab.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE);
            }
            AudioAttributes aa = aab.build();
            NewsActivity.this.tts.setAudioAttributes(aa);
            NewsActivity.this.tts.setLanguage(Locale.GERMAN);
            NewsActivity.this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onDone(String utteranceId) {
                    abandonAudioFocus(null);
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }

                @Override
                public void onError(String utteranceId) {
                    abandonAudioFocus(null);
                    if (BuildConfig.DEBUG) Log.i(TAG, "Error: " + utteranceId);
                    NewsActivity.this.ttsSpeaking = false;
                    invalidateOptionsMenu();
                }

                @Override
                public void onError(String utteranceId, int errorCode) {
                    abandonAudioFocus(null);
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
        return !this.listenerAudio.isExoPlayerPlayWhenReady() && this.listenerAudio.getExoPlayerState() == Player.STATE_READY;
    }

    private boolean isAudioPlaying() {
        return this.listenerAudio.isExoPlayerPlayWhenReady() && this.listenerAudio.getExoPlayerState() == Player.STATE_READY;
    }

    private boolean isBottomSheetCollapsed() {
        return this.bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED;
    }

    private boolean isBottomSheetCollapsedOrHidden() {
        int state = this.bottomSheetBehavior.getState();
        return state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_HIDDEN;
    }

    private boolean isBottomSheetHidden() {
        return this.bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN;
    }

    private boolean isBottomVideoPlaying() {
        return this.listenerBottom.isExoPlayerPlayWhenReady() && this.listenerBottom.getExoPlayerState() == Player.STATE_READY;
    }

    private boolean isTopVideoPlaying() {
        return this.listenerTop.isExoPlayerPlayWhenReady() && this.listenerTop.getExoPlayerState() == Player.STATE_READY;
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
                    Util.makeSnackbar(this, !TextUtils.isEmpty(result.msg) ? getString(R.string.error_download_failed, result.msg) : getString(R.string.error_download_failed2), Snackbar.LENGTH_SHORT).show();
                    return;
                }
                JsonReader reader = null;
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    reader = new JsonReader(new InputStreamReader(new FileInputStream(result.file), StandardCharsets.UTF_8));
                    @News.Flag int flags = 0;
                    boolean htmlEmbed = prefs.getBoolean(App.PREF_SHOW_EMBEDDED_HTML_LINKS, App.PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT);
                    if (htmlEmbed) flags |= News.FLAG_INCLUDE_HTMLEMBED;
                    News news = News.parseNews(reader, false, flags);
                    Util.close(reader);
                    reader = null;
                    if (prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT)) {
                        News.correct(news);
                    }
                    Intent intent = new Intent(this, NewsActivity.class);
                    intent.putExtra(NewsActivity.EXTRA_NEWS, news);
                    intent.putExtra(NewsActivity.EXTRA_JSON, result.file.getAbsolutePath());
                    // prevent going "back" to MainActivity because the preceding activity is <this>, not a MainActivity
                    intent.putExtra(NewsActivity.EXTRA_NO_HOME_AS_UP, true);
                    startActivity(intent);
                    overridePendingTransition(R.anim.grow_from_bottom, R.anim.fadeout);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "While parsing \"" + url + "\": " + e);
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
        if (this.listenerBottom.getExoPlayerState() != Player.STATE_READY) return;
        if (this.listenerBottom.isExoPlayerPlayWhenReady()) {
            stopBottomVideo();
            // show navigation bar to allow user to press the back button
            Util.goFullScreen(this);
        } else {
            playBottomVideo();
            Util.hideActionNavigationStatusBar(this, true);
        }
    }

    /** {@inheritDoc} */
    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // to have images occupy all the available space we apply the News again
        applyNews();
    }

    /** {@inheritDoc} */
    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(21)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setVolumeControlStream(App.STREAM_TYPE);

        boolean mobile = Util.isNetworkMobile(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.loadVideo = !mobile || prefs.getBoolean(App.PREF_LOAD_VIDEOS_OVER_MOBILE, App.DEFAULT_LOAD_VIDEOS_OVER_MOBILE);

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
        // determine number of Related columns; one every 2 inches, but not less than 2
        this.recyclerViewRelated.setLayoutManager(new GridLayoutManager(this, Math.max(2, (int)(Util.getDisplayDim(this).x / 2f))));
        this.recyclerViewRelated.setAdapter(new RelatedAdapter(this));
        // fling actions to recyclerViewRelated will be dispatched to its grandparent ScrollView (without this, fling actions on recyclerViewRelated would stutter…)
        this.recyclerViewRelated.setOnFlingListener(new RecyclerView.OnFlingListener() {
            public boolean onFling(int velocityX, int velocityY) {
                NewsActivity.this.recyclerViewRelated.dispatchNestedFling(velocityX, velocityY, false);
                return true;
            }
        });
        LinearLayout bottomVideoBlock = findViewById(R.id.bottomVideoBlock);
        this.textViewBottomVideoPeek = findViewById(R.id.textViewBottomVideoPeek);
        this.bottomVideoView = findViewById(R.id.bottomVideoView);
        this.textViewBottomVideoViewOverlay = findViewById(R.id.textViewBottomVideoViewOverlay);
        this.buttonAudio.setOnClickListener(this::playAudio);
        this.buttonAudio.setOnLongClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof String)) return false;
            Util.sendBinaryData(v.getContext(), (String) tag, v.getContentDescription());
            return true;
        });
        // click on the bottom video title text to expand the bottom sheet
        this.textViewBottomVideoPeek.setOnClickListener(this::expandBottomSheet);
        // long click on the bottom video title text switches between TruncateAt.END and TruncateAt.MARQUEE
        this.textViewBottomVideoPeek.setOnLongClickListener(v -> {
            if (!(v instanceof TextView)) return false;
            TextUtils.TruncateAt t = ((TextView)v).getEllipsize();
            ((TextView)v).setEllipsize(t == TextUtils.TruncateAt.MARQUEE ? TextUtils.TruncateAt.END : TextUtils.TruncateAt.MARQUEE);
            v.setSelected(true);
            return true;
        });
        // click the bottom video to pause/resume the video
        View bottomVideoViewWrapper = findViewById(R.id.bottomVideoViewWrapper);
        bottomVideoViewWrapper.setOnClickListener(this::onBottomVideoTapped);

        Typeface tf = Util.loadFont(this);
        if (tf != null) {
            this.textViewTitle.setTypeface(tf);
            this.textViewAudioTitle.setTypeface(tf);
            this.textViewContent.setTypeface(tf);
        }

        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomVideoBlock);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {

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
        this.json = intent.getStringExtra(EXTRA_JSON);
        if (ab != null && this.news != null) {
            String topline = this.news.getTopline();
            if (TextUtils.isEmpty(topline)) topline = this.news.getTitle();
            ab.setTitle(topline);
        }

        int notificationId = intent.getIntExtra(UpdateJobService.EXTRA_NOTIFICATION_ID, Integer.MIN_VALUE);
        if (notificationId != Integer.MIN_VALUE) {
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(notificationId);
        }

        if (!Util.TEST && this.news == null) {
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
    protected void onDestroy() {
        if (this.tts != null) {
            this.tts.shutdown();
            this.tts = null;
        }
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_share) {
            String url = this.news.getDetailsWeb();
            if (url == null) return true;
            Util.sendUrl(this, url, this.news.getTitle() != null ? this.news.getTitle() : this.news.getTopline());
            return true;
        }
        if (id == R.id.action_read) {
            startStopReading();
            return true;
        }
        if (id == R.id.action_print) {
            if (!PrintUtil.printNews(this, this.news, printJob -> {
                if (printJob == null) {
                    Snackbar.make(this.coordinatorLayout, R.string.msg_print_failed, Snackbar.LENGTH_SHORT).show();
                    return;
                }
                new PrintUtil.PrintJobWaiter(this, printJob).start();
            })) {
                Snackbar.make(this.coordinatorLayout, R.string.msg_print_failed, Snackbar.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_archive) {
            if (Archive.isArchived(this, this.news)) {
                Snackbar.make(this.coordinatorLayout, R.string.error_archived_already, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            if (this.json == null) {
                if (this.news.getDetails() == null) return true;
                File tmp;
                try {
                    tmp = File.createTempFile("json", this.news.isRegional() ? News.FILE_TAG_REGIONAL : News.FILE_TAG);
                    this.service.loadFile(this.news.getDetails(), tmp, (completed, result) -> {
                        if (!completed || result == null || result.file == null || result.rc > 299) {
                            Snackbar.make(this.coordinatorLayout, R.string.error_archived_failure, Snackbar.LENGTH_SHORT).show();
                            return;
                        }
                        boolean success = Archive.saveNews(this, this.news, result.file);
                        Snackbar.make(this.coordinatorLayout, success ? R.string.msg_archived_success : R.string.error_archived_failure, Snackbar.LENGTH_SHORT).show();
                    });
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                }
            } else {
                File jsonFile = new File(this.json);
                if (!jsonFile.isFile()) return true;
                boolean success = Archive.saveNews(this, this.news, jsonFile);
                Snackbar.make(this.coordinatorLayout, success ? R.string.msg_archived_success : R.string.error_archived_failure, Snackbar.LENGTH_SHORT).show();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPause() {
        abandonAudioFocus(null);
        if (this.exoPlayerTopVideo != null) {
            this.exoPlayerTopVideo.stop();
        }
        if (this.exoPlayerBottomVideo != null) {
            this.exoPlayerBottomVideo.stop();
        }
        if (this.exoPlayerAudio != null) {
            this.exoPlayerAudio.stop();
        }
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
        //
        MenuItem menuItemPrint = menu.findItem(R.id.action_print);
        menuItemPrint.setEnabled(PrintUtil.canPrint(this.news));
        MenuItem menuItemArchive = menu.findItem(R.id.action_archive);
        menuItemArchive.setVisible((this.json != null && new File(this.json).isFile()) || (this.news != null && this.news.getDetails() != null));
        // reading (aloud) is possible once tts has been initialised
        MenuItem menuItemRead = menu.findItem(R.id.action_read);
        if (this.tts != null && this.ttsInitialised) {
            menuItemRead.setEnabled(true);
            menuItemRead.setIcon(this.ttsSpeaking ? R.drawable.ic_hearing_ff0000_24dp : R.drawable.ic_hearing_content_24dp);
            menuItemRead.setTitle(this.ttsSpeaking ? R.string.action_read_stop : R.string.action_read);
            if (this.ttsSpeaking) {
                this.fab.setImageResource(R.drawable.ic_hearing_ff0000_24dp);
                this.fab.setOnClickListener(v -> {
                    startStopReading();
                    v.setOnClickListener(null);
                    NewsActivity.this.fab.hide();
                });
                this.fab.setOnLongClickListener(v -> {
                    new PopupManager().showPopup(v, getString(R.string.action_read_stop), 2_000L);
                    v.setOnLongClickListener(null);
                    return true;
                });
                this.fab.show();
            } else {
                this.fab.hide();
            }
        } else {
            menuItemRead.setEnabled(false);
            menuItemRead.setIcon(R.drawable.ic_hearing_content_24dp);
            menuItemRead.setTitle(R.string.action_read);
            this.fab.hide();
        }
        // hide menu completely if no item is enabled
        return Util.isAnyMenuItemEnabled(menu);
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
        // if the type is video, launch a VideoActivity instead of a NewsActivity
        if ("video".equals(related.getType())) {
            if (Util.isNetworkMobile(this)) {
                boolean loadVideos = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_LOAD_VIDEOS_OVER_MOBILE, App.DEFAULT_LOAD_VIDEOS_OVER_MOBILE);
                if (!loadVideos) {
                    Snackbar.make(this.coordinatorLayout, R.string.pref_title_pref_load_videos_over_mobile_off, Snackbar.LENGTH_SHORT).show();
                    return;
                }
            }
            File tempFile = new File(getCacheDir(), "temp.json");
            this.service.loadFile(url, tempFile, (completed, result) -> {
                if (!completed || result == null) {
                    Util.deleteFile(tempFile);
                    return;
                }
                if (result.rc >= 400) {
                    Snackbar.make(this.coordinatorLayout, getString(R.string.error_download_failed, result.toString()), Snackbar.LENGTH_LONG).show();
                    Util.deleteFile(tempFile);
                    return;
                }
                JsonReader reader = null;
                try {
                    reader = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(tempFile)), StandardCharsets.UTF_8));
                    reader.setLenient(true);
                    @News.Flag int flags = 0;
                    boolean htmlEmbed = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_SHOW_EMBEDDED_HTML_LINKS, App.PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT);
                    if (htmlEmbed) flags |= News.FLAG_INCLUDE_HTMLEMBED;
                    News parsed = News.parseNews(reader, this.news.isRegional(), flags);
                    Util.close(reader);
                    reader = null;
                    // it is probably not necessary here to call News.correct()
                    Intent intent = new Intent(this, VideoActivity.class);
                    intent.putExtra(VideoActivity.EXTRA_NEWS, parsed);
                    startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fadein, R.anim.fadeout).toBundle());
                } catch (Exception e) {
                    Util.close(reader);
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                    Snackbar.make(this.coordinatorLayout, R.string.error_parsing, Snackbar.LENGTH_LONG).show();
                } finally {
                    Util.deleteFile(tempFile);
                }
            });
        } else {
            newNewsActivity(url);
        }
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
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;

        if (isAudioPlaying()) {
            // audio has been playing -> pause
            this.exoPlayerAudio.setPlayWhenReady(false);
            abandonAudioFocus(am);
            return;
        }
        if (isAudioPaused()) {
            // audio has been paused -> resume
            requestAudioFocus(am);
            this.exoPlayerAudio.setPlayWhenReady(true);
            return;
        }
        // audio has neither been playing or paused
        Object src = this.buttonAudio.getTag();
        if (!(src instanceof String)) return;
        if (!Util.isNetworkAvailable(this)) {
            showNoNetworkSnackbar();
            return;
        }
        int requestResult = requestAudioFocus(am);
        if (requestResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Audio focus request denied.");
            Snackbar.make(this.coordinatorLayout, R.string.error_audio_focus_denied, Snackbar.LENGTH_SHORT).show();
            return;
        }
        am.setMode(AudioManager.MODE_NORMAL);
        MediaSource msAudio = this.mediaSourceHelper.buildMediaSource((Call.Factory) ((App)getApplicationContext()).getOkHttpClient(), Uri.parse((String) src));
        this.exoPlayerAudio.setMediaSource(msAudio, true);
        this.exoPlayerAudio.prepare();
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
        requestAudioFocus(null);
        this.exoPlayerBottomVideo.setPlayWhenReady(true);
    }

    /**
     * Plays the top (a.k.a. news) video and stops the bottom video.
     */
    private void playTopVideo() {
        if (this.exoPlayerTopVideo == null) return;
        if (this.exoPlayerBottomVideo != null) {
            if (isBottomVideoPlaying()) abandonAudioFocus(null);
            this.exoPlayerBottomVideo.setPlayWhenReady(false);
        }
        this.exoPlayerTopVideo.setPlayWhenReady(true);
    }

    /**
     * Releases the ExoPlayers.
     */
    private void releasePlayers() {
        if (this.exoPlayerTopVideo != null) {
            this.exoPlayerTopVideo.removeListener(this.listenerTop);
            this.exoPlayerTopVideo.release();
            this.exoPlayerTopVideo = null;
        }
        if (this.exoPlayerBottomVideo != null) {
            this.exoPlayerBottomVideo.removeListener(this.listenerBottom);
            this.exoPlayerBottomVideo.release();
            this.exoPlayerBottomVideo = null;
        }
        if (this.exoPlayerAudio != null) {
            this.exoPlayerAudio.removeListener(this.listenerAudio);
            this.exoPlayerAudio.release();
            this.exoPlayerAudio = null;
        }
    }

    private int requestAudioFocus(@Nullable AudioManager am) {
        int requestResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        if (am == null) am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return requestResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (this.afr == null) this.afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(this.aa).setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(this).build();
            requestResult = am.requestAudioFocus(this.afr);
        } else {
            requestResult = am.requestAudioFocus(this, App.STREAM_TYPE, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
        return requestResult;
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
            abandonAudioFocus(null);
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
        final int audioRequestResult = requestAudioFocus(null);
        for (String partToSpeak : toSpeak) {
            int result = this.tts.speak(partToSpeak, counter == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, extras, this.news.getTitle() + '_' + counter);
            if (result != TextToSpeech.SUCCESS) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Tts failed with error " + result);
                @StringRes int msg;
                switch (result) {
                    case TextToSpeech.ERROR_NETWORK:
                    case TextToSpeech.ERROR_NETWORK_TIMEOUT: msg = R.string.error_tts_fail_network; break;
                    case TextToSpeech.ERROR_NOT_INSTALLED_YET: msg = R.string.error_tts_fail_not_downloaded; break;
                    case TextToSpeech.ERROR_OUTPUT: msg = R.string.error_tts_fail_output; break;
                    case TextToSpeech.ERROR_SERVICE: msg = R.string.error_tts_fail_service; break;
                    case TextToSpeech.ERROR_SYNTHESIS: msg = R.string.error_tts_fail_synthesis; break;
                    default: msg = R.string.error_tts_fail;
                }
                Snackbar.make(this.coordinatorLayout, msg, Snackbar.LENGTH_LONG).show();
                if (audioRequestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) abandonAudioFocus(null);
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
        float vol = this.exoPlayerTopVideo.getVolume();
        if (vol <= 0.01f) {
            this.exoPlayerTopVideo.setVolume(1f);
        } else {
            this.exoPlayerTopVideo.setVolume(0f);
        }
    }

    /**
     * Picasso {@link Target} that can be used to replace an {@link ImageSpan} which is embedded within a {@link Spannable}.
     */
    private static class SpannableImageTarget implements Target {

        private final Reference<NewsActivity> refActivity;
        private final String source;
        @IntRange(from = 1, to = 100)
        private final int percentWidth;
        private Spannable spannable;
        private ImageSpan toReplace;

        /**
         * Constructor.
         * @param activity NewsActivity
         * @param spannable Spannable
         * @param toReplace ImageSpan
         * @param percentWidth percentage of the available width
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
            this.spannable.removeSpan(this.toReplace);
            NewsActivity activity = this.refActivity.get();
            if (activity != null) {
                activity.spannableImageTargets.remove(this);
                if (activity.spannableImageTargets.isEmpty()) {
                    activity.enableTextSelection(null);
                }
            }
            this.spannable = null;
            this.toReplace = null;
        }

        /** {@inheritDoc} */
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            NewsActivity activity = this.refActivity.get();
            if (activity == null) return;

            int availableWidth = activity.textViewContent.getWidth() - activity.textViewContent.getPaddingStart() - activity.textViewContent.getPaddingEnd();
            if (this.percentWidth > 0 && this.percentWidth < 100) availableWidth = Math.round(availableWidth * this.percentWidth / 100f);
            if (Math.abs(availableWidth - bitmap.getWidth()) > 20) { // let's tolerate +/- 20px
                float factor = (float) bitmap.getHeight() / (float) bitmap.getWidth();
                bitmap = Bitmap.createScaledBitmap(bitmap, availableWidth, Math.round(availableWidth * factor), false);
            }

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
            if (activity.spannableImageTargets.isEmpty()) {
                activity.enableTextSelection(null);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    }

    /**
     * URLSpan that displays a {@link Intent#createChooser(Intent, CharSequence) chooser} upon click.
     */
    private static class URLSpanWChooser extends URLSpan {
        @ColorInt
        private final int linkColor;
        @Nullable private final String mime;

        /**
         * Constructor.
         * @param url URL
         * @param linkColor link color
         * @throws NullPointerException if {@code url} is {@code null}
         */
        private URLSpanWChooser(@NonNull String url, @IntRange(from = 1) @ColorInt int linkColor) {
            this(url, linkColor, null);
        }

        /**
         * Constructor.
         * @param url URL
         * @param linkColor link color
         * @param mime MIME type
         * @throws NullPointerException if {@code url} is {@code null}
         */
        private URLSpanWChooser(@NonNull String url, @IntRange(from = 1) @ColorInt int linkColor, @Nullable String mime) {
            super(Util.makeHttps(url));
            this.linkColor = linkColor;
            this.mime = mime;
        }

        /** {@inheritDoc} */
        @Override
        public void onClick(@NonNull View widget) {
            Uri uri = Uri.parse(getURL());
            String mime = this.mime != null ? this.mime : Util.getMime(uri.getLastPathSegment(), null);
            Context ctx = widget.getContext();
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
            }
            if (ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                if (ctx instanceof Activity) Util.makeSnackbar((Activity)ctx, R.string.error_no_app, Snackbar.LENGTH_SHORT).show();
                else Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent chooserIntent = Intent.createChooser(intent, null);
            try {
                ctx.startActivity(chooserIntent);
            } catch (ActivityNotFoundException e) {
                if (BuildConfig.DEBUG) Log.w(getClass().getSimpleName(), "Actvity was not found for intent " + intent);
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
     * A replacement for {@link BackgroundColorSpan} which was created by {@link android.text.Html#fromHtml(String, int) Html#fromHtml()}
     * but does not colorise the whole line (just the text).
     */
    private static class BackgroundSpan implements LineBackgroundSpan {

        @ColorInt private final int color;
        private final Rect r = new Rect();
        private int recentLine = Integer.MIN_VALUE;

        /**
         * Constructor.
         * @param color background color
         */
        private BackgroundSpan(@ColorInt int color) {
            super();
            this.color = color;
        }

        /** {@inheritDoc} */
        @Override
        public void drawBackground(@NonNull Canvas canvas, @NonNull Paint paint, int left, int right, int top, int baseline, int bottom, @NonNull CharSequence text, int start, int end, int lineNumber) {
            final int originalColor = paint.getColor();
            paint.setColor(this.color);
            // apparently it is not possible to extend the background to the left or right because the canvas is clipped
            if (Math.abs(lineNumber - this.recentLine) > 1) {
                // for the 1st line, extend the background slightly to the top
                paint.getTextBounds("X", 0, 1, r);
                canvas.drawRect(left, top - r.height(), right, bottom, paint);
            } else {
                canvas.drawRect(left, top, right, bottom, paint);
            }
            paint.setColor(originalColor);
            this.recentLine = lineNumber;
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
