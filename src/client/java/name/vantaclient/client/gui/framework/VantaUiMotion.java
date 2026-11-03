package name.vantaclient.client.gui.framework;

/**
 * Shared elapsed-time based animation math for Vanta-native UI controls.
 *
 * Rates are expressed per second, making the same transition behave consistently
 * across different frame rates. Large frame gaps are capped to avoid jumps after
 * a pause, window drag, or temporary client stall.
 */
public final class VantaUiMotion {
    public static final float DEFAULT_FRAME_DELTA_SECONDS = 1.0f / 60.0f;
    public static final float MAX_FRAME_DELTA_SECONDS = 0.05f;
    private static final float SNAP_EPSILON = 0.002f;

    private VantaUiMotion() {}

    /**
     * Calculates a bounded render-frame delta. A zero previous timestamp represents
     * the first frame and receives a nominal 60 FPS delta.
     */
    public static float frameDeltaSeconds(long previousFrameNanos, long currentFrameNanos) {
        if (previousFrameNanos == 0L) return DEFAULT_FRAME_DELTA_SECONDS;
        long elapsedNanos = currentFrameNanos - previousFrameNanos;
        if (elapsedNanos <= 0L) return 0.0f;
        return (float) Math.min(MAX_FRAME_DELTA_SECONDS,
                elapsedNanos / 1_000_000_000.0);
    }

    /**
     * Returns the exponential ease-out interpolation factor for one frame.
     * Disabling animations deliberately returns 1 so callers snap to the target.
     */
    public static float interpolationFactor(float ratePerSecond, float deltaSeconds,
                                             boolean animationsEnabled) {
        if (!animationsEnabled) return 1.0f;
        if (!Float.isFinite(ratePerSecond) || ratePerSecond <= 0.0f
                || !Float.isFinite(deltaSeconds) || deltaSeconds <= 0.0f) {
            return 0.0f;
        }
        float boundedDelta = Math.min(MAX_FRAME_DELTA_SECONDS, deltaSeconds);
        return 1.0f - (float) Math.exp(-ratePerSecond * boundedDelta);
    }

    /**
     * Advances an indeterminate ping-pong phase in the range [0, 2). A phase of
     * 0.5 is returned when animations are disabled so progress remains centred.
     */
    public static float advancePingPongPhase(float phase, float deltaSeconds,
                                             float speedPerSecond, boolean animationsEnabled) {
        if (!animationsEnabled) return 0.5f;
        if (!Float.isFinite(phase) || !Float.isFinite(deltaSeconds)
                || !Float.isFinite(speedPerSecond) || speedPerSecond <= 0.0f
                || deltaSeconds <= 0.0f) {
            return Float.isFinite(phase) ? normalizePingPongPhase(phase) : 0.0f;
        }
        float boundedDelta = Math.min(MAX_FRAME_DELTA_SECONDS, deltaSeconds);
        return normalizePingPongPhase(phase + boundedDelta * speedPerSecond);
    }

    private static float normalizePingPongPhase(float phase) {
        float normalized = phase % 2.0f;
        return normalized < 0.0f ? normalized + 2.0f : normalized;
    }

    /**
     * Advances a scalar toward its target using exponential ease-out interpolation.
     * Non-finite current state is recovered by snapping to the finite target.
     */
    public static float approach(float current, float target, float ratePerSecond,
                                 float deltaSeconds, boolean animationsEnabled) {
        if (!Float.isFinite(target)) return Float.isFinite(current) ? current : 0.0f;
        if (!Float.isFinite(current) || !animationsEnabled) return target;

        float factor = interpolationFactor(ratePerSecond, deltaSeconds, true);
        float next = current + (target - current) * factor;
        return Math.abs(target - next) <= SNAP_EPSILON ? target : next;
    }
}
