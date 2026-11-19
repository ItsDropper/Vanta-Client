package name.vantaclient.client.gui.framework;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Small observable value for UI state. Writes are equality-checked and listeners
 * are notified outside the state lock. It does not impose a thread policy;
 * Minecraft UI consumers should mutate it on the client thread.
 */
public final class VantaState<T> {
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private T value;
    private long revision;

    public VantaState(T initialValue) {
        value = initialValue;
    }

    public synchronized T get() {
        return value;
    }

    public synchronized long revision() {
        return revision;
    }

    public boolean set(T next) {
        synchronized (this) {
            if (Objects.equals(value, next)) return false;
            value = next;
            revision++;
        }
        for (Consumer<T> listener : listeners) listener.accept(next);
        return true;
    }

    public boolean update(Consumer<T> updater) {
        Objects.requireNonNull(updater, "updater");
        T current;
        synchronized (this) {
            current = value;
        }
        updater.accept(current);
        return set(current);
    }

    public AutoCloseable subscribe(Consumer<T> listener) {
        return subscribe(listener, false);
    }

    public AutoCloseable subscribe(Consumer<T> listener, boolean emitCurrent) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        if (emitCurrent) listener.accept(get());
        return () -> listeners.remove(listener);
    }

    public int subscriberCount() {
        return listeners.size();
    }
}
