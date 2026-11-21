package name.vantaclient.client.gui.lobby;

/**
 * Owns the title lobby's transient scene state and frame clock.
 *
 * It is intentionally independent from Screen and Minecraft world ownership,
 * so the scene can later be backed by a dedicated ClientLevel without coupling
 * camera behavior to the menu widgets.
 */
public final class VantaLobbyScene {
    private static final VantaLobbyCamera CAMERA = new VantaLobbyCamera();

    private static long openedAtNanos;
    private static long lastFrameNanos;
    private static boolean initialized;

    private VantaLobbyScene() {}

    public static void begin() {
        openedAtNanos = System.nanoTime();
        lastFrameNanos = openedAtNanos;
        CAMERA.reset();
        initialized = true;
    }

    public static void update(int mouseX, int mouseY, int width, int height) {
        long now = System.nanoTime();
        if (!initialized) {
            begin();
            now = System.nanoTime();
        }

        float deltaSeconds = lastFrameNanos == 0L
                ? 0.0f : (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        CAMERA.update(mouseX, mouseY, width, height, deltaSeconds);
    }

    public static VantaLobbyCamera camera() {
        return CAMERA;
    }

    public static long elapsedMillis() {
        if (!initialized) return 0L;
        return Math.max(0L, (System.nanoTime() - openedAtNanos) / 1_000_000L);
    }
}
