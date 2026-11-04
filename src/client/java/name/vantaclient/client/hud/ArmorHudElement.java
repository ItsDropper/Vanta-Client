package name.vantaclient.client.hud;

import name.vantaclient.client.feature.ArmorHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import name.vantaclient.client.config.DropperUtilsConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHudElement extends HudElement {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private boolean horizontal;
    private final ItemStack[] armorSnapshot = {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };
    private final String[] armorDurabilityText = {"0", "0", "0", "0"};
    private final int[] armorColors = {0xFF555555, 0xFF555555, 0xFF555555, 0xFF555555};
    private final int[] armorBarWidths = new int[4];
    private long lastArmorSnapshotTick = Long.MIN_VALUE;
    private Object armorSnapshotWorld;
    private Object armorSnapshotPlayer;

    public ArmorHudElement() {
        super(DropperUtilsConfig.get().armorHudX, DropperUtilsConfig.get().armorHudY);
        scale = DropperUtilsConfig.get().armorHudScale;
        horizontal = DropperUtilsConfig.get().armorHudHorizontal;
    }

    @Override
    public boolean shouldRender() {
        return ArmorHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (!ArmorHudFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            refreshLocalArmorSnapshot(mc);
            return;
        }

        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            renderCachedArmor(graphics, mc);
            return;
        }

        refreshLocalArmorSnapshot(mc);
        renderLocalArmorSnapshot(graphics, mc);
    }

    private void renderCachedArmor(GuiGraphics graphics, Minecraft mc) {
        for (int i = 0; i < 4; i++) {
            ItemStack item = HudPerformanceCache.getArmorStack(i);
            if (item.isEmpty()) continue;
            int drawX = horizontal ? i * 22 : 0;
            int drawY = horizontal ? 0 : i * 22;
            graphics.renderItem(item, drawX, drawY);
            int color = HudPerformanceCache.getArmorColor(i);
            String durabilityText = HudPerformanceCache.getArmorDurabilityText(i);
            if (horizontal) {
                graphics.drawString(mc.font, durabilityText, drawX + 1, drawY - 10, color, false);
            } else {
                graphics.drawString(mc.font, durabilityText, drawX + 25, drawY + 8, color, false);
            }
            graphics.fill(drawX, drawY + 18, drawX + 16, drawY + 20, 0xFF555555);
            int filled = HudPerformanceCache.getArmorBarWidth(i);
            graphics.fill(drawX, drawY + 18, drawX + filled, drawY + 20, color);
        }
    }

    private void refreshLocalArmorSnapshot(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            for (int index = 0; index < armorSnapshot.length; index++) {
                armorSnapshot[index] = ItemStack.EMPTY;
                armorDurabilityText[index] = "0";
                armorColors[index] = 0xFF555555;
                armorBarWidths[index] = 0;
            }
            lastArmorSnapshotTick = Long.MIN_VALUE;
            armorSnapshotWorld = null;
            armorSnapshotPlayer = null;
            return;
        }

        long gameTime = mc.level.getGameTime();
        if (!shouldRefreshArmorSnapshot(lastArmorSnapshotTick, gameTime,
                armorSnapshotWorld, mc.level, armorSnapshotPlayer, mc.player)) return;

        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            ItemStack item = mc.player.getItemBySlot(ARMOR_SLOTS[index]);
            armorSnapshot[index] = item;
            if (item.isEmpty() || item.getMaxDamage() <= 0) {
                armorDurabilityText[index] = "0";
                armorColors[index] = 0xFF555555;
                armorBarWidths[index] = 0;
                continue;
            }

            int maxDamage = item.getMaxDamage();
            int durability = maxDamage - item.getDamageValue();
            float percent = (float) durability / maxDamage;
            armorDurabilityText[index] = Integer.toString(durability);
            armorColors[index] = percent > 0.5f ? 0xFF00FF00
                    : percent > 0.25f ? 0xFFFFFF00 : 0xFFFF0000;
            armorBarWidths[index] = (int) (16 * percent);
        }

        lastArmorSnapshotTick = gameTime;
        armorSnapshotWorld = mc.level;
        armorSnapshotPlayer = mc.player;
    }

    public static boolean shouldRefreshArmorSnapshot(long lastGameTime, long currentGameTime,
                                                       Object previousWorld, Object currentWorld,
                                                       Object previousPlayer, Object currentPlayer) {
        return lastGameTime != currentGameTime
                || previousWorld != currentWorld
                || previousPlayer != currentPlayer;
    }

    private void renderLocalArmorSnapshot(GuiGraphics graphics, Minecraft mc) {
        for (int index = 0; index < armorSnapshot.length; index++) {
            ItemStack item = armorSnapshot[index];
            if (item.isEmpty()) continue;

            int drawX = horizontal ? index * 22 : 0;
            int drawY = horizontal ? 0 : index * 22;
            int color = armorColors[index];
            graphics.renderItem(item, drawX, drawY);
            if (horizontal) {
                graphics.drawString(mc.font, armorDurabilityText[index],
                        drawX + 1, drawY - 10, color, false);
            } else {
                graphics.drawString(mc.font, armorDurabilityText[index],
                        drawX + 25, drawY + 8, color, false);
            }
            graphics.fill(drawX, drawY + 18, drawX + 16, drawY + 20, 0xFF555555);
            graphics.fill(drawX, drawY + 18, drawX + armorBarWidths[index], drawY + 20, color);
        }
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        int widestDurabilityLabel = 0;
        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            for (int index = 0; index < 4; index++) {
                ItemStack item = HudPerformanceCache.getArmorStack(index);
                if (!item.isEmpty()) {
                    widestDurabilityLabel = Math.max(widestDurabilityLabel,
                            mc.font.width(HudPerformanceCache.getArmorDurabilityText(index)));
                }
            }
        } else {
            refreshLocalArmorSnapshot(mc);
            for (int index = 0; index < armorSnapshot.length; index++) {
                if (!armorSnapshot[index].isEmpty()) {
                    widestDurabilityLabel = Math.max(widestDurabilityLabel,
                            mc.font.width(armorDurabilityText[index]));
                }
            }
        }
        return horizontal
                ? Math.max(82, 67 + widestDurabilityLabel)
                : Math.max(16, 25 + widestDurabilityLabel);
    }

    @Override
    public int getHeight() {
        // The horizontal layout uses a 10 px label above the 20 px armor/bar area.
        // The vertical layout uses four 20 px slots separated by 2 px.
        return horizontal ? 20 : 86;
    }

    @Override
    protected int getHitboxTopOffset() {
        // Horizontal durability labels render ten pixels above the armor icons.
        return horizontal ? -10 : 0;
    }

    @Override
    public void savePosition() {
        DropperUtilsConfig.get().armorHudX = xPercent;
        DropperUtilsConfig.get().armorHudY = yPercent;
        DropperUtilsConfig.save();
    }

    public void setHorizontal(boolean value) {
        horizontal = value;
        DropperUtilsConfig.get().armorHudHorizontal = value;
        DropperUtilsConfig.save();
    }

    public boolean isHorizontal() { return horizontal; }

    @Override
    public void setScale(float value) {
        super.setScale(value);
        DropperUtilsConfig.get().armorHudScale = scale;
        DropperUtilsConfig.save();
    }
}
