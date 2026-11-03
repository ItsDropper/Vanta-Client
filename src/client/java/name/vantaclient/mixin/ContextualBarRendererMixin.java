package name.vantaclient.mixin;

import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.hud.VanillaHudLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ContextualBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the vanilla XP level number attached to the repositioned experience bar. */
@Mixin(ContextualBarRenderer.class)
public abstract class ContextualBarRendererMixin {
    @Inject(method = "renderExperienceLevel", at = @At("HEAD"))
    private static void vanta$pushExperienceLevel(
            GuiGraphics graphics, Font font, int level, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.EXPERIENCE);
    }

    @Inject(method = "renderExperienceLevel", at = @At("TAIL"))
    private static void vanta$popExperienceLevel(
            GuiGraphics graphics, Font font, int level, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }
}
