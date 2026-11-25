package name.vantaclient.mixin;

import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.hud.VanillaHudLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the mount jump bar while retaining its native drawing logic. */
@Mixin(JumpableVehicleBarRenderer.class)
public abstract class JumpableVehicleBarRendererMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"))
    private void vanta$pushJumpBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.JUMP_BAR);
    }

    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void vanta$popJumpBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }
}
