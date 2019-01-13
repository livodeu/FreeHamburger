package de.freehamburger.views;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import de.freehamburger.R;
import de.freehamburger.model.Filter;
import de.freehamburger.model.TextFilter;
import de.freehamburger.util.CoordinatorLayoutHolder;

/**
 *
 */
public class FilterView extends RelativeLayout implements TextWatcher, CompoundButton.OnCheckedChangeListener {

    private static final String PREF_1F601_SHOWN = "pref_1f601_shown";
    private EditText editTextPhrase;
    private ImageButton buttonDelete;
    private RadioButton radioButtonAnywhere;
    private RadioButton radioButtonAtStart;
    private RadioButton radioButtonAtEnd;
    private Reference<Listener> reflistener;

    /**
     * Constructor.
     * @param context Context
     */
    public FilterView(Context context) {
        super(context);
        init(context);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public FilterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr int
     */
    public FilterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /** {@inheritDoc} */
    @Override
    public void afterTextChanged(Editable s) {
        if (this.reflistener != null) {
            Listener listener = this.reflistener.get();
            if (listener != null) listener.textChanged(s);
        }
        boolean phraseIsEmpty = TextUtils.isEmpty(s);
        this.buttonDelete.setImageResource(phraseIsEmpty ? R.drawable.ic_delete_black_24dp : R.drawable.ic_clear_black_24dp);
        this.buttonDelete.setContentDescription(phraseIsEmpty ? getContext().getString(R.string.hint_filter_button_delete) : getContext().getString(R.string.hint_filter_button_clear));
        if (s.toString().toLowerCase().hashCode() == 110640538 // 🎺.substring(0,5)
                && !PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(PREF_1F601_SHOWN, false)) {
            Context ctx = getContext();
            if (ctx instanceof Activity) {
                View v = ((Activity)ctx).findViewById(R.id.v1f601);
                if (v != null) {
                    v.setVisibility(View.VISIBLE);
                    Handler handler = getHandler();
                    if (handler == null) handler = new Handler();
                    handler.postDelayed(() -> v.setVisibility(View.GONE), 3_000L);
                    SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
                    ed.putBoolean(PREF_1F601_SHOWN, true);
                    ed.apply();
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        /* no-op */
    }

    public void focusEditText() {
        this.editTextPhrase.requestFocus();
        InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this.editTextPhrase, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * Initialisation.
     * @param ctx Context
     */
    private void init(Context ctx) {
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return;
        inflater.inflate(R.layout.textfilter_view, this);
        this.editTextPhrase = findViewById(R.id.editTextPhrase);
        this.radioButtonAnywhere = findViewById(R.id.radioButtonAnywhere);
        this.radioButtonAtStart = findViewById(R.id.radioButtonAtStart);
        this.radioButtonAtEnd = findViewById(R.id.radioButtonAtEnd);
        this.radioButtonAnywhere.setOnCheckedChangeListener(this);
        this.radioButtonAtStart.setOnCheckedChangeListener(this);
        this.radioButtonAtEnd.setOnCheckedChangeListener(this);
        this.buttonDelete = findViewById(R.id.buttonDelete);
        this.buttonDelete.setOnLongClickListener(new ContentDescriptionDisplayer());
        this.editTextPhrase.addTextChangedListener(this);
    }

    /** {@inheritDoc} */
    @Override
    public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
        if (!isChecked) return;
        if (buttonView == this.radioButtonAnywhere) {
            if (reflistener != null) {
                Listener listener = reflistener.get();
                if (listener != null) listener.anywhere();
            }
        } else if (buttonView == this.radioButtonAtStart) {
            if (reflistener != null) {
                Listener listener = reflistener.get();
                if (listener != null) listener.atStart();
            }
        } else if (buttonView == this.radioButtonAtEnd) {
            if (reflistener != null) {
                Listener listener = reflistener.get();
                if (listener != null) listener.atEnd();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        /* no-op */
    }

    public void setFilter(@Nullable Filter filter) {
        if (filter instanceof TextFilter) {
            setFilter((TextFilter)filter);
            return;
        }
        if (filter == null) {
            this.editTextPhrase.setText(null);
        } else {
            this.editTextPhrase.setText(filter.getText());
            this.editTextPhrase.setEnabled(filter.isEditable());
            this.radioButtonAnywhere.setEnabled(false);
            this.radioButtonAtStart.setEnabled(false);
            this.radioButtonAtEnd.setEnabled(false);

        }
    }

    private void setFilter(@Nullable TextFilter textFilter) {
        if (textFilter == null) {
            this.editTextPhrase.setText(null);
        } else {
            this.editTextPhrase.setText(textFilter.getText());
            this.editTextPhrase.setEnabled(textFilter.isEditable());
            this.radioButtonAnywhere.setEnabled(true);
            this.radioButtonAtStart.setEnabled(true);
            this.radioButtonAtEnd.setEnabled(true);
            if (textFilter.isAtStart()) this.radioButtonAtStart.setChecked(true);
            else if (textFilter.isAtEnd()) this.radioButtonAtEnd.setChecked(true);
            else this.radioButtonAnywhere.setChecked(true);

        }
    }

    public void setListener(@Nullable Listener listener) {
        this.reflistener = listener != null ? new WeakReference<>(listener) : null;
    }

    public interface Listener {
        /** the "anywhere" radio button has been checked */
        void anywhere();

        /** the "at end" radio button has been checked */
        void atEnd();

        /** the "at start" radio button has been checked */
        void atStart();

        /**
         * The text has been modified.
         * @param s Editable
         */
        void textChanged(Editable s);
    }

    /**
     * Displays a View's {@link View#getContentDescription() content description} in a Snackbar or a Toast.
     */
    private static class ContentDescriptionDisplayer implements OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            CharSequence contentDescription = v.getContentDescription();
            if (TextUtils.isEmpty(contentDescription)) return false;
            Context ctx = v.getContext();
            if (ctx instanceof CoordinatorLayoutHolder) {
                Snackbar.make(((CoordinatorLayoutHolder)ctx).getCoordinatorLayout(), contentDescription, Snackbar.LENGTH_SHORT).show();
            } else {
                Toast.makeText(v.getContext(), contentDescription, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }
}
