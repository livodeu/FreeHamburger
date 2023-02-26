package de.freehamburger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.freehamburger.adapters.ArchivedNewsAdapter;
import de.freehamburger.model.ArchivedNews;
import de.freehamburger.model.News;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

/**
 * Manages News items that have been stored on the device.
 */
public class Archive extends HamburgerActivity implements ActivityResultCallback<Uri> {

    /** subfolder within the app's files folder */
    @VisibleForTesting
    static final String DIR = "archive";
    /** size limit [bytes] for zip archives about to be imported */
    private static final long ARCHIVE_MAX_SIZE = 10_000_000L;
    /** the name of the zip file being exported */
    private static final String EXPORT_FILE_NAME = "archive.zip";
    /** find invalid chars in {@link android.os.FileUtils} */
    @Size(4) private static final CharSequence[] INVALID_CHARS = new CharSequence[] {"/", "\\", ">", "*"};
    /** division slash (2215), -, -, asterisk operator (2217) */
    @Size(4) private static final CharSequence[] INVALID_CHARS_REPLACEMENT = new CharSequence[] {"∕", "-", "-", "∗"};
    /** MIME type of zipped archive data */
    private static final String MIME_ARCHIVE = "application/zip";
    private static final String TAG = "Archive";
    private File folder;
    private List<File> files;
    private RecyclerView recyclerViewArchive;
    private ArchivedNewsAdapter adapter;
    /** retrieves data of the {@link #MIME_ARCHIVE} type (for import) */
    private ActivityResultLauncher<String> getZipContent;
    /** checks zip archives before they are unwrapped */
    private ArchiveChecker archiveChecker;
    private boolean goBackToSettings;

    /**
     * Determines whether there are any News items stored.
     * @param ctx Context
     * @return true / false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    static boolean hasItems(@NonNull Context ctx) {
        File folder = new File(ctx.getFilesDir(), DIR);
        if (!folder.isDirectory()) return false;
        String[] files = folder.list();
        return files != null && files.length > 0;
    }

    /**
     * Determines whether the given News item has been archived.
     * @param ctx Context
     * @param news News
     * @return true / false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    static boolean isArchived(@NonNull Context ctx, @Nullable News news) {
        if (news == null) return false;
        final File folder = new File(ctx.getFilesDir(), DIR);
        if (!folder.isDirectory()) return false;
        // check for both file tags, .news and .rnews
        final String fileName = makeFilename(news);
        int dot = fileName.lastIndexOf('.');
        String name = fileName.substring(0, dot);
        String tag = fileName.substring(dot);
        File f1 = new File(folder, fileName);
        File f2 = new File(folder, News.FILE_TAG_REGIONAL.equals(tag) ? name + News.FILE_TAG : name + News.FILE_TAG_REGIONAL);
        return f1.isFile() || f2.isFile();
    }

    /**
     * Loads all files from the given folder.
     * @param folder folder to load from
     * @return List of Files
     * @throws NullPointerException if {@code folder} is {@code null}
     */
    @NonNull
    private static List<File> loadAll(@NonNull File folder) {
        File[] existing = folder.listFiles();
        return (existing != null) ? Arrays.asList(existing) : new ArrayList<>(0);
    }

    /**
     * Generates a file name suitable to store the json data in for the given News.
     * @param news News
     * @return file name
     * @throws NullPointerException if {@code news} is {@code null}
     */
    @NonNull
    @VisibleForTesting
    static String makeFilename(@NonNull final News news) {
        final StringBuilder sb = new StringBuilder(24);
        if (news.hasTitle()) sb.append(news.getTitle());
        else if (!TextUtils.isEmpty(news.getTopline())) sb.append(news.getTopline());
        else if (!TextUtils.isEmpty(news.getFirstSentence())) sb.append(news.getFirstSentence());
        else if (news.getExternalId() != null) sb.append(news.getExternalId());
        else sb.append(news.getId());
        sb.append(news.isRegional() ? News.FILE_TAG_REGIONAL : News.FILE_TAG);
        // the next bit isn't probably ever necessary, but one never knows…
        while (sb.charAt(0) == '.') sb.deleteCharAt(0); // no hidden files please
        if (TextUtils.indexOf(sb, '.') < 0) sb.insert(0, System.currentTimeMillis()).append('.'); // what if the News' title was "......"?
        //
        return Util.replaceAll(sb, INVALID_CHARS, INVALID_CHARS_REPLACEMENT).toString().trim();
    }

