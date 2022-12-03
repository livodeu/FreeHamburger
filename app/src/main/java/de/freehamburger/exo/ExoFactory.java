package de.freehamburger.exo;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;

import java.util.List;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;

/**
 * The place where ExoPlayer instances are assembled.
 */
public final class ExoFactory {

    private static final String TAG = "ExoFactory";

    /**
     * Builds an ExoPlayer instance.
     * @param ctx Context
     * @param ts TrackSelector (optional)
     * @return ExoPlayer
     */
    @NonNull
    public static ExoPlayer makeExoPlayer(@NonNull Context ctx, @Nullable TrackSelector ts) {
        return new ExoPlayer.Builder(ctx)
                .setTrackSelector(ts != null ? ts : new DefaultTrackSelector(ctx))
                .setAnalyticsCollector(new NirvanaAnalyticsCollector())
                .setUsePlatformDiagnostics(false)
                .setUseLazyPreparation(true)
                .build();
    }

    @VisibleForTesting
    public static class NirvanaAnalyticsCollector implements AnalyticsCollector {

        @Override public void addListener(@NonNull AnalyticsListener listener) {
        }

        @Override public void notifySeekStarted() {
        }

        @Override public void onAudioCodecError(@NonNull Exception audioCodecError) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onAudioCodecError(" + audioCodecError + ")");
        }

        @Override
        public void onAudioDecoderInitialized(@NonNull String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        }

        @Override public void onAudioDecoderReleased(@NonNull String decoderName) {
        }

        @Override public void onAudioDisabled(@NonNull DecoderCounters counters) {
        }

        @Override public void onAudioEnabled(@NonNull DecoderCounters counters) {
        }

        @Override
        public void onAudioInputFormatChanged(@NonNull Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        }

        @Override public void onAudioPositionAdvancing(long playoutStartSystemTimeMs) {

        }

        @Override public void onAudioSinkError(@NonNull Exception audioSinkError) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onAudioSinkError(" + audioSinkError + ")");
        }

        @Override
        public void onAudioUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        }

        @Override
        public void onBandwidthSample(int elapsedMs, long bytesTransferred, long bitrateEstimate) {
        }

        @Override public void onDroppedFrames(int count, long elapsedMs) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onDroppedFrames(" + count + ", " + elapsedMs + ")");
        }

        @Override public void onRenderedFirstFrame(@NonNull Object output, long renderTimeMs) {
        }

        @Override public void onVideoCodecError(@NonNull Exception videoCodecError) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onVideoCodecError(" + videoCodecError + ")");
        }

        @Override
        public void onVideoDecoderInitialized(@NonNull String decoderName, long initializedTimestampMs, long initializationDurationMs) {
        }

        @Override public void onVideoDecoderReleased(@NonNull String decoderName) {
        }

        @Override public void onVideoDisabled(@NonNull DecoderCounters counters) {
        }

        @Override public void onVideoEnabled(@NonNull DecoderCounters counters) {
        }

        @Override
        public void onVideoFrameProcessingOffset(long totalProcessingOffsetUs, int frameCount) {
        }

        @Override
        public void onVideoInputFormatChanged(@NonNull Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
        }

        @Override public void release() {
        }

        @Override public void removeListener(@NonNull AnalyticsListener listener) {
        }

        @Override public void setPlayer(@NonNull Player player, @NonNull Looper looper) {
        }

        @Override
        public void updateMediaPeriodQueueInfo(@NonNull List<MediaSource.MediaPeriodId> queue, @Nullable MediaSource.MediaPeriodId readingPeriod) {
        }
    }

}
