package de.freehamburger.version;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ReleaseChecker {
    @VisibleForTesting public static final String DIGEST_SHA256_ANDROID_DEBUG = "667129b5474ec9ac694abc5465c1d80c393139b8784927020e216167ffce6599";
    @VisibleForTesting public static final String DIGEST_SHA256_FDROID_RELEASE = "b753becb180b904f4c111ada662d98017f3c5c6bc0d8aed4ad3e9396e2eb012f";
    @VisibleForTesting public static final String DIGEST_SHA256_GITHUB_RELEASE = "08f0b35bc523a3d06984bdb582aebb53e40236a90c33ee611c9d44f53f1970f9";
    private static final String BROWSER_URL_FDROID = "https://f-droid.org/en/packages/de.freehamburger/";
    private static final String BROWSER_URL_TAG_PREFIX_GITHUB = "https://github.com/livodeu/FreeHamburger/releases/tag/";
    @SuppressLint("SimpleDateFormat") private static final DateFormat DF_GITHUB = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // 2023-01-30T13:48:48Z
    private static final long MIN_CHECK_INTERVAL = BuildConfig.DEBUG ? 60_000L : 86_400_000L;
    private static final String PREF_LATEST_RELEASE_CHECK_FDROID = "pref_latest_release_check_fdroid";
    private static final String PREF_LATEST_RELEASE_CHECK_GITHUB = "pref_latest_release_check";
    private static final String PREF_LATEST_RELEASE_RESULT_FDROID = "pref_latest_release_check_result_fdroid";
    private static final String PREF_LATEST_RELEASE_RESULT_GITHUB = "pref_latest_release_check_result";
    private static final String TAG = "ReleaseChecker";
    private static final long TIMEOUT_CONNECT = BuildConfig.DEBUG ? 500L : 3_000L;
    private static final long TIMEOUT_READ = BuildConfig.DEBUG ? 500L : 3_000L;
    private static final String URL_FDROID_API = "https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/de.freehamburger.yml";
    private static final String URL_GITHUB_API = "https://api.github.com/repos/livodeu/FreeHamburger/releases";

    /** Private constructor. */
    private ReleaseChecker() {
    }

    /**
     * Checks for a new release at github or f-droid, depending on the certificate of the currently running app.
     * @param ctx Context
     * @param callback callback method
     * @throws NullPointerException if any parameter is null
     */
    @AnyThread
    public static void check(@NonNull final Context ctx, @NonNull final ReleaseCallback callback) {
        final String appSha256 = getCertDigest(ctx.getPackageManager(), ctx.getPackageName());
        if (BuildConfig.DEBUG) {
            if (!DIGEST_SHA256_ANDROID_DEBUG.equals(appSha256)) Log.e(TAG, "Not the Android Debug Certificate!");
            checkGithub(ctx, gitHubReleases -> checkFdroid(ctx, fdroidReleases -> collect(callback, gitHubReleases, fdroidReleases)));
        } else if (DIGEST_SHA256_FDROID_RELEASE.equals(appSha256)) {
            checkFdroid(ctx, callback);
        } else if (DIGEST_SHA256_GITHUB_RELEASE.equals(appSha256)) {
            checkGithub(ctx, callback);
        } else {
            callback.gotRelease((Release[])null);
        }
    }

    /**
     * Asks f-droid for the latest version.
     * @param ctx Context
     * @param callback callback method
     * @throws NullPointerException if any parameter is null
     */
    private static void checkFdroid(@NonNull Context ctx, @NonNull final ReleaseCallback callback) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final long now = System.currentTimeMillis();
        final Release latestResult = Release.fromString(prefs.getString(PREF_LATEST_RELEASE_RESULT_FDROID, Release.EMPTY));
        latestResult.setRepo(Release.REPO_FDROID);
        long latestCheck = prefs.getLong(PREF_LATEST_RELEASE_CHECK_FDROID, 0L);
        latestResult.setCheckedAt(latestCheck);
        if (!Util.isNetworkAvailable(ctx)) {
            callback.gotRelease(latestResult.isValid() ? latestResult : null);
            return;
        }
        if (now - latestCheck < MIN_CHECK_INTERVAL && latestResult.isValid()) {
            callback.gotRelease(latestResult);
            return;
        }
        Request request = new Request.Builder().url(URL_FDROID_API).build();
        try {
            ((App) ctx.getApplicationContext())
                    .getOkHttpClient()
                    .newBuilder()
                    .connectTimeout(TIMEOUT_CONNECT, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_READ, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
                    .enqueue(new Callback() {

                        /** {@inheritDoc} */
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            if (BuildConfig.DEBUG) Log.e(TAG, "Fdroid release check failed: " + e);
                            callback.gotRelease(latestResult.isValid() ? latestResult : null);
                        }

                        /** {@inheritDoc} */
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            int sp;
                            if (!response.isSuccessful() || response.body() == null) {
                                if (BuildConfig.DEBUG) Log.w(TAG, "Fdroid release check not successful: " + response);
                                callback.gotRelease(latestResult.isValid() ? latestResult : null);
                                return;
                            }
                            boolean callbackInvoked = false;
                            BufferedReader reader = null;
                            try (ResponseBody responseBody = response.body()) {
                                reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(responseBody).byteStream()));
                                Release current = new Release(Release.REPO_FDROID);
                                while (true) {
                                    String line = reader.readLine();
                                    if (line == null) {
                                        break;
                                    } else if (line.contains("CurrentVersion:") && (sp = line.lastIndexOf(32)) >= 0) {
                                        current.setTagName(line.substring(sp + 1).trim());
                                        int len = Objects.requireNonNull(current.getTagName()).length();
                                        if (len >= 1) {
                                            if (current.getTagName().charAt(0) == '\'') {
                                                current.setTagName (current.getTagName().substring(1));
                                                len--;
                                            }
                                            if (len >= 1) {
                                                if (current.getTagName().charAt(len - 1) == '\'') {
                                                    current.setTagName(current.getTagName().substring(0, len - 1));
                                                }
                                                if (BuildConfig.DEBUG) Log.i(TAG, "Current f-droid version is \"" + current.getTagName() + "\"");
                                            }
                                        }
                                    }
                                }
                                if (current.isValid()) {
                                    current.setCheckedAt(now);
                                    SharedPreferences.Editor ed = prefs.edit();
                                    ed.putString(PREF_LATEST_RELEASE_RESULT_FDROID, current.toString());
                                    ed.putLong(PREF_LATEST_RELEASE_CHECK_FDROID, now);
                                    ed.apply();
                                    callback.gotRelease(current);
                                    callbackInvoked = true;
                                }
                                if (!callbackInvoked) callback.gotRelease(latestResult.isValid() ? latestResult : null);
                            } catch (Exception e) {
                                if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
                            } finally {
                                Util.close(reader);
                            }
                        }
                    });
        } catch (RuntimeException ex) {
            if (BuildConfig.DEBUG) Log.e(TAG, ex.toString(), ex);
            callback.gotRelease((Release[])null);
        }
    }

    /**
     * Asks github for the latest version.
     * @param ctx Context
     * @param callback callback method
     * @throws NullPointerException if any parameter is null
     */
    private static void checkGithub(@NonNull Context ctx, @NonNull final ReleaseCallback callback) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final long now = System.currentTimeMillis();
        final Release latestResult = Release.fromString(prefs.getString(PREF_LATEST_RELEASE_RESULT_GITHUB, Release.EMPTY));
        latestResult.setRepo(Release.REPO_GITHUB);
        long latestCheck = prefs.getLong(PREF_LATEST_RELEASE_CHECK_GITHUB, 0L);
        latestResult.setCheckedAt(latestCheck);
        if (!Util.isNetworkAvailable(ctx)) {
            callback.gotRelease(latestResult.isValid() ? latestResult : null);
            return;
        }
        if (now - latestCheck < MIN_CHECK_INTERVAL && latestResult.isValid()) {
            callback.gotRelease(latestResult);
            return;
        }
        Request request = new Request.Builder().url(URL_GITHUB_API).addHeader("X-GitHub-Api-Version", "2022-11-28").build();
        try {
            ((App) ctx.getApplicationContext())
                    .getOkHttpClient()
                    .newBuilder()
                    .connectTimeout(TIMEOUT_CONNECT, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_READ, TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
                    .enqueue(new Callback() {

                        /** {@inheritDoc} */
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            if (BuildConfig.DEBUG) Log.e(TAG, "Github release check failed: " + e);
                            callback.gotRelease(latestResult.isValid() ? latestResult : null);
                        }

                        /** {@inheritDoc} */
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                            if (!response.isSuccessful() || response.body() == null) {
                                if (BuildConfig.DEBUG) Log.w(TAG, "Github release check not successful: " + response);
                                callback.gotRelease(latestResult.isValid() ? latestResult : null);
                                return;
                            }
                            boolean callbackInvoked = false;
                            InputStream in = null;

                            try (ResponseBody responseBody = response.body()) {
                                in = Objects.requireNonNull(responseBody).byteStream();
                                Release release = parseGithub(new InputStreamReader(in, StandardCharsets.UTF_8));
                                if (release.isValid()) {
                                    release.setCheckedAt(now);
                                    SharedPreferences.Editor ed = prefs.edit();
                                    ed.putString(PREF_LATEST_RELEASE_RESULT_GITHUB, release.toString());
                                    ed.putLong(PREF_LATEST_RELEASE_CHECK_GITHUB, now);
                                    ed.apply();
                                    callback.gotRelease(release);
                                    callbackInvoked = true;
                                }
                            } finally {
                                Util.close(in);
                            }
                            if (!callbackInvoked) callback.gotRelease(latestResult.isValid() ? latestResult : null);
                        }
                    });
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString(), e);
            callback.gotRelease((Release[])null);
        }
    }

    /**
     * For debug only.
     * @param callback callback method
     * @param releaseGithub github release(s)
     * @param releaseFdroid fdroid release(s)
     */
    private static void collect(@NonNull ReleaseCallback callback, Release[] releaseGithub, Release[] releaseFdroid) {
        final List<Release> all = new ArrayList<>(2);
        if (releaseGithub != null) all.addAll(Arrays.asList(releaseGithub));
        if (releaseFdroid != null) all.addAll(Arrays.asList(releaseFdroid));
        Log.i(TAG, "Latest github release: " + Arrays.toString(releaseGithub));
        Log.i(TAG, "Latest fdroid release: " + Arrays.toString(releaseFdroid));
        Release[] a = new Release[all.size()];
        all.toArray(a);
        callback.gotRelease(a);
    }

    /**
     * Returns the SHA-256 hash of the first valid certificate.
     * @param pm PackageManager
     * @param packageName package name
     * @return hexadecimal representation of the SHA-256 hash
     */
    @VisibleForTesting
    @Nullable
    public static String getCertDigest(@NonNull PackageManager pm, String packageName) {
        if (packageName == null) return null;
        try {
            final X509Certificate[] certs;
            final CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            if (Build.VERSION.SDK_INT >= 28) {
                Signature[] sis = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners();
                if (sis == null) return null;
                certs = new X509Certificate[sis.length];
                for (int i = 0; i < sis.length; i++) {
                    certs[i] = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(sis[i].toByteArray()));
                }
            } else {
                @SuppressLint("PackageManagerGetSignatures") PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                certs = new X509Certificate[info.signatures.length];
                for (int i = 0; i < certs.length; i++) {
                    certs[i] = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(info.signatures[i].toByteArray()));
                }
            }
            for (X509Certificate xcert : certs) {
                if (xcert == null) continue;
                try {
                    xcert.checkValidity();
                    byte[] digestBytes = MessageDigest.getInstance("SHA-256").digest(xcert.getEncoded());
                    return VersionUtil.asHex(digestBytes).toString();
                } catch (CertificateException e) {
                    // skip invalid certificates
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
        }
        return null;
    }

    public static boolean isNewerReleaseAvailable(@Nullable Release latestRelease) {
        if (latestRelease == null || !latestRelease.isValid()) {
            return false;
        }
        int firstDot, lastDot;

        final String thisVersion = Util.trimNumber(BuildConfig.VERSION_NAME);
        firstDot = thisVersion.indexOf('.');
        if (firstDot < 0) return false;
        lastDot = thisVersion.lastIndexOf('.');
        final int thisMajor, thisMinor, thisPatch;
        thisMajor = Integer.parseInt(thisVersion.substring(0, firstDot));
        if (firstDot == lastDot) {
            thisMinor = Integer.parseInt(thisVersion.substring(firstDot + 1));
            thisPatch = 0;
        } else {
            thisMinor = Integer.parseInt(thisVersion.substring(firstDot + 1, lastDot));
            thisPatch = Integer.parseInt(thisVersion.substring(lastDot + 1));
        }

        final String latestReleaseVersion = latestRelease.getPrettyTagName();
        if (TextUtils.isEmpty(latestReleaseVersion)) return false;
        firstDot = latestReleaseVersion.indexOf('.');
        if (firstDot < 0) return false;
        lastDot = latestReleaseVersion.lastIndexOf('.');
        final int latestReleaseMajor = Integer.parseInt(latestReleaseVersion.substring(0, firstDot));
        final int latestReleaseMinor, latestReleasePatch;
        if (firstDot == lastDot) {
            latestReleaseMinor = Integer.parseInt(latestReleaseVersion.substring(firstDot + 1));
            latestReleasePatch = 0;
        } else {
            latestReleaseMinor = Integer.parseInt(latestReleaseVersion.substring(firstDot + 1, lastDot));
            latestReleasePatch = Integer.parseInt(latestReleaseVersion.substring(lastDot + 1));
        }

        return latestReleaseMajor > thisMajor
                || (latestReleaseMajor == thisMajor && latestReleaseMinor > thisMinor)
                || (latestReleaseMajor == thisMajor && latestReleaseMinor == thisMinor && latestReleasePatch > thisPatch);
    }

    /**
     * Creates an Intent designed to open the app in a suitable store app or show it in the f-droid web page.
     * @param ctx Context
     * @return Intent
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    @NonNull
    @TargetApi(Build.VERSION_CODES.N)
    public static Intent makeBrowseFdroidIntent(@NonNull Context ctx) {
        Intent seeNewReleaseIntent = new Intent(Intent.ACTION_VIEW);
        seeNewReleaseIntent
                .setData(Uri.parse("market://details?id=de.freehamburger"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(Intent.EXTRA_REFERRER, Uri.parse(Util.PROTOCOL_ANDROID_APP + ctx.getPackageName()));
        if (BuildConfig.DEBUG) seeNewReleaseIntent.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
        Intent chooser = Intent.createChooser(seeNewReleaseIntent, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ComponentName[] excluded = {
                    new ComponentName("cm.aptoide.pt", "cm.aptoide.pt.DeepLinkIntentReceiver"),
                    new ComponentName("com.apkpure.aegon", "com.apkpure.aegon.main.activity.SplashActivity"),
                    new ComponentName("com.aurora.store", "com.aurora.store.view.ui.details.AppDetailsActivity"),
                    new ComponentName("com.qooapp.qoohelper", "com.qooapp.qoohelper.arch.game.info.view.NewGameInfoActivity"),
                    new ComponentName("com.sec.android.app.samsungapps", "com.sec.android.app.samsungapps.MainForPlayStore"),
                    new ComponentName("com.android.vending", "com.google.android.finsky.activities.LaunchUrlHandlerActivity"),
                    new ComponentName("com.android.vending", "com.google.android.finsky.activities.MainActivity"),
                    new ComponentName("com.android.vending", "com.google.android.finsky.activities.MarketDeepLinkHandlerActivity")
            };
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            chooser.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true);
        }
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[] {
                new LabeledIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(BROWSER_URL_FDROID)), ctx.getPackageName(), "f-droid.org", 0)}
        );
        return chooser;
    }

    /**
     * Creates an Intent designed to view the given release on github (or in the github app).
     * @param githubRelease github release
     * @return Intent
     * @throws NullPointerException if {@code githubRelease} is {@code null}
     */
    @NonNull
    public static Intent makeBrowseGithubIntent(@NonNull Release githubRelease) {
        Intent seeNewReleaseIntent = new Intent(Intent.ACTION_VIEW);
        seeNewReleaseIntent.setData(Uri.parse(BROWSER_URL_TAG_PREFIX_GITHUB + githubRelease.getTagName()));
        seeNewReleaseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return seeNewReleaseIntent;
    }

    @NonNull
    @VisibleForTesting
    public static Release parseGithub(@NonNull Reader r) {
        final Release release = new Release(Release.REPO_GITHUB);
        final JsonReader reader = new JsonReader(r);
        reader.setLenient(true);
        try {
            JsonToken token;
            reader.beginArray();
            reader.beginObject();
            String name = null;
            for (; reader.hasNext();) {
                token = reader.peek();
                if (token == JsonToken.END_DOCUMENT) break;
                if (token == JsonToken.NAME) {
                    name = reader.nextName();
                    continue;
                }
                if (token == JsonToken.END_OBJECT) {
                    // stop after the first (=latest) release
                    reader.endObject();
                    break;
                }
                if ("tag_name".equals(name)) {
                    release.setTagName(reader.nextString());
                    continue;
                }
                if ("assets_url".equals(name)) {
                    release.setAssetsUrl(reader.nextString());
                    continue;
                }
                if ("published_at".equals(name)) {
                    try {
                        release.setPublishedAt(Objects.requireNonNull(DF_GITHUB.parse(reader.nextString())).getTime());
                    } catch (Exception pe) {
                    }
                    continue;
                }
                if ("assets".equals(name)) {
                    reader.beginArray();
                    reader.beginObject();
                    Asset currentAsset = new Asset();
                    for (; reader.hasNext();) {
                        token = reader.peek();
                        if (token == JsonToken.END_ARRAY) {
                            reader.endArray();
                            break;
                        }
                        if (token == JsonToken.END_OBJECT) {
                            reader.endObject();
                            if (currentAsset.isValid()) release.addAsset(currentAsset);
                            else if (BuildConfig.DEBUG) Log.e(TAG, "Invalid asset " + currentAsset);
                            currentAsset = new Asset();
                            continue;
                        }
                        if (token == JsonToken.NAME) {
                            name = reader.nextName();
                            continue;
                        }
                        if ("name".equals(name)) {
                            currentAsset.name = reader.nextString();
                            continue;
                        }
                        if ("browser_download_url".equals(name)) {
                            currentAsset.url = reader.nextString();
                            continue;
                        }
                        if (reader.hasNext()) {
                            reader.skipValue();
                        }
                    }
                }
                if (reader.hasNext()) {
                    reader.skipValue();
                }
            }
        } catch (Exception e) {
            Util.close(reader);
        }
        return release;
    }

    /**
     * Receives information when a release has been found (or not).
     */
    @FunctionalInterface
    public interface ReleaseCallback {
        @AnyThread
        void gotRelease(@Nullable Release... releases);
    }

}