    /**
     * Stores the json data for a News instance.
     * Will overwrite existing files!
     * @param ctx Context
     * @param news News
     * @param json File containing the original json data to store
     * @return true / false
     * @throws NullPointerException if any parameter is {@code null}
     */
    static boolean saveNews(@NonNull Context ctx, @NonNull News news, @NonNull File json) {
        File folder = new File(ctx.getFilesDir(), DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) return false;
        File newArchivedJsonFile = new File(folder, makeFilename(news));
        if (BuildConfig.DEBUG && newArchivedJsonFile.isFile()) Log.w(TAG, "Overwriting " + newArchivedJsonFile);
        boolean ok = Util.copyFile(json, newArchivedJsonFile, Long.MAX_VALUE);
        if (!ok) {
            Util.deleteFile(newArchivedJsonFile);
        } else if (news.getDate() != null) {
            //noinspection ResultOfMethodCallIgnored
            newArchivedJsonFile.setLastModified(news.getDate().getTime());
        }
        return ok;
    }

    /**
     * Deletes all known archive files.
     */
    private void deleteAllWithoutConfirmation() {
        for (File file : this.files) {
            Util.deleteFile(file);
        }
        loadFiles();
    }

    /**
     * Offers to delete the given ArchivedNews at the given position.
     * @param archivedNews archived News to delete
     * @param position adapter position
     * @throws NullPointerException if {@code archivedNews} is {@code null}
     */
    @SuppressLint("NotifyDataSetChanged")
    public void deleteWithConfirmation(@NonNull final ArchivedNews archivedNews, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.label_confirmation)
                .setMessage(getString(R.string.msg_delete_confirm, archivedNews.getDisplayName()))
                .setIcon(R.drawable.ic_warning_red_24dp)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    Util.deleteFile(archivedNews.getFile());
                    if (position >= 0 && position < this.adapter.getItemCount()) {
                        this.adapter.remove(position);
                        this.adapter.notifyItemRemoved(position);
                    } else {
                        this.adapter.notifyDataSetChanged();
                    }
                    if (this.adapter.getItemCount() == 0) {
                        finish();
                    } else {
                        loadFiles();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                ;
        builder.show();
    }

