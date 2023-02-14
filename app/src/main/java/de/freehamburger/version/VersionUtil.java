package de.freehamburger.version;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.nio.CharBuffer;

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class VersionUtil {
    private static final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private VersionUtil() {
    }

    /**
     * Converts some bytes into their hexadecimal representation.<br>
     * Is about 10 times faster than {@link Integer#toHexString(int)}.
     * @param data input data
     * @return hexadecimal representation
     */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static CharSequence asHex(@Nullable final byte[] data) {
        if (data == null) return "";
        final char[] cs = new char[data.length << 1];
        int j = 0;
        for (byte b : data) {
            int i = ((int)b) & 0xff;
            if (i < 0x10) {
                cs[j++] = '0';
                cs[j++] = HEX[i];
            } else {
                cs[j++] = HEX[(i & 0xf0) >> 4];
                cs[j++] = HEX[(i & 0x0f)];
            }
        }
        return CharBuffer.wrap(cs);
    }
}
