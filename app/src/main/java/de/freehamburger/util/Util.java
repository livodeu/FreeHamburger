package de.freehamburger.util;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.AnyThread;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.R;

/**
 *
 */
public class Util {

    public static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private static final String TAG = "Util";
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);

    /**
     * Returns true if the characters following {@code array[pos]} are those given in {@code chars}.
     * @param array char array
     * @param pos position in {@code array}
     * @param chars chars to heck
     * @return true / false
     * @throws NullPointerException if {@code array} or {@code chars} are {@code null}
     */
    @VisibleForTesting
    public static boolean areNextChars(@NonNull final char[] array, final int pos, @NonNull final char... chars) {
        final int n = chars.length;
        try {
            for (int i = 0; i < n; i++) {
                if (array[pos + i + 1] != chars[i]) return false;
            }
        } catch (IndexOutOfBoundsException ioe) {
            return false;
        }
        return true;
    }

    /**
     * Clears the "app_webview" folder which is a sibling of the files folder.
     * This folder contains some suspicious files (e.g. "Cookies") that we do not need and certainly do not want to keep.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static void clearAppWebview(@NonNull Context ctx) {
        File dir = new File(ctx.getFilesDir().getParentFile(), "app_webview");
        if (!dir.isDirectory()) return;
        final List<File> files = listFiles(dir);
        if (files.isEmpty()) return;
        // sort to that files come first, and sub-directories precede their parents
        Collections.sort(files, (o1, o2) -> {
            if (o1.isFile() && o2.isDirectory()) return -1;
            if (o1.isDirectory() && o2.isFile()) return 1;
            if (o1.isDirectory() && o2.isDirectory()) {
                String p1 = o1.getAbsolutePath();
                String p2 = o2.getAbsolutePath();
                if (p1.length() < p2.length() && p2.startsWith(p1)) return 1;
                if (p2.length() < p1.length() && p1.startsWith(p2)) return -1;
            }
            return o1.compareTo(o2);
        });
        // first, delete all files
        for (File file : files) {
            if (file.isDirectory()) continue;
            if (!file.delete()) {if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete file " + file);}
        }
        // second, delete all directories
        for (File file : files) {
            if (!file.isDirectory()) continue;
            if (!file.delete()) {if (BuildConfig.DEBUG) Log.w(TAG, "Could not delete directory " + file);}
        }
    }

    /**
     * Deletes the {@link App#EXPORTS_DIR exports} directory.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void clearExports(@NonNull Context ctx) {
        File exports = new File(ctx.getCacheDir(), App.EXPORTS_DIR);
        if (!exports.isDirectory()) return;
        final List<File> contents = listFiles(exports);
        for (File file : contents) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Deleting exports file " + file + " (from " + new java.util.Date(file.lastModified()) + ", " + file.length() + " bytes)");
            deleteFile(file);
        }
        exports.delete();
    }

    /**
     * Closes a Closeable.
     * @param c Closeable
     */
    public static void close(@Nullable Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Closes some Closeables.
     * @param cc Closeable
     */
    public static void close(@Nullable Closeable... cc) {
        if (cc == null) return;
        for (Closeable c : cc) {
            if (c == null) continue;
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Copies all available data from the given InputStream to the given File.<br>
     * This method does not close the InputStream.<br>
     * If anything goes wrong, it will delete the destination file!
     * @param in InputStream
     * @param dest destination file
     * @param estimatedSize estimated file size (-1 if not known)
     * @param maxSize number of bytes after which the operation would be aborted (0 for no limit)
     * @return true / false
     */
    public static boolean copyFile(InputStream in, File dest, int estimatedSize, long maxSize) {
        final byte[] buffer = new byte[estimatedSize > 0 && estimatedSize <= 16384 ? estimatedSize : 8192];
        OutputStream out = null;
        boolean ok;
        try {
            out = new BufferedOutputStream(new FileOutputStream(dest));
            for (long counter = 0L; ; ) {
                int read = in.read(buffer);
                if (read <= 0) break;
                counter += read;
                if (maxSize > 0L && counter > maxSize) {
                    throw new RuntimeException("Exceeded max. size of " + maxSize + " bytes");
                }
                out.write(buffer, 0, read);
            }
            ok = true;
        } catch (IOException e) {
            ok = false;
            if (BuildConfig.DEBUG) Log.e(TAG, "copyFile(): " + e.toString(), e);
        } finally {
            close(out);
        }
        if (!ok) deleteFile(dest);
        return ok;

    }

    /**
     * Copies one file to another.<br>
     * If anything goes wrong while copying, the destination file will be deleted!
     * @param src source file
     * @param dest destination file
     * @param maxSize maximum number of bytes to copy
     * @return true / false
     * @throws NullPointerException if either file parameter is {@code null}
     */
    public static boolean copyFile(File src, File dest, long maxSize) {
        if (!src.isFile()) return false;
        File destParent = dest.getParentFile();
        if (destParent == null) return false;
        long filesize = src.length();
        if (destParent.getFreeSpace() < filesize) {
            if (BuildConfig.DEBUG)
                Log.e(TAG, "Not enough space (" + dest.getFreeSpace() + " bytes) (less than " + filesize + " bytes)!");
            return false;
        }
        if (!destParent.isDirectory()) {
            if (destParent.isFile()) return false;
            if (!destParent.mkdirs()) return false;
        }
        InputStream in = null;
        boolean ok;
        try {
            in = new FileInputStream(src);
            ok = copyFile(in, dest, (int)filesize, maxSize);
        } catch (IOException e) {
            ok = false;
            if (BuildConfig.DEBUG) Log.e(TAG, "While copying file \"" + src + "\" to \"" + dest + "\": " + e.toString());
        } finally {
            close(in);
        }
        if (!ok) deleteFile(dest);
        return ok;
    }

    /**
     * Displays the menu items' alphabetic shortcuts by underlining the matching character in the menu item title.<br>
     * See also <a href="https://en.wikipedia.org/wiki/Combining_Diacritical_Marks">here</a>.
     * @param menu Menu to work on
     * @throws NullPointerException if {@code menu} is {@code null}
     */
    public static void decorateMenuWithShortcuts(Menu menu) {
        final int n = menu.size();
        for (int i = 0; i < n; i++) {
            MenuItem item = menu.getItem(i);
            char sc = item.getAlphabeticShortcut();
            if (sc == 0) continue;
            CharSequence t = item.getTitle();
            if (t == null) continue;
            final int tn = t.length();
            for (int j = 0; j < tn; j++) {
                char c = t.charAt(j);
                if (c == sc || Character.toLowerCase(c) == sc) {
                    // insert a U+0332
                    item.setTitle(t.subSequence(0, j + 1) + "̲" + t.subSequence(j + 1, tn));
                    break;
                }
            }
        }
    }

    /**
     * Deletes a file or directory.<br>
     * If the file could not be deleted, it is passed on to the {@link FileDeleter}.
     * @param file File
     */
    public static void deleteFile(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (!file.delete()) {
            FileDeleter.add(file);
        }
    }

    /**
     * Deletes old files in the cache directory.
     * @param ctx Context
     * @param maxSize maximum cache size in bytes
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @AnyThread
    public static void deleteOldestCacheFiles(@NonNull Context ctx, @IntRange(from = 0) final long maxSize) {
        // get all cache files and directories
        File cacheDir = ctx.getCacheDir();
        final List<File> c = listFiles(cacheDir);
        // put them into a map with key = file, value = timestamp and sum up the sizes
        final Map<File, Long> lm = new HashMap<>(c.size());
        // initialise total with the size for the cache directory itself
        long total = getOccupiedSpace(cacheDir);
        for (File f : c) {
            total += getOccupiedSpace(f);
            lm.put(f, f.lastModified());
        }
        // if the total size is below maxSize, return
        if (total < maxSize) {
            return;
        }
        // sort: oldest first, newest last
        try {
            Collections.sort(c, new Comparator<File>() {
                /** {@inheritDoc} */
                @Override
                public int compare(File o1, File o2) {
                    if (o1.equals(o2)) return 0;
                    Long lm1 = lm.get(o1);
                    if (lm1 == null) return -1;
                    Long lm2 = lm.get(o2);
                    if (lm2 == null) return 1;
                    return Long.compare(lm1, lm2);
                }
            });
        } catch (Throwable ignored) {
            return;
        }
        final long bytesToDelete = maxSize > 0L ? total - maxSize : Long.MAX_VALUE;
        long deleted = 0L;
        for (File file : c) {
            if (file.isDirectory() || file.equals(FileDeleter.MORITURI)) continue;
            long l = getOccupiedSpace(file);
            if (l <= 0L) continue;
            if (file.delete()) {
                deleted += l;
            }
            if (deleted >= bytesToDelete) break;
        }
    }

    /**
     * Displays a Snackbar and fades it over a given period of time.<br>
     * <b>The Snackbar will not be dismissed after the time is up, though!</b>
     * @param sb Snackbar <em>which is not shown yet</em>
     * @param duration duration in ms
     * @throws NullPointerException if {@code sb} is {@code null}
     * @throws IllegalArgumentException if {@code duration} is less than 0
     */
    public static void fadeSnackbar(@NonNull final Snackbar sb, @IntRange(from = 0) final long duration) {
        final Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) sb.getView();
        final TextView textView = snackLayout.findViewById(android.support.design.R.id.snackbar_text);
        final Button action = snackLayout.findViewById(android.support.design.R.id.snackbar_action);
        final int textColor = textView.getCurrentTextColor();
        final int textColorEnd = Color.argb(0, Color.red(textColor), Color.green(textColor), Color.blue(textColor));
        final int buttonTextColor = action.getCurrentTextColor();
        final int buttonTextColorEnd = Color.argb(0, Color.red(buttonTextColor), Color.green(buttonTextColor), Color.blue(buttonTextColor));
        final String propertyName = "textColor";
        final ObjectAnimator oa = ObjectAnimator.ofArgb(textView, propertyName, textColor, textColorEnd).setDuration(duration);
        final ObjectAnimator aa = ObjectAnimator.ofArgb(action, propertyName, buttonTextColor, buttonTextColorEnd).setDuration(duration);
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(oa, aa);
        animatorSet.setInterpolator(new AccelerateInterpolator(1.5f));
        sb.show();
        animatorSet.start();
    }

    /**
     * Replaces "Text." with „Text.”<br>
     * Lower/first is „ (0x201e), upper/last is “ (0x201c).<br>
     * See <a href="https://en.wikipedia.org/wiki/Quotation_mark#German">here</a> &amp; <a href="https://de.wikipedia.org/wiki/Anf%C3%BChrungszeichen#Anf%C3%BChrungszeichen_im_Deutschen">hier</a>.
     * @param value String
     * @return CharSequence
     */
    @NonNull
    public static CharSequence fixQuotationMarks(@Nullable final String value) {
        if (value == null || value.length() == 0) return "";
        final char wrong = '"';
        final char correctLower = '„';
        final char correctUpper = '”';
        final StringBuilder out = new StringBuilder(value.length());
        boolean recentWasLower = false;
        for (int pos = 0;;) {
            int found = value.indexOf(wrong, pos);
            if (found < 0) {
                out.append(value.substring(pos));
                break;
            }
            if (found == 0) {
                out.append(correctLower);
                recentWasLower = true;
                pos = 1;
                continue;
            }
            // check whether found is within a html tag
            int nextHtmlTag = value.lastIndexOf('<', found);
            int nextHtmlTagEnd = value.lastIndexOf('>', found);
            if (nextHtmlTag >= 0 && nextHtmlTagEnd < nextHtmlTag) {
                nextHtmlTagEnd = value.indexOf('>', found + 1);
                if (nextHtmlTagEnd > 0) {
                    out.append(value, pos, nextHtmlTagEnd + 1);
                    pos = nextHtmlTagEnd + 1;
                    continue;
                }
                out.append(value.substring(pos));
                break;
            }
            out.append(value, pos, found);
            if (recentWasLower) {
                out.append(correctUpper);
                recentWasLower = false;
            } else {
                out.append(correctLower);
                recentWasLower = true;
            }
            pos = found + 1;
        }
        return out;
    }

    /**
     * Translates a color resource to a color value. Wraps getters for pre and post-Marshmallow.
     * @param ctx ContextWrapper
     * @param colorRes color resource
     * @return color value
     * @throws NullPointerException if {@code ctx} is {@code null}
     * @throws Resources.NotFoundException if the given color resource does not exist.
     */
    @ColorInt
    public static int getColor(@NonNull ContextWrapper ctx, @ColorRes int colorRes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ctx.getResources().getColor(colorRes, ctx.getTheme());
        }
        //noinspection deprecation
        return ctx.getResources().getColor(colorRes);
    }

    /**
     * Gets the height of the display in pixels. The height is adjusted based on the current rotation of the display
     * @param ctx Context
     * @return display height
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static int getDisplayHeight(@NonNull Context ctx) {
        return getDisplaySize(ctx).y;
    }

    /**
     * Gets the size of the display in pixels. The size is adjusted based on the current rotation of the display.
     * @param ctx Context
     * @return Point
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static Point getDisplaySize(@NonNull Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        if (wm != null) wm.getDefaultDisplay().getSize(size);
        return size;
    }

    /**
     * Returns the ExoPlayer error message contained in the given Exception.
     * @param error ExoPlaybackException
     * @return error message
     * @throws NullPointerException if {@code error} is {@code null}
     */
    @NonNull
    public static String getExoPlaybackExceptionMessage(@NonNull final ExoPlaybackException error) {
        String msg;
        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE: msg = error.getSourceException().getMessage(); break;
            case ExoPlaybackException.TYPE_RENDERER: msg = error.getRendererException().getMessage(); break;
            case ExoPlaybackException.TYPE_UNEXPECTED: msg = error.getUnexpectedException().getMessage(); break;
            default: msg = null;
        }
        return msg != null ? msg : error.toString();
    }

    /**
     * Returns the Space occupied by a file or directory.<br>
     * Will return 0 if the file does not exist or if it could not be accessed.
     * @param file file <em>or</em> directory
     * @return space used in bytes
     */
    @RequiresApi(21)
    @VisibleForTesting
    public static long getOccupiedSpace(@Nullable File file) {
        if (file == null) return 0L;
        long space;
        try {
            space = Os.stat(file.getAbsolutePath()).st_blocks << 9;
        } catch (ErrnoException e) {
            space = 0L;
            if (BuildConfig.DEBUG) Log.e(TAG, "getOccupiedSpace(\"" + file + "\"): " + e.toString());
        }
        return space;
    }

    /**
     * Returns the Space occupied by several files. Directories are not skipped.
     * @param files Collection of files
     * @return space occupied in bytes
     */
    public static long getOccupiedSpace(@Nullable final Collection<File> files) {
        if (files == null) return 0L;
        long space = 0L;
        for (File file : files) {
            try {
                space += Os.stat(file.getAbsolutePath()).st_blocks << 9;
            } catch (ErrnoException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "getOccupiedSpace(" + file + "): " + e.toString());
            }
        }
        return space;
    }

    /**
     * Returns a Typeface appropriate for the given TextView.
     * @param tv TextView
     * @param text text that will be set
     * @return Typeface
     * @throws NullPointerException if {@code tv} is {@code null}
     */
    @NonNull
    public static Typeface getTypefaceForTextView(@NonNull final TextView tv, @Nullable final String text) {
        if (text == null) return NORMAL;
        Rect rect = new Rect();
        int width = tv.getWidth();
        if (width > 0) {
            tv.setTypeface(NORMAL);
            tv.getPaint().getTextBounds(text, 0, text.length(), rect);
            int avail = tv.getWidth() - tv.getTotalPaddingLeft() - tv.getTotalPaddingRight();
            if (rect.width() >= avail) {
                return CONDENSED;
            } else {
                return NORMAL;
            }
        }
        Context ctx = tv.getContext();
        if (ctx == null) return NORMAL;
        Point ds = getDisplaySize(ctx);
        if (ds.x >= 320) {
            tv.getPaint().getTextBounds(text, 0, text.length(), rect);
            if (rect.width() >= ds.x - 32) {
                return CONDENSED;
            } else {
                return NORMAL;
            }
        }
        return NORMAL;
    }

    /**
     * Returns the visible Views in a RecyclerView.
     * @param r RecyclerView
     * @return Set of child Views that are <em>partly or completely</em> visible
     * @throws NullPointerException if {@code r} is {@code null}
     */
    @NonNull
    public static Set<View> getVisibleKids(@NonNull RecyclerView r) {
        if (r.getVisibility() != View.VISIBLE) return new HashSet<>(0);
        final RecyclerView.LayoutManager lm = r.getLayoutManager();
        if (lm == null) return new HashSet<>(0);
        final int n = lm.getChildCount();
        final Set<View> vc = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            View kid = lm.getChildAt(i);
            if (kid == null) continue;
            if (lm.isViewPartiallyVisible(kid, false, true)) {
                vc.add(kid);
            } else if (lm.isViewPartiallyVisible(kid, true, true)) {
                vc.add(kid);
            }
        }
        return vc;
    }

    /**
     * Hides or shows the action, status and navigation bars.
     * @param a Activity
     * @param hide {@code true} to hide, {@code false} to reset
     * @throws NullPointerException if {@code a} is {@code null}
     */
    public static void hideActionNavigationStatusBar(@NonNull AppCompatActivity a, boolean hide) {
        View decorView = a.getWindow().getDecorView();
        ActionBar actionBar = a.getSupportActionBar();
        if (actionBar != null) {
            if (hide) actionBar.hide(); else actionBar.show();
        }
        int uiOptions = hide ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE : 0;
        decorView.setSystemUiVisibility(uiOptions);
    }

    /**
     * Determines whether a network connection is available.<br>
     * Takes the user's preference as to whether loading via mobile is allowed into account.
     * @param ctx Context
     * @return true/false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isNetworkAvailable(@NonNull Context ctx) {
        return isNetworkAvailable((App)ctx.getApplicationContext());
    }

    /**
     * Determines whether a network connection is available.<br>
     * Takes the user's preference as to whether loading via mobile is allowed into account.
     * @param app App
     * @return true/false
     * @throws NullPointerException if {@code app} is {@code null}
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isNetworkAvailable(@NonNull final App app) {
        ConnectivityManager connMgr = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr != null ? connMgr.getActiveNetworkInfo() : null;
        if (networkInfo == null || !networkInfo.isConnected()) return false;
        boolean allowDownloadOverMobile = PreferenceManager.getDefaultSharedPreferences(app).getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE);
        return allowDownloadOverMobile || (networkInfo.getType() != ConnectivityManager.TYPE_MOBILE && networkInfo.getType() != ConnectivityManager.TYPE_MOBILE_DUN);
    }

    /**
     * Determines whether a network connection is considered to be of the mobile type.
     * @param ctx Context
     * @return true/false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public static boolean isNetworkMobile(@NonNull Context ctx) {
        ConnectivityManager connMgr = (ConnectivityManager) ctx.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr != null ? connMgr.getActiveNetworkInfo() : null;
        if (networkInfo == null || !networkInfo.isConnected()) return false;
        return (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE || networkInfo.getType() == ConnectivityManager.TYPE_MOBILE_DUN);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. Forexample, 10" tablets are extra-large.
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static boolean isXLargeTablet(@NonNull Context ctx) {
        return (ctx.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Lists the files in a directory.<br>
     * Includes sub-directories and their contents.
     * @param dir directory
     * @return list of Files
     */
    @NonNull
    public static List<File> listFiles(@Nullable File dir) {
        if (dir == null || !dir.isDirectory()) return new ArrayList<>(0);
        File[] files = dir.listFiles();
        if (files == null) return new ArrayList<>(0);
        final List<File> list = new ArrayList<>(Arrays.asList(files));
        final List<File> fromSub = new ArrayList<>();
        for (File file : list) {
            if (file.isDirectory()) {
                fromSub.addAll(listFiles(file));
            }
        }
        list.addAll(fromSub);
        return list;
    }

    /**
     * Loads the font from {@link App#FONT_FILE} (if it exists).
     * @param ctx Context
     * @return Typeface (or {@code null} if the file failed to load)
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @Nullable
    public static Typeface loadFont(@NonNull Context ctx) {
        File fontFile = new File(ctx.getFilesDir(), App.FONT_FILE);
        if (!fontFile.isFile()) return null;
        return Typeface.createFromFile(fontFile);
    }

    /**
     * Loads a line-based text file from the resources.<br>
     * Lines that start with # are considered to be a comment.
     * @param ctx Context
     * @param rawId raw resource id
     * @param expextedNumberOfLines expected number of lines to be read
     * @return List of Strings, one for each line
     * @throws IllegalArgumentException if {@code expectedNumberOfLines} is negative
     */
    @NonNull
    public static List<String> loadResourceTextFile(@NonNull Context ctx, @RawRes int rawId, @IntRange(from = 0) int expextedNumberOfLines) {
        final List<String> lines = new ArrayList<>(expextedNumberOfLines);
        InputStream in = null;
        try {
            in = ctx.getResources().openRawResource(rawId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            for (; ; ) {
                String line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "loadResourceTextFile(..., " + rawId + ", ...): " + e.toString());
        } finally {
            close(in);
        }
        return lines;
    }

    /**
     * Creates a bitmap that contains a text
     * @param s text
     * @param textSize text size (set to 0 to maximise the text)
     * @param wx width
     * @param wy height
     * @param color text color
     * @param background background color
     * @param cf ColorFilter (optional)
     * @return Bitmap
     * @throws NullPointerException if {@code s} is {@code null}
     */
    public static Bitmap makeCharBitmap(final String s, float textSize, final int wx, final int wy, @ColorInt int color, @ColorInt int background, @Nullable ColorFilter cf) {
        final Bitmap bitmap = Bitmap.createBitmap(wx, wy, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(background);
        final Canvas canvas = new Canvas();
        final int l = s.length();
        final Rect bounds = new Rect();
        final Paint paint = new Paint();
        paint.setColor(color);
        if (textSize > 0f) {
            paint.setTextSize(textSize);
        } else {
            textSize = 8f;
            for (;; textSize += 1f) {
                paint.setTextSize(textSize);
                paint.getTextBounds(s, 0, l, bounds);
                if (bounds.width() > wx || bounds.height() > wy) {
                    paint.setTextSize(textSize - 1f);
                    break;
                }
            }
        }
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        // https://keithp.com/~keithp/porterduff/p253-porter.pdf
        paint.setFilterBitmap(true);
        if (cf != null) {
            paint.setColorFilter(cf);
        }
        paint.getTextBounds(s, 0, l, bounds);
        canvas.setBitmap(bitmap);
        canvas.drawText(s, wx / 2f, wy / 2f + bounds.height() / 2f, paint);
        return bitmap;
    }

    /**
     * Makes sure that the given url uses https and not http.
     * @param url http(s) url
     * @return https url
     * @throws NullPointerException if {@code url} is {@code null}
     */
    @NonNull
    public static String makeHttps(@NonNull final String url) {
        return url.toLowerCase(Locale.US).startsWith("http:") ? "https:" + url.substring(5) : url;
    }

    /**
     * Removes &lt;a&gt;...&lt;a/&gt;.
     * @param value StringBuilder to remove the anchors from
     * @return StringBuilder with anchors removed
     */
    @NonNull
    public static StringBuilder removeLinks(@Nullable final StringBuilder value) {
        final int n = value != null ? value.length() : 0;
        if (n == 0) return new StringBuilder(0);
        char[] c = new char[n];
        value.getChars(0, n, c, 0);
        final StringBuilder out = new StringBuilder(n);
        boolean insideA = false;
        // <a href="https://url.de">Link</a>
        for (int pos = 0; pos < n; pos++) {
            if (c[pos] == '<') {
                if (areNextChars(c, pos, 'a', ' ')) {
                    insideA = true;
                } else if (areNextChars(c, pos, '/', 'a', '>')) {
                    pos += 3;
                    insideA = false;
                } else {
                    out.append('<');
                }
             } else if (!insideA) {
                out.append(c[pos]);
            }
        }
        return out;
    }

    /**
     * Removes &lt;ul&gt; and &lt;ol&gt;.
     */
    @NonNull
    public static StringBuilder removeUlliAndOlli(@Nullable final String value) {
        final int n = value != null ? value.length() : 0;
        if (n == 0) return new StringBuilder(0);
        char[] c = new char[n];
        value.getChars(0, n, c, 0);
        final StringBuilder out = new StringBuilder(n);
        boolean insideUl = false;
        boolean insideOl = false;
        int olCounter = 1;
        Set<Integer> dotsInsertedAt = new HashSet<>();
        for (int pos = 0; pos < n; pos++) {
            if (c[pos] == '<') {
                if (areNextChars(c, pos, 'u', 'l', '>')) {
                    pos += 3;
                    insideUl = true;
                } else if (areNextChars(c, pos, 'o', 'l', '>')) {
                    pos += 3;
                    insideOl = true;
                } else if (areNextChars(c, pos, '/', 'u', 'l', '>')) {
                    if (dotsInsertedAt.size() == 1) {
                        // if the <ul> contains only one item it does not make sense to add bullet points
                        // remove "• " as inserted below in the "li>" case
                        int insertedAt = TextUtils.lastIndexOf(out, '•');
                        out.deleteCharAt(insertedAt);
                        out.deleteCharAt(insertedAt);   // yes, do it twice, to remove the ' ' after the bullet, too
                    }
                    pos += 4;
                    insideUl = false;
                    dotsInsertedAt.clear();
                } else if (areNextChars(c, pos, '/', 'o', 'l', '>')) {
                    pos += 4;
                    insideOl = false;
                    olCounter = 1;
                } else if (areNextChars(c, pos, '/', 'l', 'i', '>')) {
                    pos += 4;
                } else if (insideUl && areNextChars(c, pos, 'l', 'i', '>')) {
                    out.append("<br>• ");
                    dotsInsertedAt.add(pos + 4);
                    pos += 3;
                } else if (insideOl && areNextChars(c, pos, 'l', 'i', '>')) {
                    out.append("<br>").append(olCounter++).append(". ");
                    pos += 3;
                } else {
                    out.append(c[pos]);
                }
            } else {
                out.append(c[pos]);
            }
        }
        return out;
    }

    /**
     * Shares the given stream of data (represented by the url) via {@link Intent#ACTION_SEND ACTION_SEND}.
     * Displays an error message if there isn't any suitable app installed.
     * @param ctx Context
     * @param url url
     * @param title title (optional)
     * @throws NullPointerException if {@code ctx} or {@code url} are {@code null}
     */
    public static void sendBinaryData(@NonNull final Context ctx, @NonNull final String url, @Nullable CharSequence title) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        String mime = null;
        int posFiletag = url.lastIndexOf('.');
        if (posFiletag > 0 && posFiletag < url.length() - 1) {
            String filetag = url.substring(posFiletag + 1);
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filetag);
        }
        if (mime == null) {
            if (url.endsWith(".mp4")) {
                mime = "video/mp4";
            } else if (url.endsWith(".mp3")) {
                mime = "audio/mpeg";
            } else if (url.endsWith(".ogg")) {
                mime = "application/ogg";
            } else if (url.endsWith(".jpg")) {
                mime = "image/jpeg";
            } else if (url.endsWith(".m3u8")) {
                mime = "application/vnd.apple.mpegurl";
            } else {
                mime = "video/*";
            }
        }
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
        if (title != null) {
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
        }
        if (!(ctx instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            ctx.startActivity(intent);
        } else {
            if (ctx instanceof CoordinatorLayoutHolder) {
                Snackbar.make(((CoordinatorLayoutHolder)ctx).getCoordinatorLayout(), R.string.error_no_app, Snackbar.LENGTH_LONG)
                        .show();
            } else {
                Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    /**
     * Shares the given Bitmap via {@link Intent#ACTION_SEND ACTION_SEND}.
     * Does not always display an error message in case of failure.
     * @param ctx Context
     * @param bm Bitmap
     * @param title title (optional)
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    static void sendBitmap(@NonNull Context ctx, @NonNull Bitmap bm, @Nullable String title) {
        boolean ok = true;
        OutputStream out = null;
        File temp = null;
        try {
            File exports = new File(ctx.getCacheDir(), App.EXPORTS_DIR);
            if (!exports.isDirectory()) {
                if (!exports.mkdirs()) {
                    return;
                }
            }

            temp = File.createTempFile(title != null ? title.substring(0, Math.min(title.length(), 16)) : "pic", ".jpg", exports);
            out = new BufferedOutputStream(new FileOutputStream(temp));
            ok = bm.compress(Bitmap.CompressFormat.JPEG, 80, out);
            close(out);
            out = null;
            if (ok) {
                App app = (App) ctx.getApplicationContext();
                sendBinaryData(app, FileProvider.getUriForFile(app, App.getFileProvider(), temp).toString(), title);
            }
        } catch (IOException e) {
            ok = false;
            if (BuildConfig.DEBUG) Log.e(TAG, "sendBitmap(): " + e.toString());
        } finally {
            close(out);
            if (!ok && temp != null) {
                deleteFile(temp);
            }
        }
    }

    /**
     * Shares the given url via {@link Intent#ACTION_SEND ACTION_SEND}. Displays an error message if there isn't any suitable app installed.
     * @param ctx Context
     * @param url URL
     * @param title title (optional)
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static void sendUrl(@NonNull Context ctx, @NonNull String url, @Nullable CharSequence title) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
        }
        intent.setType("text/plain");
        if (!(ctx instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            ctx.startActivity(intent);
        } else {
            if (ctx instanceof CoordinatorLayoutHolder) {
                Snackbar.make(((CoordinatorLayoutHolder)ctx).getCoordinatorLayout(), R.string.error_no_app, Snackbar.LENGTH_LONG)
                        .show();
            } else {
                Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Sets a {@link Snackbar snackbar's} action font (family and size).
     * @param s Snackbar
     * @param font font to set (optional), see {@link Typeface#create(String, int)}
     * @param textSize text size to set (set this to 0 to skip)
     */
    public static void setSnackbarActionFont(@Nullable Snackbar s, @Nullable Typeface font, float textSize) {
        if (s == null) return;
        Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) s.getView();
        TextView textView = snackLayout.findViewById(android.support.design.R.id.snackbar_action);
        if (textView == null) {
            return;
        }
        if (font != null) {
            textView.setTypeface(font);
        }
        if (textSize >= 1f) {
            textView.setTextSize(textSize);
        }
    }

    /**
     * Sets a {@link Snackbar snackbar's} font (family and size).<br>
     * Tested with compileSdkVersion 28 on device running API 26.
     * @param s Snackbar
     * @param font font to set (optional), see {@link Typeface#create(String, int)}
     * @param textSize text size to set (set this to 0 to skip)
     */
    public static void setSnackbarFont(@Nullable Snackbar s, @Nullable Typeface font, float textSize) {
        if (s == null) return;
        Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) s.getView();
        TextView textView = snackLayout.findViewById(android.support.design.R.id.snackbar_text);
        if (textView == null) {
            return;
        }
        if (font != null) {
            textView.setTypeface(font);
        }
        if (textSize >= 1f) {
            textView.setTextSize(textSize);
        }
    }

    /**
     * Splits a String into several parts of a maximum length.
     * @param s String to split
     * @param maxLength maximum length of each part
     * @return List of Strings
     * @throws IllegalArgumentException if {@code maxLength} is negative
     * @throws ArithmeticException if {@code maxLength} is -1
     */
    @NonNull
    public static List<String> splitString(@NonNull final String s, @IntRange(from = 1) final int maxLength) {
        final int n = s.length();
        if (n == 0) return new ArrayList<>(0);
        final List<String> list = new ArrayList<>(n / maxLength + 1);
        for (int p = 0;;) {
            int l = Math.min(p + maxLength, n);
            list.add(s.substring(p, l));
            p = l;
            if (p >= n) break;
        }
        return list;
    }

}
