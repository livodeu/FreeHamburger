package de.freehamburger.util;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;

/**
 * See <a href="https://google.github.io/ExoPlayer/shrinking.html>here</a>.
 */
class Mp34ExtractorsFactory implements ExtractorsFactory {

    /** {@inheritDoc} */
    @Override
    public Extractor[] createExtractors() {
        return new Extractor[] {new Mp4Extractor(), new Mp3Extractor()};
    }
}