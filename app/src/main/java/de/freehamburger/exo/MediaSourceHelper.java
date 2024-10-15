package de.freehamburger.exo;

import android.net.Uri;

import androidx.annotation.NonNull;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.datasource.DataSource;

import java.util.List;

import okhttp3.Call;

/**
 *
 */
public class MediaSourceHelper {

    /** "ExoPlayer" seems to be used by the 'other' app */
    private static final String USER_AGENT = "ExoPlayer";

    private ProgressiveMediaSource.Factory pms;
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
            // call of OkHttpDataSource.Factory constructor could be replaced by "new DefaultHttpDataSourceFactory(USER_AGENT);" if extension-okhttp were not used
            DataSource.Factory dsf = new OkHttpDataSource.Factory(cf).setUserAgent(USER_AGENT);
            this.hms = new HlsMediaSource.Factory(dsf);
        }
        return this.hms.createMediaSource(new MediaItem.Builder().setUri(uri).build());
    }

    /**
     * Creates a MediaSource for one given Uri.
     * @param cf Call.Factory (aka OkHttpClient)
     * @param uri Uri
     * @return MediaSource
     */
    @NonNull
    public MediaSource buildMediaSource(@NonNull Call.Factory cf, @NonNull Uri uri) {
        int contentType = androidx.media3.common.util.Util.inferContentType(uri);
        if (contentType == C.CONTENT_TYPE_HLS) return buildHlsMediaSource(cf, uri);
        if (this.pms == null) {
            // call of OkHttpDataSourceFactory constructor could be replaced by "new DefaultHttpDataSourceFactory(USER_AGENT);" if extension-okhttp were not used
            DataSource.Factory dsf = new OkHttpDataSource.Factory(cf).setUserAgent(USER_AGENT);
            this.pms = new ProgressiveMediaSource.Factory(dsf, new Mp34ExtractorsFactory());
        }
        return this.pms.createMediaSource(new MediaItem.Builder().setUri(uri).build());
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
