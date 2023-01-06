package de.freehamburger.util;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.freehamburger.App;

/**
 * Executes several Runnables one after the other.
 */
public class Intro implements Runnable {

    private final App app;
    private final Reference<Activity> refAct;
    private final Handler handler = new Handler();
    private final List<Runnable> steps = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private boolean playing;
    private boolean cancelRequested;

    /**
     * Constructor.
     * @param activity Activity
     * @throws NullPointerException if {@code activity} is {@code null}
     */
    public Intro(@NonNull Activity activity) {
        super();
        this.app = (App)activity.getApplicationContext();
        this.refAct = new WeakReference<>(activity);
    }

    public void addStep(@NonNull Runnable step, @IntRange(from = 0) long delay) {
        this.steps.add(step);
        this.delays.add(delay);
    }

    public void cancel() {
        this.cancelRequested = true;
        this.handler.removeCallbacksAndMessages(null);
    }

    /**
     * Sets the appropriate flag in the preferences to enable the Intro.
     */
    private void enable() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.app);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean(App.PREF_PLAY_INTRO, true);
        ed.apply();
    }

    public boolean isPlaying() {
        return this.playing;
    }

    @Override
    public void run() {
        Activity activity = this.refAct.get();
        if (activity == null) {
            enable();
            return;
        }
        this.playing = true;
        long total = 0L;
        final int nSteps = this.steps.size();
        for (int i = 0; i < nSteps; i++) {
            if (this.cancelRequested || activity.isFinishing() || activity.isDestroyed()) {
                // if this happens, re-enable intro to run at the next opportunity
                enable();
                break;
            }
            total += this.delays.get(i);
            this.handler.postDelayed(this.steps.get(i), total);
        }
        // clean up
        this.handler.postDelayed(() -> {
            this.steps.clear();
            this.cancelRequested = false;
            this.playing = false;
        }, total + 500L);
    }
}
