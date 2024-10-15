package de.freehamburger.exo;

import androidx.annotation.NonNull;

import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.extractor.mp4.Mp4Extractor;

/**
 * See <a href="https://google.github.io/ExoPlayer/shrinking.html">here</a>.
 */
public class Mp34ExtractorsFactory implements ExtractorsFactory {

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Extractor[] createExtractors() {
        return new Extractor[] {new Mp4Extractor(), new Mp3Extractor()};
    }
}