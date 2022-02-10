package de.freehamburger.util;

import android.annotation.SuppressLint;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.freehamburger.BuildConfig;

/**
 * Proxy for {@link android.util.Log} that writes to a log file, too.
 */
@SuppressLint("LogConditional")
public class Log {

    private static final String FILENAME = "log.txt";
    private static final String TEMPFILE_PREFIX = "templog";
    private static final DateFormat DF = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    /** max. log file size */
    private static final long MAX_SIZE = 1_048_576L;
    private static final Deque<String> QUEUE = new ArrayDeque<>(96);
    private static final Object QUEUE_LOCK = new Object();
    public static final Object FILE_LOCK = new Object();
    private static File file = new File(System.getProperty("java.io.tmpdir"), FILENAME);

    static {
        init();
    }

    public static boolean deleteFile() {
        boolean deleted;
        synchronized (FILE_LOCK) {
            deleted = file.delete();
        }
        return deleted;
    }

    public static void e(@NonNull String tag, String msg) {
        if (!BuildConfig.DEBUG || msg == null) return;
        android.util.Log.e(tag, msg.trim());
        write('E', tag, msg);
    }

    public static void e(@NonNull String tag, String msg, Throwable t) {
        if (!BuildConfig.DEBUG) return;
        android.util.Log.e(tag, msg, t);
        write('E', tag, msg);
    }

    /**
     * @return the log file
     */
    @Nullable
    public static File getFile() {
        return file;
    }

    public static void i(@NonNull String tag, String msg) {
        if (!BuildConfig.DEBUG || msg == null) return;
        android.util.Log.i(tag, msg.trim());
        write('I', tag, msg);
    }

    private static void init() {
        if (!BuildConfig.DEBUG) return;
        /*
        java.io.tmpdir: /data/user/0/de.freehamburger.debug/cache
        context.getFilesDir(): /data/user/0/de.freehamburger.debug/files
         */
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir == null) {
            android.util.Log.wtf("Log", "java.io.tmpdir is null!");
            return;
        }
        File filesDir = new File(new File(tmpdir).getParentFile(), "files");
        if (filesDir.isDirectory()) {
            file = new File(filesDir, FILENAME);
            // as there once was some residue, a little light housekeeping ⛯
            java.util.List<File> files = Util.listFiles(filesDir);
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(TEMPFILE_PREFIX)) {
                    Util.deleteFile(file);
                }
            }
        }
        new Writer().start();
    }

    /**
     * Writes pending log messages to the log file.
     */
    public static void sync() {
        Writer.sync();
    }

    private static void truncate() {
        if (file == null || !file.isFile()) return;
        final long toCut;
        synchronized (FILE_LOCK) {
            toCut = file.length() - MAX_SIZE;
        }
        if (toCut <= 0L) return;
        InputStream in = null;
        File temp = null;
        try {
            synchronized (FILE_LOCK) {
                temp = File.createTempFile(TEMPFILE_PREFIX, ".tmp", file.getParentFile());
                in = new FileInputStream(file);
                if (in.skip(toCut) < toCut) {
                    Util.close(in);
                    Log.e(Log.class.getSimpleName(), "Failed to truncate the log file! (1)");
                    return;
                }
                for (int c = 0; c != -1 && c != '\n'; ) {
                    c = in.read();
                }
                Util.copyFile(in, temp, (int) MAX_SIZE, MAX_SIZE);
                Util.close(in);
                in = null;
                if (!temp.renameTo(file)) {
                    Util.deleteFile(temp);
                    Log.e(Log.class.getSimpleName(), "Failed to truncate the log file! (2)");
                }
            }
        } catch (IOException e) {
            android.util.Log.e(Log.class.getSimpleName(), e.toString(), e);
        } finally {
            Util.close(in);
            Util.deleteFile(temp);
        }
    }

    public static void w(@NonNull String tag, String msg) {
        if (!BuildConfig.DEBUG || msg == null) return;
        android.util.Log.w(tag, msg.trim());
        write('W', tag, msg);
    }

    public static void w(@NonNull String tag, String msg, Throwable t) {
        if (!BuildConfig.DEBUG) return;
        android.util.Log.w(tag, msg, t);
        write('W', tag, msg);
    }

    public static void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable t, int depth) {
        if (!BuildConfig.DEBUG) return;
        final StackTraceElement[] ste = t.getStackTrace();
        final int l = Math.min(ste.length, depth);
        final StringBuilder sb = new StringBuilder(msg.length() + 128);
        sb.append(msg);
        for (int i = 0; i < l; i++) {
            sb.append("\nat ").append(ste[i].toString());
        }
        msg = sb.toString();

        android.util.Log.w(tag, msg);
        write('W', tag, msg);
    }

    /**
     * Enqueues the log messages.
     * @param level log level
     * @param tag log tag
     * @param msg log message
     */
    private static void write(char level, @NonNull String tag, String msg) {
        int l = msg != null ? msg.length() : 0;
        if (l == 0) return;
        StringBuilder msgb = new StringBuilder(l + tag.length() + 20)   // +20 is sufficient for dates formatted for Locale.GERMAN
                .append(DF.format(new Date()))
                .append(' ')
                .append(level)
                .append('/')
                .append(tag)
                .append(": ")
                .append(msg)
                ;
        if (msg.charAt(l - 1) != '\n') msgb.append('\n');
        synchronized (QUEUE_LOCK) {
            QUEUE.push(msgb.toString());
        }
    }

    public static void wtf(@NonNull String tag, String msg, Throwable t) {
        if (!BuildConfig.DEBUG) return;
        android.util.Log.wtf(tag, msg, t);
        write('F', tag, msg);
    }

    /**
     * Writes the log messages to the log file.
     */
    private static class Writer extends Thread {

        private Writer() {
            super();
            setPriority(Thread.NORM_PRIORITY - 1);
        }

        private static void sync() {
            OutputStream out = null;
            synchronized (QUEUE_LOCK) {
                if (QUEUE.isEmpty()) return;
                synchronized (FILE_LOCK) {
                     try {
                        out = new BufferedOutputStream(new FileOutputStream(file, true));
                        do {
                            String s = QUEUE.pollLast();
                            if (s != null) out.write(s.getBytes(CHARSET));
                        } while (!QUEUE.isEmpty());
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) android.util.Log.e(Log.class.getSimpleName(), e.toString(), e);
                    } finally {
                        Util.close(out);
                    }
                }
            }
        }

        @Override
        public void run() {
            for (;;) {
                try {
                    //noinspection BusyWait
                    sleep(10_000L);
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) android.util.Log.w(Log.class.getSimpleName(), t.toString());
                    break;
                }
                sync();
                truncate();
            }
        }
    }
}
