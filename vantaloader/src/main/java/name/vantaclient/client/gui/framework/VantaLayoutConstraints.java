package name.vantaclient.client.gui.framework;

/**
 * Immutable size constraints used by Vanta's retained layout engine.
 * Bounds are normalized so a minimum can never exceed its maximum.
 */
public record VantaLayoutConstraints(int minWidth, int minHeight, int maxWidth, int maxHeight) {
    public static final VantaLayoutConstraints UNBOUNDED =
            new VantaLayoutConstraints(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

    public VantaLayoutConstraints {
        minWidth = Math.max(0, minWidth);
        minHeight = Math.max(0, minHeight);
        maxWidth = Math.max(minWidth, maxWidth);
        maxHeight = Math.max(minHeight, maxHeight);
    }

    public VantaLayoutConstraints withMinimum(int width, int height) {
        return new VantaLayoutConstraints(width, height, maxWidth, maxHeight);
    }

    public VantaLayoutConstraints withMaximum(int width, int height) {
        return new VantaLayoutConstraints(minWidth, minHeight, width, height);
    }

    public int constrainWidth(int desired) {
        return clamp(desired, minWidth, maxWidth);
    }

    public int constrainHeight(int desired) {
        return clamp(desired, minHeight, maxHeight);
    }

    public int constrainWidth(int desired, int available) {
        int upper = Math.max(0, Math.min(available, maxWidth));
        int lower = Math.min(upper, minWidth);
        return clamp(desired, lower, upper);
    }

    public int constrainHeight(int desired, int available) {
        int upper = Math.max(0, Math.min(available, maxHeight));
        int lower = Math.min(upper, minHeight);
        return clamp(desired, lower, upper);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
