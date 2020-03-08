package de.freehamburger;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import de.freehamburger.model.News;
import de.freehamburger.util.Log;
import de.freehamburger.util.Util;

public class WebViewActivity extends AppCompatActivity {

    /** if {@link #EXTRA_NEWS} is <em>not</em> set, this can provide a url to display */
    public static final String EXTRA_URL = "extra_url";
    /** boolean; if true, the ActionBar will not show the home arrow which would lead to MainActivity */
    public static final String EXTRA_NO_HOME_AS_UP = "extra_no_home_as_up";
    /** the News whose {@link News#getDetailsWeb() detailsWeb} attribute provides the url to display */
    static final String EXTRA_NEWS = "extra_news";
    private static final String TAG = "WebViewActivity";
    private static final String CHARSET = "UTF-8";
    private static final byte[] HTTP_404_BYTES = "<!DOCTYPE html><html lang=\"en\"><head><title>404 Not found.</title></head><body></body></html>".getBytes(Charset.forName(CHARSET));
    private static final byte[] HTTP_400_BYTES_MAILTO = "<!DOCTYPE html><html lang=\"en\"><head><title>400 Bad Request.</title></head><body><h1>Mail links are not supported.</h1></body></html>".getBytes(Charset.forName(CHARSET));
    private static final int MAX_WEBSITE_ERRORS_TO_DISPLAY = 30;
    /** these are a big no-no (well, favicon isn't really, but we don't need it) */
    private static final String[] BADWORDS = new String[] {"analytics", "cpix", "favicon", "sitestat", "tracker", "tracking", "webtrekk", "xtcore"};
    WebView webView;
    private News news;

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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // if we can use a View that has been previously inflated, this method takes much less time
        ViewGroup preInflatedView = ((App)getApplicationContext()).getInflatedViewForWebViewActivity();
        if (preInflatedView != null) {
            ViewGroup content = findViewById(android.R.id.content);
            content.addView(preInflatedView);
        } else {
            getDelegate().setContentView(R.layout.activity_web_view);
        }

        setSupportActionBar(findViewById(R.id.toolbar));

        this.webView = findViewById(R.id.webView);
        boolean nightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        this.webView.setBackgroundColor(nightMode ? Color.BLACK : Color.WHITE);
        this.webView.setWebViewClient(new HamburgerWebViewClient(this));
        this.webView.setWebChromeClient(new HamburgerWebChromeClient(this));
        this.webView.clearHistory();

        final WebSettings ws = this.webView.getSettings();
        ws.setUserAgentString(App.USER_AGENT);
        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE)
                && Util.isNetworkMobile(this)) {
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
    public void onDetachedFromWindow() {
        // prepare a new instance of the content view for the next time this Acticity is launched
        ((App)getApplicationContext()).createInflatedViewForWebViewActivity(false);
        //
        ViewGroup content = findViewById(android.R.id.content);
        content.removeAllViews();
    }

    /** {@inheritDoc} */
    @Override
    protected void onResume() {
        super.onResume();
        this.webView.clearHistory();
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
                this.webView.loadUrl(Util.makeHttps(url));
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
    @VisibleForTesting
    static class HamburgerWebViewClient extends WebViewClient {

        private static final String[] DOWNLOADABLE_RESOURCES = new String[] {
                ".7z", ".apk", ".arw", ".bin", ".bz2", ".cr2", ".deb", ".dng", ".doc", ".epub", ".exe",
                ".gz", ".iso", ".jar", ".nef", ".ods", ".odt", ".pdf", ".ppt", ".rar", ".tgz", ".ttf", ".vhd", ".xls", ".xz", ".zip"
        };

        private final Handler handler = new Handler();
        private final WebViewActivity activity;

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
        @VisibleForTesting
        static boolean shouldBlock(@NonNull Uri uri) {
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

        private HamburgerWebViewClient(@NonNull WebViewActivity activity) {
            super();
            this.activity = activity;
        }

        /** {@inheritDoc} */
        @Override
        public void onPageFinished(WebView view, String url) {
            if ("about:blank".equals(url)) return;
            PageFinishedListener pageFinishedListener = this.activity.getPageFinishedListener();
            if (pageFinishedListener != null) pageFinishedListener.pageFinished(url);
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
                    wr = new WebResourceResponse("text/html", CHARSET, new ByteArrayInputStream(HTTP_400_BYTES_MAILTO));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_BAD_REQUEST, "Bad Request.");
                } else {
                    wr = new WebResourceResponse("text/html", CHARSET, new ByteArrayInputStream(HTTP_404_BYTES));
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NOT_FOUND, "Not found.");
                }
                //if (BuildConfig.DEBUG) Log.w(TAG, "shouldInterceptRequest() - blocking " + uri);
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
        private final AppCompatActivity activity;
        @Nullable private final Handler handler;
        @Nullable private final ProgressBar progressBar;
        @Nullable private final ViewHider viewHider;
        private final List<String> errors = new ArrayList<>();
        @Nullable private String urlForErrors;
        @Nullable private Snackbar sb;

        private HamburgerWebChromeClient(@NonNull AppCompatActivity activity) {
            super();
            this.activity = activity;
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
                    case WARNING: Log.w(getClass().getSimpleName(), consoleMessage.sourceId() + (line > 0 ? " - Line " + line : "") + ": " + consoleMessage.message()); break;
                    case ERROR: Log.e(getClass().getSimpleName(), consoleMessage.sourceId() + (line > 0 ? " - Line " + line : "") + ": " + consoleMessage.message()); break;
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
                boolean showErrors = PreferenceManager.getDefaultSharedPreferences(this.activity).getBoolean(App.PREF_SHOW_WEB_ERRORS, App.PREF_SHOW_WEB_ERRORS_DEFAULT);
                if (showErrors && (this.sb == null || !this.sb.isShown())) {
                    Snackbar sb = Snackbar.make(this.activity.findViewById(R.id.coordinator_layout), R.string.msg_website_errors, Snackbar.LENGTH_INDEFINITE);
                    Util.setSnackbarFont(sb, Util.CONDENSED, (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? -1 : 12));
                    sb.setAction("↗", v -> {
                        final int n = HamburgerWebChromeClient.this.errors.size();
                        StringBuilder msg = new StringBuilder(Math.min(2048, n * 256));
                        for (int count = 0; count < n; count++) {
                            String error = HamburgerWebChromeClient.this.errors.get(count);
                            msg.append(count + 1).append(". ").append(error).append('\n');
                            if (count == MAX_WEBSITE_ERRORS_TO_DISPLAY && count < n - 1) {msg.append("…"); break;}
                        }
                        LayoutInflater i = LayoutInflater.from(HamburgerWebChromeClient.this.activity);
                        @SuppressLint("InflateParams")
                        View view = i.inflate(R.layout.multi_text_view, null);
                        TextView tv = view.findViewById(R.id.textView);
                        tv.setTypeface(Util.CONDENSED);
                        tv.setTextSize(14f);
                        tv.setText(msg);
                        AlertDialog.Builder b = new AlertDialog.Builder(HamburgerWebChromeClient.this.activity)
                                .setTitle(R.string.label_website_errors)
                                .setView(view)
                                .setNeutralButton(R.string.action_show_weberrors_no, (dialog, which) -> {
                                    SharedPreferences.Editor ed =  PreferenceManager.getDefaultSharedPreferences(HamburgerWebChromeClient.this.activity).edit();
                                    ed.putBoolean(App.PREF_SHOW_WEB_ERRORS, false);
                                    ed.apply();
                                    dialog.dismiss();
                                })
                                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss());
                        b.show();
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
