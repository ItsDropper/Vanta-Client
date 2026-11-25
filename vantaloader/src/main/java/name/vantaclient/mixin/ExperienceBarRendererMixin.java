package name.vantaclient.mixin;

import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.hud.VanillaHudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the vanilla experience bar while retaining its native drawing logic. */
@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarRendererMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void vanta$pushExperienceBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.EXPERIENCE);
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void vanta$popExperienceBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }
}
