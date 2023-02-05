package de.freehamburger.exo;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelector;

/**
 * Supplies {@link com.google.android.exoplayer2.ExoPlayer.Builder ExoPlayer.Builders} with some stuff
 * that they all need.
 */
public interface ExoSupply {

    @NonNull LoadControl getLoadControl();

    @NonNull MediaSource.Factory getMediaSourceFactory();

    @NonNull RenderersFactory getRenderersFactory();

    @NonNull TrackSelector getTrackSelector();

    @NonNull ExtractorsFactory getExtractorsFactory();
}
