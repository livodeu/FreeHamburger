package de.freehamburger.model;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class Dictionary {

    private static final Dictionary DWDS = new Dictionary("DWDS", "https://www.dwds.de/?q=");
    private static final Dictionary WIKTIONARY = new Dictionary("Wiktionary", "https://de.m.wiktionary.org/wiki/");

    private static final Dictionary[] ALL = new Dictionary[] {DWDS, WIKTIONARY};

    @NonNull private final String name;
    @NonNull private final String url;

    /**
     * Constructor.
     * @param name name to be displayed
     * @param url url prefix
     */
    private Dictionary(@NonNull String name, @NonNull String url) {
        super();
        this.name = name;
        this.url = url;
    }

    public static void enable(@NonNull TextView textView) {
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            final MenuItem[] items = new MenuItem[ALL.length];

            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                String selection = textView.getText().subSequence(textView.getSelectionStart(), textView.getSelectionEnd()).toString().trim();
                boolean validSelection = selection.length() > 0 && TextUtils.indexOf(selection, ' ') < 0;
                if (!validSelection) return false;
                for (Dictionary d : ALL) {
                    if (d.name.equals(item.getTitle().toString())) {
                        Intent lookup = new Intent(Intent.ACTION_VIEW);
                        lookup.setData(Uri.parse(d.url + selection));
                        lookup.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                        try {
                            textView.getContext().startActivity(lookup);
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                int i = 0;
                for (Dictionary d : ALL) {
                    this.items[i++] = menu.add(0, 0, 100 + i, d.name);
                }
                return true;
            }

            @Override public void onDestroyActionMode(ActionMode mode) {
            }

            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                String selection = textView.getText().subSequence(textView.getSelectionStart(), textView.getSelectionEnd()).toString().trim();
                boolean validSelection = selection.length() > 0 && TextUtils.indexOf(selection, ' ') < 0;
                for (MenuItem item : this.items) {
                    item.setVisible(validSelection);
                }
                return true;
            }
        });

    }
}
