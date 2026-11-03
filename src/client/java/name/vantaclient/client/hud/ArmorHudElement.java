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

    private boolean horizontal;

    public ArmorHudElement() {

        super(
                DropperUtilsConfig.get().armorHudX,
                DropperUtilsConfig.get().armorHudY
        );

        scale = DropperUtilsConfig.get().armorHudScale;

        horizontal =
                DropperUtilsConfig.get().armorHudHorizontal;
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {

        if (!ArmorHudFeature.INSTANCE.isEnabled()
                && !HudEditorFeature.INSTANCE.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            renderCachedArmor(graphics, mc);
            return;
        }

        ItemStack[] armor = new ItemStack[]{
                mc.player.getItemBySlot(EquipmentSlot.HEAD),
                mc.player.getItemBySlot(EquipmentSlot.CHEST),
                mc.player.getItemBySlot(EquipmentSlot.LEGS),
                mc.player.getItemBySlot(EquipmentSlot.FEET)
        };

        if (horizontal) {
            renderHorizontal(graphics, armor);
        } else {
            renderVertical(graphics, armor);
        }
    }

    private void renderCachedArmor(GuiGraphics graphics, Minecraft mc) {
        for (int i = 0; i < 4; i++) {
            ItemStack item = HudPerformanceCache.getArmorStack(i);
            if (item.isEmpty()) continue;

            int drawX = horizontal ? i * 22 : 0;
            int drawY = horizontal ? 0 : i * 22;
            graphics.renderItem(item, drawX, drawY);

            int durability = HudPerformanceCache.getArmorDurability(i);
            int color = HudPerformanceCache.getArmorColor(i);
            if (horizontal) {
                graphics.drawString(mc.font, Integer.toString(durability),
                        drawX + 1, drawY - 10, color, false);
            } else {
                graphics.drawString(mc.font, Integer.toString(durability),
                        drawX + 25, drawY + 8, color, false);
            }

            graphics.fill(drawX, drawY + 18, drawX + 16, drawY + 20, 0xFF555555);
            int maxDamage = item.getMaxDamage();
            int filled = maxDamage <= 0 ? 0
                    : (int) (16.0f * durability / maxDamage);
            graphics.fill(drawX, drawY + 18, drawX + filled, drawY + 20, color);
        }
    }

    private void renderVertical(
            GuiGraphics graphics,
            ItemStack[] armor
    ) {

        Minecraft mc = Minecraft.getInstance();

        int offset = 0;

        for (ItemStack item : armor) {

            drawArmor(
                    graphics,
                    mc,
                    item,
                    0,
                    offset
            );

            offset += 22;
        }
    }

    private void renderHorizontal(
            GuiGraphics graphics,
            ItemStack[] armor
    ) {

        Minecraft mc = Minecraft.getInstance();

        int offset = 0;

        for (ItemStack item : armor) {

            drawArmor(
                    graphics,
                    mc,
                    item,
                    offset,
                    0
            );

            offset += 22;
        }
    }

    private void drawArmor(
            GuiGraphics graphics,
            Minecraft mc,
            ItemStack item,
            int drawX,
            int drawY
    ) {

        if (item.isEmpty()) {
            return;
        }

        int durability =
                item.getMaxDamage() - item.getDamageValue();

        float percent =
                (float) durability / item.getMaxDamage();

        int color;

        if (percent > 0.5f) {
            color = 0xFF00FF00;
        } else if (percent > 0.25f) {
            color = 0xFFFFFF00;
        } else {
            color = 0xFFFF0000;
        }

        graphics.renderItem(
                item,
                drawX,
                drawY
        );

        if (horizontal) {
            graphics.drawString(
                    mc.font,
                    durability + "",
                    drawX + 1,
                    drawY - 10,
                    color,
                    false
            );
        } else {
            graphics.drawString(
                    mc.font,
                    durability + "",
                    drawX + 25,
                    drawY + 8,
                    color,
                    false
            );
        }

        int barWidth = 16;

        int filled =
                (int) (barWidth * percent);

        graphics.fill(
                drawX,
                drawY + 18,
                drawX + barWidth,
                drawY + 20,
                0xFF555555
        );

        graphics.fill(
                drawX,
                drawY + 18,
                drawX + filled,
                drawY + 20,
                color
        );
    }

    @Override
    public int getWidth() {
        return horizontal ? 85 : 50;
    }

    @Override
    public int getHeight() {
        return horizontal ? 35 : 140;
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

    public boolean isHorizontal() {
        return horizontal;
    }

    @Override
    public void setScale(float value) {

        scale = Math.max(
                0.25f,
                Math.min(value, 3.0f)
        );

        DropperUtilsConfig.get().armorHudScale = scale;
        DropperUtilsConfig.save();
    }
}
