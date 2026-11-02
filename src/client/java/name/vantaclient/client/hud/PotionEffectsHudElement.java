package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.PotionEffectsFeature;
import name.vantaclient.client.hudeditor.HudElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

public class PotionEffectsHudElement extends HudElement {

    public PotionEffectsHudElement() {
        super(
                DropperUtilsConfig.get().potionHudX,
                DropperUtilsConfig.get().potionHudY
        );

        scale = DropperUtilsConfig.get().potionHudScale;
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {

        if (!PotionEffectsFeature.INSTANCE.isEnabled()
                && !HudEditorFeature.INSTANCE.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            return;
        }

        int offset = 0;

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {

            Identifier texture = effect.getEffect()
                    .unwrapKey()
                    .map(key -> key.identifier().withPrefix("mob_effect/"))
                    .orElse(MissingTextureAtlasSprite.getLocation());

            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    0,
                    offset,
                    18,
                    18
            );

            String name = effect.getEffect()
                    .value()
                    .getDisplayName()
                    .getString();

            int amplifier = effect.getAmplifier();

            if (amplifier > 0) {
                name += " " + toRoman(amplifier + 1);
            }

            Component duration = MobEffectUtil.formatDuration(
                    effect,
                    1.0F,
                    mc.level.tickRateManager().tickrate()
            );

            graphics.drawString(
                    mc.font,
                    name,
                    22,
                    offset + 1,
                    0xFFFFFFFF
            );

            graphics.drawString(
                    mc.font,
                    duration,
                    22,
                    offset + 10,
                    0xFFAAAAAA
            );

            offset += 24;
        }
    }

    @Override
    public void savePosition() {
        DropperUtilsConfig.get().potionHudX = xPercent;
        DropperUtilsConfig.get().potionHudY = yPercent;

        DropperUtilsConfig.save();
    }

    @Override
    public int getWidth() {
        return 125;
    }

    @Override
    public int getHeight() {

        if (Minecraft.getInstance().player == null) {
            return 24;
        }

        return Math.max(
                24,
                Minecraft.getInstance()
                        .player
                        .getActiveEffects()
                        .size() * 24
        );
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }

    @Override
    public void setScale(float value) {

        scale = Math.max(
                0.25f,
                Math.min(value, 3.0f)
        );

        DropperUtilsConfig.get().potionHudScale = scale;
        DropperUtilsConfig.save();
    }
}