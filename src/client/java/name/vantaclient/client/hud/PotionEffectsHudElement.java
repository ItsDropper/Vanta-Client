package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.PotionEffectsFeature;
import name.vantaclient.client.hudeditor.HudElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsHudElement extends HudElement {
    private List<EffectSnapshot> snapshot = List.of();
    private long lastSnapshotTick = Long.MIN_VALUE;
    private Object snapshotWorld;
    private Object snapshotPlayer;

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
        refreshSnapshot(mc);
        renderSnapshot(graphics);
    }

    private void refreshSnapshot(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            snapshot = List.of();
            lastSnapshotTick = Long.MIN_VALUE;
            snapshotWorld = null;
            snapshotPlayer = null;
            return;
        }
        if (hasSnapshotContextChanged(snapshotWorld, mc.level, snapshotPlayer, mc.player)) {
            snapshotWorld = mc.level;
            snapshotPlayer = mc.player;
            lastSnapshotTick = Long.MIN_VALUE;
        }
        long gameTime = mc.level.getGameTime();
        if (!shouldRefreshSnapshot(lastSnapshotTick, gameTime)) return;

        List<EffectSnapshot> updated = new ArrayList<>();
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            String duration = MobEffectUtil.formatDuration(
                    effect, 1.0F, mc.level.tickRateManager().tickrate()).getString();
            updated.add(new EffectSnapshot(getTexture(effect), getEffectName(effect), duration));
        }
        snapshot = List.copyOf(updated);
        lastSnapshotTick = gameTime;
    }

    public static boolean hasSnapshotContextChanged(Object previousWorld, Object currentWorld,
                                                    Object previousPlayer, Object currentPlayer) {
        return previousWorld != currentWorld || previousPlayer != currentPlayer;
    }

    public static boolean shouldRefreshSnapshot(long lastSnapshotTick, long currentGameTime) {
        return lastSnapshotTick == Long.MIN_VALUE || currentGameTime < lastSnapshotTick
                || currentGameTime - lastSnapshotTick >= 5L;
    }

    private void renderSnapshot(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int offset = 0;
        for (EffectSnapshot effect : snapshot) {
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

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        refreshSnapshot(mc);
        int widestLabel = 0;
        for (EffectSnapshot effect : snapshot) {
            widestLabel = Math.max(widestLabel,
                    Math.max(mc.font.width(effect.name()), mc.font.width(effect.duration())));
        }
        return 22 + widestLabel;
    }

    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        refreshSnapshot(mc);
        int count = snapshot.size();
        if (count <= 0) return 24;
        return (count - 1) * 24 + Math.max(18, 10 + mc.font.lineHeight);
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
        super.setScale(value);
        DropperUtilsConfig.get().potionHudScale = scale;
        DropperUtilsConfig.save();
    }
}
