package name.vantaclient.client.init;

import name.vantaclient.client.hud.HudRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class HudRegistration {

    public static void register() {

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("dropperutils", "hud_editor"),
                (graphics, tickCounter) -> HudRenderer.render(graphics)
        );

    }
}