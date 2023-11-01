package de.freehamburger;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.freehamburger.model.News;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class WebViewActivity extends AppCompatActivity {

    /** boolean; if true, the ActionBar will not show the home arrow which would lead to MainActivity */
    public static final String EXTRA_NO_HOME_AS_UP = "extra_no_home_as_up";
    /** if {@link #EXTRA_NEWS} is <em>not</em> set, this can provide a url to display */
    public static final String EXTRA_URL = "extra_url";
    /** the News whose {@link News#getDetailsWeb() detailsWeb} attribute provides the url to display */
    static final String EXTRA_NEWS = "extra_news";
    /** these are a big no-no (well, favicon isn't really, but we don't need it) */
    private static final String[] BADWORDS = new String[] {"analytics", "cpix", "favicon", "sitestat", "tracker", "tracking", "webtrekk", "xtcore"};
    private static final String CHARSET = "UTF-8";
    private static final byte[] HTTP_400_BYTES_MAILTO = "<!DOCTYPE html><html lang=\"en\"><head><title>400 Bad Request.</title></head><body><h1>Mail links are not supported.</h1></body></html>".getBytes(Charset.forName(CHARSET));
    private static final byte[] HTTP_404_BYTES = "<!DOCTYPE html><html lang=\"en\"><head><title>404 Not found.</title></head><body></body></html>".getBytes(Charset.forName(CHARSET));
    private static final int MAX_WEBSITE_ERRORS_TO_DISPLAY = 30;
    private static final String MIME_HTML = "text/html";
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final String TAG = "WebViewActivity";
    WebView webView;
    private News news;
    /** vertical scroll position as a fraction of the page's height (can thus be larger than 1) */
    private float currentScrollY = 0f;
    /** {@code true} while the page is loading */
    private boolean loading;
    /** set to {@code true} when the device's configuration has changed*/
    private boolean configurationJustChanged;
    protected int errorCode = 0;

    /**
     * Initiates a download via the system's {@link DownloadManager}.
     * @param ctx Context
     * @param uri Uri
     */
    private static void download(@NonNull Context ctx, @NonNull final Uri uri) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        final DownloadManager.Request r = new DownloadManager.Request(uri);
        String s = uri.toString();
        String mime = Util.getMime(s, null);
        if (mime != null) r.setMimeType(mime);
        r.setTitle(uri.getLastPathSegment());
        r.setVisibleInDownloadsUi(true);
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setAllowedOverMetered(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE));
        dm.enqueue(r);
    }

    @Nullable
    private String extractUrl() {
        if (this.news == null) {
            Intent intent = getIntent();
            return intent.getStringExtra(EXTRA_URL);
        }
        String url = this.news.getDetailsWeb();
        if (TextUtils.isEmpty(url)) url = this.news.getShareUrl();
        return url;
    }

    /**
     * Returns a {@link PageFinishedListener PageFinishedListener} which gets notified when a page has been loaded. Defaults to null.
     * @return PageFinishedListener
     */
    @Nullable
    PageFinishedListener getPageFinishedListener() {
        return null;
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

    /** {@inheritDoc} */
    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) {
        this.configurationJustChanged = true;
        super.onConfigurationChanged(newConfig);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        HamburgerActivity.applyOrientation(this, prefs);

        getDelegate().setContentView(R.layout.activity_web_view);
        getDelegate().setSupportActionBar(findViewById(R.id.toolbar));

        this.webView = getDelegate().findViewById(R.id.webView);
        assert this.webView != null;
        this.webView.setBackgroundColor(Util.isNightMode(this) ? Color.BLACK : Color.WHITE);
        this.webView.setWebViewClient(new HamburgerWebViewClient(this));
        this.webView.setWebChromeClient(new HamburgerWebChromeClient(this));
        this.webView.clearHistory();

        final WebSettings ws = this.webView.getSettings();
        ws.setUserAgentString(App.USER_AGENT);
        if (!prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE) && Util.isNetworkMobile(this)) {
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

        ActionBar ab = getDelegate().getSupportActionBar();
        if (ab != null) {
            boolean noHomeAsUp = getIntent().getBooleanExtra(EXTRA_NO_HOME_AS_UP, false);
            if (!noHomeAsUp) {
                ab.setDisplayHomeAsUpEnabled(true);
                Toolbar toolbar = findViewById(R.id.toolbar);
                HamburgerActivity.setHomeArrowTooltipText(toolbar, getString(R.string.hint_back_to_main));
            }
        }

        this.news = (News)getIntent().getSerializableExtra(EXTRA_NEWS);
        if (ab != null && this.news != null) {
            String topline = this.news.getTopline();
            if (TextUtils.isEmpty(topline)) topline = this.news.getTitle();
            ab.setTitle(topline);
        }

        // remember the vertical scroll position to restore it after the device has been rotated
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (this.loading || this.configurationJustChanged) return;
                this.currentScrollY = scrollY / (float)((WebView)v).getContentHeight();
            });
        } else {
            this.webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (this.loading || this.configurationJustChanged) return;
                this.currentScrollY = this.webView.getScrollY() / (float)this.webView.getContentHeight();
            });
        }
    }

    /** {@inheritDoc} */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.web_menu, menu);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void onDestroy() {
        this.webView.clearCache(true);
        CookieManager.getInstance().removeAllCookies(null);
        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (R.id.action_open_in_browser == item.getItemId()) {
            String url = extractUrl();
            if (TextUtils.isEmpty(url)) return true;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** {@inheritDoc} */
    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem itemOpenInBrowser = menu.findItem(R.id.action_open_in_browser);
        if (itemOpenInBrowser != null) itemOpenInBrowser.setEnabled(!TextUtils.isEmpty(extractUrl()));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        this.webView.clearHistory();
        if (!Util.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.error_no_network, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (this.news != null) {
            String url = extractUrl();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, R.string.error_no_further_details, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            url = Util.makeHttps(url);
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (App.isSchemeAllowed(scheme)) {
                if (App.isHostAllowed(host)) {
                    this.webView.loadUrl(url);
                } else {
                    Toast.makeText(this, getString(R.string.error_link_not_supported, host), Toast.LENGTH_LONG).show();
                    finish();
                }
            } else {
                Toast.makeText(this, getString(R.string.error_link_not_supported, !TextUtils.isEmpty(scheme) ? scheme : url), Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            Intent intent = getIntent();
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null) {
                url = Util.makeHttps(url);
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (App.isSchemeAllowed(scheme)) {
                    if (App.isHostAllowed(host)) {
                        this.webView.loadUrl(uri.toString());
                    } else {
                        Toast.makeText(this, getString(R.string.error_link_not_supported, host), Toast.LENGTH_LONG).show();
                        finish();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.error_link_not_supported, !TextUtils.isEmpty(scheme) ? scheme : url), Toast.LENGTH_LONG).show();
                    finish();
                }
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

    @FunctionalInterface
    interface PageFinishedListener {

        /**
         * A page has finished loading.
         * See {@link WebViewClient#onPageFinished(WebView, String)}.
         * @param url url
         */
        void pageFinished(String url);
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
    public static class HamburgerWebViewClient extends WebViewClient {

        private static final String[] DOWNLOADABLE_RESOURCES = new String[] {
                ".7z", ".apk", ".arw", ".bin", ".bz2", ".cr2", ".deb", ".dng", ".doc", ".docx", ".epub", ".exe",
                ".gz", ".iso", ".jar", ".nef", ".ods", ".odt", ".pdf", ".ppt", ".rar", ".tgz", ".ttf", ".vhd", ".xls", ".xlsx", ".xz", ".zip"
        };
        private final Handler handler = new Handler();
        private final WebViewActivity activity;
        /**
         * Constructor.
         * @param activity WebViewActivity
         */
        private HamburgerWebViewClient(@NonNull WebViewActivity activity) {
            super();
            this.activity = activity;
        }

        /**
         * Checks whether the given resource should be downloaded.
         * @param url url of resource to load
         * @return {@code true} if the resource should be passed to the {@link DownloadManager}.
         */
        @VisibleForTesting
        public static boolean isDownloadableResource(@NonNull String url) {
            String path = Uri.parse(url).getPath();
            if (path == null) return false;
            path = path.toLowerCase(Locale.US);
            for (String ext : DOWNLOADABLE_RESOURCES) {
                if (path.endsWith(ext)) return true;
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
        public static boolean shouldBlock(@NonNull Uri uri) {
            @Nullable final String host = uri.getHost();    // is null when scheme is 'data'
            @Nullable final String path = uri.getPath();    // is null when scheme is 'data'
            if (host == null || path == null) return false;
            // block schemes other than "http" and "data"
            String scheme = uri.getScheme();
            if (scheme != null && !scheme.startsWith("http") && !scheme.equals("data")) return true;
            // block script resources from greylisted hosts
            if (App.isHostRestrictedToNonScript(host)) {
                if (path.endsWith(".js")) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Host " + host + " is restricted to non-script data but tried to provide " + path);
                    return true;
                }
            }
            // block non-whitelisted hosts
            if (!App.isHostAllowed(host)) return true;
            // block resources with unwanted content
            for (String badword : BADWORDS) {
                if (path.contains(badword)) return true;
            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public void onPageFinished(WebView view, String url) {
            this.activity.loading = false;
            this.activity.configurationJustChanged = false;
            if ("about:blank".equals(url)) return;
            // scroll to the vertical scroll position that applied before the page was (re-)loaded - the delay was determined empirically and therefore adheres to the highest scientific standards…
            this.handler.postDelayed(() -> view.scrollTo(0, Math.round(this.activity.currentScrollY * view.getContentHeight())), 404L);
            //
            PageFinishedListener pageFinishedListener = this.activity.getPageFinishedListener();
            if (pageFinishedListener != null) pageFinishedListener.pageFinished(url);
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            boolean allowedScheme = (scheme == null || SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme));
            boolean allowed = allowedScheme && App.isHostAllowed(uri.getHost());
            if (!allowed && (view.getUrl() == null || view.getUrl().equals(url))) {
                // determine what was the culprit, scheme (possibly "whatsapp") or host
                String offending = !allowedScheme ? scheme : uri.getHost();
                //
                Snackbar sb = Snackbar.make(view, view.getContext().getString(R.string.error_link_not_supported, offending), Snackbar.LENGTH_INDEFINITE);
                // "file:" uris as data would cause an android.os.FileUriExposedException: "<uri> exposed beyond app through Intent.getData()"
                if (!"file".equals(uri.getScheme())) {
                    sb.setAction("↗", v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "text/plain");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        boolean canDo = v.getContext().getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
                        if (canDo) {
                            try {
                                v.getContext().startActivity(intent);
                            } catch (Exception e) {
                                if (BuildConfig.DEBUG) Log.e(TAG, "While starting " + intent + ": " + e);
                            }
                        } else {
                            if (v.getContext() instanceof Activity) Util.makeSnackbar((Activity) v.getContext(), R.string.error_no_app, Snackbar.LENGTH_SHORT).show();
                            else Toast.makeText(v.getContext(), R.string.error_no_app, Toast.LENGTH_SHORT).show();
                        }
                        sb.dismiss();
                    });
                }
                sb.show();
                view.goBack();
            }
        }

        /** {@inheritDoc} */
        @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            this.activity.loading = true;
            super.onPageStarted(view, url, favicon);
        }

        /** {@inheritDoc} */
        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (!request.isForMainFrame()) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
            this.activity.errorCode = error.getErrorCode();
            String msg;
            switch (this.activity.errorCode) {
                case WebViewClient.ERROR_CONNECT:
                case WebViewClient.ERROR_HOST_LOOKUP:
                    msg = view.getContext().getString(R.string.error_connection_failed, request.getUrl().getHost()); break;
                default:
                    msg = error.getDescription().toString();
                    if (msg.startsWith("net::")) msg = msg.substring(5);
            }
            view.loadDataWithBaseURL(null, "<!DOCTYPE html><html><head><title>Error</title></head><body>"
                    + "<h3>"
                    + msg
                    + "</h3>"
                    +"</body></html>", "text/html", "UTF-8", null);
            this.activity.invalidateOptionsMenu();
            this.activity.setResult(RESULT_CANCELED);
            this.handler.postDelayed(() -> {if (!activity.isFinishing()) activity.finish();}, 5_000L);
        }

        /** {@inheritDoc} */
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (BuildConfig.DEBUG) Log.e(TAG, "onReceivedSslError(..., " + error + ")");
            handler.cancel();
        }

        /** {@inheritDoc} */
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
                    wr = new WebResourceResponse(MIME_HTML, CHARSET, new ByteArrayInputStream(HTTP_400_BYTES_MAILTO));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_BAD_REQUEST, "Bad Request.");
                } else {
                    wr = new WebResourceResponse(MIME_HTML, CHARSET, new ByteArrayInputStream(HTTP_404_BYTES));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NOT_FOUND, "Not found.");
                }
                return wr;
            }
            if ((SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme)) && "GET".equals(request.getMethod()) && isDownloadableResource(uri.toString())) {
                if (request.hasGesture()) {
                    final Context ctx = view.getContext();
                    if (ctx != null) this.handler.post(() -> download(ctx, uri));
                }
                WebResourceResponse wr = new WebResourceResponse(MIME_HTML, CHARSET, new ByteArrayInputStream("<html><head></head><body></body></html>".getBytes()));
                wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NO_CONTENT, "No Content.");
                if (BuildConfig.DEBUG) android.util.Log.w(TAG, "shouldInterceptRequest(\"" + request.getMethod() + " " + uri + "\") - uri.host=" + uri.getHost() + " -> " + wr.getReasonPhrase());
                return wr;
            }
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // if the request was initiated by the user, reset the scroll position to apply after loading
            if (request.hasGesture()) this.activity.currentScrollY = 0f;
            //
            Uri uri = request.getUrl();
            if (SCHEME_HTTP.equalsIgnoreCase(uri.getScheme())) {
                this.handler.postDelayed(() -> view.loadUrl(SCHEME_HTTPS + uri.toString().substring(SCHEME_HTTP.length())), 200L);
                return true;
            }
            return super.shouldOverrideUrlLoading(view, request);
        }
    }

    /**
     * A WebChromeClient implementation.
     */
    private static class HamburgerWebChromeClient extends WebChromeClient {
        private final Reference<AppCompatActivity> refactivity;
        @Nullable private final Handler handler;
        @Nullable private final ProgressBar progressBar;
        @Nullable private final ViewHider viewHider;
        private final List<String> errors = new ArrayList<>();
        @Nullable private String urlForErrors;
        @Nullable private Snackbar sb;

        /**
         * Constructor.
         * @param activity AppCompatActivity
         */
        private HamburgerWebChromeClient(@NonNull AppCompatActivity activity) {
            super();
            this.refactivity = new WeakReference<>(activity);
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
            if (this.viewHider == null) return;
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
                switch (consoleMessage.messageLevel()) {
                    case WARNING: Log.i(getClass().getSimpleName(), consoleMessage.sourceId() + (line > 0 ? " - Line " + line : "") + ": " + consoleMessage.message()); break;
                    case ERROR: Log.w(getClass().getSimpleName(), consoleMessage.sourceId() + (line > 0 ? " - Line " + line : "") + ": " + consoleMessage.message()); break;
                    default: return true;
                }
            }
            // collect errors
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                if (this.urlForErrors == null) {
                    this.urlForErrors = consoleMessage.sourceId();
                } else if (!this.urlForErrors.equalsIgnoreCase(consoleMessage.sourceId())) {
                    this.errors.clear();
                    this.urlForErrors = consoleMessage.sourceId();
                }
                if (!this.errors.contains(consoleMessage.message())) this.errors.add(consoleMessage.message());
                AppCompatActivity activity = this.refactivity.get();
                if (activity == null) return true;
                boolean showErrors = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(App.PREF_SHOW_WEB_ERRORS, App.PREF_SHOW_WEB_ERRORS_DEFAULT);
                if (showErrors && (this.sb == null || !this.sb.isShown())) {
                    Snackbar sb = Snackbar.make(activity.findViewById(R.id.coordinator_layout), R.string.msg_website_errors, Snackbar.LENGTH_INDEFINITE);
                    Util.setSnackbarFont(sb, Util.CONDENSED, (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? -1 : 12));
                    sb.setAction("↗", v -> {
                        final int n = HamburgerWebChromeClient.this.errors.size();
                        StringBuilder msg = new StringBuilder(Math.min(2048, n * 256));
                        for (int count = 0; count < n; count++) {
                            String error = HamburgerWebChromeClient.this.errors.get(count);
                            msg.append(count + 1).append(". ").append(error).append('\n');
                            if (count == MAX_WEBSITE_ERRORS_TO_DISPLAY && count < n - 1) {msg.append("…"); break;}
                        }
                        LayoutInflater i = LayoutInflater.from(activity);
                        @SuppressLint("InflateParams")
                        View view = i.inflate(R.layout.multi_text_view, null);
                        TextView tv = view.findViewById(R.id.textView);
                        tv.setTypeface(Util.CONDENSED);
                        tv.setTextSize(14f);
                        tv.setWidth((int)(Util.getDisplaySize(activity).x * 0.75));
                        tv.setText(msg);
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.label_website_errors)
                                .setView(view)
                                .setNeutralButton(R.string.action_show_weberrors_no, (dialog, which) -> {
                                    SharedPreferences.Editor ed =  PreferenceManager.getDefaultSharedPreferences(activity).edit();
                                    ed.putBoolean(App.PREF_SHOW_WEB_ERRORS, false);
                                    ed.apply();
                                    dialog.dismiss();
                                })
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                                .show();
                    });
                    sb.show();
                }
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
