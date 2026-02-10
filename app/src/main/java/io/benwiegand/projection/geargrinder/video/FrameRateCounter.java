package io.benwiegand.projection.geargrinder.video;

import android.os.SystemClock;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FrameRateCounter {
    private static final long AVERAGE_OVER_MS = 300;

    private final Queue<Long> frameTimes = new ConcurrentLinkedQueue<>();

    private void prune() {
        synchronized (frameTimes) {
            Long frameTime;
            while ((frameTime = frameTimes.peek()) != null) {
                if (frameTime > SystemClock.elapsedRealtime() - AVERAGE_OVER_MS) return;
                frameTimes.poll();
            }
        }
    }

    public void onFrame() {
        frameTimes.add(SystemClock.elapsedRealtime());
        prune();
    }


    public int getFrameRate() {
        prune();
        return (int) (frameTimes.size() * 1000 / AVERAGE_OVER_MS);
    }


}
