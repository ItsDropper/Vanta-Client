package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
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

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsHudElement extends HudElement {
    private List<EffectSnapshot> performanceSnapshot = List.of();
    private long lastSnapshotTick = Long.MIN_VALUE;

    public PotionEffectsHudElement() {
        super(DropperUtilsConfig.get().potionHudX, DropperUtilsConfig.get().potionHudY);
        scale = DropperUtilsConfig.get().potionHudScale;
    }

    @Override
    public boolean shouldRender() {
        return PotionEffectsFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (!PotionEffectsFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            refreshPerformanceSnapshot(mc);
            renderSnapshot(graphics);
            return;
        }

        int offset = 0;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Identifier texture = getTexture(effect);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, 0, offset, 18, 18);
            String name = getEffectName(effect);
            Component duration = MobEffectUtil.formatDuration(
                    effect, 1.0F, mc.level.tickRateManager().tickrate());
            graphics.drawString(mc.font, name, 22, offset + 1, 0xFFFFFFFF);
            graphics.drawString(mc.font, duration, 22, offset + 10, 0xFFAAAAAA);
            offset += 24;
        }
    }

    private void refreshPerformanceSnapshot(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            performanceSnapshot = List.of();
            lastSnapshotTick = Long.MIN_VALUE;
            return;
        }
        long gameTime = mc.level.getGameTime();
        if (lastSnapshotTick != Long.MIN_VALUE && gameTime >= lastSnapshotTick
                && gameTime - lastSnapshotTick < 5L) return;

        List<EffectSnapshot> updated = new ArrayList<>();
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            String duration = MobEffectUtil.formatDuration(
                    effect, 1.0F, mc.level.tickRateManager().tickrate()).getString();
            updated.add(new EffectSnapshot(getTexture(effect), getEffectName(effect), duration));
        }
        performanceSnapshot = List.copyOf(updated);
        lastSnapshotTick = gameTime;
    }

    private void renderSnapshot(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int offset = 0;
        for (EffectSnapshot effect : performanceSnapshot) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, effect.texture(), 0, offset, 18, 18);
            graphics.drawString(mc.font, effect.name(), 22, offset + 1, 0xFFFFFFFF);
            graphics.drawString(mc.font, effect.duration(), 22, offset + 10, 0xFFAAAAAA);
            offset += 24;
        }
    }

    private static Identifier getTexture(MobEffectInstance effect) {
        return effect.getEffect().unwrapKey()
                .map(key -> key.identifier().withPrefix("mob_effect/"))
                .orElse(MissingTextureAtlasSprite.getLocation());
    }

    private static String getEffectName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int amplifier = effect.getAmplifier();
        if (amplifier > 0) name += " " + toRoman(amplifier + 1);
        return name;
    }

    @Override public void savePosition() {
        DropperUtilsConfig.get().potionHudX = xPercent;
        DropperUtilsConfig.get().potionHudY = yPercent;
        DropperUtilsConfig.save();
    }

    @Override public int getWidth() { return 125; }

    @Override
    public int getHeight() {
        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            refreshPerformanceSnapshot(mc);
            return Math.max(24, performanceSnapshot.size() * 24);
        }
        if (Minecraft.getInstance().player == null) return 24;
        return Math.max(24, Minecraft.getInstance().player.getActiveEffects().size() * 24);
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

    private record EffectSnapshot(Identifier texture, String name, String duration) {}

    @Override
    public void setScale(float value) {
        scale = Math.max(0.25f, Math.min(value, 3.0f));
        DropperUtilsConfig.get().potionHudScale = scale;
        DropperUtilsConfig.save();
    }
}
