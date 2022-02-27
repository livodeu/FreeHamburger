package de.freehamburger.util;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.material.snackbar.Snackbar;

import org.xml.sax.XMLReader;

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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.MainActivity;
import de.freehamburger.R;
import de.freehamburger.model.Content;
import de.freehamburger.model.Source;

/**
 *
 */
public class Util {

    public static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    /** Determines whether this is a test build. */
    public static final boolean TEST;
    private static final String TAG = "Util";
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    /**
     * Selection of wrong quotation marks<br>
     * <pre>
     * 0x0022   0x201d  0x201f
     * "        ”       ‟
     * </pre>
     */
    private static final char[] WRONG_QUOTES = new char[] {'\u0022', '\u201d', '\u201f'};

    /** Throwables that might carry important information about a playback failure */
    private static final Collection<Class<? extends Throwable>> POSSIBLE_PLAYBACK_ERROR_CAUSES = Arrays.asList(UnknownHostException.class, SSLPeerUnverifiedException.class, HttpDataSource.InvalidResponseCodeException.class);

    static {
        boolean found = false;
        try {
            Class.forName("androidx.test.espresso.Espresso");
            found = true;
        } catch (Throwable ignored) {
        }
        TEST = found;
    }

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean canCreateShortcut(@Nullable ShortcutManager shortcutManager) {
        return shortcutManager != null && shortcutManager.isRequestPinShortcutSupported();
    }

    /**
     * Clears the "app_webview" folder which is a sibling of the files folder.
     * This folder contains some suspicious files (e.g. "Cookies") that we do not need and certainly do not want to keep.
     * @param ctx Context
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @AnyThread
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
     * @param minAgeMs only delete files that are older than this
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @AnyThread
    public static void clearExports(@NonNull Context ctx, long minAgeMs) {
        File exports = new File(ctx.getCacheDir(), App.EXPORTS_DIR);
        if (!exports.isDirectory()) return;
        final List<File> contents = listFiles(exports);
        final long now = System.currentTimeMillis();
        boolean skippedAtLeastOne = false;
        for (File file : contents) {
            if (now - file.lastModified() < minAgeMs) {skippedAtLeastOne = true; continue;}
            if (BuildConfig.DEBUG) Log.w(TAG, "Deleting exports file " + file + " (from " + new java.util.Date(file.lastModified()) + ", " + file.length() + " bytes)");
            deleteFile(file);
        }
        if (!skippedAtLeastOne) exports.delete();
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
            if (BuildConfig.DEBUG) Log.e(TAG, "copyFile(): " + e, e);
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
            if (BuildConfig.DEBUG) Log.e(TAG, "While copying file \"" + src + "\" to \"" + dest + "\": " + e);
        } finally {
            close(in);
        }
        if (!ok) deleteFile(dest);
        return ok;
    }

    /**
     * Creates a pinned shortcut to the given Source.<br>
     * The shortcut id is {@link Source#name()}.
     * @param ctx Context
     * @param source Source
     * @return true / false
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean createShortcut(@NonNull Context ctx, @NonNull Source source, @Nullable ShortcutManager shortcutManager) {
        if (shortcutManager == null) shortcutManager = (ShortcutManager)ctx.getSystemService(Context.SHORTCUT_SERVICE);
        if (shortcutManager == null) return false;
        if (!shortcutManager.isRequestPinShortcutSupported() || (!TEST && hasShortcut(ctx, source, shortcutManager))) {
            return false;
        }
        String label = ctx.getString(source.getLabel());
        String action = source.getAction();
        if (TextUtils.isEmpty(action)) return false;
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setAction(action);
        ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(ctx, source.name())
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(Icon.createWithResource(ctx, source.getIcon()))
                .setIntent(intent)
                .build();
         if (TEST) {
            return true;
        }
        return shortcutManager.requestPinShortcut(pinShortcutInfo, null);
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
            Collections.sort(c, (o1, o2) -> {
                if (o1.equals(o2)) return 0;
                Long lm1 = lm.get(o1);
                if (lm1 == null) return -1;
                Long lm2 = lm.get(o2);
                if (lm2 == null) return 1;
                return Long.compare(lm1, lm2);
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
     * Returns true if the given String does not contain invalid quotation marks.
     * @param s String to check
     * @return true / false
     */
    private static boolean doesNotContainWrongQuotes(@NonNull final String s) {
        for (char wrong : WRONG_QUOTES) {
            if (s.indexOf(wrong) >= 0) return false;
        }
        return true;
    }

