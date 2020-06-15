package de.freehamburger.util;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import java.util.List;

import androidx.annotation.NonNull;
import okhttp3.Call;

/**
 *
 */
public class MediaSourceHelper {

    /** "ExoPlayer" seems to be used by the 'other' app */
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
     * For HLS streams like http://akajunisd.akamaihd.net/i/aktuellekamera_3@66339/muster.m3u8.
     * @param cf Call.Factory (aka OkHttpClient)
     * @param uri Uri
     * @return MediaSource
     */
    @NonNull
    private MediaSource buildHlsMediaSource(@NonNull Call.Factory cf, @NonNull Uri uri) {
        if (this.hms == null) {
            // call of OkHttpDataSourceFactory constructor could be replaced by "new DefaultHttpDataSourceFactory(USER_AGENT);" if extension-okhttp were not used
            DataSource.Factory dsf = new OkHttpDataSourceFactory(cf, USER_AGENT);
            this.hms = new HlsMediaSource.Factory(dsf);
        }
        return this.hms.createMediaSource(uri);
    }

    /**
     * Creates a MediaSource for one given Uri.
     * @param cf Call.Factory (aka OkHttpClient)
     * @param uri Uri
     * @return MediaSource
     */
    @NonNull
    public MediaSource buildMediaSource(@NonNull Call.Factory cf, @NonNull Uri uri) {
        int contentType = com.google.android.exoplayer2.util.Util.inferContentType(uri);
        if (contentType == C.TYPE_HLS) return buildHlsMediaSource(cf, uri);
        if (this.ems == null) {
            // call of OkHttpDataSourceFactory constructor could be replaced by "new DefaultHttpDataSourceFactory(USER_AGENT);" if extension-okhttp were not used
            DataSource.Factory dsf = new OkHttpDataSourceFactory(cf, USER_AGENT);
            this.ems = new ExtractorMediaSource.Factory(dsf);
            this.ems.setExtractorsFactory(new Mp34ExtractorsFactory());
        }
        return this.ems.createMediaSource(uri);
    }

    /**
     * Creates a MediaSource for more than one Uri.
     * @param cf Call.Factory (aka OkHttpClient)
     * @param uris List of Uris (must not contain {@code null} elements)
     * @return MediaSource
     */
    @NonNull
    public MediaSource buildMediaSource(@NonNull Call.Factory cf, @NonNull final List<Uri> uris) {
        if (uris.size() == 1) {
            return buildMediaSource(cf, uris.get(0));
        }
        final MediaSource[] array = new MediaSource[uris.size()];
        int i = 0;
        for (Uri uri : uris) {
            array[i++] = buildMediaSource(cf, uri);
        }
        return new ConcatenatingMediaSource(array);
    }
}
