package name.vantaclient.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClickRateTracker {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static volatile int snapshotLeftCps;
    private static volatile int snapshotRightCps;

    private ClickRateTracker() {}

    public static void recordLeftClick() {
        record(LEFT);
    }

    public static void recordRightClick() {
        record(RIGHT);
    }

    public static int getLeftCps() {
        return count(LEFT);
    }

    public static int getRightCps() {
        return count(RIGHT);
    }

    /** Prune and count both input queues once per client tick, not once per rendered frame. */
    public static void refreshSnapshot() {
        long now = System.nanoTime();
        snapshotLeftCps = count(LEFT, now);
        snapshotRightCps = count(RIGHT, now);
    }

    public static int getSnapshotLeftCps() {
        return snapshotLeftCps;
    }

    public static int getSnapshotRightCps() {
        return snapshotRightCps;
    }

    private static void record(Deque<Long> clicks) {
        long now = System.nanoTime();
        synchronized (clicks) {
            prune(clicks, now);
            clicks.addLast(now);
        }
    }

    private static int count(Deque<Long> clicks) {
        return count(clicks, System.nanoTime());
    }

    private static int count(Deque<Long> clicks, long now) {
        synchronized (clicks) {
            prune(clicks, now);
            return clicks.size();
        }
    }

    private static void prune(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() >= WINDOW_NANOS) {
            clicks.removeFirst();
        }
    }
}