    /**
     * Displays a Snackbar and fades it over a given period of time.<br>
     * <b>The Snackbar will not be dismissed after the time is up, though!</b>
     * @param sb Snackbar <em>which is not shown yet</em>
     * @param handler Handler to use to hide the {@link Snackbar#getView() Snackbar View} after it has been faded out
     * @param duration duration in ms
     * @throws NullPointerException if {@code sb} is {@code null}
     * @throws IllegalArgumentException if {@code duration} is less than 0
     */
    public static void fadeSnackbar(@NonNull final Snackbar sb, @Nullable Handler handler, @IntRange(from = 0) long duration) {
        float durationScale = Settings.Global.getFloat(sb.getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (durationScale < 0.1f) {
            // make sure the animation does not happen if the animator scale is 0! (the Snackbar would be gone immediately)
            sb.setDuration((int)duration).show();
            return;
        }
        if (durationScale < 1f) duration = (long)(duration / durationScale);
        final Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) sb.getView();
        ObjectAnimator oa = ObjectAnimator.ofFloat(snackLayout, "alpha", 1f, 0f).setDuration(duration);
        oa.setInterpolator(new AccelerateInterpolator(2f));
        sb.show();
        oa.start();
        /*
        the Snackbar must be hidden when the fadeout period has elapsed;
        otherwise BaseTransientBottomBar.startFadeOutAnimation() would be called (from BaseTransientBottomBar.animateViewOut())
        and startFadeOutAnimation() starts anew from an alpha value of 1…
         */
        if (handler == null) handler = new Handler();
        handler.postDelayed(() -> snackLayout.setVisibility(View.GONE), duration - 50L);
    }

    /**
     * Attempts to find a TextView with a given text. The search is case-sensitive.
     * @param parent starting point
     * @param text text to find
     * @return TextView
     * @throws NullPointerException if {@code text} is {@code null}
     */
    @Nullable
    public static TextView findTextView(@Nullable final ViewGroup parent, @NonNull final String text) {
        if (parent == null) return null;
        final int nc = parent.getChildCount();
        for (int i = 0; i < nc; i++) {
            View k = parent.getChildAt(i);
            if (k instanceof ViewGroup) {
                TextView kk = findTextView((ViewGroup)k, text);
                if (kk != null) return kk; else continue;
            }
            if (!(k instanceof TextView)) continue;
            if (text.equals(((TextView)k).getText().toString())) return (TextView)k;
        }
        return null;
    }

