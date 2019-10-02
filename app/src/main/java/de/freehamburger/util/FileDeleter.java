package de.freehamburger.util;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;

import de.freehamburger.BuildConfig;

/**
 * Surprisingly, this class deletes files.
 */
public final class FileDeleter {
    /** the text file that contains the paths of the files that resisted deletion in the first place (resides in the cache dir) */
    static final File MORITURI = new File(System.getProperty("java.io.tmpdir"), "morituri.txt");

    private static final String TAG = "FileDeleter";

    /**
     * Adds a file that should be deleted.
     * @param file File
     */
    public static void add(@Nullable File file) {
        if (file == null || !file.isFile()) return;
        BufferedWriter w = null;
        synchronized (MORITURI) {
            try {
                w = new BufferedWriter(new FileWriter(MORITURI, true));
                w.write(file.getAbsolutePath() + '\n');
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "While adding entry to list of deleteable files: " + e.toString());
            } finally {
                Util.close(w);
            }
        }
    }

    /**
     * Deletes files that should be deleted<br>
     * (that have been added via {@link #add(File)}).
     */
    public static void run() {
        if (!MORITURI.exists() || MORITURI.length() == 0L) return;
        File temp = null;
        BufferedReader r = null;
        BufferedWriter w = null;
        synchronized (MORITURI) {
            try {
                temp = File.createTempFile("dfd", null);
                w = new BufferedWriter(new FileWriter(temp));
                r = new BufferedReader(new InputStreamReader(new FileInputStream(MORITURI)));
                for (; ; ) {
                    String line = r.readLine();
                    if (line == null) break;
                    File toDelete = new File(line);
                    if (!toDelete.exists()) continue;
                    boolean deleted = toDelete.delete();
                    if (!deleted) {
                        w.write(line + '\n');
                    }
                }
            } catch (Throwable e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            }
            Util.close(r, w);
            if (temp != null && temp.length() > 0L) {
                // not all files deleted
                //noinspection ResultOfMethodCallIgnored
                temp.renameTo(MORITURI);
            } else {
                // all files deleted
                if (temp != null && !temp.delete()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to delete " + temp);
                }
                if (!MORITURI.delete()) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to delete " + MORITURI);
                }
            }
        }
    }

    /**
     * Private constructor.
     */
    private FileDeleter() {
        super();
    }
}
