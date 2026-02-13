package io.benwiegand.projection.libprivd.sec;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

public class PendingSec<T> {
    private final Object lock = new Object();

    private boolean started = false;
    private boolean wrapped = false;

    private final Supplier<Sec<T>> secStarter;

    PendingSec(Supplier<Sec<T>> secStarter) {
        this.secStarter = secStarter;
    }

    public Sec<T> start() {
        synchronized (lock) {
            if (started) throw new IllegalStateException("already started");
            started = true;
        }
        return secStarter.get();
    }

    private <U> PendingSec<U> wrapSec(Function<Sec<T>, Sec<U>> mapping) {
        synchronized (lock) {
            if (started) throw new IllegalStateException("can't wrap after call to start()");
            if (wrapped) throw new IllegalStateException("already wrapped!");
            wrapped = true;
            return new PendingSec<>(() -> mapping.apply(this.start()));
        }
    }

    public <U> PendingSec<U> flatMapSec(Function<T, Sec<U>> mapping) {
        return wrapSec(sec -> sec.flatMap(mapping));
    }

    public <U> PendingSec<U> mapSec(Function<T, U> mapping) {
        return wrapSec(sec -> sec.map(mapping));
    }

    public <U> PendingSec<U> flatMap(Function<T, PendingSec<U>> mapping) {
        return flatMapSec(r -> mapping.apply(r).start());
    }

    public static PendingSec<Void> createDelay(long delay) {
        return new PendingSec<>(() -> {
            SecAdapter.SecWithAdapter<Void> secWithAdapter = SecAdapter.createThreadless();
            boolean started = new Handler(Looper.getMainLooper())
                    .postDelayed(() -> secWithAdapter.secAdapter().provideResult(null), delay);
            if (!started) return Sec.premeditatedError(new RejectedExecutionException("main looper is dead"));
            return secWithAdapter.sec();
        });
    }

}
