package name.vantaclient.mixin;

import name.vantaclient.client.feature.SaturationFeature;
import name.vantaclient.client.feature.PotionEffectsFeature;
import name.vantaclient.client.util.InGameOverlayCache;
import name.vantaclient.client.gui.VantaNotificationManager;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;

import java.util.List;

@Mixin(Gui.class)
public class GuiMixin {
    private static final Identifier SATURATION_TEXTURE =
            Identifier.fromNamespaceAndPath("dropperutils", "textures/gui/saturation.png");

    @Inject(
            method = "renderFood",
            at = @At("TAIL")
    )
    private void renderSaturation(
            GuiGraphics guiGraphics,
            Player player,
            int i,
            int j,
            CallbackInfo ci
    ) {
        if (!SaturationFeature.INSTANCE.isEnabled()) {
            return;
        }

        FoodData food = player.getFoodData();
        int saturation = (int) Math.ceil(food.getSaturationLevel() / 2.0f);

        for (int l = 0; l < saturation; l++) {
            int iconX = j - l * 8 - 9;
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    SATURATION_TEXTURE,
                    iconX,
                    i,
                    0,
                    0,
                    9,
                    9,
                    9,
                    9
            );
        }
    }

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void renderVantaNotifications(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        VantaNotificationManager.render(guiGraphics,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void renderPotionEffects(
            GuiGraphics guiGraphics,
            net.minecraft.client.DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (!PotionEffectsFeature.INSTANCE.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        List<String> effectLines = InGameOverlayCache.getPotionEffectLines();
        int y = 20;
        for (String line : effectLines) {
            guiGraphics.drawString(mc.font, line, 5, y, 0xFFFFFF);
            y += 12;
        }
    }
}
