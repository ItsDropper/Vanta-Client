package name.vantaclient.client.gui.lobby;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;

/** Renders the live account avatar as the lobby's interactive 3D focal point. */
public final class VantaLobbyRenderer {
    private VantaLobbyRenderer() {}

    public static boolean renderPlayerPreview(
            GuiGraphics graphics,
            PlayerModel model,
            PlayerSkin skin,
            long elapsedMillis,
            boolean compact,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (graphics == null || model == null || skin == null || skin.body() == null) return false;

        AvatarRenderState pose = new AvatarRenderState();
        pose.skin = skin;
        pose.showHat = true;
        pose.showJacket = true;
        pose.showLeftPants = true;
        pose.showRightPants = true;
        pose.showLeftSleeve = true;
        pose.showRightSleeve = true;
        model.setupAnim(pose);

        VantaLobbyCamera camera = VantaLobbyScene.camera();
        float idleYaw = (float) Math.sin(elapsedMillis / 1900.0) * 4.0f;
        float subjectYaw = camera.subjectYaw();
        float subjectPitch = camera.subjectPitch();

        // The head tracks the pointer more than the torso, keeping the pose natural.
        model.head.yRot = (float) Math.toRadians(idleYaw + subjectYaw * 0.58f);
        model.head.xRot = (float) Math.toRadians(-3.0f + subjectPitch * 0.52f);
        model.body.yRot = (float) Math.toRadians(subjectYaw * 0.16f);

        graphics.submitSkinRenderState(
                model,
                skin.body().texturePath(),
                compact ? 38.0f : 44.0f,
                -4.0f,
                idleYaw + subjectYaw * 0.18f,
                subjectPitch * 0.12f,
                left,
                top,
                right,
                bottom
        );
        return true;
    }
}
