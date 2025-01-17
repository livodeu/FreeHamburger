package de.freehamburger.util;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.Os;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.Size;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.xml.sax.XMLReader;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerActivity;
import de.freehamburger.HamburgerService;
import de.freehamburger.MainActivity;
import de.freehamburger.PictureActivity;
import de.freehamburger.R;
import de.freehamburger.model.Content;
import de.freehamburger.model.News;
import de.freehamburger.model.Source;
import de.freehamburger.model.TeaserImage;
import de.freehamburger.supp.ShareReceiver;

/**
 *
 */
public class Util {

    public static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    public static final String PROTOCOL_ANDROID_APP = "android-app://";
    /** Determines whether this is a test build. */
    public static final boolean TEST;
    private static final long MS_PER_MINUTE = 60_000L;
    private static final long MS_PER_HOUR = 60 * MS_PER_MINUTE;
    private static final long MS_PER_DAY = 24 * MS_PER_HOUR;
    private static final long MS_PER_WEEK = 7 * MS_PER_DAY;
    private static final long MS_PER_MONTH = 365 * MS_PER_DAY / 12;
    private static final long MS_PER_YEAR = 365 * MS_PER_DAY;
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    /** Throwables that might carry important information about a playback failure */
    private static final Collection<Class<? extends Throwable>> POSSIBLE_PLAYBACK_ERROR_CAUSES = Arrays.asList(UnknownHostException.class, SSLPeerUnverifiedException.class, HttpDataSource.InvalidResponseCodeException.class);
    private static final String TAG = "Util";
    /**
     * Selection of wrong quotation marks<br>
     * <pre>
     * 0x0022   0x201d  0x201f
     * "        ”       ‟
     * </pre>
     */
    private static final char[] WRONG_QUOTES = new char[] {'\u0022', '\u201d', '\u201f'};

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

