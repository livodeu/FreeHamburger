package de.freehamburger.util;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

import java.util.List;

/**
 *
 */
public class MediaSourceHelper {

    /** "ExoPlayer" seems to be used by the original app, at least for contacts to hls.tagesschau.de */
    private static final String USER_AGENT = "ExoPlayer";

    private ExtractorMediaSource.Factory ems;
    private HlsMediaSource.Factory hms;

    /**
     * Constructor.
     */
    public MediaSourceHelper() {
        super();
    }

    /**
     * For HLS streams like http://tagesschau-lh.akamaihd.net/i/tagesschau_3@66339/master.m3u8.
     * @param uri Uri
     * @return MediaSource
     */
    @NonNull
    private MediaSource buildHlsMediaSource(@NonNull Uri uri) {
        if (this.hms == null) {
            DataSource.Factory dsf = new DefaultHttpDataSourceFactory(USER_AGENT);
            this.hms = new HlsMediaSource.Factory(dsf);
        }
        return this.hms.createMediaSource(uri);
    }

    /**
     * Creates a MediaSource for one given Uri.
     * @param uri Uri
     * @return MediaSource
     */
    @NonNull
    public MediaSource buildMediaSource(@NonNull Uri uri) {
        int contentType = com.google.android.exoplayer2.util.Util.inferContentType(uri);
        if (contentType == C.TYPE_HLS) return buildHlsMediaSource(uri);
        if (this.ems == null) {
            DataSource.Factory dsf = new DefaultHttpDataSourceFactory(USER_AGENT);
            this.ems = new ExtractorMediaSource.Factory(dsf);
            this.ems.setExtractorsFactory(new Mp34ExtractorsFactory());
        }
        return this.ems.createMediaSource(uri);
    }

    /**
     * Creates a MediaSource for more than one Uri.
     * @param uris List of Uris (must not contain {@code null} elements)
     * @return MediaSource
     */
    @NonNull
    public MediaSource buildMediaSource(@NonNull final List<Uri> uris) {
        if (uris.size() == 1) {
            return buildMediaSource(uris.get(0));
        }
        final MediaSource[] array = new MediaSource[uris.size()];
        int i = 0;
        for (Uri uri : uris) {
            array[i++] = buildMediaSource(uri);
        }
        return new ConcatenatingMediaSource(array);
    }

}
