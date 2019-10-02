package de.freehamburger.util;

import android.app.Activity;
import android.os.Handler;
import androidx.annotation.NonNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class Intro implements Runnable {

    private final Reference<Activity> refAct;
    private final Handler handler = new Handler();
    private final List<Step> steps = new ArrayList<>();
    private boolean playing;
    private boolean cancelRequested;

    public Intro(@NonNull Activity activity) {
        super();
        this.refAct = new WeakReference<>(activity);
    }

    public void addStep(@NonNull Step step) {
        this.steps.add(step);
    }

    public void cancel() {
        this.cancelRequested = true;
        this.handler.removeCallbacksAndMessages(null);
    }

    public boolean isPlaying() {
        return playing;
    }

    @Override
    public void run() {
        Activity activity = refAct.get();
        if (activity == null) return;
        playing = true;
        long total = 0L;
        for (Step step : steps) {
            if (this.cancelRequested || activity.isFinishing() || activity.isDestroyed()) break;
            long delay = step.delay;
            total += delay;
            handler.postDelayed(step, total);
        }
        handler.postDelayed(() -> {
            steps.clear();
            cancelRequested = false;
            playing = false;
        }, total + 500L);
    }

    public abstract static class Step implements Runnable {

        private final long delay;

        protected Step(long delay) {
            super();
            this.delay = delay;
        }
    }
}
