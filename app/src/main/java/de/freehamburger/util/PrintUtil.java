package de.freehamburger.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.preference.PreferenceManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.print.PrintHelper;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Locale;

import de.freehamburger.App;
import de.freehamburger.BuildConfig;
import de.freehamburger.HamburgerActivity;
import de.freehamburger.R;
import de.freehamburger.WebViewActivity;
import de.freehamburger.model.News;

public final class PrintUtil {

    private static final String TAG = "PrintUtil";

    private PrintUtil() {
    }

    /**
     * Determines whether the given News can be printed.
     * @param news News
     * @return true / false
     */
    public static boolean canPrint(@Nullable final News news) {
        return news != null && news.getContent() != null && !TextUtils.isEmpty(news.getContent().getHtmlText());
    }

    private static CharSequence makeHttps(final String html) {
        if (html == null) return "";
        final StringBuilder sb = new StringBuilder(html.length());
        for (int pos = 0;;) {
            int start = html.indexOf("http:", pos);
            if (start < 0) {
                if (pos < html.length()) sb.append(html.substring(pos));
                break;
            }
            sb.append(html.substring(pos, start)).append("https:");
            pos = start + 5;
        }
        return sb;
    }

    private static void printHtml(@NonNull Activity activity, @NonNull String html, @NonNull String title, @Nullable final PrintJobReceiver pr) {
        if (!html.toLowerCase(Locale.US).startsWith("<html>")) {
            html = "<html lang=\"de\"><head><title>" + title + "</title></head><body>" + html + "</body></html>";
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int fontZoom = prefs.getInt(App.PREF_FONT_ZOOM, App.PREF_FONT_ZOOM_DEFAULT);
        boolean overMobile = prefs.getBoolean(App.PREF_LOAD_OVER_MOBILE, App.DEFAULT_LOAD_OVER_MOBILE);
        WebView webView = new WebView(activity);
        WebSettings ws = webView.getSettings();
        ws.setUserAgentString(App.USER_AGENT);
        if (!overMobile && Util.isNetworkMobile(activity)) {
            ws.setBlockNetworkLoads(true);
        }
        if (fontZoom != 100) {
            ws.setTextZoom(fontZoom);
        }
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        ws.setDomStorageEnabled(false);
        ws.setGeolocationEnabled(false);
        ws.setDatabaseEnabled(false);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ws.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ws.setSafeBrowsingEnabled(false);
        }
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                if ("about:blank".equals(url)) {
                    if (pr != null) pr.setPrintJob(null);
                    return;
                }
                PrintManager printManager = (PrintManager) view.getContext().getSystemService(Context.PRINT_SERVICE);
                // Get a print adapter instance
                PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(title);
                // Create a print job with name and adapter instance
                PrintJob pj = null;
                try {
                    pj = printManager.print(title, printAdapter, new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, e.toString());
                }
                if (pr != null) pr.setPrintJob(pj);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (BuildConfig.DEBUG) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.w(TAG, "While loading " + request.getUrl() + ": " + error.getErrorCode() + " " + error.getDescription());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (BuildConfig.DEBUG) Log.w(TAG, "While loading " + request.getUrl() + ": " + errorResponse.getStatusCode() + " " + errorResponse.getReasonPhrase());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                if (BuildConfig.DEBUG) Log.w(TAG, "SSL error " + error);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (pr != null) pr.setPrintJob(null);
                view.destroy();
                return true;
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                final Uri uri = request.getUrl();
                if (WebViewActivity.HamburgerWebViewClient.shouldBlock(uri)) {
                    if (BuildConfig.DEBUG) Log.i(TAG, "shouldInterceptRequest(…, " + uri + ") returns 404");
                    WebResourceResponse wr;
                    wr = new WebResourceResponse("text/html", "UTF-8", null);
                    wr.setStatusCodeAndReasonPhrase(HttpURLConnection.HTTP_NOT_FOUND, "Not found.");
                    return wr;
                }
                return null;
            }

