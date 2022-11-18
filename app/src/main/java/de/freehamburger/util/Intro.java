package de.freehamburger.util;

import android.app.Activity;
import android.os.Handler;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes several Runnables one after the other.
 */
public class Intro implements Runnable {

    private final Reference<Activity> refAct;
    private final Handler handler = new Handler();
    private final List<Runnable> steps = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private boolean playing;
    private boolean cancelRequested;

    /**
     * Constructor.
     * @param activity Activity
     */
    public Intro(@NonNull Activity activity) {
        super();
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

    public boolean isPlaying() {
        return this.playing;
    }

    @Override
    public void run() {
        Activity activity = this.refAct.get();
        if (activity == null) return;
        this.playing = true;
        long total = 0L;
        final int nSteps = this.steps.size();
        for (int i = 0; i < nSteps; i++) {
            if (this.cancelRequested || activity.isFinishing() || activity.isDestroyed()) break;
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