    /**
     * Exports archived data packed into a zip archive.
     */
    private void exportArchive() {
        if (this.files.isEmpty()) return;
        ZipOutputStream out = null;
        InputStream in = null;
        try {
            File exportsDir = new File(getCacheDir(), App.EXPORTS_DIR);
            if (!exportsDir.isDirectory() && !exportsDir.mkdirs()) {
                Util.makeSnackbar(this, R.string.error_archive_export_failed, Snackbar.LENGTH_SHORT).show();
                return;
            }
            final String label = getString(R.string.label_archive);
            File zip = new File(exportsDir, EXPORT_FILE_NAME);
            out = new ZipOutputStream(new FileOutputStream(zip));
            out.setLevel(Deflater.BEST_COMPRESSION);
            out.setMethod(ZipOutputStream.DEFLATED);
            out.setComment(getString(R.string.app_name) + " " + label + " " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new java.util.Date()));
            final byte[] buf = new byte[8192];
            for (File file : this.files) {
                if (!file.isFile()) continue;
                ZipEntry entry = new ZipEntry(file.getName());
                entry.setTime(file.lastModified());
                out.putNextEntry(entry);
                in = new FileInputStream(file);
                for (;;) {
                    int read = in.read(buf, 0, buf.length);
                    if (read <= 0) break;
                    out.write(buf, 0, read);
                }
                Util.close(in);
                in = null;
                out.closeEntry();
            }
            Util.close(out);
            out = null;
            Util.sendBinaryData(this, FileProvider.getUriForFile(this, App.getFileProvider(), zip).toString(), label);
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
            Util.makeSnackbar(this, R.string.error_archive_export_failed, Snackbar.LENGTH_SHORT).show();
        } finally {
            Util.close(in, out);
        }
    }

    /** {@inheritDoc} */
    @Override int getMainLayout() {
        return R.layout.activity_archive;
    }

    /** {@inheritDoc} */
    @Override boolean hasMenuOverflowButton() {
        return true;
    }

    /**
     * Imports archived data from zip data.
     * @param uri Uri that points to zip data
     */
    private void importArchive(@Nullable final Uri uri) {
        if (uri == null || this.files == null) return;
        final List<File> filesSnapshot = new ArrayList<>(this.files);
        if (!this.folder.isDirectory()) {
            if (!this.folder.mkdirs()) return;
        }
        new Thread() {
            @Override public void run() {
                InputStream in = null;
                OutputStream out = null;
                int restoredCounter = 0;
                int skippedCounter = 0;
                try {
                    in = getContentResolver().openInputStream(uri);
                    ZipInputStream zin = new ZipInputStream(in);
                    final byte[] buf = new byte[8192];
                    File restored;
                    for (;;) {
                        ZipEntry entry = zin.getNextEntry();
                        if (entry == null) break;
                        boolean alreadyExists = false;
                        for (File file : filesSnapshot) {
                            if (entry.getName().equals(file.getName())) {alreadyExists = true; break;}
                        }
                        if (alreadyExists) {skippedCounter++; if (BuildConfig.DEBUG) Log.i(TAG, "Skipping "+ entry.getName()); continue;}
                        restored = new File(Archive.this.folder, entry.getName());
                        out = new BufferedOutputStream(new FileOutputStream(restored));
                        for (;;) {
                            int read = zin.read(buf, 0, buf.length);
                            if (read <= 0) break;
                            out.write(buf, 0, read);
                        }
                        Util.close(out);
                        out = null;
                        if (entry.getTime() > 0L) {
                            //noinspection ResultOfMethodCallIgnored
                            restored.setLastModified(entry.getTime());
                        }
                        restoredCounter++;
                    }
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "While importing from " + uri + ": " + e, e);
                } finally {
                    Util.close(out, in);
                }
                final int r = restoredCounter;
                final int s = skippedCounter;
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {if (BuildConfig.DEBUG) Log.i(TAG, "Not displaying import result"); return;}
                    String msgRestored = getResources().getQuantityString(R.plurals.msg_archive_import_result1, r, r);
                    String msgSkipped = getResources().getQuantityString(R.plurals.msg_archive_import_result2, s, s);
                    Snackbar sb = Snackbar.make(Archive.this.coordinatorLayout, msgRestored + ", " + msgSkipped + '.', Snackbar.LENGTH_LONG);
                    sb.setDuration(5_000);
                    sb.setAction(android.R.string.ok, v -> sb.dismiss());
                    sb.show();
                    if (r > 0) loadFiles();
                });
            }
        }.start();
    }

    /**
     * Loads the existing files and fills the {@link #adapter}.
     */
    @MainThread
    private void loadFiles() {
        this.files = new ArrayList<>(loadAll(this.folder));
        final List<ArchivedNews> list = new ArrayList<>(this.files.size());
        for (File file : this.files) {
            if (file == null) continue;
            list.add(new ArchivedNews(file, file.getName().endsWith(News.FILE_TAG_REGIONAL)));
        }
        Collections.sort(list);
        this.adapter.setItems(list);
        invalidateOptionsMenu();
    }

    /** {@inheritDoc} */
    @Override public void onActivityResult(Uri result) {
        onArchiveSelected(result);
    }

    /** {@inheritDoc} */
    @Override
    void onColumnCountChanged(@NonNull SharedPreferences prefs) {
        selectLayoutManager(prefs, this.recyclerViewArchive);
    }

    /** {@inheritDoc} */
    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        selectLayoutManager(null, this.recyclerViewArchive);
    }

    /** {@inheritDoc} */
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.recyclerViewArchive = findViewById(R.id.recyclerViewArchive);
        assert this.recyclerViewArchive != null;
        this.recyclerViewArchive.setHasFixedSize(true);
        selectLayoutManager(null, this.recyclerViewArchive);
        this.adapter = new ArchivedNewsAdapter();
        this.recyclerViewArchive.setAdapter(this.adapter);
        this.folder = new File(getFilesDir(), DIR);
        this.adapter.setTypeface(Util.loadFont(this));
        this.getZipContent = registerForActivityResult(new ActivityResultContracts.GetContent(), this);
    }

    /**
     * The user has selected a zip archive to import.
     * @param archive zip archive
     */
    private void onArchiveSelected(@Nullable final Uri archive) {
        if (archive == null) {
            if (this.goBackToSettings) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
            }
            return;
        }
        // get display name and size of the data that 'result' points to
        long fileSize = 0L;
        String displayName = archive.getLastPathSegment();
        final String finalDisplayName;
        Cursor cursor = getContentResolver().query(archive, null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeIndex > -1) fileSize = cursor.getLong(sizeIndex);
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex > -1) displayName = cursor.getString(nameIndex);
        }
        Util.close(cursor);
        finalDisplayName = displayName;
        // impose a hard size limit to avoid whatever huge files would cause (ANR, explosions, radiation accidents etc.)
        if (fileSize > ARCHIVE_MAX_SIZE) {
            Snackbar sb = Snackbar.make(this.coordinatorLayout, getString(R.string.error_archive_import_failed, finalDisplayName), Snackbar.LENGTH_LONG);
            sb.setTextMaxLines(3);
            sb.show();
        }
        // on a reasonably fast device the next message might never be read…
        Snackbar.make(this.coordinatorLayout, getString(R.string.msg_archive_importing, finalDisplayName), Snackbar.LENGTH_SHORT).show();
        // check the zip archive and, if successful, import the data
        this.archiveChecker = new ArchiveChecker(this, archive, () -> importArchive(archive), () -> {
            Snackbar sb = Snackbar.make(this.coordinatorLayout, getString(R.string.error_archive_import_failed, finalDisplayName), Snackbar.LENGTH_LONG);
            sb.setTextMaxLines(3);
            sb.show();
        });
        this.archiveChecker.start();
        // disable the import menu item for now
        invalidateOptionsMenu();
    }

    /** {@inheritDoc} */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.archive_menu, menu);
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // once the user interacts with this Activity, don't go back to Settings (if the user arrived from there)
        this.goBackToSettings = false;
        //
        final int id = item.getItemId();
        if (id == R.id.action_archive_clear) {
            String msg = getString(R.string.msg_archive_delete_all, NumberFormat.getNumberInstance().format(Util.getOccupiedSpace(this.files) / 1_000L));
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.label_confirmation)
                    .setMessage(msg)
                    .setIcon(R.drawable.ic_warning_red_24dp)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        dialog.dismiss();
                        deleteAllWithoutConfirmation();
                    })
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                    ;
            builder.show();
            return true;
        }
        if (id == R.id.action_archive_export) {
            exportArchive();
            return true;
        }
        if (id == R.id.action_archive_import) {
            this.getZipContent.launch(MIME_ARCHIVE);
            return true;
        }
        if (id == R.id.action_help_archive) {
            WebView webViewForHelp = new WebView(this);
            WebSettings ws = webViewForHelp.getSettings();
            ws.setBlockNetworkLoads(true);
            ws.setAllowContentAccess(false);
            ws.setGeolocationEnabled(false);
            webViewForHelp.setNetworkAvailable(false);
            webViewForHelp.setBackgroundColor(Util.getColor(this, R.color.colorPrimarySemiTrans));
            Util.showHelp(this, R.raw.help_archive_de, webViewForHelp);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override public boolean onPrepareOptionsMenu(final Menu menu) {
        final boolean hasFiles = !this.files.isEmpty();
        MenuItem menuItemClear = menu.findItem(R.id.action_archive_clear);
        MenuItem menuItemExmport = menu.findItem(R.id.action_archive_export);
        MenuItem menuItemImport = menu.findItem(R.id.action_archive_import);
        menuItemClear.setEnabled(hasFiles);
        menuItemExmport.setEnabled(hasFiles);
        menuItemImport.setEnabled(this.archiveChecker == null || !this.archiveChecker.isAlive());
        return true;
    }

    /** {@inheritDoc} */
    @Override protected void onResume() {
        super.onResume();
        loadFiles();

        if (getString(R.string.appaction_archive_import).equals(getIntent().getAction())) {
            getIntent().setAction(null);
            this.goBackToSettings = true;
            this.getZipContent.launch(MIME_ARCHIVE);
            return;
        }
        // finish if we don't have any archived files and if we are not importing currently
        if (this.files.isEmpty() && (this.archiveChecker == null || !this.archiveChecker.isAlive())) finish();
    }

    /**
     * Launches a {@link NewsActivity} in order to display the given ArchivedNews.
     * @param archivedNews ArchivedNews to show
     * @param from View that the user has just used to initiate this action
     */
    public void showArchivedNews(@Nullable ArchivedNews archivedNews, @Nullable View from) {
        if (archivedNews == null) return;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        JsonReader reader = null;
        boolean parsedSuccessfully = true;
        try {
            reader = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(archivedNews.getFile())), StandardCharsets.UTF_8));
            reader.setLenient(true);
            @News.Flag int flags = 0;
            boolean htmlEmbed = prefs.getBoolean(App.PREF_SHOW_EMBEDDED_HTML_LINKS, App.PREF_SHOW_EMBEDDED_HTML_LINKS_DEFAULT);
            if (htmlEmbed) flags |= News.FLAG_INCLUDE_HTMLEMBED;
            News parsed = News.parseNews(reader, archivedNews.getFile().getName().endsWith(News.FILE_TAG_REGIONAL), flags);
            Util.close(reader);
            reader = null;
            if (prefs.getBoolean(App.PREF_CORRECT_WRONG_QUOTATION_MARKS, App.PREF_CORRECT_WRONG_QUOTATION_MARKS_DEFAULT)) {
                News.correct(parsed);
            }
            Intent intent = new Intent(this, NewsActivity.class);
            intent.putExtra(NewsActivity.EXTRA_NEWS, parsed);
            intent.putExtra(NewsActivity.EXTRA_JSON, archivedNews.getFile().getAbsolutePath());
            // prevent going "back" to MainActivity because the preceding activity is <this>, not a MainActivity
            intent.putExtra(NewsActivity.EXTRA_NO_HOME_AS_UP, true);
            if (from != null) startActivity(intent, ActivityOptionsCompat.makeClipRevealAnimation(from, 0, 0, from.getMeasuredWidth(), from.getMeasuredHeight()).toBundle());
            else startActivity(intent, ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fadein, R.anim.fadeout).toBundle());
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "While trying to show \"" + archivedNews + "\": " + e);
        } finally {
            Util.close(reader);
        }
    }

    /**
     * Checks a zip archive for invalid entries.
     */
    private static class ArchiveChecker extends Thread {
        private final Reference<Activity> refActivity;
        private final Uri zip;
        private Runnable onFailure;
        private Runnable onSuccess;
        private boolean result = true;

        /**
         * Constructor.
         * @param activity Activity
         * @param zip Uri pointing to zip data
         * @param onSuccess to be run upon success
         * @param onFailure to be run upon failure
         */
        ArchiveChecker(@NonNull Activity activity, Uri zip, Runnable onSuccess, Runnable onFailure) {
            super();
            this.refActivity = new WeakReference<>(activity);
            this.zip = zip;
            this.onFailure = onFailure;
            this.onSuccess = onSuccess;
        }

        @Override public void run() {
            if (this.zip == null) return;
            Activity activity = this.refActivity.get();
            if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                this.onFailure = this.onSuccess = null;
                return;
            }
            ZipInputStream zin = null;
            try {
                zin = new ZipInputStream(activity.getContentResolver().openInputStream(this.zip));
                for (;this.result;) {
                    ZipEntry entry = zin.getNextEntry();
                    if (entry == null) break;
                    try {
                        JsonReader jsonReader = new JsonReader(new InputStreamReader(zin));
                        jsonReader.setLenient(true);
                        News.parseNews(jsonReader, entry.getName().endsWith(News.FILE_TAG_REGIONAL), 0);
                    } catch (Throwable fail) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "While checking \"" + entry.getName() + "\" in \"" + this.zip.getLastPathSegment() + "\": " + fail);
                        this.result = false;
                    }
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) Log.e(TAG, "While checking \"" + this.zip.getLastPathSegment() + "\": " + e);
                this.result = false;
            } finally {
                Util.close(zin);
            }
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                if (this.result) {
                    if (this.onSuccess != null) activity.runOnUiThread(this.onSuccess);
                } else {
                    if (this.onFailure != null) activity.runOnUiThread(this.onFailure);
                }
                activity.invalidateOptionsMenu();
            }
            this.onFailure = this.onSuccess = null;
        }
    }
}
