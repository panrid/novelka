package space.panrid.novelka.support;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Event listeners run after the request's commit, on another thread: wait a little for them. */
public final class Eventually {

    private Eventually() {
    }

    public static <T> T eventually(Supplier<T> fetch, Predicate<T> ready) {
        long deadline = System.currentTimeMillis() + 5_000;
        T value = fetch.get();
        while (!ready.test(value) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return value;
            }
            value = fetch.get();
        }
        return value;
    }
}
