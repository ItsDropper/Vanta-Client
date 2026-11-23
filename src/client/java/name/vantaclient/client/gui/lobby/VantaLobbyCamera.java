package name.vantaclient.client.gui.lobby;

/**
 * Cinematic camera and subject controller for the title lobby.
 *
 * The opening move is a short, eased crane-in. Once it settles, low-amplitude
 * environmental drift keeps the world alive while bounded pointer parallax
 * preserves the sense of depth without making the camera feel attached to the mouse.
 */
public final class VantaLobbyCamera {
    private static final float MAX_SCENE_YAW = 1.6f;
    private static final float MAX_SCENE_PITCH = 0.45f;
    private static final float MAX_SUBJECT_YAW = 18.0f;
    private static final float MAX_SUBJECT_PITCH = 8.0f;
    private static final float FOLLOW_RATE = 6.0f;
    private static final float REST_RATE = 2.5f;

    private static final double INTRO_SECONDS = 5.0;
    private static final float INTRO_CAMERA_X = -1.85f;
    private static final float INTRO_CAMERA_Y = 1.10f;
    private static final float INTRO_CAMERA_Z = 4.20f;
    private static final float INTRO_YAW = -8.5f;
    private static final float INTRO_PITCH = -2.6f;
    private static final float SWAY_YAW = 0.13f;
    private static final float SWAY_PITCH = 0.09f;
    private static final float DRIFT_X = 0.055f;
    private static final float DRIFT_Y = 0.035f;
    private static final float DRIFT_Z = 0.075f;

    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    private float pointerSceneYaw;
    private float pointerScenePitch;
    private float sceneYaw;
    private float scenePitch;
    private float subjectYaw;
    private float subjectPitch;
    private float cameraOffsetX;
    private float cameraOffsetY;
    private float cameraOffsetZ;
    private float avatarHeadYaw;
    private float avatarBodyYaw;
    private float avatarBreathOffset;

    public void reset() {
        pointerX = 0.5f;
        pointerY = 0.5f;
        pointerSceneYaw = 0.0f;
        pointerScenePitch = 0.0f;
        sceneYaw = 0.0f;
        scenePitch = 0.0f;
        subjectYaw = 0.0f;
        subjectPitch = 0.0f;
        cameraOffsetX = 0.0f;
        cameraOffsetY = 0.0f;
        cameraOffsetZ = 0.0f;
        avatarHeadYaw = 0.0f;
        avatarBodyYaw = 0.0f;
        avatarBreathOffset = 0.0f;
    }

    /**
     * Retains the original pointer-only controller for callers that do not own
     * the lobby scene clock (including the focused camera regression test).
     */
    public void update(int mouseX, int mouseY, int width, int height, float deltaSeconds) {
        updatePointer(mouseX, mouseY, width, height, deltaSeconds, true);
        sceneYaw = pointerSceneYaw;
        scenePitch = pointerScenePitch;
        cameraOffsetX = 0.0f;
        cameraOffsetY = 0.0f;
        cameraOffsetZ = 0.0f;
        avatarHeadYaw = subjectYaw;
        avatarBodyYaw = subjectYaw;
        avatarBreathOffset = 0.0f;
    }

