package de.freehamburger.util;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import de.freehamburger.BuildConfig;
import de.freehamburger.R;

/**
 * Implementation of ExoPlayer's Player.Listener that wraps the player state and the play-when-ready flag.
 * Also displays playback errors if desired.
 */
public class PlayerListener implements Player.Listener {

    private static final String TAG = "PlayerListener";
    private final Reference<Activity> refActivity;
    private final boolean showErrors;
    protected int exoPlayerState = 0; // 0 is not a defined Player.State so this means undefined
    protected boolean exoPlayerPlayWhenReady = false;

    /**
     * Returns a String representation of a Player's playback state.
     * For debug only.
     * @param playbackState com.google.android.exoplayer2.Player.State
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
     * Returns a String representation of a Player's change of playWhenReady reason.
     * For debug only.
     * @param reason com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
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
     * Constructor.
     *
     * @param activity   Activity this listener is used in
     * @param showErrors true to display playback errors, false to suppress them
     */
    public PlayerListener(@NonNull Activity activity, boolean showErrors) {
        super();
        this.refActivity = new WeakReference<>(activity);
        this.showErrors = showErrors;
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
        if (BuildConfig.DEBUG) Log.i(TAG, "onPlayWhenReadyChanged(" + playWhenReady + ", " + playWhenReadyReason(reason) + ")");
        this.exoPlayerPlayWhenReady = playWhenReady;
        onPlayerStateOrOnPlayWhenReadyChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onPlaybackStateChanged(@Player.State int playbackState) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onPlaybackStateChanged(" + playbackState(playbackState) + ")");
        this.exoPlayerState = playbackState;
        onPlayerStateOrOnPlayWhenReadyChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPlayerError(@NonNull PlaybackException error) {
        if (BuildConfig.DEBUG) Log.e(TAG, "onPlayerError(" + error + ")");
        if (!this.showErrors) return;
        Activity activity = this.refActivity.get();
        if (activity == null) return;
        String msg = Util.playbackExceptionMsg(activity, error);
        View coordinatorLayout = activity.findViewById(R.id.coordinator_layout);
        if (coordinatorLayout != null) {
            Snackbar sb = Snackbar.make(coordinatorLayout, msg, Snackbar.LENGTH_LONG);
            Util.setSnackbarFont(sb, Util.CONDENSED, 14f);
            sb.show();
        } else {
            Toast.makeText(activity.getApplicationContext(), msg, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Either {@link #exoPlayerState} or {@link #exoPlayerPlayWhenReady} have changed.
     * Default implementation does not do anything.
     */
    public void onPlayerStateOrOnPlayWhenReadyChanged() {
    }
}
