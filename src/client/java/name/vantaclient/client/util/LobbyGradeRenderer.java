package name.vantaclient.client.util;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;

/**
 * Applies a restrained cinematic grade to the live title-world render target.
 *
 * The chain runs after terrain and world entities but before the title-screen UI,
 * so the grade affects the 3D scene without tinting menu text or controls.
 */
public final class LobbyGradeRenderer {
    private static final Identifier EFFECT_ID =
            Identifier.fromNamespaceAndPath("dropperutils", "lobby_grade");

    private static boolean failed;

    private LobbyGradeRenderer() {}

    public static void reset() {
        failed = false;
    }

    public static void render(GraphicsResourceAllocator allocator) {
        if (failed) return;

        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof TitleScreen)
                || client.level == null
                || !VantaLobbyWorld.isWorldReadyForDisplay()) {
            return;
        }

        try {
            PostChain chain = client.getShaderManager()
                    .getPostChain(EFFECT_ID, LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) return;
            chain.process(client.getMainRenderTarget(), allocator);
        } catch (Throwable error) {
            failed = true;
            System.err.println("[Vanta Client] Lobby color grading failed; disabled for this session.");
            error.printStackTrace();
        }
    }
}