    /** Advances the complete lobby camera, opening choreography and idle motion. */
    public void update(int mouseX, int mouseY, int width, int height, float deltaSeconds,
                       double elapsedSeconds, boolean animationsEnabled) {
        updatePointer(mouseX, mouseY, width, height, deltaSeconds, animationsEnabled);
        if (!animationsEnabled) {
            sceneYaw = pointerSceneYaw;
            scenePitch = pointerScenePitch;
            cameraOffsetX = 0.0f;
            cameraOffsetY = 0.0f;
            cameraOffsetZ = 0.0f;
            avatarHeadYaw = subjectYaw;
            avatarBodyYaw = subjectYaw;
            avatarBreathOffset = 0.0f;
            return;
        }

        double time = Double.isFinite(elapsedSeconds) ? Math.max(0.0, elapsedSeconds) : 0.0;
        float opening = 1.0f - easeOutCubic((float) (time / INTRO_SECONDS));
        sceneYaw = pointerSceneYaw - INTRO_YAW * -opening
                + (float) Math.sin(time * 0.17) * SWAY_YAW;
        scenePitch = pointerScenePitch + INTRO_PITCH * opening
                + (float) Math.sin(time * 0.23 + 0.7) * SWAY_PITCH;

        // A small lateral arc during the crane-in gives the camera a real 3D path,
        // followed by barely perceptible positional drift once it reaches its mark.
        float arc = (float) Math.sin(Math.PI * clamp((float) (time / INTRO_SECONDS), 0.0f, 1.0f));
        cameraOffsetX = INTRO_CAMERA_X * opening + arc * 0.32f
                + (float) Math.sin(time * 0.11) * DRIFT_X;
        cameraOffsetY = INTRO_CAMERA_Y * opening + arc * 0.16f
                + (float) Math.sin(time * 0.29) * DRIFT_Y;
        cameraOffsetZ = INTRO_CAMERA_Z * opening
                + (float) Math.cos(time * 0.13) * DRIFT_Z;

        // The avatar has its own restrained idle pose instead of remaining a rigid statue.
        avatarHeadYaw = subjectYaw + (float) Math.sin(time * 0.41) * 1.1f;
        avatarBodyYaw = subjectYaw * 0.55f + (float) Math.sin(time * 0.25) * 0.32f;
        avatarBreathOffset = (float) Math.sin(time * 1.15) * 0.018f;
    }

    private void updatePointer(int mouseX, int mouseY, int width, int height,
                               float deltaSeconds, boolean interpolate) {
        if (width <= 0 || height <= 0) return;

        boolean pointerInside = mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
        pointerX = pointerInside ? clamp(mouseX / (float) width, 0.0f, 1.0f) : 0.5f;
        pointerY = pointerInside ? clamp(mouseY / (float) height, 0.0f, 1.0f) : 0.5f;

        float horizontal = (pointerX - 0.5f) * 2.0f;
        float vertical = (pointerY - 0.5f) * 2.0f;
        float targetSceneYaw = -horizontal * MAX_SCENE_YAW;
        float targetScenePitch = vertical * MAX_SCENE_PITCH;
        float targetSubjectYaw = horizontal * MAX_SUBJECT_YAW;
        float targetSubjectPitch = vertical * MAX_SUBJECT_PITCH;

        float delta = clamp(deltaSeconds, 0.0f, 0.05f);
        boolean resting = Math.abs(horizontal) < 0.015f && Math.abs(vertical) < 0.015f;
        float rate = resting ? REST_RATE : FOLLOW_RATE;
        float blend = interpolate
                ? 1.0f - (float) Math.exp(-rate * delta)
                : 1.0f;

        pointerSceneYaw = approach(pointerSceneYaw, targetSceneYaw, blend);
        pointerScenePitch = approach(pointerScenePitch, targetScenePitch, blend);
        subjectYaw = approach(subjectYaw, targetSubjectYaw, blend);
        subjectPitch = approach(subjectPitch, targetSubjectPitch, blend);
    }

    public float sceneYaw() { return sceneYaw; }
    public float scenePitch() { return scenePitch; }
    public float subjectYaw() { return subjectYaw; }
    public float subjectPitch() { return subjectPitch; }
    public float cameraOffsetX() { return cameraOffsetX; }
    public float cameraOffsetY() { return cameraOffsetY; }
    public float cameraOffsetZ() { return cameraOffsetZ; }
    public float avatarHeadYaw() { return avatarHeadYaw; }
    public float avatarBodyYaw() { return avatarBodyYaw; }
    public float avatarBreathOffset() { return avatarBreathOffset; }

    /** Opposing offsets create a subtle depth separation between the brand and avatar. */
    public float brandOffsetX() { return sceneYaw * 1.8f; }
    public float brandOffsetY() { return scenePitch * 1.4f; }
    public float cardOffsetX() { return -sceneYaw * 1.5f; }
    public float cardOffsetY() { return -scenePitch * 1.2f; }

    private static float easeOutCubic(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        float inverse = 1.0f - t;
        return 1.0f - inverse * inverse * inverse;
    }

    private static float approach(float current, float target, float amount) {
        return current + (target - current) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
