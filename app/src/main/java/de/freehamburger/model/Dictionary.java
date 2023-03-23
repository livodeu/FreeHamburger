package de.freehamburger.model;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;

import de.freehamburger.BuildConfig;
import de.freehamburger.R;
import de.freehamburger.util.CoordinatorLayoutHolder;
import de.freehamburger.util.Util;

/**
 * Definition of a web resource that can be used to look up words or phrases.
 */
public class Dictionary {

    private static final Dictionary DWDS = new Dictionary("DWDS", "https://www.dwds.de/?q=", false);
    private static final Dictionary WIKIPEDIA = new Dictionary("Wikipedia", "https://de.m.wikipedia.org/wiki/Special:Search?search=", true);
    private static final Dictionary WIKTIONARY = new Dictionary("Wiktionary", "https://de.m.wiktionary.org/wiki/", false);
    private static final Dictionary[] ALL = new Dictionary[] {DWDS, WIKIPEDIA, WIKTIONARY};

    /** if a selection is longer than this, ask user for confirmation as it might be in error */
    private static final int CONFIRMATION_THRESHOLD = 20;

    /** period of time to display the confirmation snackbar in ms */
    private static final int CONFIRMATION_DURATION = 7_000;

    @NonNull private final String name;
    @NonNull private final String url;
    private final boolean spaceAllowed;

    /**
     * Constructor.
     * @param name name to be displayed
     * @param url url prefix
     * @param spaceAllowed {@code true} to allow spaces within the selected text
     */
    private Dictionary(@NonNull String name, @NonNull String url, boolean spaceAllowed) {
        super();
        this.name = name;
        this.url = url;
        this.spaceAllowed = spaceAllowed;
    }

    /**
     * Enables the known dictionaries for the given TextView.
     * @param textView TextView
     * @throws NullPointerException if {@code textView} is {@code null}
     */
    public static void enable(@NonNull final TextView textView) {
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            final MenuItem[] items = new MenuItem[ALL.length];
            Snackbar confirmation;

            /**
             * Peforms the actual lookup.<br>
             * <i>Note that the current implementation assumes that the text is simply appended to the url!</i>
             * @param d Dictionary to use
             * @param selection text to look up
             * @throws NullPointerException if {@code d} is {@code null}
             */
            private void lookup(@NonNull Dictionary d, @NonNull CharSequence selection) {
                final Intent lookup = new Intent(Intent.ACTION_VIEW);
                lookup.setData(Uri.parse(d.url + selection));
                lookup.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                try {
                    textView.getContext().startActivity(lookup);
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(textView.getContext(), R.string.error_no_app, Toast.LENGTH_SHORT).show();
                }
            }

            /** {@inheritDoc} */
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                final String selection = textView.getText().subSequence(textView.getSelectionStart(), textView.getSelectionEnd()).toString().trim();
                if (selection.length() <= 0) return true;
                final boolean selectionHasSpace = TextUtils.indexOf(selection, ' ') >= 0;
                for (Dictionary d : ALL) {
                    if (TextUtils.equals(d.name, item.getTitle()) && (d.spaceAllowed || !selectionHasSpace)) {
                        if (selection.length() > CONFIRMATION_THRESHOLD) {
                            View anchor = null;
                            if (textView.getContext() instanceof CoordinatorLayoutHolder) {
                                anchor = ((CoordinatorLayoutHolder)textView.getContext()).getCoordinatorLayout();
                            }
                            if (anchor == null) anchor = textView;
                            this.confirmation = Snackbar.make(anchor, textView.getContext().getString(R.string.msg_confirm_lookup, d.name), CONFIRMATION_DURATION);
                            this.confirmation.setAction(R.string.label_yes, v -> lookup(d, selection));
                            Util.setSnackbarMaxLines(this.confirmation, 6);
                            this.confirmation.addCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar transientBottomBar, int event) {
                                    // it's a android.text.SpannableString
                                    if (textView.getText() instanceof Spannable) {
                                        Selection.setSelection((Spannable)textView.getText(), textView.getSelectionStart());
                                    }
                                }
                            });
                            this.confirmation.show();
                            Util.fadeSnackbar(this.confirmation, null, CONFIRMATION_DURATION);
                        } else {
                            lookup(d, selection);
                        }
                        return true;
                    }
                }
                return false;
            }

            /** {@inheritDoc} */
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.setTitle("Hello People!");
                int i = 0;
                for (Dictionary d : ALL) {
                    this.items[i++] = menu.add(0, 0, 100 + i, d.name);
                }
                return true;
            }

            /** {@inheritDoc} */
            @Override public void onDestroyActionMode(ActionMode mode) {
                if (this.confirmation != null) {
                    if (this.confirmation.isShown()) this.confirmation.dismiss();
                    this.confirmation = null;
                }
            }

            /** {@inheritDoc} */
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                boolean modified = false;
                try {
                    String selection = textView.getText().subSequence(textView.getSelectionStart(), textView.getSelectionEnd()).toString().trim();
                    final int selectionLength = selection.length();
                    final boolean selectionHasSpace = TextUtils.indexOf(selection, ' ') >= 0;
                    for (int i = 0; i < ALL.length; i++) {
                        this.items[i].setVisible(selectionLength > 0 && (ALL[i].spaceAllowed || !selectionHasSpace));
                        modified = true;
                    }
                    return modified;
                } catch (RuntimeException re) {
                    if (BuildConfig.DEBUG) android.util.Log.e(getClass().getSimpleName(), re.toString());
                }
                return modified;
            }
        });

    }
}