            /** {@inheritDoc} */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
        String encodedHtml = Base64.encodeToString(html.getBytes(), Base64.NO_PADDING);
        webView.loadData(encodedHtml, "text/html", "base64");
    }

    /**
     * Prints the given picture.
     * @param activity Context
     * @param bmp Bitmap
     * @param title title to be used as job name (optional
     */
    @RequiresApi(19)
    public static void printImage(@NonNull Activity activity, @NonNull Bitmap bmp, @Nullable String title) {
        if (title == null) title = activity.getString(R.string.app_name);
        PrintHelper photoPrinter = new PrintHelper(activity);
        photoPrinter.setScaleMode(PrintHelper.SCALE_MODE_FIT);
        PrintHelper.OnPrintFinishCallback callback;
        if (activity instanceof HamburgerActivity) {
            callback = () -> {
                HamburgerActivity a = (HamburgerActivity)activity;
                if (a.isFinishing() || a.isDestroyed()) return;
                Util.makeSnackbar(activity, R.string.msg_print_finished, Snackbar.LENGTH_SHORT).show();
            };
        } else {
            callback = null;
        }
        photoPrinter.printBitmap(title, bmp, callback);
    }

    /**
     * Prints the given News' html text.
     * @param activity Activity
     * @param news News
     * @param pr optional PrintJobReceiver
     * @return true / false
     * @throws NullPointerException if {@code ctx} is {@code null}
     */
    public static boolean printNews(@NonNull Activity activity, @NonNull News news, @Nullable final PrintJobReceiver pr) {
        if (!canPrint(news)) return false;
        String title = news.getTitle();
        if (title == null) title = news.getFirstSentence();
        if (title == null) title = activity.getString(R.string.app_name);
        title = title.replace(' ', '_').replace('/', '_').replace(':', '_');
        //noinspection ConstantConditions
        printHtml(activity, unwrapLinks(makeHttps(news.getContent().getHtmlText()).toString()).toString(), title, pr);
        return true;
    }

    /**
     * Converts <pre><a href="#">Label</a></pre> to <pre>Label</pre>
     * @param html html to remove anchors from
     * @return html without anchors
     */
    @NonNull
    private static CharSequence unwrapLinks(final String html) {
        if (html == null) return "";
        final StringBuilder sb = new StringBuilder(html.length());
        for (int pos = 0;;) {
            int astart = html.indexOf("<a", pos);
            if (astart < 0) {
                if (pos < html.length()) sb.append(html.substring(pos));
                break;
            }
            int aend = html.indexOf('>', astart + 2);
            if (aend < 0) break;
            int closingstart = html.indexOf("</a>", aend + 1);
            if (closingstart < 0) break;
            sb.append(html.substring(pos, astart)).append(html.substring(aend + 1, closingstart));
            pos = closingstart + 4;
        }
        return sb;
    }

    @FunctionalInterface
    public interface PrintJobReceiver {
        void setPrintJob(@Nullable PrintJob printJob);
    }

    /**
     * Monitors the state of a print job.
     */
    public static class PrintJobWaiter extends Thread {

        private final Reference<HamburgerActivity> refa;
        private PrintJob printJob;

        public PrintJobWaiter(@NonNull HamburgerActivity a, PrintJob printJob) {
            super();
            this.refa = new WeakReference<>(a);
            this.printJob = printJob;
            setPriority(Thread.NORM_PRIORITY - 1);
        }

        @Override
        public void run() {
            if (this.printJob == null) return;
            for (;;) {
                HamburgerActivity a = this.refa.get();
                if (a == null || a.isFinishing() || a.isDestroyed()) break;
                if (this.printJob.isCancelled()) {
                    Util.makeSnackbar(a, R.string.msg_print_cancelled, 1_000).show();
                    break;
                } else if (this.printJob.isCompleted()) {
                    Util.makeSnackbar(a, R.string.msg_print_finished, Snackbar.LENGTH_SHORT).show();
                    break;
                } else if (this.printJob.isFailed() || this.printJob.isBlocked()) {
                    PrintJobInfo pi =  this.printJob.getInfo();
                    String label = pi.getLabel();
                    if (TextUtils.isEmpty(label)) {
                        Util.makeSnackbar(a , R.string.msg_print_failed, Snackbar.LENGTH_LONG).show();
                    } else {
                        // attempt to determine the status text
                        String pis = pi.toString();
                        String status = null;
                        int status0 = pis.indexOf("status: ");
                        if (status0 >= 0) {
                            int status1 = pis.indexOf(",", status0 + 8);
                            if (status1 - status0 > 8) status = pis.substring(status0 + 8, status1).trim();
                        }
                        try {
                            Snackbar sb = Util.makeSnackbar(a, !TextUtils.isEmpty(status) ? a.getString(R.string.msg_print_failed_wlabel_wstatus, label, status) : a.getString(R.string.msg_print_failed_wlabel, label), Snackbar.LENGTH_INDEFINITE);
                            Util.setSnackbarMaxLines(sb, 5);
                            sb.setAction(android.R.string.ok, v -> sb.dismiss());
                            sb.show();
                        } catch (Exception ignored) {
                        }
                    }
                    this.printJob.cancel();
                    break;
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(2_000L);
                } catch (InterruptedException e) {
                    if (BuildConfig.DEBUG) Log.w(TAG, e.toString());
                    break;
                }
            }
            this.printJob = null;
        }
    }
}
