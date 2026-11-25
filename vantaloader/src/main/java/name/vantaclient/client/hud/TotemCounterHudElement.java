package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.TotemCounterFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TotemCounterHudElement extends HudElement {
    private static final ItemStack TOTEM_ICON = new ItemStack(Items.TOTEM_OF_UNDYING);

    private int cachedInventoryTotems;
    private long lastInventoryTick = Long.MIN_VALUE;
    private Object cachedWorld;
    private Object cachedPlayer;
    private int lastDisplayedCount = Integer.MIN_VALUE;
    private String cachedCountText = "0";

    public TotemCounterHudElement() {
        super(DropperUtilsConfig.get().totemHudX, DropperUtilsConfig.get().totemHudY);
        scale = DropperUtilsConfig.get().totemCounterScale;
    }

    @Override
    public boolean shouldRender() {
        return TotemCounterFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (!TotemCounterFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        graphics.renderItem(TOTEM_ICON, 0, 0);
        graphics.drawString(mc.font, getCountText(mc), 20, 5, 0xFF00FF00);
    }

    private int getCount(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            cachedInventoryTotems = 0;
            lastInventoryTick = Long.MIN_VALUE;
            cachedWorld = null;
            cachedPlayer = null;
            return 0;
        }
        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            return HudPerformanceCache.getTotemCount();
        }

        long gameTime = mc.level.getGameTime();
        if (shouldRefreshInventoryCount(lastInventoryTick, gameTime,
                cachedWorld, mc.level, cachedPlayer, mc.player)) {
            cachedInventoryTotems = mc.player.getInventory().countItem(Items.TOTEM_OF_UNDYING);
            lastInventoryTick = gameTime;
            cachedWorld = mc.level;
            cachedPlayer = mc.player;
        }
        return cachedInventoryTotems;
    }

    private String getCountText(Minecraft mc) {
        int count = getCount(mc);
        if (count != lastDisplayedCount) {
            lastDisplayedCount = count;
            cachedCountText = Integer.toString(count);
        }
        return cachedCountText;
    }

    public static boolean shouldRefreshInventoryCount(long lastGameTime, long currentGameTime,
                                                       Object previousWorld, Object currentWorld,
                                                       Object previousPlayer, Object currentPlayer) {
        return lastGameTime != currentGameTime
                || previousWorld != currentWorld
                || previousPlayer != currentPlayer;
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(16, 20 + mc.font.width(getCountText(mc)));
    }

    @Override
    public int getHeight() {
        return Math.max(16, 5 + Minecraft.getInstance().font.lineHeight);
    }

    @Override
    public void savePosition() {
        DropperUtilsConfig.get().totemHudX = xPercent;
        DropperUtilsConfig.get().totemHudY = yPercent;
        DropperUtilsConfig.save();
    }

    @Override
    public void setScale(float value) {
        super.setScale(value);
        DropperUtilsConfig.get().totemCounterScale = scale;
        DropperUtilsConfig.save();
    }

    public float getScale() { return scale; }
}