    @NonNull
    public static SpannableString createClickable(@NonNull final Context ctx, @NonNull CharSequence cs, @NonNull final Intent intent) {
        SpannableString spannableString = new SpannableString(cs);
        spannableString.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                try {
                    ctx.startActivity(intent);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                }
            }
        }, 0, cs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableString;
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
        try {
            return shortcutManager.requestPinShortcut(pinShortcutInfo, null);
        } catch (IllegalStateException ignored) {
        }
        return false;
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
     * Puts the longest out of a selection of texts that fits into a TextView.
     * @param tv TextView
     * @param placeholder (optional)
     * @param txts alternative texts, <em>ordered from longest to shortest</em>
     */
    public static void fitText(@NonNull final TextView tv, @Nullable CharSequence placeholder, @NonNull @Size(min = 1) final String... txts) {
        int w = tv.getWidth() - tv.getPaddingStart() - tv.getPaddingEnd() - 8;
        if (w <= 0) {
            tv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right - left <= 0) return;
                    tv.removeOnLayoutChangeListener(this);
                    fitText(tv, placeholder, txts);
                }
            });
            // sad to say, but the next line has been added to satisfy a test checking for non-empty content (this will probably never show since the view's width is <= 0)
            tv.setText(placeholder);
            //
            return;
        }
        final TextPaint tp = tv.getPaint();
        final Rect r = new Rect();
        for (String txt : txts) {
            tp.getTextBounds(txt, 0, txt.length(), r);
            if (r.width() <= w) {
                tv.setEllipsize(null);
                tv.setText(txt);
                return;
            }
        }
        if (txts.length == 0) return;
        tv.setText(txts[txts.length - 1]);
    }

    /**
     * Removes leading and trailing '+' from a given String.
     * @param value String to work on
     * @return String without leading or trailing pluses
     */
    @NonNull
    public static String fixPlus(@Nullable final String value) {
        if (value == null) return "";
        final int l = value.length();
        if (l == 0) return "";
        int start = 0, end = l - 1;
        while (start < l && value.charAt(start) == '+') start++;
        if (start == l) return "";
        while (end >= 0 && value.charAt(end) == '+') end--;
        if (end == 0) return "";
        if (start == 0 && end == l - 1) return value;
        return value.substring(start, end + 1).trim();
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

    @NonNull
    public static String formatFloatTime(@FloatRange(from = 0f, to = 24f) float t) {
        int h = (int)t;
        int m = (int)(60f * (t - h));
        return String.format(Locale.getDefault(), "%02d:%02d", h, m);
    }

    /**
     * Formats a timestamp using the given format.
     * @param dateFormat format to use
     * @param ts timestamp to format
     * @param defaultValue default value to return in case of parsing/formatting failure
     * @return formatted value or null
     */
    @Nullable
    public static String formatTs(@Nullable DateFormat dateFormat, long ts, @Nullable String defaultValue) {
        if (dateFormat == null) {
            return defaultValue;
        }
        try {
            return dateFormat.format(new Date(ts));
        } catch (Exception re) {
            if (BuildConfig.DEBUG) {
                if (dateFormat instanceof SimpleDateFormat) Log.e(TAG, "Cannot parse/format " + ts + " with " + ((SimpleDateFormat)dateFormat).toPattern() +  ": " + re, re);
                else Log.e(TAG, "Cannot parse/format " + ts + ": " + re, re);
            }
        }
        return defaultValue;
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
     * Attempts to determine the MIME type for the given resource.
     * @param fileName resource
     * @param defaultValue default MIME type value
     * @return MIME type value
     */
    @Nullable
    public static String getMime(final String fileName, @Nullable final String defaultValue) {
        if (fileName == null) return defaultValue;
        String mime = null;
        int posFiletag = fileName.lastIndexOf('.');
        if (posFiletag > 0 && posFiletag < fileName.length() - 1) {
            String filetag = fileName.substring(posFiletag + 1);
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filetag);
        }
        if (mime == null) {
            if (fileName.endsWith(".mp4")) {
                mime = "video/mp4";
            } else if (fileName.endsWith(".mp3")) {
                mime = "audio/mpeg";
            } else if (fileName.endsWith(".ogg")) {
                mime = "application/ogg";
            } else if (fileName.endsWith(".jpg")) {
                mime = "image/jpeg";
            } else if (fileName.endsWith(".m3u8")) {
                mime = "application/vnd.apple.mpegurl";
            } else {
                mime = defaultValue;
            }
        }
        return mime;
    }

    /**
     * Returns the mode that the navigation bar is operating under.
     * <ul>
     * <li>0: 3-button-mode</li>
     * <li>1: 2-button-mode</li>
     * <li>2: gesture mode</li>
     * </ul>
     * <hr>
     * Btw., on a device this can be set under Settings -> System -> Gestures -> System navigation.
     * @param ctx Context
     * @return navigation mode or -1
     */
    @IntRange(from = -1, to = 2)
    public static int getNavigationMode(@NonNull Context ctx) {
        try {
            return Settings.Secure.getInt(ctx.getContentResolver(), "navigation_mode", -1);
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Returns the Space occupied by a file or directory.<br>
     * Will return 0 if the file does not exist or if it could not be accessed.
     * @param file file <em>or</em> directory
     * @return space used in bytes
     */
    @RequiresApi(21)
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
            if (file.isDirectory()) {
                File[] subdirFiles = file.listFiles();
                if (subdirFiles != null) space += getOccupiedSpace(Arrays.asList(subdirFiles));
                continue;
            }
            try {
                // If <a href="https://www.man7.org/linux/man-pages/man2/stat.2.html">this</a> is applicable, the number of blocks must always be multiplied with 512.
                space += Os.stat(file.getAbsolutePath()).st_blocks << 9;
            } catch (ErrnoException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "getOccupiedSpace(" + file + "): " + e);
            }
        }
        return space;
    }

    public static String getRelativeTime(@NonNull Context ctx, long timestamp, @Nullable Date basedOn) {
        return getRelativeTime(ctx, timestamp, basedOn, false);
    }

    /**
     * Returns a textual representation of a time difference, e.g. "2¼ hours ago".
     * @param ctx Context
     * @param timestamp timestamp
     * @param basedOn Date (set to {@code null} to compare time to current time)
     * @param skirt {@code true} to return a short variant
     * @return relative time
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    public static String getRelativeTime(@NonNull Context ctx, long timestamp, @Nullable Date basedOn, final boolean skirt) {
        if (timestamp == 0L) return "";
        final long delta;
        if (basedOn == null) {
            delta = Math.abs(timestamp - System.currentTimeMillis());
        } else {
            delta = Math.abs(timestamp - basedOn.getTime());
        }
        final Resources res = ctx.getResources();
        if (delta < MS_PER_MINUTE) {
            int seconds = (int)(delta / 1_000L);
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_seconds_short : R.plurals.label_time_rel_seconds, seconds, seconds);
        }
        if (delta < MS_PER_HOUR) {
            int minutes = (int)(delta / MS_PER_MINUTE);
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_minutes_short : R.plurals.label_time_rel_minutes, minutes, minutes);
        }
        if (delta < MS_PER_DAY) {
            double dhours = delta / ((double)MS_PER_HOUR);
            int hours = (int)dhours;
            final double frac = dhours - (double)hours;
            if (frac >= 0.125 && frac < 0.375) {
                return res.getQuantityString(skirt ? R.plurals.label_time_rel_hours1_short : R.plurals.label_time_rel_hours1, hours, hours);
            }
            if (frac >= 0.375 && frac < 0.625) {
                return res.getQuantityString(skirt ? R.plurals.label_time_rel_hours2_short : R.plurals.label_time_rel_hours2, hours, hours);
            }
            if (frac >= 0.625 && frac < 0.875) {
                return res.getQuantityString(skirt ? R.plurals.label_time_rel_hours3_short : R.plurals.label_time_rel_hours3, hours, hours);
            }
            if (frac >= 0.875) hours++;
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_hours_short : R.plurals.label_time_rel_hours, hours, hours);
        }
        if (delta < MS_PER_WEEK) {
            int days = (int)(delta / MS_PER_DAY);
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_days_short : R.plurals.label_time_rel_days, days, days);
        }
        if (delta < MS_PER_MONTH) {
            int weeks = (int)(delta / MS_PER_WEEK);
            int days = (int)((delta - (weeks * MS_PER_WEEK)) / MS_PER_DAY);
            if (days >= 1) {
                return res.getQuantityString(skirt ? R.plurals.label_time_rel_weeks_plus_short : R.plurals.label_time_rel_weeks_plus, weeks, weeks);
            }
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_weeks_short : R.plurals.label_time_rel_weeks, weeks, weeks);
        }
        if (delta < MS_PER_YEAR) {
            int months = (int) (delta / MS_PER_MONTH);
            int weeks = (int) ((delta - (months * MS_PER_MONTH)) / MS_PER_WEEK);
            if (weeks >= 2) {
                return res.getQuantityString(skirt ? R.plurals.label_time_rel_months2_short : R.plurals.label_time_rel_months2, months, months);
            }
            return res.getQuantityString(skirt ? R.plurals.label_time_rel_months_short : R.plurals.label_time_rel_months, months, months);
        }
        int years = (int)(delta / MS_PER_YEAR);
        return res.getQuantityString(skirt ? R.plurals.label_time_rel_years_short : R.plurals.label_time_rel_years, years, years);
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
     * Goes into fullscreen mode; the navigation and action bars will be visible, the status bar will be hidden.
     * @param a AppCompatActivity
     */
    public static void goFullScreen(@NonNull AppCompatActivity a) {
        ActionBar actionBar = a.getSupportActionBar();
        if (actionBar != null) actionBar.show();
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
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
     * Prepares the given Intent to be logged.<br>
     * Just in case anyone asked: for debugging only.
     * @param intent Intent to log
     * @param indent number of spaces by which to indent the output
     * @return CharSequence
     */
    @NonNull
    private static CharSequence intentAsCharSequence(@NonNull final Intent intent, final int indent) {
        final StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < indent; i++) sb.append(' ');
        sb.append(intent);
        // MIME
        String mime = intent.getType();
        sb.append("\n");
        for (int i = 0; i < indent; i++) sb.append(' ');
        sb.append("Type: ").append(mime);
        // Uri
        Uri uri = intent.getData();
        sb.append("\n");
        for (int i = 0; i < indent; i++) sb.append(' ');
        sb.append("Data: ").append(uri);
        // Category
        final Set<String> cats = intent.getCategories();
        if (cats != null && !cats.isEmpty()) {
            for (String cat : cats) {
                sb.append("\n");
                for (int i = 0; i < indent; i++) sb.append(' ');
                sb.append("Category: \"").append(cat).append("\"");
            }
        }
        // Flags
        final int flax = intent.getFlags();
        if (flax != 0) {
            try {
                final Field[] fields = Intent.class.getDeclaredFields();
                for (Field field : fields) {
                    if (field.getName().contains("FLAG")) {
                        int val = field.getInt(Intent.class);
                        if ((flax & val) > 0) {
                            sb.append("\n");
                            for (int i = 0; i < indent; i++) sb.append(' ');
                            sb.append("⛳ ").append(field.getName()).append(" (0x").append(Integer.toHexString(val)).append(")");
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        final Bundle e = intent.getExtras();
        if (e != null) {
            sb.append('\n');
            for (int i = 0; i < indent; i++) sb.append(' ');
            final Set<String> keys = e.keySet();
            for (String key : keys) {
                sb.append(key).append("=");
                Object value = e.get(key);
                if (value == null) {
                    sb.append("<null>\n");
                    continue;
                }
                if (value instanceof Intent) {
                    sb.append(intentAsCharSequence((Intent)value, indent + 4).toString().trim()).append("\n");
                    continue;
                }
                if (value.getClass().isArray()) {
                    try {
                        sb.append(Arrays.toString((Object[]) value));
                    } catch (Exception ignored) {
                        sb.append(value);
                    }
                } else {
                    sb.append(value);
                }
                sb.append('\n');
                for (int i = 0; i < indent; i++) sb.append(' ');
            }
        }
        return sb;
    }

    /**
     * Determines whether the given menu has at least one item that is visible and enabled.
     * @param menu Menu to inspect
     * @return true / false
     */
    public static boolean isAnyMenuItemEnabled(@Nullable final Menu menu) {
        if (menu == null) return false;
        final int n = menu.size();
        for (int i = 0; i < n; i++) {
            MenuItem item = menu.getItem(i);
            if (item == null) continue;
            if (item.isVisible() && item.isEnabled()) return true;
        }
        return false;
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
        int type = networkInfo.getType();
        return (type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE_DUN);
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
     * Determines whether the most recent process exit was caused by the user stopping the app.<br>
     * Can be caused via <pre>adb shell cmd activity stop-app &lt;package&gt;</pre>
     * @param ctx Context
     * @return true / false
     */
    @TargetApi(Build.VERSION_CODES.R)
    public static boolean isRecentExitStopApp(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        ActivityManager am = (ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(ctx.getPackageName(), 0, 0);
        if (exits.isEmpty()) return false;
        ApplicationExitInfo latest = exits.get(0);
        int subReason = 0;
        try {
            String s = latest.toString();
            int sr0 = s.indexOf("subreason=");
            int sr1 = s.indexOf(' ', sr0 + 10);
            if (sr0 >= 0 && sr1 > sr0) subReason = Integer.parseInt(s.substring(sr0 + 10, sr1));
        } catch (Exception ignored) {
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "Latest process exit at "
                + DateFormat.getDateTimeInstance().format(new java.util.Date(latest.getTimestamp()))
                + " (Reason "
                + latest.getReason()
                + ", sub-reason "
                + subReason
                + "): "
                + latest.getDescription());
        return latest.getReason() == ApplicationExitInfo.REASON_USER_REQUESTED && subReason == 23;
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
     * Logs the given Intent (in debug versions only).
     * @param intent Intent to log
     */
    private static void logIntent(Intent intent) {
        if (intent == null || !BuildConfig.DEBUG) return;
        Log.i(TAG, intentAsCharSequence(intent, 0).toString());
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
     * Also prepends https before urls without a protocol.
     * @param url http(s) url
     * @return https url
     * @throws NullPointerException if {@code url} is {@code null}
     */
    @NonNull
    public static String makeHttps(@NonNull final String url) {
        return url.toLowerCase(Locale.US).startsWith("http:") ? "https:" + url.substring(5)
                : url.startsWith("//") ? "https:" + url
                : url.startsWith("/") ? "https:/" + url
                : url;
    }

    @NonNull
    public static Snackbar makeSnackbar(@NonNull Activity a, @StringRes int resid, @IntRange(from = -2) int duration) {
        return makeSnackbar(a, a.getString(resid), duration);
    }

    @NonNull
    public static Snackbar makeSnackbar(@NonNull Activity a, @NonNull CharSequence text, @IntRange(from = -2) int duration) {
        View anchor = null;
        if (a instanceof CoordinatorLayoutHolder) anchor = ((CoordinatorLayoutHolder)a).getCoordinatorLayout();
        if (anchor == null) anchor = a.getWindow().getDecorView();
        return Snackbar.make(anchor, text, duration);
    }

    /**
     * Simulates up-down taps near the right edge of the given DrawerLayout.
     * @param drawerLayout DrawerLayout
     * @param handler Handler
     * @throws NullPointerException if {@code drawerLayout} is {@code null}
     */
    public static void peekRightDrawer(@NonNull final DrawerLayout drawerLayout, @Nullable Handler handler) {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        final int x = drawerLayout.getWidth() - 50;
        final int y = 50;
        final long now = SystemClock.uptimeMillis();
        final long upDelay = 500L;
        MotionEvent motionEventDown = MotionEvent.obtain(now, now + 50L, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent motionEventUp = MotionEvent.obtain(now, now + 50L + upDelay, MotionEvent.ACTION_UP, x, y, 0);
        drawerLayout.dispatchTouchEvent(motionEventDown);
        handler.postDelayed(() -> {
            drawerLayout.dispatchTouchEvent(motionEventUp);
            motionEventUp.recycle();
            }, upDelay);
        motionEventDown.recycle();
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
     * Replaces a HTML table with simple &lt;br&gt;-separated lines where each table row is wrapped in &lt;tbl&gt;…&lt;/tbl&gt;.
     * See {@link PositionedSpan#TAG_TABLE}.
     * @param in CharSequence
     * @return CharSequence
     */
    @NonNull
    public static CharSequence replaceHtmlTable(@NonNull CharSequence in) {
        final SpannableStringBuilder sb = replaceAll(in, new String[] {
            "<table>", "<tbody>",
                "<caption>", "</caption>",
                "<th>", "</th>",
                "<tr>", "<td>", "</td>", "</tr>",
                "</tbody>", "</table>"
        }, new CharSequence[] {
            "", "",
                "<i>", "</i><br>",
                "&nbsp;", "",
                "<br>" + PositionedSpan.TAG_TABLE_OPENING, "&nbsp;", "", PositionedSpan.TAG_TABLE_CLOSING,
                "", ""
        });
        // if a <br> comes first, remove it
        int firstBr = TextUtils.indexOf(sb, "<br>");
        int firstTbl = TextUtils.indexOf(sb, PositionedSpan.TAG_TABLE_OPENING);
        if (firstBr >= 0 && firstBr < firstTbl) {
            sb.delete(firstBr, firstBr + 4);
        }
        return sb;
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
        Uri uri = Uri.parse(url);
        String mime = getMime(url, "video/*");
        if ("content".equals(uri.getScheme())) {
            intent.setDataAndType(uri, mime);
            intent.setClipData(ClipData.newUri(ctx.getContentResolver(), title, uri));
        } else {
            intent.setType(mime);
        }
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
        if (title != null) {
            intent.putExtra(Intent.EXTRA_TITLE, title);
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
        }
        if (!(ctx instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(PROTOCOL_ANDROID_APP + ctx.getPackageName()));
        final Intent chooserIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // 2022-03: FYG! Nowhere, repeat: NOWHERE, in the frigging Android docs any trace of the fact that the PendingIntent needs the FLAG_MUTABLE flag set!!!
            @SuppressLint("InlinedApi")
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, ShareReceiver.class), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            chooserIntent = Intent.createChooser(intent, null, pi.getIntentSender());
        } else {
            chooserIntent = Intent.createChooser(intent, null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, App.EXCLUDED_SEND_TARGETS);
        }
        logIntent(intent);
        if (ctx.getPackageManager().resolveActivity(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            ctx.startActivity(chooserIntent);
        }
    }

    /**
     * Shares the given Bitmap via {@link Intent#ACTION_SEND ACTION_SEND}.
     * Does not always display an error message in case of failure.<br>
     * Delegates to {@link #sendBinaryData(Context, String, CharSequence)}.
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
     * @param preview (optional)
     * @throws NullPointerException if {@code ctx} or {@code url} are {@code null}
     */
    public static void sendUrl(@NonNull Context ctx, @NonNull String url, @Nullable CharSequence title, @Nullable Bitmap preview) {
        url = makeHttps(url);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        if (BuildConfig.DEBUG) intent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
        intent.putExtra(Intent.EXTRA_TEXT, url);
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra(Intent.EXTRA_TITLE, title);
            intent.putExtra(Intent.EXTRA_SUBJECT, title);
        }
        Uri uri = Uri.parse(url);
        if ("content".equals(uri.getScheme())) {
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri(title, uri));
        }
        intent.setType(getMime(uri.getLastPathSegment(), "text/plain"));
        //
        if (preview != null) {
            OutputStream out = null;
            try {
                File exportsDir = new File(ctx.getCacheDir(), App.EXPORTS_DIR);
                if (exportsDir.isDirectory() || exportsDir.mkdirs()) {
                    File tmp = File.createTempFile("tmp", ".jpg", exportsDir);
                    out = new FileOutputStream(tmp);
                    preview = Bitmap.createScaledBitmap(preview, preview.getWidth() >> 2, preview.getHeight() >> 2, true);
                    preview.compress(Bitmap.CompressFormat.JPEG, 50, out);
                    close(out);
                    out = null;
                    Uri thumbnail = FileProvider.getUriForFile(ctx, App.getFileProvider(), tmp);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setClipData(new ClipData("thumbnail", new String[]{"image/jpeg"}, new ClipData.Item(thumbnail)));
                }
            } catch (IOException e) {
                if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            } finally {
                close(out);
            }
        }
        //
        if (!(ctx instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse(PROTOCOL_ANDROID_APP + ctx.getPackageName()));
        if (ctx.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            final Intent chooserIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                @SuppressLint("InlinedApi")
                PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, ShareReceiver.class), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                chooserIntent = Intent.createChooser(intent, null, pi.getIntentSender());
            } else {
                chooserIntent = Intent.createChooser(intent, null);
            }
            if (BuildConfig.DEBUG) chooserIntent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, App.EXCLUDED_SEND_TARGETS);
            }
            if (intent.getType() != null && intent.getType().startsWith("image/")) {
                Intent view = new Intent(Intent.ACTION_VIEW);
                view.setData(uri);
                view.setComponent(new ComponentName(ctx.getPackageName(), PictureActivity.class.getName()));
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{
                        new LabeledIntent(view, ctx.getPackageName(), ctx.getString(R.string.action_view), 0)
                });
            }
            logIntent(chooserIntent);
            ctx.startActivity(chooserIntent);
        } else {
            if (ctx instanceof Activity) {
                makeSnackbar((Activity)ctx, R.string.error_no_app, Snackbar.LENGTH_LONG).show();
            } else {
                Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Shares the given url via {@link Intent#ACTION_SEND ACTION_SEND}. Displays an error message if there isn't any suitable app installed.
     * @param ctx Context
     * @param url URL
     * @param title title (optional)
     * @throws NullPointerException if {@code ctx} or {@code url} are {@code null}
     */
    public static void sendUrl(@NonNull Context ctx, @NonNull String url, @Nullable CharSequence title) {
        sendUrl(ctx, url, title, null);
    }

    /**
     * Shares the given url via {@link Intent#ACTION_SEND ACTION_SEND}. Displays an error message if there isn't any suitable app installed.<br>
     * Uses a preview image from the given News object, if available.<br>
     * If no News object is given, identical to {@link #sendUrl(Context, String, CharSequence)}.
     * @param activity HamburgerActivity
     * @param url URL
     * @param title title (optional)
     * @param news News to take a preview image from (optional)
     * @throws NullPointerException if {@code activity} or {@code url} are {@code null}
     */
    @AnyThread
    public static void sendUrlWithNewsPreview(@NonNull HamburgerActivity activity, @NonNull String url, @Nullable CharSequence title, @Nullable News news) {
        final HamburgerService service = activity.getHamburgerService();
        if (service == null || news == null) {
            sendUrl(activity, url, title);
            return;
        }
        TeaserImage teaserImage = news.getTeaserImage();
        final String previewUrl = teaserImage != null ? teaserImage.getBestImage() : null;
        if (previewUrl == null) {
            sendUrl(activity, url, title);
            return;
        }
        new Thread() {
            @Override public void run() {
                // this must run on a worker thread
                Bitmap preview = service.loadImageFromCache(previewUrl);
                //
                new Handler(Looper.getMainLooper()).post(() -> sendUrl(activity, url, title, preview));
            }
        }.start();
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
     * Makes links within a Snackbar clickable.
     * @param s Snackbar
     */
    public static void setSnackbarLinksClickable(@Nullable Snackbar s) {
        if (s == null) return;
        Snackbar.SnackbarLayout snackLayout = (Snackbar.SnackbarLayout) s.getView();
        TextView textView = snackLayout.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView == null) return;
        textView.setMovementMethod(LinkMovementMethod.getInstance());
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
     * @param activity Activity
     * @param rawRes raw res
     * @param webView WebView to (re-)use
     * @return AlertDialog
     */
    @NonNull
    public static AlertDialog showHelp(@NonNull Activity activity, @RawRes int rawRes, @NonNull final WebView webView) {
        byte[] b = new byte[2048];
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        InputStream in = null;
        try {
            in = activity.getResources().openRawResource(rawRes);
            for (;;) {
                int read = in.read(b);
                if (read < 0) break;
                //noinspection ObjectAllocationInLoop
                sb.append(new String(b, 0, read));
            }
        } catch (Exception ignored) {
        } finally {
            close(in);
        }
        String textColorForCss = makeCssColor(activity, R.color.color_onPrimaryContainer);
        String aColorForCss = makeCssColor(activity, R.color.colorAccent);
        String bgColorForCss = makeCssColor(activity, R.color.color_primaryContainer);
        CharSequence cs = TextUtils.replace(sb,
                new String[] {"<style></style>"},
                new CharSequence[] {
                        "<style>body{color:#" + textColorForCss + ";background:#" + bgColorForCss + ";margin:10px 20px 0px 20px}a{color:#" + aColorForCss + ";}</style>"
                }
        );
        webView.loadDataWithBaseURL("about:blank", cs.toString(), "text/html", "UTF-8", null);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.action_help)
                .setView(webView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                ;
        final AlertDialog ad = builder.create();
        Window w = ad.getWindow();
        ad.setCanceledOnTouchOutside(true);
        ad.setOnCancelListener(dialog -> {
            ViewParent p = webView.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup)p).removeView(webView);
            }
        });
        ad.setOnDismissListener(dialog -> {
            ViewParent p = webView.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup)p).removeView(webView);
            }
        });
        try {
            ad.show();
            // add some space below the dialog title
            ViewGroup topPanel = ad.findViewById(R.id.topPanel);
            if (topPanel != null) topPanel.setPadding(0, 0, 0, 28);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        return ad;
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

    @NonNull
    public static String trimNumber(@NonNull String s) {
        char firstChar;
        int length = s.length();
        int start = 0;
        while (start < length && ((firstChar = s.charAt(start)) < '+' || firstChar == ',' || firstChar == '/' || firstChar > '9')) {
            start++;
        }
        while (start < length) {
            char lastChar = s.charAt(length - 1);
            if (lastChar >= '0' && lastChar <= '9') {
                break;
            }
            length--;
        }
        return (start > 0 || length < s.length()) ? s.substring(start, length) : s;
    }

    /**
     * Determines whether the device is configured to use gesture navigation.
     * @param ctx Context
     * @return true / false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static boolean usesGestureNavigation(@NonNull Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        return Settings.Secure.getInt(ctx.getContentResolver(), "navigation_mode", -1) == 2;
    }

}
