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
        SCOREBOARD("Scoreboard", 160, 180);

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
                case MOUNT_HEALTH -> (screenWidth - width) / 2;
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
            };
        }
    }

    private final Part part;

    public VanillaHudElement(Part part) {
        this(part, position(part));
    }

    private VanillaHudElement(Part part, VantaClientPreferences.HudPosition position) {
        super(position.x, position.y);
        this.part = part;
        this.scale = position.scale;
    }

    public Part getPart() {
        return part;
    }

    public String getDisplayName() {
        return part.label();
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
    public boolean shouldRender() {
        return HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        int width = getWidth();
        int height = getHeight();
        graphics.fill(0, 0, width, height, 0x30243142);
        graphics.fill(0, 0, width, 1, 0xAA9BAEC8);
        graphics.fill(0, height - 1, width, height, 0xAA9BAEC8);

        switch (part) {
            case HOTBAR -> {
                for (int i = 0; i < 9; i++) {
                    int x = 2 + i * 20;
                    graphics.fill(x, 2, x + 18, 20, 0x665D6C82);
                    graphics.fill(x, 2, x + 18, 3, 0xFF9BAEC8);
                }
            }
            case CROSSHAIR -> {
                int centerX = width / 2;
                int centerY = height / 2;
                graphics.fill(centerX - 5, centerY, centerX + 5, centerY + 1, 0xFFFFFFFF);
                graphics.fill(centerX, centerY - 5, centerX + 1, centerY + 5, 0xFFFFFFFF);
            }
            case HEALTH, ARMOR, FOOD, AIR -> {
                int count = part == Part.FOOD || part == Part.AIR ? 10 : 10;
                int cellWidth = part == Part.FOOD || part == Part.AIR ? 7 : 8;
                for (int i = 0; i < count; i++) {
                    int x = i * cellWidth;
                    graphics.fill(x, 2, x + cellWidth - 1, height - 2, 0xAA9BAEC8);
                }
            }
            case EFFECTS -> {
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 5; column++) {
                        int x = column * 24 + 2;
                        int y = row * 24 + 2;
                        graphics.fill(x, y, x + 20, y + 20, 0x775D6C82);
                    }
                }
            }
            case BOSS_BAR -> {
                graphics.fill(2, 4, width - 2, 8, 0xFF6A2837);
                graphics.fill(2, 4, width - 28, 8, 0xFFB64D66);
                graphics.fill(2, 12, width - 2, 16, 0xFF6A2837);
            }
            case MOUNT_HEALTH -> {
                for (int i = 0; i < 10; i++) {
                    int x = 2 + i * 18;
                    graphics.fill(x, 2, x + 16, 10, 0xAA9BAEC8);
                    graphics.fill(x, 12, x + 16, 14, 0x665D6C82);
                }
            }
            case SCOREBOARD -> {
                graphics.fill(2, 2, width - 2, height - 2, 0x88465368);
                graphics.fill(8, 10, width - 8, 13, 0xFFD0D9E6);
                for (int row = 0; row < 12; row++) {
                    int y = 23 + row * 12;
                    graphics.fill(8, y, width - 8, y + 5, 0x889BAEC8);
                }
            }
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
