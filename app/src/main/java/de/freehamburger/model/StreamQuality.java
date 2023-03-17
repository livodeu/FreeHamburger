package de.freehamburger.model;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Map;

import de.freehamburger.R;
import de.freehamburger.util.Util;

/**
 */
public enum StreamQuality {
    /** 320 x 180 = 57600 */
    H264S(R.string.label_streamquality_s, 320),
    /** 480 x 270 = 129600 */
    PODCASTVIDEOM(R.string.label_streamquality_m, 480),
    /** 512 x 288 = 147456 */
    H264M(R.string.label_streamquality_m, 512),
    /** 960 x 540 = 518400 */
    H264L(R.string.label_streamquality_l, 960),
    /** 1280 x 720 = 921600 */
    H264XL(R.string.label_streamquality_xl, 1280),
    /** adaptive streaming with dimensions from 256x144 to 960x540 */
    PODCASTVIDEOM_IAS(R.string.label_streamquality_adaptive_small, -1),
    /** adaptive streaming with dimensions from 480x270 to 1920x1080 */
    ADAPTIVESTREAMING(R.string.label_streamquality_adaptive, -1);

    /** StreamQuality array where XL is preferred; after that ordered by descending quality */
    private static final StreamQuality[] PREF_XL = new StreamQuality[] {StreamQuality.H264XL, StreamQuality.H264L, StreamQuality.H264M, StreamQuality.H264S};
    /** StreamQuality array where L is preferred */
    private static final StreamQuality[] PREF_L = new StreamQuality[] {StreamQuality.H264L, StreamQuality.H264XL, StreamQuality.H264M, StreamQuality.H264S};
    /** StreamQuality array where L is preferred - after that smaller ones are preferred over larger ones*/
    private static final StreamQuality[] PREF_L_MOBILE = new StreamQuality[] {StreamQuality.H264L, StreamQuality.H264M, StreamQuality.H264S, StreamQuality.H264XL};
    /** StreamQuality array where M is preferred */
    private static final StreamQuality[] PREF_M = new StreamQuality[] {StreamQuality.H264M, StreamQuality.H264L, StreamQuality.H264XL, StreamQuality.H264S};
    /** StreamQuality array where M is preferred - after that smaller ones are preferred over larger ones */
    private static final StreamQuality[] PREF_M_MOBILE = new StreamQuality[] {StreamQuality.H264M, StreamQuality.H264S, StreamQuality.H264L, StreamQuality.H264XL};
    /** StreamQuality array where S is preferred; after that ordered by ascending quality */
    private static final StreamQuality[] PREF_S = new StreamQuality[] {StreamQuality.H264S, StreamQuality.H264M, StreamQuality.H264L, StreamQuality.H264XL};
    /** video width */
    private final int width;
    @StringRes private final int label;

    /**
     * Attempts to return a video stream url, based on the given Map.<br>
     * If the network connection is a mobile connection, one quality level lower will be picked.
     * @param ctx Context (allows picking a quality based on screen size and network connection)
     * @param streams maps StreamQualities to urls
     * @return video stream url
     */
    @Nullable
    public static String getStreamsUrl(@Nullable Context ctx, @Nullable final Map<StreamQuality, String> streams) {
        if (streams == null) return null;
        int n = streams.size();
        if (n == 0) return null;
        // if there is only one, return that (probably "adaptivestreaming")
        if (n == 1) return streams.values().iterator().next();
        //
        final StreamQuality[] pref;
        if (ctx != null) {
            final boolean mobile = Util.isNetworkMobile(ctx);
            final int screenWidth = Util.getDisplaySize(ctx).x;
            // if network is mobile, pick one quality level below
            if (screenWidth >= StreamQuality.H264XL.getWidth()) pref = mobile ? PREF_L_MOBILE : PREF_XL;
            else if (screenWidth >= StreamQuality.H264L.getWidth()) pref = mobile ? PREF_M_MOBILE : PREF_L;
            else if (screenWidth >= StreamQuality.H264M.getWidth()) pref = mobile ? PREF_S : PREF_M;
            else pref = PREF_S;
        } else {
            pref = PREF_S;
        }
        for (StreamQuality sq : pref) {
            if (streams.containsKey(sq)) {
                return streams.get(sq);
            }
        }
        // return something
        return streams.values().iterator().next();
    }

    /**
     * Constructor.
     * @param label string resource to use as label
     * @param width video width
     */
    StreamQuality(@StringRes int label, int width) {
        this.label = label;
        this.width = width;
    }

    /**
     * Returns the string resource id of the label.
     * @return string resource id of the label
     */
    @StringRes
    public int getLabel() {
        return label;
    }

    /**
     * Returns the width of the video stream.
     * @return width of the video stream or -1, if not known (which is the case for {@link #ADAPTIVESTREAMING} and {@link #PODCASTVIDEOM_IAS})
     */
    @IntRange(from = -1)
    public int getWidth() {
        return width;
    }
}