    /**
     * Replaces "Text." with „Text.“<br>
     * Lower/first is „ (0x201e), upper/last is “ (0x201c).<br>
     * See <a href="https://en.wikipedia.org/wiki/Quotation_mark#German">here</a> &amp; <a href="https://de.wikipedia.org/wiki/Anf%C3%BChrungszeichen#Anf%C3%BChrungszeichen_im_Deutschen">hier</a>.<br>
     * <hr>
     * <i>This code, of course, does not help if a well-paid employee is negligent and enters only part of the required quotation marks or a wild mix of different ones…</i>
     * @param value String
     * @return CharSequence
     */
    @NonNull
    public static CharSequence fixQuotationMarks(@Nullable final String value) {
        if (value == null || value.length() == 0) return "";
        if (doesNotContainWrongQuotes(value)) return value;
        final char correctLower = '\u201e'; // '„';
        final char correctUpper = '\u201c'; // '“';
        final StringBuilder out = new StringBuilder(value.length());
        boolean recentWasLower = false;
        for (int pos = 0;;) {
            // find the next wrong quotation mark, starting from position <pos>
            int found = -1;
            for (char wrong : WRONG_QUOTES) {
                int f = value.indexOf(wrong, pos);
                if (f < 0) continue;
                if (found < 0 || f < found) found = f;
            }
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
            // check whether <found> is within a html tag
            int nextHtmlTag = value.lastIndexOf('<', found);
            int nextHtmlTagEnd = value.lastIndexOf('>', found);
            if (nextHtmlTag >= 0 && nextHtmlTagEnd < nextHtmlTag) {
                nextHtmlTagEnd = value.indexOf('>', found + 1);
                if (nextHtmlTagEnd > 0) {
                    String remainderBeforeHtmlTag = value.substring(pos, nextHtmlTag);
                    int lastCorrectLower = remainderBeforeHtmlTag.lastIndexOf(correctLower);
                    int lastCorrectUpper = remainderBeforeHtmlTag.lastIndexOf(correctUpper);
                    if (lastCorrectLower > lastCorrectUpper) recentWasLower = true;
                    else if (lastCorrectUpper > lastCorrectLower) recentWasLower = false;
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
     * Converts html text into plain text.
     * @param html html text
     * @param im Html.ImageGetter implementation (can be {@code null} if images are not wanted)
     * @return Spanned (specifically, a {@link SpannableStringBuilder})
     */
    @NonNull
    public static Spanned fromHtml(final Context ctx, @NonNull String html, @Nullable Html.ImageGetter im) {
        Spanned spanned;
        final List<PositionedSpan> additionalSpans = new ArrayList<>();
        final Html.TagHandler tagHandler = new Html.TagHandler() {

            private PositionedSpan pendingSpan = null;
            private String tagForSpan = null;

            @Override
            public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
                if (ctx == null || "body".equals(tag) || "html".equals(tag)) return;
                if (opening) {
                    this.pendingSpan = PositionedSpan.forTag(ctx, tag, output.length());
                    if (this.pendingSpan != null) this.tagForSpan = tag;
                } else {
                    if (this.pendingSpan != null && tag.equals(this.tagForSpan)) {
                        this.pendingSpan.setLength(output.length() - this.pendingSpan.getPos());
                        additionalSpans.add(pendingSpan);
                    }
                    this.pendingSpan = null;
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, im, tagHandler);
            if (spanned instanceof Spannable) {
                for (PositionedSpan span : additionalSpans) {
                    span.applyToSpannable((Spannable)spanned);
                }
            }
        } else {
            /*
             Html.fromHtml in API 23 (Android 6.0) does not create BackgroundColorSpans!
             It seems that API 24 introduces the "startCssStyle()" method.
             */
            spanned = Html.fromHtml(html, im, tagHandler);
            if (spanned instanceof Spannable) {
                for (PositionedSpan span : additionalSpans) {
                    span.applyToSpannable((Spannable)spanned);
                }
            }
            // remove superfluous blank lines that have been introduced by Html.FROM_HTML_MODE_LEGACY
            spanned = replaceAll(spanned, "\n" + Content.REMOVE_NEW_LINE, "");
            // this is just to be sure that no REMOVE_NEW_LINE will be left
            spanned = replaceAll(spanned, Content.REMOVE_NEW_LINE, "");
        }
        return spanned;
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
     * Returns the display size in inches.
     * Examples:<ul>
     * <li>10 inch tablet / landscape:    8.00 x 4.50</li>
     * <li>7 inch tablet / landscape:     6.40 x 3.75</li>
     * <li>5.2 inch phone / portrait:     2.52 x 4.53</li>
     * </ul>
     * @param ctx Context
     * @return display size in inches
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static PointF getDisplayDim(@NonNull Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        final PointF size = new PointF();
        if (wm == null) return size;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        size.x = dm.widthPixels / dm.xdpi;
        size.y = dm.heightPixels / dm.ydpi;
        return size;
    }

    /**
     * Gets the height of the display in pixels. The height is adjusted based on the current rotation of the display
     * @param ctx Context
     * @return display height
     */
    public static int getDisplayHeight(@NonNull Context ctx) {
        return getDisplaySize(ctx).y;
    }

    /**
     * Gets the size of the display in pixels.
     * The size is adjusted based on the current rotation of the display.
     * @param ctx Context
     * @return Point
     */
    @NonNull
    public static Point getDisplaySize(@NonNull Context ctx) {
        final Point size = new Point();
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getSize(size);
        } catch (Exception e) {
            DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
            if (dm != null) {
                size.x = dm.widthPixels;
                size.y = dm.heightPixels;
            }
            if (BuildConfig.DEBUG) Log.e(TAG, "While getting display size: " + e + "; returning " + size, e);
        }
        return size;
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
            if (BuildConfig.DEBUG) Log.e(TAG, "getOccupiedSpace(\"" + file + "\"): " + e);
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
                if (BuildConfig.DEBUG) Log.e(TAG, "getOccupiedSpace(" + file + "): " + e);
            }
        }
        return space;
    }

    /**
     * Returns a resource's name.
     * @param ctx Context
     * @param id resource id
     * @return resource name
     */
    @NonNull
    public static String getResourceName(Context ctx, int id) {
        if (id == 0xffffffff) return "";
        Resources r = ctx.getResources();
        if (r == null) return "<NOR>";
        StringBuilder out = new StringBuilder();
        try {
            String pkgname;
            switch (id & 0xff000000) {
                case 0x7f000000:
                    pkgname = "app";
                    break;
                case 0x01000000:
                    pkgname = "android";
                    break;
                default:
                    pkgname = r.getResourcePackageName(id);
                    break;
            }
            String typename;
            try {
                typename = r.getResourceTypeName(id);
            } catch (UnsupportedOperationException ee) {
                typename = "<null>";
            }
            String entryname;
            try {
                entryname = r.getResourceEntryName(id);
            } catch (UnsupportedOperationException ee) {
                entryname = "<null>";
            }
            out.append(pkgname);
            out.append(":");
            out.append(typename);
            out.append("/");
            out.append(entryname);
        } catch (Resources.NotFoundException e) {
            out.append("0x").append(Integer.toHexString(id));
        }
        return out.toString();
    }

    /**
     * Attempts to find a specific Throwable among the causes of the given Throwable.
     * Returns the Throwable itself if it is already an instance of the given class.
     * @param e Throwable
     * @return T
     * @throws NullPointerException if any parameter is {@code null}
     */
    @Nullable
    public static <T extends Throwable> T getSpecificCause(@NonNull Throwable e, @NonNull final Class<T> causeClass) {
        do {
            if (causeClass.isAssignableFrom(e.getClass())) return causeClass.cast(e);
            e = e.getCause();
        } while (e != null);
        return null;
    }

    /**
     * @param crash Throwable
     * @return stack trace
     */
    @NonNull
    public static String getStackTrace(@NonNull Throwable crash) {
        final StringBuilder sb = new StringBuilder(2048);
        final StackTraceElement[] ste = crash.getStackTrace();
        for (StackTraceElement st : ste) {
            sb.append(st.getClassName()).append('.').append(st.getMethodName());
            if (st.isNativeMethod()) sb.append("(native)");
            String file = st.getFileName();
            if (file != null) {
                int ln = st.getLineNumber();
                if (ln >= 0) sb.append('(').append(file).append(':').append(ln).append(')');
                else sb.append('(').append(file).append(')');
            } else {
                sb.append("(unknown src)");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
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
     * Determines whether a pinned shortcut to the given Source exists.
     * @param ctx Context
     * @param source Source
     * @return true / false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static boolean hasShortcut(@NonNull Context ctx, @Nullable Source source, @Nullable ShortcutManager shortcutManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || source == null) return false;
        if (shortcutManager == null) shortcutManager = (ShortcutManager)ctx.getSystemService(Context.SHORTCUT_SERVICE);
        if (shortcutManager == null) return false;
        if (!shortcutManager.isRequestPinShortcutSupported()) {
            return false;
        }
        final List<ShortcutInfo> allExistingShortcuts = shortcutManager.getPinnedShortcuts();
        for (ShortcutInfo existingShortcut : allExistingShortcuts) {
            if (existingShortcut.getId().equals(source.name())) {
                return true;
            }
        }
        return false;
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
     * Returns {@code true} if the {@link Configuration configuration} is in night mode.
     * @param ctx Context
     * @return true/false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static boolean isNightMode(@NonNull Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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
     * @param trim if {@code true}, trim lines
     * @return List of Strings, one for each line
     * @throws IllegalArgumentException if {@code expectedNumberOfLines} is negative
     */
    @NonNull
    public static List<String> loadResourceTextFile(@NonNull Context ctx, @RawRes int rawId, @IntRange(from = 0) int expextedNumberOfLines, final boolean trim) {
        final List<String> lines = new ArrayList<>(expextedNumberOfLines);
        InputStream in = null;
        try {
            in = ctx.getResources().openRawResource(rawId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            for (; ; ) {
                String line = reader.readLine();
                if (line == null) break;
                if (trim) line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "loadResourceTextFile(..., " + rawId + ", ...): " + e);
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
                if (bounds.width() >= wx || bounds.height() >= wy) {
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
        canvas.drawText(s, wx / 2f, wy / 2f + bounds.height() / 2f - paint.getFontMetricsInt().bottom, paint);
        return bitmap;
    }

    /**
     * Converts a color resource to a css value.
     * @param ctx Context
     * @param color color resource
     * @return css value
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static String makeCssColor(@NonNull final Context ctx, @ColorRes final int color) {
        @ColorInt int value = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? ctx.getResources().getColor(color, ctx.getTheme()) : ctx.getResources().getColor(color);
        return String.format("%06X", value & ~0xff000000);
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
     * Returns an error message for the given PlaybackException.
     * @param ctx Context
     * @param error PlaybackException
     * @return error message
     * @throws NullPointerException if any parameter is {@code null}
     */
    @NonNull
    public static String playbackExceptionMsg(@NonNull Context ctx, @NonNull final PlaybackException error) {
        String msg = null;
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
            msg = ctx.getString(R.string.error_connection_interrupted);
        } else {
            for (Class<? extends Throwable> possibleCause : POSSIBLE_PLAYBACK_ERROR_CAUSES) {
                Throwable cause = getSpecificCause(error, possibleCause);
                if (cause != null) {
                    if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                        int rc = ((HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                        switch (rc) {
                            case 403:
                            case 451: msg = ctx.getString(R.string.error_http_403); break;
                            case 404: msg = ctx.getString(R.string.error_http_404); break;
                            case 410: msg = ctx.getString(R.string.error_http_410); break;
                            default: msg = cause.getMessage();
                        }
                    } else {
                        msg = cause.getMessage();
                    }
                    break;
                }
            }
            if (msg == null) msg = error.getMessage();
        }
        if (BuildConfig.DEBUG) Log.e(TAG, "Playback error: " + msg + " (" + error.getErrorCodeName() + ")");
        if (msg == null) msg = error.getErrorCodeName();
        return msg;
    }

    /**
     * Removes &lt;ul&gt; and &lt;ol&gt; tags and replaces their list items with &lt;br&gt; line feeds.<br>
     * List items in a &lt;ul&gt; list will start with •,<br>
     * list items in a &lt;ol&gt; list will start with a plain number (1,2,3 etc.).
     * @param value characters to remove the tags from
     */
    @NonNull
    public static StringBuilder removeHtmlLists(@Nullable final CharSequence value) {
        final int n = value != null ? value.length() : 0;
        if (n == 0) return new StringBuilder(0);
        final char[] c = new char[n];
        TextUtils.getChars(value, 0, n, c, 0);
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
     * Replaces each occurrence of {@code source} with {@code destination}.
     * @param template CharSequence to replace characters in
     * @param source characters to replace
     * @param destination characters to be inserted instead
     * @return SpannableStringBuilder
     * @throws NullPointerException if any parameter is {@code null}
     */
    private static SpannableStringBuilder replaceAll(@NonNull final CharSequence template, @NonNull final CharSequence source, @NonNull final CharSequence destination) {
        final SpannableStringBuilder editable = new SpannableStringBuilder(template);
        for (int pos = 0; ;) {
            int where = TextUtils.indexOf(editable, source, pos);
            if (where < 0) break;
            editable.replace(where, where + source.length(), destination);
            pos = where + destination.length();
        }
        return editable;
    }

    /**
     * Replaces each occurrence of each element in {@code sources} with the matching element in {@code destinations}.
     * @param template CharSequence to replace characters in
     * @param sources elements to replace
     * @param destinations elements to be inserted instead
     * @return SpannableStringBuilder
     * @throws NullPointerException if any parameter is {@code null}
     * @throws IllegalArgumentException if the arrays have different lengths
     */
    @NonNull
    public static SpannableStringBuilder replaceAll(@NonNull final CharSequence template, @NonNull final CharSequence[] sources, @NonNull final CharSequence[] destinations) {
        if (sources.length != destinations.length) throw new IllegalArgumentException("Non-matching lengths");
        final int n = sources.length;
        final SpannableStringBuilder editable = new SpannableStringBuilder(template);
        for (int i = 0; i < n; i++) {
            for (int pos = 0; ; ) {
                int where = TextUtils.indexOf(editable, sources[i], pos);
                if (where < 0) break;
                editable.replace(where, where + sources[i].length(), destinations[i]);
                pos = where + destinations[i].length();
            }
        }
        return editable;
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
            ctx.startActivity(Intent.createChooser(intent, null));
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
            if (BuildConfig.DEBUG) Log.e(TAG, "sendBitmap(): " + e);
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
            ctx.startActivity(Intent.createChooser(intent, null));
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
        TextView textView = snackLayout.findViewById(com.google.android.material.R.id.snackbar_action);
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
        TextView textView = snackLayout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView == null) return;
        if (font != null) textView.setTypeface(font);
        if (textSize >= 1f) textView.setTextSize(textSize);
    }

    /**
     * Allow more lines of text in a Snackbar.
     * @param s Snackbar
     * @param maxLines max. number of text lines
     */
    public static void setSnackbarMaxLines(@Nullable Snackbar s, @IntRange(from = 1) int maxLines) {
        if (s == null) return;
        Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) s.getView();
        TextView textView = snackLayout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView == null) return;
        textView.setMaxLines(maxLines);
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
