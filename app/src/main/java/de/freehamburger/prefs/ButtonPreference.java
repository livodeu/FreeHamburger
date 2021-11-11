package de.freehamburger.prefs;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import androidx.annotation.ArrayRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import de.freehamburger.BuildConfig;
import de.freehamburger.R;

/**
 * A preference that can represent multiple states.<br>
 * These attributes are available:<br>
 * <ul>
 * <li>app:entries="@array/entries_list" (string-array, required)</li>
 * <li>app:colors="@array/colors_list" (array of color values, optional)</li>
 * <li>app:soundeffect="@raw/soundfile" (optional)</li>
 * </ul>
 */
public class ButtonPreference extends Preference implements View.OnClickListener {

    private static final float INITIAL_ROTATION = 270f;
    private static final float PI180 = (float)(Math.PI / 180.);
    /** rotation period in ms */
    private static final long ROTATION = 333L;
    private static final float SHADOW_BLUR = 4f;
    private static final float SHADOW_X = 4f;
    private static final float SHADOW_Y = 4f;

    private final Handler handler = new Handler();

    private View layout;

    private Button button;
    @IntRange(from = 0) private int selectedIndex = 0;
    @NonNull private final CharSequence[] states;
    @Nullable private int[] colors;

    private SoundPool soundPool;
    @RawRes private int sound;

    /**
     * Creates the color used for text shadows.
     * @param textColor text color
     * @return color for text shadow
     */
    @ColorInt
    private static int makeShadowColor(@ColorInt final int textColor) {
        return Color.argb(0x7c, Color.red(textColor), Color.green(textColor), Color.blue(textColor));
    }

    /**
     * Constructor.
     * @param context Context
     */
    public ButtonPreference(Context context) {
        this(context, null, 0);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     */
    public ButtonPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor.
     * @param context Context
     * @param attrs AttributeSet
     * @param defStyleAttr default style attr
     * @throws IllegalArgumentException if no {@link R.styleable#ButtonPreference_entries entries} have been defined
     */
    @SuppressWarnings("WeakerAccess")
    public ButtonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_widget_button);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ButtonPreference, defStyleAttr, 0);
        CharSequence[] cs = a.getTextArray(R.styleable.ButtonPreference_entries);
        if (cs == null || cs.length == 0) throw new IllegalArgumentException("No entries!");
        this.states = cs;
        @ArrayRes
        int colorsId = a.getResourceId(R.styleable.ButtonPreference_colors, 0);
        if (colorsId != 0) {
            this.colors = context.getResources().getIntArray(colorsId);
            if (this.colors.length != this.states.length) {
                this.colors = null;
            }
        }
        @RawRes int soundEffectId = a.getResourceId(R.styleable.ButtonPreference_soundeffect,0);
        a.recycle();
        if (soundEffectId != 0) {
            this.soundPool = new SoundPool.Builder().setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            this.sound = this.soundPool.load(context.getApplicationContext(), soundEffectId, 1);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        this.button = (Button)holder.findViewById(R.id.button);
        if (this.colors != null) {
            this.button.setTextColor(this.colors[this.selectedIndex]);
        }
        this.button.setOnClickListener(this);

        // super hides the widget_frame
        View wf = holder.findViewById(android.R.id.widget_frame);
        if (wf != null) wf.setVisibility(View.VISIBLE);
        else if (BuildConfig.DEBUG) android.util.Log.e(getClass().getSimpleName(), "android.R.id.widget_frame not found!");

        rotate(true);

        this.handler.postDelayed(new SummarySetter(), 200L);
    }

    /** {@inheritDoc} */
    @Override
    public void onClick(View button) {
        if (this.sound != 0 && this.soundPool != null) {
            this.soundPool.play(this.sound, 1f, 1f, 1, 0, 1f);
        }
        if (this.states.length == 0) return;
        int newValue = this.selectedIndex + 1;
        newValue %= this.states.length;
        if (callChangeListener(newValue)) {
            this.selectedIndex = newValue;
            persistInt(this.selectedIndex);
            rotate(false);
            this.handler.postDelayed(this::notifyChanged, ROTATION);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    /** {@inheritDoc} */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        this.selectedIndex = Math.abs(savedState.selectedIndex);    // not that a value smaller than 0 gets ever written, but you never know...
    }

    /** {@inheritDoc} */
    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting.
         * We must save the instance state so it is able to, for example, survive orientation changes.
         */
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState savedState = new SavedState(superState);
        savedState.selectedIndex = this.selectedIndex;
        return savedState;
    }

