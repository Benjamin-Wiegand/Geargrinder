package io.benwiegand.projection.libprivd.sec;

import static io.benwiegand.projection.libprivd.sec.SecAdapter.createThreadless;

import android.util.Log;

import java.util.function.Consumer;
import java.util.function.Function;

// as in "just a sec"
public class Sec<T> {
    private static final String TAG = Sec.class.getSimpleName();

    private final Object lock = new Object();
    private boolean finished = false;
    private T result = null;
    private Throwable error = null;

    private boolean callbacksSet = false;
    private boolean callbacksCalled = false;
    private Consumer<T> onResult = null;
    private Consumer<Throwable> onError = null;

    Sec() {}

    Adapter createAdapter() {
        return new Adapter();
    }

    public boolean isFinished() {
        synchronized (lock) {
            return finished;
        }
    }

    public boolean isSuccessful() {
        synchronized (lock) {
            if (!finished) throw new IllegalStateException("not finished, success is not yet known");

            return error == null;
        }
    }

    public T getResult() {
        synchronized (lock) {
            if (!finished) throw new IllegalStateException("not finished, result doesn't exist yet");

            return result;
        }
    }

    public Throwable getError() {
        synchronized (lock) {
            if (!finished) throw new IllegalStateException("not finished, error doesn't exist yet");

            return error;
        }
    }

    public T getResultOrThrow() throws Throwable {
        synchronized (lock) {
            if (!finished) throw new IllegalStateException("not finished, result/error doesn't exist yet");

            if (error != null) throw error;
            return result;
        }
    }

    public Sec<T> doOnResult(Consumer<T> onResult) {
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callbacks already set up");
            this.onResult = onResult;
            return this;
        }
    }

    public Sec<T> doOnError(Consumer<Throwable> onError) {
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callbacks already set up");
            this.onError = onError;
            return this;
        }
    }

    private static <T, U> Consumer<T> applyMap(Function<T, U> mapper, Consumer<U> downstreamResult, Consumer<Throwable> downstreamError) {
        return r -> {
            U mapped;
            try {
                mapped = mapper.apply(r);
            } catch (Throwable t) {
                downstreamError.accept(t);
                return;
            }
            downstreamResult.accept(mapped);
        };
    }

    public <U> Sec<U> map(Function<T, U> map) {
        // for now just use the callbacks. this may change in the future
        SecAdapter.SecWithAdapter<U> secWithAdapter;
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callbacks already set up");
            callbacksSet = true;

            secWithAdapter = createThreadless();

            SecAdapter<U> adapter = secWithAdapter.secAdapter();
            this.onResult = applyMap(map, adapter::provideResult, adapter::throwError);
            this.onError = adapter::throwError;
        }

        if (isFinished()) callCallbacks();

        return secWithAdapter.sec();
    }

    public Sec<T> mapError(Function<Throwable, Throwable> map) {
        // for now just use the callbacks. this may change in the future
        SecAdapter.SecWithAdapter<T> secWithAdapter;
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callbacks already set up");
            callbacksSet = true;

            secWithAdapter = createThreadless();

            SecAdapter<T> adapter = secWithAdapter.secAdapter();
            this.onResult = adapter::provideResult;
            this.onError = applyMap(map, adapter::throwError, adapter::throwError);
        }

        if (isFinished()) callCallbacks();

        return secWithAdapter.sec();
    }

    public <U> Sec<U> flatMap(Function<T, Sec<U>> map) {
        // for now just use the callbacks. this may change in the future
        SecAdapter.SecWithAdapter<U> secWithAdapter;
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callbacks already set up");
            callbacksSet = true;

            secWithAdapter = createThreadless();

            SecAdapter<U> adapter = secWithAdapter.secAdapter();
            this.onResult = applyMap(
                    map,
                    nextSec -> nextSec
                            .doOnResult(adapter::provideResult)
                            .doOnError(adapter::throwError)
                            .callMeWhenDone(),
                    adapter::throwError);
            this.onError = adapter::throwError;
        }

        if (isFinished()) callCallbacks();

        return secWithAdapter.sec();

    }

    public void callMeWhenDone() {
        synchronized (lock) {
            if (callbacksSet) throw new IllegalStateException("callMeWhenDone() cannot be called twice");
            callbacksSet = true;
        }

        if (isFinished()) callCallbacks();
    }

    private void callCallbacks() {
        synchronized (lock) {
            assert finished;
            if (!callbacksSet) return; // callbacks aren't ready
            if (callbacksCalled) return; // don't call back twice
            callbacksCalled = true;
        }

        try {
            if (error == null && onResult != null)
                onResult.accept(result);
        } catch (Throwable t) {
            Log.e(TAG, "error during onResult callback", t);
        }

        try {
            if (error != null && onError != null)
                onError.accept(error);
        } catch (Throwable t) {
            Log.e(TAG, "error during onError callback", t);
        }
    }

    private class Adapter implements SecAdapter<T> {
        @Override
        public void provideResult(T r) {
            synchronized (lock) {
                if (finished) throw new IllegalStateException("a result or error has already been provided");
                finished = true;

                result = r;
            }

            callCallbacks();
        }

        @Override
        public void throwError(Throwable t) {
            if (t == null) throw new IllegalArgumentException("throwable cannot be null");
            synchronized (lock) {
                if (finished) throw new IllegalStateException("a result or error has already been provided");
                finished = true;

                error = t;
            }

            callCallbacks();
        }
    }

    public static <T> Sec<T> premeditatedError(Throwable t) {
        SecAdapter.SecWithAdapter<T> secWithAdapter = createThreadless();
        secWithAdapter.secAdapter().throwError(t);
        return secWithAdapter.sec();
    }

    public static <T> Sec<T> premeditated(T r) {
        SecAdapter.SecWithAdapter<T> secWithAdapter = createThreadless();
        secWithAdapter.secAdapter().provideResult(r);
        return secWithAdapter.sec();
    }

}
