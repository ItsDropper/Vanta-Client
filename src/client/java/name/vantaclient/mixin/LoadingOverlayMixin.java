package name.vantaclient.mixin;

import name.vantaclient.client.gui.VantaLoadingScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers vanilla's Mojang-logo loading screen with Vanta's. Vanilla keeps owning the reload
 * lifecycle, fade timing and completion callback; this only paints on top of it.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Inject(method = "render", at = @At("TAIL"))
    private void vanta$paintLoadingScreen(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VantaLoadingScreen.render(graphics, currentProgress, fadeOutStart);
    }
}
