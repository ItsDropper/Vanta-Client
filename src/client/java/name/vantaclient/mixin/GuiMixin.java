package name.vantaclient.mixin;

import name.vantaclient.client.feature.SaturationFeature;
import name.vantaclient.client.feature.PotionEffectsFeature;
import name.vantaclient.client.hud.VanillaHudElement;
import name.vantaclient.client.hud.HudBackgroundRenderer;
import name.vantaclient.client.hud.VanillaHudLayout;
import name.vantaclient.client.util.InGameOverlayCache;
import name.vantaclient.client.gui.VantaNotificationManager;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Gui.class)
public class GuiMixin {
    private static final Identifier SATURATION_TEXTURE =
            Identifier.fromNamespaceAndPath("dropperutils", "textures/gui/saturation.png");

    @Inject(method = "renderFood", at = @At("TAIL"))
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

    @Inject(method = "render", at = @At("HEAD"))
    private void renderVantaHudBackgrounds(
            GuiGraphics graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        HudBackgroundRenderer.render(graphics);
    }

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void renderVantaNotifications(
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker,
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
            DeltaTracker deltaTracker,
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

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void vanta$pushCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.CROSSHAIR);
    }

    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void vanta$popCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void vanta$pushHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.HOTBAR);
    }

    @Inject(method = "renderItemHotbar", at = @At("TAIL"))
    private void vanta$popHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderHearts", at = @At("HEAD"))
    private void vanta$pushHealth(
            GuiGraphics graphics, Player player, int x, int y, int lines,
            int regeneratingHeartIndex, float maxHealth, int lastHealth,
            int health, int absorption, boolean blinking, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.HEALTH);
    }

    @Inject(method = "renderHearts", at = @At("TAIL"))
    private void vanta$popHealth(
            GuiGraphics graphics, Player player, int x, int y, int lines,
            int regeneratingHeartIndex, float maxHealth, int lastHealth,
            int health, int absorption, boolean blinking, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderArmor", at = @At("HEAD"))
    private static void vanta$pushArmor(
            GuiGraphics graphics, Player player, int y, int x, int healthBarLines,
            int arg5, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.ARMOR);
    }

    @Inject(method = "renderArmor", at = @At("TAIL"))
    private static void vanta$popArmor(
            GuiGraphics graphics, Player player, int y, int x, int healthBarLines,
            int arg5, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderFood", at = @At("HEAD"))
    private static void vanta$pushFood(
            GuiGraphics graphics, Player player, int top, int right, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.FOOD);
    }

    @Inject(method = "renderFood", at = @At("TAIL"))
    private static void vanta$popFood(
            GuiGraphics graphics, Player player, int top, int right, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderAirBubbles", at = @At("HEAD"))
    private static void vanta$pushAir(
            GuiGraphics graphics, Player player, int heartCount, int top, int left, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.AIR);
    }

    @Inject(method = "renderAirBubbles", at = @At("TAIL"))
    private static void vanta$popAir(
            GuiGraphics graphics, Player player, int heartCount, int top, int left, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void vanta$pushEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.EFFECTS);
    }

    @Inject(method = "renderEffects", at = @At("TAIL"))
    private void vanta$popEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }



    @Redirect(
            method = "renderHotbarAndDecorations",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"
            )
    )
    private void vanta$renderExperienceLevel(
            GuiGraphics graphics,
            Font font,
            int level
    ) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.EXPERIENCE);
        try {
            net.minecraft.client.gui.contextualbar.ContextualBarRenderer
                    .renderExperienceLevel(graphics, font, level);
        } finally {
            VanillaHudLayout.pop(graphics);
        }
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"))
    private void vanta$pushMountHealth(GuiGraphics graphics, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.MOUNT_HEALTH);
    }

    @Inject(method = "renderVehicleHealth", at = @At("TAIL"))
    private void vanta$popMountHealth(GuiGraphics graphics, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"))
    private void vanta$pushScoreboard(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.SCOREBOARD);
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("TAIL"))
    private void vanta$popScoreboard(
            GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }

    @Inject(method = "renderBossOverlay", at = @At("HEAD"))
    private void vanta$pushBossBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.push(graphics, VanillaHudElement.Part.BOSS_BAR);
    }

    @Inject(method = "renderBossOverlay", at = @At("TAIL"))
    private void vanta$popBossBar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudLayout.pop(graphics);
    }
}
