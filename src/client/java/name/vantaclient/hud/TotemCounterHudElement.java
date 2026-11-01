package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.TotemCounterFeature;
import name.vantaclient.client.hudeditor.HudElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemCounterHudElement extends HudElement {

    public TotemCounterHudElement() {
        super(
                DropperUtilsConfig.get().totemHudX,
                DropperUtilsConfig.get().totemHudY
        );

        scale = DropperUtilsConfig.get().totemCounterScale;
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {

        if (!TotemCounterFeature.INSTANCE.isEnabled()
                && !HudEditorFeature.INSTANCE.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return;
        }

        int count = mc.player
                .getInventory()
                .countItem(Items.TOTEM_OF_UNDYING);

        ItemStack stack =
                new ItemStack(Items.TOTEM_OF_UNDYING);

        graphics.renderItem(
                stack,
                0,
                0
        );

        graphics.drawString(
                mc.font,
                String.valueOf(count),
                20,
                5,
                0xFF00FF00
        );

        if (HudEditorFeature.INSTANCE.isEnabled()) {
            graphics.fill(
                    -2,
                    -2,
                    getWidth(),
                    getHeight(),
                    0x55FFFFFF
            );
        }
    }

    @Override
    public void savePosition() {

        DropperUtilsConfig.get().totemHudX = xPercent;
        DropperUtilsConfig.get().totemHudY = yPercent;

        DropperUtilsConfig.save();
    }

    @Override
    public int getWidth() {
        return 45;
    }

    @Override
    public int getHeight() {
        return 35;
    }

    @Override
    public void setScale(float value) {

        super.setScale(value);

        DropperUtilsConfig.get().totemCounterScale = scale;
        DropperUtilsConfig.save();
    }

    public float getScale() {
        return scale;
    }
}