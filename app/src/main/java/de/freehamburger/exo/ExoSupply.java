package de.freehamburger.exo;

import androidx.annotation.NonNull;

import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.TrackSelector;

/**
 * Supplies {@link androidx.media3.exoplayer.ExoPlayer.Builder ExoPlayer.Builders} with some stuff
 * that they all need.
 */
public interface ExoSupply {

    @NonNull LoadControl getLoadControl();

    @NonNull MediaSource.Factory getMediaSourceFactory();

    @NonNull RenderersFactory getRenderersFactory();

    @NonNull TrackSelector getTrackSelector();

    @NonNull ExtractorsFactory getExtractorsFactory();
}
