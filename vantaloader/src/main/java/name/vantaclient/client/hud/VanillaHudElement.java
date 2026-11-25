package name.vantaclient.client.hud;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Editor representation and persisted position for one vanilla HUD component. */
public final class VanillaHudElement extends HudElement {
    public enum Part {
        HOTBAR("Hotbar", 182, 22),
        CROSSHAIR("Crosshair", 15, 15),
        HEALTH("Health", 182, 10),
        ARMOR("Armor", 182, 10),
        FOOD("Hunger", 91, 10),
        AIR("Air", 91, 10),
        EFFECTS("Status Effects", 124, 80),
        BOSS_BAR("Boss Bar", 182, 20),
        MOUNT_HEALTH("Mount Health", 182, 18),
        SCOREBOARD("Scoreboard", 160, 180),
        EXPERIENCE("Experience Bar", 182, 12),
        JUMP_BAR("Vehicle Jump Bar", 182, 12);

        private final String label;
        private final int width;
        private final int height;

        Part(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        public String label() { return label; }
        public int width() { return width; }
        public int height() { return height; }

        public int defaultX(int screenWidth) {
            return switch (this) {
                case HOTBAR, HEALTH, ARMOR, BOSS_BAR -> (screenWidth - width) / 2;
                case CROSSHAIR -> screenWidth / 2 - width / 2;
                case FOOD, AIR -> screenWidth / 2 + 10;
                case EFFECTS -> screenWidth - width - 4;
                case SCOREBOARD -> screenWidth - width - 8;
                case MOUNT_HEALTH, EXPERIENCE, JUMP_BAR -> (screenWidth - width) / 2;
            };
        }

        public int defaultY(int screenHeight) {
            return switch (this) {
                case HOTBAR -> screenHeight - 22;
                case CROSSHAIR -> screenHeight / 2 - height / 2;
                case HEALTH, FOOD -> screenHeight - 39;
                case ARMOR -> screenHeight - 49;
                case AIR -> screenHeight - 59;
                case EFFECTS -> 4;
                case BOSS_BAR -> 10;
                case MOUNT_HEALTH -> screenHeight - 60;
                case SCOREBOARD -> (screenHeight - height) / 2;
                case EXPERIENCE, JUMP_BAR -> screenHeight - 31;
            };
        }
    }

    private final Part part;
    private final String backgroundId;

    public VanillaHudElement(Part part) {
        this(part, position(part));
    }

    private VanillaHudElement(Part part, VantaClientPreferences.HudPosition position) {
        super(position.x, position.y);
        this.part = part;
        this.backgroundId = "vanilla." + part.name();
        this.scale = position.scale;
    }

    public Part getPart() {
        return part;
    }

    public String getDisplayName() {
        return part.label();
    }

    @Override
    public String getBackgroundId() {
        return backgroundId;
    }

    public static VantaClientPreferences.HudPosition getPosition(Part part) {
        return position(part);
    }

    private static VantaClientPreferences.HudPosition position(Part part) {
        var preferences = VantaClientPreferences.get();
        var saved = preferences.vanillaHudPositions.get(part.name());
        if (saved != null) return saved;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(1, mc.getWindow().getGuiScaledHeight());
        saved = new VantaClientPreferences.HudPosition(
                part.defaultX(screenWidth) / (float) screenWidth,
                part.defaultY(screenHeight) / (float) screenHeight,
                1.0f);
        preferences.vanillaHudPositions.put(part.name(), saved);
        return saved;
    }

    @Override
    protected boolean usesGuiScaleCompensation() {
        // These are editor proxies for Minecraft-owned HUD elements. Their real
        // renderers already apply the game's GUI scale and must not be compensated twice.
        return false;
    }

    @Override
    public boolean shouldRender() {
        return HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        int width = getWidth();
        int height = getHeight();
        // Quiet footprint: a faint tint and hairline frame, so the editor reads as a layout, not a mock-up.
        graphics.fill(0, 0, width, height, 0x1C243142);
        graphics.fill(0, 0, width, 1, 0x389BAEC8);
        graphics.fill(0, height - 1, width, height, 0x389BAEC8);
        graphics.fill(0, 0, 1, height, 0x389BAEC8);
        graphics.fill(width - 1, 0, width, height, 0x389BAEC8);
        if (part == Part.CROSSHAIR) {
            int centerX = width / 2;
            int centerY = height / 2;
            graphics.fill(centerX - 5, centerY, centerX + 5, centerY + 1, 0xC8FFFFFF);
            graphics.fill(centerX, centerY - 5, centerX + 1, centerY + 5, 0xC8FFFFFF);
        }
    }

    @Override
    public int getWidth() {
        return part.width();
    }

    @Override
    public int getHeight() {
        return part.height();
    }

    @Override
    public void savePosition() {
        var saved = position(part);
        saved.x = xPercent;
        saved.y = yPercent;
        VantaClientPreferences.save();
    }

    @Override
    public void setScale(float value) {
        super.setScale(value);
        position(part).scale = scale;
        VantaClientPreferences.save();
    }
}
