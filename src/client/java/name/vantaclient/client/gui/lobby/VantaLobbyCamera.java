package name.vantaclient.client.gui.lobby;

/**
 * Smoothed cursor-driven camera and subject look controller for the title lobby.
 *
 * The small scene angles deliberately match the restrained parallax used by
 * PolyPlus: the environment should feel responsive, not like the camera is
 * attached to the mouse.
 */
public final class VantaLobbyCamera {
    private static final float MAX_SCENE_YAW = 1.6f;
    private static final float MAX_SCENE_PITCH = 0.45f;
    private static final float MAX_SUBJECT_YAW = 18.0f;
    private static final float MAX_SUBJECT_PITCH = 8.0f;
    private static final float FOLLOW_RATE = 6.0f;
    private static final float REST_RATE = 2.5f;

    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    private float sceneYaw;
    private float scenePitch;
    private float subjectYaw;
    private float subjectPitch;

    public void reset() {
        pointerX = 0.5f;
        pointerY = 0.5f;
        sceneYaw = 0.0f;
        scenePitch = 0.0f;
        subjectYaw = 0.0f;
        subjectPitch = 0.0f;
    }

    public void update(int mouseX, int mouseY, int width, int height, float deltaSeconds) {
        if (width <= 0 || height <= 0) return;

        pointerX = clamp(mouseX / (float) width, 0.0f, 1.0f);
        pointerY = clamp(mouseY / (float) height, 0.0f, 1.0f);

        float horizontal = (pointerX - 0.5f) * 2.0f;
        float vertical = (pointerY - 0.5f) * 2.0f;
        float targetSceneYaw = -horizontal * MAX_SCENE_YAW;
        float targetScenePitch = vertical * MAX_SCENE_PITCH;
        float targetSubjectYaw = horizontal * MAX_SUBJECT_YAW;
        float targetSubjectPitch = vertical * MAX_SUBJECT_PITCH;

        float delta = clamp(deltaSeconds, 0.0f, 0.05f);
        boolean resting = Math.abs(horizontal) < 0.015f && Math.abs(vertical) < 0.015f;
        float rate = resting ? REST_RATE : FOLLOW_RATE;
        float blend = 1.0f - (float) Math.exp(-rate * delta);

        sceneYaw = approach(sceneYaw, targetSceneYaw, blend);
        scenePitch = approach(scenePitch, targetScenePitch, blend);
        subjectYaw = approach(subjectYaw, targetSubjectYaw, blend);
        subjectPitch = approach(subjectPitch, targetSubjectPitch, blend);
    }

    public float sceneYaw() {
        return sceneYaw;
    }

    public float scenePitch() {
        return scenePitch;
    }

    public float subjectYaw() {
        return subjectYaw;
    }

    public float subjectPitch() {
        return subjectPitch;
    }

    /** Opposing offsets create a subtle depth separation between the brand and avatar. */
    public float brandOffsetX() {
        return sceneYaw * 1.8f;
    }

    public float brandOffsetY() {
        return scenePitch * 1.4f;
    }

    public float cardOffsetX() {
        return -sceneYaw * 1.5f;
    }

    public float cardOffsetY() {
        return -scenePitch * 1.2f;
    }

    private static float approach(float current, float target, float amount) {
        return current + (target - current) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
