package de.freehamburger.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.freehamburger.BuildConfig;

/**
 *
 */
public class TtfInfo {

    @Nullable
    private String fontFullName;
    @Nullable
    private String manufacturerName;
    @Nullable
    private String designerName;

    /**
     * Obtains some info from a ttf file.
     * See <a href="https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html">https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html</a>
     * @param file ttf file
     * @return TtfInfo
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("ObjectAllocationInLoop")
    @NonNull
    public static TtfInfo getTtfInfo(@NonNull File file) throws IOException {
        final TtfInfo ttfInfo = new TtfInfo();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            raf.seek(4);
            final int nTables = raf.readShort();
            final Map<String, TtfTable> tables = new HashMap<>(nTables);
            raf.skipBytes(6);
            byte[] buf = new byte[4];
            for (int i = 0; i < nTables; i++) {
                raf.read(buf, 0, 4);
                String tag = new String(buf, 0, 4);
                raf.skipBytes(4);
                int offset = raf.readInt();
                int length = raf.readInt();
                tables.put(tag, new TtfTable(tag, offset, length));
            }
            TtfTable nameTable = tables.get("name");
            if (nameTable != null) {
                TtfNameTable ttfNameTable = TtfNameTable.read(raf, nameTable.offset);
                ttfInfo.fontFullName = ttfNameTable.getFontFullName();
                ttfInfo.manufacturerName = ttfNameTable.getManufacturerName();
                ttfInfo.designerName = ttfNameTable.getDesignerName();
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(TtfInfo.class.getSimpleName(), e.toString(), e);
            Util.close(raf);
            throw e;
        } finally {
            Util.close(raf);
        }
        return ttfInfo;
    }

    @Nullable
    public String getFontFullName() {
        return fontFullName;
    }

    /**
     *
     */
    private static class TtfTable {
        private final String tag;
        private final int offset;
        private final int length;

        TtfTable(String tag, int offset, int length) {
            super();
            this.tag = tag;
            this.offset = offset;
            this.length = length;
        }

        int getLength() {
            return length;
        }

        int getOffset() {
            return offset;
        }

        String getTag() {
            return tag;
        }

        @Override
        @NonNull
        public String toString() {
            return "Table \"" + tag + "\" of " + length + " bytes at " + offset;
        }
    }

    /**
     * See <a href="https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html">https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html</a>
     */
    private static class TtfNameTable {
        private final TtfNameRecord[] nameRecords;

        private TtfNameTable(int numRecords) {
            super();
            this.nameRecords = new TtfNameRecord[numRecords];
        }

        @NonNull
        private static TtfNameTable read(@NonNull final RandomAccessFile raf, int offset) throws IOException {
            raf.seek(offset);
            raf.skipBytes(2);
            final int numRecords = raf.readUnsignedShort();
            final TtfNameTable ttfNameTable = new TtfNameTable(numRecords);
            final long stringOffset = raf.getFilePointer() + raf.readUnsignedShort();
            for (int i = 0; i < numRecords; i++) {
                ttfNameTable.nameRecords[i] = TtfNameRecord.read(raf, stringOffset);
            }
            return ttfNameTable;
        }

        @Nullable
        private String getDesignerName() {
            for (TtfNameRecord record : nameRecords) {
                if (record.nameID == 9) {
                    return record.content;
                }
            }
            return null;
        }

        @Nullable
        private String getFontFullName() {
            for (TtfNameRecord record : nameRecords) {
                if (record.nameID == 4) {
                    return record.content;
                }
            }
            return null;
        }

        @Nullable
        private String getManufacturerName() {
            for (TtfNameRecord record : nameRecords) {
                if (record.nameID == 8) {
                    return record.content;
                }
            }
            return null;
        }

        @Override
        @NonNull
        public String toString() {
            return "TtfNameTable{" + "nameRecords=" + Arrays.toString(nameRecords) + '}';
        }

    }

    /**
     * See <a href="https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html">https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6name.html</a>
     */
    private static class TtfNameRecord {
        private short platformID;
        private short platformSpecificID;
        private short languageID;
        private short nameID;
        private short length;
        private short offset;
        private String content;

        @NonNull
        static TtfNameRecord read(@NonNull final RandomAccessFile raf, final long stringOffset) throws IOException {
            final TtfNameRecord ttfNameRecord = new TtfNameRecord();
            ttfNameRecord.platformID = raf.readShort();
            ttfNameRecord.platformSpecificID = raf.readShort();
            ttfNameRecord.languageID = raf.readShort();
            ttfNameRecord.nameID = raf.readShort();
            ttfNameRecord.length = raf.readShort();
            ttfNameRecord.offset = raf.readShort();
            if (ttfNameRecord.length > 0) {
                final long pos = raf.getFilePointer();
                raf.seek(stringOffset - 4 + ttfNameRecord.offset);
                byte[] buf = new byte[ttfNameRecord.length];
                raf.read(buf);
                ttfNameRecord.content = new String(buf, 0, buf.length, buf[0] == 0 ? "UTF-16BE" : "ISO-8859-1");
                raf.seek(pos);
            }
            return ttfNameRecord;
        }

        @Override
        @NonNull
        public String toString() {
            return "\nTtfNameRecord{" + "platformID=" + platformID + ", platformSpecificID=" + platformSpecificID + ", languageID=" + languageID + ", nameID=" + nameID + ", length=" + length + ", offset=" + offset + "; content=" + content + '}';
        }
    }

}
