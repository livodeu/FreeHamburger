package de.freehamburger.model;

import android.content.Context;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import java.util.Arrays;
import java.util.Map;

import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 *
 */
public enum StreamQuality {
    /** 320 x 180 */
    H264S(320),
    /** 480 x 270 */
    PODCASTVIDEOM(480),
    /** 512 x 288 */
    H264M(512),
    /** 960 x 540 */
    H264L(960),
    /** 1280 x 720 */
    H264XL(1280),
    /** unknown dimensions */
    PODCASTVIDEOM_IAS(-1),
    /** unknown dimensions */
    ADAPTIVESTREAMING(-1);

    /** StreamQuality array where XL is preferred; after that ordered by descending quality */
    private static final StreamQuality[] PREF_XL = new StreamQuality[] {StreamQuality.H264XL, StreamQuality.H264L, StreamQuality.H264M, StreamQuality.H264S};
    /** StreamQuality array where L is preferred */
    private static final StreamQuality[] PREF_L = new StreamQuality[] {StreamQuality.H264L, StreamQuality.H264XL, StreamQuality.H264M, StreamQuality.H264S};
    /** StreamQuality array where M is preferred */
    private static final StreamQuality[] PREF_M = new StreamQuality[] {StreamQuality.H264M, StreamQuality.H264L, StreamQuality.H264XL, StreamQuality.H264S};
    /** StreamQuality array where S is preferred; after that ordered by ascending quality */
    private static final StreamQuality[] PREF_S = new StreamQuality[] {StreamQuality.H264S, StreamQuality.H264M, StreamQuality.H264L, StreamQuality.H264XL};
    /** video width */
    private final int width;

    /**
     * Attempts to return a video stream url, based on the given Map.<br>
     * If the network connection is a mobile connection, one quality level lower will be picked.
     * @param ctx Context (allows picking a quality based on screen size and network connection)
     * @param streams Map
     * @return video stream url
     */
    @Nullable
    public static String getStreamsUrl(@Nullable Context ctx, @Nullable final Map<StreamQuality, String> streams) {
        if (streams == null || streams.isEmpty()) return null;
        final StreamQuality[] pref;
        if (ctx != null) {
            final boolean mobile = Util.isNetworkMobile(ctx);
            final int screenWidth = Util.getDisplaySize(ctx).x;
            // if network is mobile, pick one quality level below
            if (screenWidth >= StreamQuality.H264XL.getWidth()) pref = mobile ? PREF_L : PREF_XL;
            else if (screenWidth >= StreamQuality.H264L.getWidth()) pref = mobile ? PREF_M : PREF_L;
            else if (screenWidth >= StreamQuality.H264M.getWidth()) pref = mobile ? PREF_S : PREF_M;
            else pref = PREF_S;
            if (BuildConfig.DEBUG) Log.i(StreamQuality.class.getSimpleName(), "We'd like stream quality preference " + Arrays.toString(pref) + " because screen width is " + screenWidth + " px, mobile network: " + mobile);
        } else {
            pref = PREF_S;
        }
        for (StreamQuality sq : pref) {
            if (streams.containsKey(sq)) {
                return streams.get(sq);
            }
        }
        // sometimes (live programs) there is only "adaptivestreaming"
        String adaptive = streams.get(StreamQuality.ADAPTIVESTREAMING);
        if (adaptive != null) return adaptive;
        // anything?!
        if (!streams.isEmpty()) {
            return streams.values().iterator().next();
        }
        // apparently not
        return null;
    }

    StreamQuality(int width) {
        this.width = width;
    }

    /**
     * Returns the width of the video stream.
     * @return width of the video stream or -1, if not known (which is the case for {@link #ADAPTIVESTREAMING})
     */
    @IntRange(from = -1)
    private int getWidth() {
        return width;
    }
}
