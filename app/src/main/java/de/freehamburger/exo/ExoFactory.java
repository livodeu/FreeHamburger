package de.freehamburger.exo;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.source.MediaSource;

import org.jetbrains.annotations.TestOnly;

import java.util.List;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * The place where ExoPlayer instances are assembled.
 */
public final class ExoFactory {

    private static final NirvanaAnalyticsCollector NAC = new NirvanaAnalyticsCollector();
    private static final String TAG = "ExoFactory";
    private static volatile boolean initialised = false;

    private ExoFactory() {
    }

    @TestOnly
    @VisibleForTesting
    public static boolean isInitialised() {
        if (!Util.TEST) throw new RuntimeException("isInitialised() called from non-test code!");
        return initialised;
    }

    /**
     * Builds an ExoPlayer instance.
     * @param ctx Context
     * @return ExoPlayer
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static ExoPlayer makeExoPlayer(@NonNull Context ctx) {
        long t = System.currentTimeMillis();
        final ExoSupply exoSupply = ((App)ctx.getApplicationContext()).getExoSupply();
        ExoPlayer exoPlayer = new ExoPlayer.Builder(ctx, exoSupply.getRenderersFactory(), exoSupply.getMediaSourceFactory())
                .setTrackSelector(exoSupply.getTrackSelector())
                .setAnalyticsCollector(NAC)
                .setLoadControl(exoSupply.getLoadControl())
                .setUsePlatformDiagnostics(false)
                .setUseLazyPreparation(true)
                .build();
        if (BuildConfig.DEBUG) {
            t = System.currentTimeMillis() - t;
            if (t > 40L) Log.w(TAG, "Building ExoPlayer took " + t + " ms");
            initialised = true;
        }
        return exoPlayer;
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
        public void onAudioTrackInitialized(@NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
        }

        @Override
        public void onAudioTrackReleased(@NonNull AudioSink.AudioTrackConfig audioTrackConfig) {
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
