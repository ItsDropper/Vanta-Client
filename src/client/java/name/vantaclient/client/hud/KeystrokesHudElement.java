package name.vantaclient.client.hud;

import name.vantaclient.client.feature.KeystrokesFeature;
import name.vantaclient.client.hudeditor.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class KeystrokesHudElement extends HudElement {
    private static final int KEY_SIZE = 18;
    private static final int GAP = 3;

    public KeystrokesHudElement() {
        super(name.vantaclient.client.config.DropperUtilsConfig.get().keystrokesHudX,
                name.vantaclient.client.config.DropperUtilsConfig.get().keystrokesHudY);
        scale = name.vantaclient.client.config.DropperUtilsConfig.get().keystrokesHudScale;
    }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!KeystrokesFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        drawKey(graphics, mc, mc.options.keyUp.isDown(), "W", KEY_SIZE, 0);
        drawKey(graphics, mc, mc.options.keyLeft.isDown(), "A", 0, KEY_SIZE + GAP);
        drawKey(graphics, mc, mc.options.keyDown.isDown(), "S", KEY_SIZE + GAP, KEY_SIZE + GAP);
        drawKey(graphics, mc, mc.options.keyRight.isDown(), "D",
                (KEY_SIZE + GAP) * 2, KEY_SIZE + GAP);
        drawKey(graphics, mc, mc.options.keyJump.isDown(), "JUMP", 0,
                (KEY_SIZE + GAP) * 2);
    }

    private void drawKey(GuiGraphics graphics, Minecraft mc, boolean pressed,
                         String label, int x, int y) {
        int width = label.equals("JUMP") ? KEY_SIZE * 3 + GAP * 2 : KEY_SIZE;
        int background = pressed ? 0xFF3B82FF : 0xCC171E29;
        graphics.fill(x, y, x + width, y + KEY_SIZE, background);
        graphics.fill(x, y, x + width, y + 1, pressed ? 0xFF8CB8FF : 0xFF465164);
        graphics.drawCenteredString(mc.font, label, x + width / 2,
                y + 5, 0xFFF2F5FA);
    }

    @Override public void savePosition() {
        var config = name.vantaclient.client.config.DropperUtilsConfig.get();
        config.keystrokesHudX = xPercent;
        config.keystrokesHudY = yPercent;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override public void setScale(float value) {
        super.setScale(value);
        name.vantaclient.client.config.DropperUtilsConfig.get().keystrokesHudScale = scale;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override public int getWidth() { return KEY_SIZE * 3 + GAP * 2; }
    @Override public int getHeight() { return KEY_SIZE * 3 + GAP * 2; }
}
