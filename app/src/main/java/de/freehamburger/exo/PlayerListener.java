package de.freehamburger.exo;

import android.app.Activity;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import de.freehamburger.util.Util;

/**
 * Implementation of ExoPlayer's Player.Listener that wraps the player state and the play-when-ready flag.
 * Also displays playback errors if desired.
 */
public class PlayerListener implements Player.Listener {

    private static final String TAG = "PlayerListener";
    private final Reference<Activity> refActivity;
    /**  true to display playback errors, false to suppress them */
    private final boolean showErrors;
    /** true to finish the Activity after errors have been acknowledged by the user (only applies if {@link #showErrors} is true) */
    private final boolean finishAfterShow;
    protected int exoPlayerState = 0; // 0 is not a defined Player.State so this means undefined
    protected boolean exoPlayerPlayWhenReady = false;

    protected String currentResource;

    /**
     * Constructor.
     * @param activity   Activity this listener is used in
     * @param showErrors true to display playback errors, false to suppress them
     */
    public PlayerListener(@NonNull Activity activity, boolean showErrors) {
        this(activity, showErrors, showErrors);
    }

    /**
     * Constructor.
     * @param activity   Activity this listener is used in
     * @param showErrors true to display playback errors, false to suppress them
     * @param finishAfterShow true to finish the Activity after errors have been acknowledged by the user (only applies if showErrors is true)
     */
    private PlayerListener(@NonNull Activity activity, boolean showErrors, boolean finishAfterShow) {
        super();
        this.refActivity = new WeakReference<>(activity);
        this.showErrors = showErrors;
        this.finishAfterShow = finishAfterShow;
    }

    /**
     * Returns a String representation of a Player's change of playWhenReady reason.
     * For debug only.
     * @param reason androidx.media3.common.Player.PlayWhenReadyChangeReason
     * @return String representation
     */
    @NonNull
    private static String playWhenReadyReason(@Player.PlayWhenReadyChangeReason int reason) {
        switch (reason) {
            case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY: return "Audio becoming noisy";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS: return "Audio focus loss";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM: return "End of media item";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE: return "Remote";
            case Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST: return "User request";
        }
        return String.valueOf(reason);
    }

    /**
     * Returns a String representation of a Player's playback state.
     * For debug only.
     * @param playbackState androidx.media3.common.Player.State
     * @return String representation
     */
    @NonNull
    private static String playbackState(@Player.State int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING: return "buffering";
            case Player.STATE_ENDED: return "ended";
            case Player.STATE_IDLE: return "idle";
            case Player.STATE_READY: return "ready";
        }
        return String.valueOf(playbackState);
    }

    /**
     * Returns the player state
     * @return player state or 0
     */
    public final int getExoPlayerState() {
        return this.exoPlayerState;
    }

    /**
     * Returns the playWhenReady flag
     * @return true / false
     */
    public final boolean isExoPlayerPlayWhenReady() {
        return this.exoPlayerPlayWhenReady;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onPlayWhenReadyChanged(boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
        this.exoPlayerPlayWhenReady = playWhenReady;
        onPlayerStateOrOnPlayWhenReadyChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onPlaybackStateChanged(@Player.State int playbackState) {
        this.exoPlayerState = playbackState;
        onPlayerStateOrOnPlayWhenReadyChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPlayerError(@NonNull PlaybackException error) {
        if (!this.showErrors) return;
        final Activity activity = this.refActivity.get();
        if (activity == null || activity.isDestroyed()) return;
        String msg = Util.playbackExceptionMsg(activity, error);
        Snackbar sb = Util.makeSnackbar(activity, msg, Snackbar.LENGTH_INDEFINITE);
        Util.setSnackbarFont(sb, Util.CONDENSED, 14f);
        sb.setAction(android.R.string.ok, (v) -> {sb.dismiss(); if (this.finishAfterShow) activity.finish();});
        sb.show();
    }

    /**
     * Either {@link #exoPlayerState} or {@link #exoPlayerPlayWhenReady} have changed.
     * Default implementation does not do anything.
     */
    public void onPlayerStateOrOnPlayWhenReadyChanged() {
    }

    /**
     * Sets the current resource.
     * This can, for example, be used for reporting playback failures.
     * @param currentResource current resource
     */
    public void setCurrentResource(@Nullable String currentResource) {
        this.currentResource = currentResource != null ? currentResource.trim() : null;
    }
}
