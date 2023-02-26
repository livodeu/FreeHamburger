package de.freehamburger;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.Date;

import de.freehamburger.util.Util;
import de.freehamburger.version.Release;
import de.freehamburger.version.ReleaseChecker;

public class InfoActivity extends AppCompatActivity {

    private AlertDialog infoDialog;

    @NonNull
    static CharSequence makeInfo(@NonNull Context ctx, @Nullable Release... releases) {
        final SpannableStringBuilder info = new SpannableStringBuilder().append('\n');
        SpannableString title = new SpannableString(ctx.getString(R.string.app_name));
        title.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString version = new SpannableString(BuildConfig.VERSION_NAME);
        version.setSpan(new RelativeSizeSpan(0.75f), 0, version.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        info.append(title).append(' ').append(version);
        info.append("\n\n").append(ctx.getString(R.string.app_build_date, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(BuildConfig.BUILD_TIME))));
        info.append("\n\n").append(ctx.getString(R.string.app_license));
        if (releases != null && releases.length > 0) {
            info.append("\n\n");
            final int n = releases.length;
            for (int i = 0; i < n; i++) {
                Release release = releases[i];
                if (release == null) continue;
                String releaseInfo;
                if (release.getPublishedAt() > 0L) releaseInfo = ctx.getString(R.string.msg_release_latest, release.getPrettyTagName(), DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(release.getPublishedAt())));
                else releaseInfo = ctx.getString(R.string.msg_release_latest_no_date, release.getPrettyTagName());
                boolean newerReleaseAvailable = ReleaseChecker.isNewerReleaseAvailable(release);
                if (release.getRepo() == Release.REPO_GITHUB && newerReleaseAvailable) {
                    info.append(Util.createClickable(ctx, releaseInfo, ReleaseChecker.makeBrowseGithubIntent(release)));
                } else if (release.getRepo() == Release.REPO_FDROID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && newerReleaseAvailable) {
                    info.append(Util.createClickable(ctx, releaseInfo, ReleaseChecker.makeBrowseFdroidIntent(ctx)));
                } else {
                    info.append(releaseInfo);
                }
                if (i < n - 1) info.append("\n");
            }
        }
        return info;
    }

    /** {@inheritDoc} */
    @Override protected void onPause() {
        if (this.infoDialog != null && this.infoDialog.isShowing()) {
            this.infoDialog.dismiss();
            this.infoDialog = null;
        }
        super.onPause();
        finish();
    }

    /** {@inheritDoc} */
    @Override protected void onResume() {
        super.onResume();
        this.infoDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setIcon(R.mipmap.ic_launcher)
                .setMessage(makeInfo(this))
                .setOnCancelListener((dialog) -> finish())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {dialog.dismiss(); finish();})
                .show();
    }
}
