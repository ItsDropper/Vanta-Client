package name.vantaclient.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClickRateTracker {
    private static final long WINDOW_MILLIS = 1000L;
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

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

    private static void record(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        synchronized (clicks) {
            prune(clicks, now);
            clicks.addLast(now);
        }
    }

    private static int count(Deque<Long> clicks) {
        long now = System.currentTimeMillis();
        synchronized (clicks) {
            prune(clicks, now);
            return clicks.size();
        }
    }

    private static void prune(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() >= WINDOW_MILLIS) {
            clicks.removeFirst();
        }
    }
}
