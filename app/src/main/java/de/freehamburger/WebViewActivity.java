package de.freehamburger;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.freehamburger.model.News;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class WebViewActivity extends AppCompatActivity {

    /** if {@link #EXTRA_NEWS} is <em>not</em> set, this can provide a url to display */
    public static final String EXTRA_URL = "extra_url";
    /** boolean; if true, the ActionBar will not show the home arrow which would lead to MainActivity */
    public static final String EXTRA_NO_HOME_AS_UP = "extra_no_home_as_up";
    /** the News whose {@link News#getDetailsWeb() detailsWeb} attribute provides the url to display */
    private static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "WebViewActivity";
    private static final String CHARSET = "UTF-8";
    private static final String HTTP_404 = "<!DOCTYPE html><html lang=\"en\"><head><title>404 Not found.</title></head><body></body></html>";
    private static final String HTTP_400_MAILTO = "<!DOCTYPE html><html lang=\"en\"><head><title>400 Bad Request.</title></head><body><h1>Mail links are not supported.</h1></body></html>";
    private static final byte[] HTTP_404_BYTES = HTTP_404.getBytes(Charset.forName(CHARSET));
    private static final byte[] HTTP_400_BYTES_MAILTO = HTTP_400_MAILTO.getBytes(Charset.forName(CHARSET));
    /** these are a big no-no (well, favicon isn't really, but we don't need it) */
    private static final String[] BADWORDS = new String[] {"analytics", "cpix", "favicon", "sitestat", "tracker", "tracking", "webtrekk", "xtcore"};

    private News news;
    private WebView webView;

    /**
     * Initiates a download via the system's {@link DownloadManager}.
     * @param ctx Context
     * @param uri Uri
     */
    private static void download(Context ctx, @NonNull final Uri uri) {
        if (ctx == null) {
            if (BuildConfig.DEBUG) Log.e(TAG, "download(null, " + uri + ")");
            return;
        }
        if (BuildConfig.DEBUG) Log.i(TAG, "download(..., " + uri + ")");
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        final DownloadManager.Request r = new DownloadManager.Request(uri);
        String s = uri.toString();
        int dot = s.lastIndexOf('.');
        if (dot > 0 && dot < s.length() - 1) {
            String extension = s.substring(dot + 1);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) r.setMimeType(mime);
        }
        r.setTitle(uri.getLastPathSegment());
        r.setVisibleInDownloadsUi(true);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE));
        dm.enqueue(r);
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (this.webView.canGoBack()) {
            this.webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        boolean loadMobile = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE);

        this.webView = findViewById(R.id.webView);
        this.webView.setWebViewClient(new HamburgerWebViewClient());
        this.webView.setWebChromeClient(new HamburgerWebChromeClient(this));

        final WebSettings ws = this.webView.getSettings();
        ws.setUserAgentString(App.USER_AGENT);
        if (!loadMobile && Util.isNetworkMobile(this)) {
            ws.setBlockNetworkLoads(true);
        }
        ws.setJavaScriptEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setGeolocationEnabled(false);
        ws.setDomStorageEnabled(false);
        ws.setSaveFormData(false);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(false);
        }

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            boolean noHomeAsUp = getIntent().getBooleanExtra(EXTRA_NO_HOME_AS_UP, false);
            if (!noHomeAsUp) ab.setDisplayHomeAsUpEnabled(true);
        }

        this.news = (News)getIntent().getSerializableExtra(EXTRA_NEWS);
        if (ab != null && this.news != null) {
            String topline = this.news.getTopline();
            if (TextUtils.isEmpty(topline)) topline = this.news.getTitle();
            ab.setTitle(topline);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onDestroy() {
        this.webView.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        if (!Util.isNetworkAvailable(this)) {
            Toast.makeText(getApplicationContext(), R.string.error_no_network, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (this.news != null) {
            String url = this.news.getDetailsWeb();
            if (TextUtils.isEmpty(url)) url = this.news.getShareUrl();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getApplicationContext(), R.string.error_no_further_details, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            url = Util.makeHttps(url);
            this.webView.loadUrl(url);
        } else {
            Intent intent = getIntent();
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null) {
                url = Util.makeHttps(url);
                if (BuildConfig.DEBUG) android.util.Log.i(TAG, "Loading \"" + url + "\"");
                this.webView.loadUrl(url);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_MODERATE) {
            this.webView.clearCache(false);
        }
        super.onTrimMemory(level);
    }

    /**
     * Sets a View's visibility to {@link View#INVISIBLE INVISIBLE}.
     */
    private static class ViewHider implements Runnable {

        private final View v;

        private ViewHider(@NonNull View v) {
            this.v = v;
        }

        @Override
        public void run() {
            this.v.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * A WebViewClient implementation.
     */
    private static class HamburgerWebViewClient extends WebViewClient {

        private static final String[] DOWNLOADABLE_RESOURCES = new String[] {
                ".7z", ".apk", ".arw", ".bin", ".bz2", ".cr2", ".deb", ".dng", ".doc", ".epub", ".exe",
                ".gz", ".iso", ".jar", ".nef", ".ods", ".odt", ".pdf", ".ppt", ".rar", ".tgz", ".ttf", ".vhd", ".xls", ".xz", ".zip"
        };

        private final Handler handler = new Handler();

        /**
         * Checks whether the given resource should be downloaded.
         * @param url url of resource to load
         * @return {@code true} if the resource should be passed to the {@link DownloadManager}.
         */
        private static boolean isDownloadableResource(@NonNull String url) {
            final String urlToCheck;
            int q = url.lastIndexOf('?');
            if (q > 0) {
                urlToCheck = url.substring(0, q).toLowerCase(Locale.US);
            } else {
                urlToCheck = url.toLowerCase(Locale.US);
            }
            for (String ext : DOWNLOADABLE_RESOURCES) {
                if (urlToCheck.endsWith(ext)) return true;
            }
            return false;
        }

        /**
         * Determines whether an Uri should be blocked.<br>
         * <ul>
         * <li>Uris that are neither http(s) nor data will be blocked.</li>
         * <li>Uris that refer to hosts that are not on the white list will be blocked.</li>
         * <li>Uris that contain {@link #BADWORDS naughty} words will be blocked.</li>
         * </ul>
         * @param uri Uri to check
         * @return {@code true} if the given Uri should be blocked
         * @throws NullPointerException if {@code uri} is {@code null}
         */
        private static boolean shouldBlock(@NonNull Uri uri) {
            @Nullable final String host = uri.getHost();    // is null when scheme is 'data'
            @Nullable final String path = uri.getPath();    // is null when scheme is 'data'
            if (host == null || path == null) return false;
            // block schemes other than "http" and "data"
            String scheme = uri.getScheme();
            if (scheme != null && !scheme.startsWith("http") && !scheme.startsWith("data")) return true;
            // block non-whitelisted hosts
            if (!App.isHostAllowed(host)) return true;
            //
            for (String badword : BADWORDS) {
                if (path.contains(badword)) return true;
            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public void onPageFinished(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            boolean allowedScheme = (scheme == null || "http".equals(scheme) || "https".equals(scheme));
            boolean allowed = allowedScheme && App.isHostAllowed(uri.getHost());
            if (!allowed && view.getUrl().equals(url)) {
                // determine what was the culprit, scheme (possibly "whatsapp") or host
                String offending = !allowedScheme ? scheme : uri.getHost();
                //
                Snackbar sb = Snackbar.make(view, view.getContext().getString(R.string.error_link_not_supported, offending), Snackbar.LENGTH_INDEFINITE);
                sb.setAction("↗", v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "text/plain");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    boolean canDo = v.getContext().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
                    if (canDo) {
                        v.getContext().startActivity(intent);
                    } else {
                        Toast.makeText(v.getContext(), R.string.error_no_app, Toast.LENGTH_SHORT).show();
                    }
                    sb.dismiss();
                });
                sb.show();
                view.goBack();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onPageStarted(..., \"" + url + "\", " + (favicon != null ? "favicon" : "no favicon") + ")");
        }

        /** {@inheritDoc} */
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onReceivedError(..., \"" + request.getUrl()  + "\", " + error + ")");
        }

        /** {@inheritDoc} */
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            if (BuildConfig.DEBUG) Log.w(TAG, "onReceivedHttpError(..., \"" + request.getUrl() + "\", HTTP " + errorResponse.getStatusCode() + ")");
        }

        /** {@inheritDoc} */
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (BuildConfig.DEBUG) Log.e(TAG, "onReceivedSslError(..., " + error + ")");
            handler.cancel();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (BuildConfig.DEBUG) Log.e(TAG, "onRenderProcessGone(..., " + detail + ")");
            Context ctx = view.getContext();
            if (ctx instanceof Activity) {
                ((Activity)ctx).finish();
            }
            return true;
        }

        /** {@inheritDoc} */
        @Override
        @WorkerThread
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            final Uri uri = request.getUrl();
            @Nullable final String scheme = uri.getScheme();
            if (shouldBlock(uri)) {
                WebResourceResponse wr;
                if ("mailto".equals(scheme)) {
                    wr = new WebResourceResponse("text/html", CHARSET, new ByteArrayInputStream(HTTP_400_BYTES_MAILTO));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_BAD_REQUEST, "Bad Request.");
                } else {
                    wr = new WebResourceResponse("text/html", CHARSET, new ByteArrayInputStream(HTTP_404_BYTES));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NOT_FOUND, "Not found.");
                }
                if (BuildConfig.DEBUG) Log.w(TAG, "shouldInterceptRequest() - blocking " + uri);
                return wr;
            }
            if (("http".equals(scheme) || "https".equals(scheme)) && "GET".equals(request.getMethod()) && isDownloadableResource(uri.toString())) {
                if (request.hasGesture()) {
                    this.handler.post(() -> download(view.getContext(), uri));
                }
                WebResourceResponse wr = new WebResourceResponse("text/html", CHARSET, new ByteArrayInputStream("<html><head></head><body></body></html>".getBytes()));
                wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NO_CONTENT, "No Content.");
                if (BuildConfig.DEBUG) android.util.Log.w(TAG, "shouldInterceptRequest(\"" + request.getMethod() + " " + uri + "\") - uri.host=" + uri.getHost() + " -> " + wr.getReasonPhrase());
                return wr;
            }
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (BuildConfig.DEBUG) Log.i(TAG, "shouldOverrideUrlLoading(..., \"" + uri + "\"" + ")");
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                this.handler.postDelayed(() -> {
                    String oldUrl = uri.toString();
                    String newUrl = "https" + oldUrl.substring(4);
                    Map<String, String> addtlHeaders = new HashMap<>(1);
                    addtlHeaders.put("Warning", "199 INACCEPTABLE HTTPS TO HTTP REDIRECTION");
                    view.loadUrl(newUrl, addtlHeaders);
                }, 200L);
                return true;
            }
            return super.shouldOverrideUrlLoading(view, request);
        }
    }

    /**
     * A WebChromeClient implementation.
     */
    private static class HamburgerWebChromeClient extends WebChromeClient {
        @Nullable private final Handler handler;
        @Nullable private final ProgressBar progressBar;
        @Nullable private final ViewHider viewHider;

        private HamburgerWebChromeClient(@NonNull AppCompatActivity activity) {
            super();
            this.progressBar = activity.findViewById(R.id.webProgress);
            if (this.progressBar != null) {
                this.handler = new Handler();
                this.viewHider = new ViewHider(this.progressBar);
            } else {
                this.handler = null;
                this.viewHider = null;
            }
        }

        /**
         * Fades a View and adjusts its visibility to {@link View#VISIBLE VISIBLE} respectively {@link View#INVISIBLE INVISIBLE}.
         * @param v View
         * @param in {@code true} to fade in, {@code false} to fade out
         * @param handler Handler (used only if {@code in} is {@code false})
         * @param duration duration in ms
         */
        private void fade(@NonNull View v, boolean in, @NonNull Handler handler, int duration) {
            ObjectAnimator oa;
            if (in) {
                handler.removeCallbacks(this.viewHider);
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
                oa = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).setDuration(duration);
            } else {
                oa = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f).setDuration(duration);
                handler.postDelayed(this.viewHider, duration + 10);
            }
            oa.start();
        }

        /** {@inheritDoc} */
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (BuildConfig.DEBUG) {
                int line = consoleMessage.lineNumber();
                Log.i(getClass().getSimpleName(), consoleMessage.sourceId() + (line > 0 ? " - Line " + line : "") + ": " + consoleMessage.message());
            }
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (this.progressBar == null || this.handler == null) return;
            this.progressBar.setProgress(newProgress);
            if (newProgress < 100) {
                if (this.progressBar.getVisibility() == View.INVISIBLE) {
                    fade(this.progressBar, true, this.handler, 250);
                }
            } else {
                fade(this.progressBar, false, this.handler, 750);
            }
        }

    }
}