    /** {@inheritDoc} */
    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        this.selectedIndex = getPersistedInt(this.selectedIndex);
    }

    private void rotate(boolean quick) {
        float nextRotation = INITIAL_ROTATION + this.selectedIndex * 360f / (this.states.length);
        while (nextRotation <= 0f) nextRotation += 360f;
        while (nextRotation > 360f) nextRotation -= 360f;
        float currentRotation = this.button.getRotation();
        if (currentRotation >= 360f) {
            currentRotation -= 360f;
            this.button.setRotation(currentRotation);
        } else if (currentRotation < 0f) {
            currentRotation += 360f;
            this.button.setRotation(currentRotation);
        }
        if (Math.abs(nextRotation - currentRotation) <= 0.1f) {
            // No need to rotate because the new rotation is the same as the current one
            return;
        }
        if (nextRotation < currentRotation) nextRotation += 360f;
        if (quick) {
            this.button.setRotation(nextRotation);
            float r = nextRotation * PI180;
            this.button.setShadowLayer(SHADOW_BLUR, SHADOW_X * (float)Math.sin(r), SHADOW_Y * (float)Math.cos(r), makeShadowColor(this.button.getCurrentTextColor()));
            if (this.colors != null) {
                this.button.setTextColor(this.colors[this.selectedIndex]);
            }
        } else {
            ObjectAnimator rotator = ObjectAnimator.ofFloat(this.button, "rotation", nextRotation).setDuration(ROTATION);
            TimeAnimator ta = new TimeAnimator();
            ta.setDuration(ROTATION);
            ta.setTimeListener(new ShadowUpdater(this.button));
            ObjectAnimator phaser = null;
            if (this.colors != null) {
                phaser = ObjectAnimator.ofArgb(this.button, "textColor", this.button.getCurrentTextColor(), this.colors[this.selectedIndex]).setDuration(ROTATION);
            }

            AnimatorSet set = new AnimatorSet();
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            if (phaser != null) {
                set.playTogether(rotator, phaser, ta);
            } else {
                set.playTogether(rotator, ta);
            }
            set.start();
        }
    }

    /**
     *
     */
    private static class ShadowUpdater implements TimeAnimator.TimeListener {

        private final Reference<TextView> refView;

        private ShadowUpdater(@NonNull TextView tv) {
            super();
            this.refView = new WeakReference<>(tv);
        }

        /** {@inheritDoc} */
        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            TextView tv = this.refView.get();
            if (tv == null) return;
            float r = tv.getRotation() * PI180;
            int color = makeShadowColor(tv.getCurrentTextColor());
            tv.setShadowLayer(SHADOW_BLUR, SHADOW_X * (float)Math.sin(r), SHADOW_Y * (float)Math.cos(r), color);
        }
    }

    /**
     * Stores the state of the preference.
     */
    private static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<ButtonPreference.SavedState>() {
            public ButtonPreference.SavedState createFromParcel(Parcel in) {
                return new ButtonPreference.SavedState(in);
            }
            public ButtonPreference.SavedState[] newArray(int size) {
                return new ButtonPreference.SavedState[size];
            }
        };
        private int selectedIndex;

        /**
         * Constructor.
         * @param source Parcel
         */
        SavedState(Parcel source) {
            super(source);
            this.selectedIndex = source.readInt();
        }

        /**
         * Constructor.
         * @param superState Parcelable
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /** {@inheritDoc} */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.selectedIndex);
        }
    }

    private class SummarySetter implements Runnable {
        @Override
        public void run() {
            if (ButtonPreference.this.states.length > 0) {
                ButtonPreference.this.button.setEnabled(true);
                setSummary(ButtonPreference.this.states[ButtonPreference.this.selectedIndex]);
            } else {
                ButtonPreference.this.button.setEnabled(false);
                setSummary("");
            }
        }
    }
}
